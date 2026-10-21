package io.zahori.framework.core;

import static org.assertj.core.api.Assertions.assertThat;

import io.zahori.framework.core.BrowserStackLocalTunnel.Settings;
import io.zahori.framework.core.BrowserStackLocalTunnel.TunnelLog;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BooleanSupplier;

/** Binarios BrowserStack Local falsos que se comportan como el real y cuentan los daemons vivos. */
final class FakeBinaries {

    static final long CONNECT_MILLIS = 100;
    static final String FAILURE = "Could not connect to www.browserstack.com!";

    enum Behaviour {
        CONNECTS, CONNECTS_IMMEDIATELY, FAILS, FAILS_WITH_DAEMON_UP, CONNECTS_BUT_DEAD, BLOCKS, BLOCKS_WITH_DAEMON_UP,
        BLOCKS_THEN_FAILS, BLOCKS_THEN_ERROR, THROWS_ERROR, FAILS_WITHOUT_MESSAGE
    }

    final AtomicInteger created = new AtomicInteger();
    final AtomicInteger connected = new AtomicInteger();
    final AtomicInteger alive = new AtomicInteger();
    final AtomicInteger stops = new AtomicInteger();
    final AtomicInteger stopsByIdentifier = new AtomicInteger();
    final AtomicInteger aborts = new AtomicInteger();
    final AtomicInteger started = new AtomicInteger();
    final AtomicInteger aliveChecks = new AtomicInteger();
    final AtomicInteger hungStops = new AtomicInteger();
    final List<FakeTunnelProcess> all = Collections.synchronizedList(new ArrayList<>());
    // El ultimo comportamiento se repite para los binarios siguientes
    private volatile List<Behaviour> script = List.of(Behaviour.CONNECTS);
    volatile CountDownLatch release = new CountDownLatch(1);
    volatile boolean stopFails;
    volatile boolean stopHangs;
    volatile boolean abortIgnored;
    volatile int factoryFailures;
    volatile String unreachableKey;
    volatile String hangingKey;

    void script(Behaviour... behaviours) {
        script = List.of(behaviours);
    }

    LocalTunnelProcess next() {
        if (factoryFailures > 0) {
            factoryFailures--;
            throw new IllegalStateException("no binary");
        }
        int number = created.incrementAndGet();
        List<Behaviour> current = script;
        FakeTunnelProcess process = new FakeTunnelProcess(this, number, current.get(Math.min(number, current.size()) - 1));
        all.add(process);
        return process;
    }

    void killAll() {
        all.forEach(FakeTunnelProcess::die);
    }

    void releaseAll() {
        release.countDown();
        stopHangs = false;
        all.forEach(process -> process.aborted.countDown());
    }

    void awaitStarted(int count) {
        awaitUntil(() -> started.get() >= count, "binarios arrancados: " + count);
    }

