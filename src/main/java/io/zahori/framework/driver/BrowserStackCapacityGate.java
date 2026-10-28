package io.zahori.framework.driver;

import java.io.IOException;
import java.time.Duration;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.LongSupplier;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Puerta de admision por el plan de BrowserStack: una peticion de sesion solo pasa si el plan de la cuenta tiene ahora
 * una plaza libre, y si no espera sin ocupar la cola. El maximo no se fija en el proceso: sale del plan en cada momento,
 * con lo que usen otros procesos u otras personas de la misma cuenta.
 */
final class BrowserStackCapacityGate {

    private static final Logger LOG = LogManager.getLogger(BrowserStackCapacityGate.class);

    /** Producto de BrowserStack: cada uno tiene su propio plan de sesiones en paralelo. */
    enum Product {
        AUTOMATE("https://api.browserstack.com/automate/plan.json"),
        APP_AUTOMATE("https://api-cloud.browserstack.com/app-automate/plan.json");

        private final String planUrl;

        Product(String planUrl) {
            this.planUrl = planUrl;
        }

        String planUrl() {
            return planUrl;
        }
    }

    /** Estado del plan: sesiones en marcha, tope en paralelo y sesiones en cola. */
    record Plan(int running, int max, int queued) {
        int free() {
            return max - running - queued;
        }
    }

    /** De donde sale el plan. */
    interface PlanSource {
        Plan read(Product product, String user, String key) throws IOException, InterruptedException;
    }

    /** Pausa entre sondeos. */
    interface Sleeper {
        void sleep(Duration duration) throws InterruptedException;
    }

    /**
     * Tiempos de la puerta. El plan se relee como mucho cada {@code planTtl}; una admision cuenta como plaza tomada hasta
     * {@code settle}, el tiempo que tarda el plan en mostrar la sesion; tras un fallo de la API se deja pasar sin
     * consultarla durante {@code failureCooldown}.
     */
    record Settings(Duration planTtl, Duration settle, Duration poll, Duration failureCooldown) {

        static final Settings DEFAULTS = new Settings(Duration.ofSeconds(3), Duration.ofSeconds(8), Duration.ofSeconds(3),
                Duration.ofSeconds(30));

        Settings {
            Objects.requireNonNull(planTtl, "planTtl");
            Objects.requireNonNull(settle, "settle");
            Objects.requireNonNull(poll, "poll");
            Objects.requireNonNull(failureCooldown, "failureCooldown");
        }
    }

    private record Account(Product product, String user) {
    }

    private static final class State {
        private Plan plan;
        private long planAtNanos;
        private long failedAtNanos;
        private boolean failed;
        private final Deque<Long> admissions = new ArrayDeque<>();
    }

    private static final class Holder {
        private static final BrowserStackCapacityGate INSTANCE = new BrowserStackCapacityGate(new BrowserStackPlanClient(),
                System::nanoTime, duration -> Thread.sleep(duration.toMillis()), Settings.DEFAULTS);
    }

    private final PlanSource source;
    private final LongSupplier nanoClock;
    private final Sleeper sleeper;
    private final Settings settings;
    private final Map<Account, State> states = new ConcurrentHashMap<>();

    BrowserStackCapacityGate(PlanSource source, LongSupplier nanoClock, Sleeper sleeper, Settings settings) {
        this.source = Objects.requireNonNull(source, "source");
        this.nanoClock = Objects.requireNonNull(nanoClock, "nanoClock");
        this.sleeper = Objects.requireNonNull(sleeper, "sleeper");
        this.settings = Objects.requireNonNull(settings, "settings");
    }

    /** La puerta de este proceso: todos los casos comparten la cuenta, asi que comparten el recuento. */
    static BrowserStackCapacityGate shared() {
        return Holder.INSTANCE;
    }

    /**
     * Espera a que el plan tenga una plaza libre y la toma. Si no se puede leer el plan, o pasa {@code maxWait} sin plaza,
     * deja pasar: ni una caida de la API del plan ni una cuenta ocupada del todo pueden dejar un caso esperando sin fin.
     */
    void admit(Product product, String user, String key, Duration maxWait) throws InterruptedException {
        State state = states.computeIfAbsent(new Account(product, user), account -> new State());
        long deadline = nanoClock.getAsLong() + maxWait.toNanos();
        boolean announced = false;
        while (true) {
            synchronized (state) {
                Boolean verdict = tryToTake(state, product, user, key);
                if (verdict == null || verdict) {
                    return;
                }
            }
            long remaining = deadline - nanoClock.getAsLong();
            if (remaining <= 0) {
                LOG.warn("BrowserStack {} plan of {} has no free slot after {} s: requesting the session anyway", product, user,
                        maxWait.toSeconds());
                return;
            }
            if (!announced) {
                LOG.info("BrowserStack {} plan of {} is full: waiting for a free slot", product, user);
                announced = true;
            }
            sleeper.sleep(Duration.ofNanos(Math.min(settings.poll().toNanos(), remaining)));
        }
    }

    /** @return true si toma la plaza, false si no hay, null si no se puede saber (se deja pasar). */
    private Boolean tryToTake(State state, Product product, String user, String key) throws InterruptedException {
        long now = nanoClock.getAsLong();
        if (state.failed && now - state.failedAtNanos < settings.failureCooldown().toNanos()) {
            return null;
        }
        if (state.plan == null || now - state.planAtNanos >= settings.planTtl().toNanos()) {
            try {
                Plan read = source.read(product, user, key);
                if (read == null || read.max() <= 0) {
                    throw new IOException("unusable plan: " + read);
                }
                state.plan = read;
                state.planAtNanos = now;
                state.failed = false;
            } catch (IOException | RuntimeException e) {
                state.failed = true;
                state.failedAtNanos = now;
                state.plan = null;
                LOG.warn("BrowserStack {} plan of {} could not be read ({}): requests go through without waiting for a slot",
                        product, user, e.getMessage());
                return null;
            }
        }
        while (!state.admissions.isEmpty() && now - state.admissions.peekFirst() >= settings.settle().toNanos()) {
            state.admissions.pollFirst();
        }
        if (state.plan.free() - state.admissions.size() > 0) {
            state.admissions.addLast(now);
            return true;
        }
        return false;
    }
}