    static void awaitUntil(BooleanSupplier condition, String what) {
        long deadline = System.currentTimeMillis() + 5_000;
        while (!condition.getAsBoolean() && System.currentTimeMillis() < deadline) {
            try {
                Thread.sleep(5);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
        assertThat(condition.getAsBoolean()).describedAs(what).isTrue();
    }

    static <T> List<T> runAll(List<Callable<T>> tasks) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(tasks.size());
        try {
            List<T> results = new ArrayList<>();
            for (Future<T> result : pool.invokeAll(tasks, 60, TimeUnit.SECONDS)) {
                results.add(result.get());
            }
            return results;
        } finally {
            pool.shutdownNow();
        }
    }

    static Map<String, String> args(String accessKey) {
        Map<String, String> args = new HashMap<>();
        args.put("key", accessKey);
        args.put("forcelocal", "true");
        return args;
    }

    /** Settings legibles en los tests: por defecto 3 intentos, sin enfriamiento y plazos holgados. */
    static final class SettingsBuilder {
        private int maxAttempts = 3;
        private Duration startTimeout = Duration.ofSeconds(5);
        private Duration stopTimeout = Duration.ofSeconds(2);
        private Duration retryBackoff = Duration.ofMillis(150);
        private Duration waitTimeout = Duration.ofSeconds(30);
        private Duration shutdownTimeout = Duration.ofSeconds(1);
        private Duration failureCooldown = Duration.ZERO;

        SettingsBuilder maxAttempts(int value) {
            maxAttempts = value;
            return this;
        }

        SettingsBuilder startTimeout(Duration value) {
            startTimeout = value;
            return this;
        }

        SettingsBuilder stopTimeout(Duration value) {
            stopTimeout = value;
            return this;
        }

        SettingsBuilder waitTimeout(Duration value) {
            waitTimeout = value;
            return this;
        }

        SettingsBuilder shutdownTimeout(Duration value) {
            shutdownTimeout = value;
            return this;
        }

        SettingsBuilder failureCooldown(Duration value) {
            failureCooldown = value;
            return this;
        }

        Settings build() {
            return new Settings(maxAttempts, startTimeout, stopTimeout, retryBackoff, waitTimeout, shutdownTimeout, failureCooldown);
        }
    }

    /** Traza en memoria de lo que el tunel deja en las evidencias del caso. */
    static final class RecordingLog implements TunnelLog {
        final List<String> infos = Collections.synchronizedList(new ArrayList<>());
        final List<String> warnings = Collections.synchronizedList(new ArrayList<>());
        final List<String> errors = Collections.synchronizedList(new ArrayList<>());

        @Override
        public void info(String message) {
            infos.add(message);
        }

        @Override
        public void warn(String message) {
            warnings.add(message);
        }

        @Override
        public void error(String message) {
            errors.add(message);
        }
    }

    /**
     * Como el binario real: tarda en conectar; sin conectar no esta vivo ni se deja parar por pid,
     * pero si por identificador; abortado, el lanzador muere y no levanta daemon.
     */
    static final class FakeTunnelProcess implements LocalTunnelProcess {
        private final FakeBinaries binaries;
        private final int number;
        private final Behaviour behaviour;
        final CountDownLatch aborted = new CountDownLatch(1);
        volatile Map<String, String> startArgs;
        volatile boolean startedOnDaemonThread;
        private boolean daemonUp;
        private boolean connected;
        private boolean reportsAlive = true;

        FakeTunnelProcess(FakeBinaries binaries, int number, Behaviour behaviour) {
            this.binaries = binaries;
            this.number = number;
            this.behaviour = behaviour;
        }

        @Override
        public void start(Map<String, String> args) throws Exception {
            startArgs = new HashMap<>(args);
            startedOnDaemonThread = Thread.currentThread().isDaemon();
            binaries.started.incrementAndGet();
            Behaviour effective = effectiveBehaviour(args.get("key"));
            switch (effective) {
                case BLOCKS, BLOCKS_THEN_FAILS, BLOCKS_THEN_ERROR -> blockUntilReleasedOrAborted();
                case BLOCKS_WITH_DAEMON_UP -> {
                    upDaemon();
                    blockUntilReleasedOrAborted();
                }
                case THROWS_ERROR -> throw new LinkageError("broken binary");
                case CONNECTS_IMMEDIATELY -> { }
                default -> Thread.sleep(CONNECT_MILLIS);
            }
            switch (effective) {
                case FAILS, BLOCKS_THEN_FAILS -> throw new IllegalStateException(FAILURE);
                case FAILS_WITHOUT_MESSAGE -> throw new NullPointerException();
                case BLOCKS_THEN_ERROR -> throw new LinkageError("broken binary");
                case FAILS_WITH_DAEMON_UP -> {
                    upDaemon();
                    throw new IllegalStateException("Could not parse the binary output");
                }
                case CONNECTS_BUT_DEAD -> {
                    upDaemon();
                    synchronized (this) {
                        connected = true;
                        reportsAlive = false;
                    }
                }
                default -> {
                    if (effective != Behaviour.BLOCKS_WITH_DAEMON_UP) {
                        upDaemon();
                    }
                    synchronized (this) {
                        connected = true;
                    }
                    binaries.connected.incrementAndGet();
                }
            }
        }

        private Behaviour effectiveBehaviour(String key) {
            if (key != null && key.equals(binaries.unreachableKey)) {
                return Behaviour.FAILS;
            }
            if (key != null && key.equals(binaries.hangingKey)) {
                return Behaviour.BLOCKS;
            }
            return behaviour;
        }

        private void blockUntilReleasedOrAborted() throws InterruptedException {
            while (!binaries.release.await(10, TimeUnit.MILLISECONDS)) {
                if (aborted.getCount() == 0) {
                    throw new IllegalStateException("launcher killed");
                }
            }
        }

        private synchronized void upDaemon() {
            daemonUp = true;
            binaries.alive.incrementAndGet();
        }

        @Override
        public synchronized boolean isAlive() {
            binaries.aliveChecks.incrementAndGet();
            return connected && daemonUp && reportsAlive;
        }

        @Override
        public synchronized long pid() {
            return connected ? 1000 + number : 0;
        }

        @Override
        public void stop() throws Exception {
            hangIfAsked();
            synchronized (this) {
                if (binaries.stopFails) {
                    throw new IllegalStateException("daemon not responding");
                }
                if (!connected) {
                    return;
                }
                binaries.stops.incrementAndGet();
                downDaemon();
                connected = false;
            }
        }

        @Override
        public void stopByIdentifier(Map<String, String> args) throws Exception {
            hangIfAsked();
            synchronized (this) {
                binaries.stopsByIdentifier.incrementAndGet();
                if (binaries.stopFails) {
                    throw new IllegalStateException("daemon not responding");
                }
                downDaemon();
                connected = false;
            }
        }

        // Como Process.waitFor del real: con la interrupcion puesta no espera a que pare
        private void hangIfAsked() throws InterruptedException {
            if (Thread.currentThread().isInterrupted()) {
                throw new InterruptedException("stop interrupted");
            }
            if (binaries.stopHangs) {
                binaries.hungStops.incrementAndGet();
            }
            while (binaries.stopHangs) {
                if (aborted.await(10, TimeUnit.MILLISECONDS)) {
                    throw new IllegalStateException("stop killed");
                }
            }
        }

        @Override
        public void abort() {
            binaries.aborts.incrementAndGet();
            if (!binaries.abortIgnored) {
                aborted.countDown();
            }
        }

        synchronized void die() {
            downDaemon();
        }

        private void downDaemon() {
            if (daemonUp) {
                daemonUp = false;
                binaries.alive.decrementAndGet();
            }
        }
    }
}
