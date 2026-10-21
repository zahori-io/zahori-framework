package io.zahori.framework.core;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Consumer;
import java.util.function.LongSupplier;
import java.util.function.Supplier;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/** Tunel BrowserStack Local compartido por todos los casos del proceso con la misma configuracion. */
final class BrowserStackLocalTunnel {

    private static final Logger LOG = LogManager.getLogger(BrowserStackLocalTunnel.class);

    static final String LOCAL_IDENTIFIER_ARG = "localIdentifier";

    static final Sleeper THREAD_SLEEP = duration -> Thread.sleep(duration.toMillis());

    /** Intentos y plazos del tunel. */
    record Settings(int maxAttempts, Duration startTimeout, Duration stopTimeout, Duration retryBackoff,
            Duration waitTimeout, Duration shutdownTimeout, Duration failureCooldown) {

        // Los que esperan cubren todos los intentos de quien arranca; el hook cabe en los 90 s de systemd
        static final Settings DEFAULTS = new Settings(5, Duration.ofMinutes(2), Duration.ofSeconds(20),
                Duration.ofSeconds(5), Duration.ofMinutes(15), Duration.ofSeconds(10), Duration.ofMinutes(1));

        Settings {
            if (maxAttempts < 1) {
                throw new IllegalArgumentException("maxAttempts must be at least 1: " + maxAttempts);
            }
            requireNotNegative(startTimeout, "startTimeout");
            requireNotNegative(stopTimeout, "stopTimeout");
            requireNotNegative(retryBackoff, "retryBackoff");
            requireNotNegative(waitTimeout, "waitTimeout");
            requireNotNegative(shutdownTimeout, "shutdownTimeout");
            requireNotNegative(failureCooldown, "failureCooldown");
        }

        private static void requireNotNegative(Duration duration, String name) {
            Objects.requireNonNull(duration, name);
            if (duration.isNegative()) {
                throw new IllegalArgumentException(name + " must not be negative: " + duration);
            }
        }
    }

    /** Traza del caso que pide el tunel. */
    interface TunnelLog {
        void info(String message);

        void warn(String message);

        void error(String message);
    }

    /** Pausa entre intentos. */
    interface Sleeper {
        void sleep(Duration duration) throws InterruptedException;
    }

    /** El caso no puede contar con el tunel; el mensaje dice por que. */
    static final class TunnelUnavailableException extends Exception {
        private static final long serialVersionUID = 1L;

        TunnelUnavailableException(String message) {
            super(message);
        }
    }

    private static final TunnelLog PROCESS_LOG = new TunnelLog() {
        @Override
        public void info(String message) {
            LOG.info(message);
        }

        @Override
        public void warn(String message) {
            LOG.warn(message);
        }

        @Override
        public void error(String message) {
            LOG.error(message);
        }
    };

    private record Starting(LocalTunnelProcess process, Map<String, String> args) {
    }

    /** Fallo de un arranque; se compara por identidad, no por mensaje: el binario repite el texto. */
    private static final class Failure {
        private final String message;
        private final long atNanos;

        Failure(String message, long atNanos) {
            this.message = message;
            this.atNanos = atNanos;
        }
    }

    private final String identifier;
    private final Supplier<LocalTunnelProcess> processFactory;
    private final Settings settings;
    private final Consumer<Thread> shutdownHooks;
    private final Sleeper sleeper;
    private final LongSupplier nanoClock;
    private final ReentrantLock lock = new ReentrantLock();
    private final AtomicReference<LocalTunnelProcess> running = new AtomicReference<>();

    // Se escriben con el lock (closed tambien desde el hook); se leen tambien sin el
    private volatile Starting starting;
    private volatile Failure lastFailure;
    private volatile boolean closed;
    private boolean shutdownHookRegistered;

    BrowserStackLocalTunnel(String identifier, Supplier<LocalTunnelProcess> processFactory) {
        this(identifier, processFactory, Settings.DEFAULTS, Runtime.getRuntime()::addShutdownHook,
                THREAD_SLEEP, System::nanoTime);
    }

    BrowserStackLocalTunnel(String identifier, Supplier<LocalTunnelProcess> processFactory, Settings settings,
            Consumer<Thread> shutdownHooks, Sleeper sleeper, LongSupplier nanoClock) {
        this.identifier = Objects.requireNonNull(identifier, "identifier");
        this.processFactory = Objects.requireNonNull(processFactory, "processFactory");
        this.settings = Objects.requireNonNull(settings, "settings");
        this.shutdownHooks = Objects.requireNonNull(shutdownHooks, "shutdownHooks");
        this.sleeper = Objects.requireNonNull(sleeper, "sleeper");
        this.nanoClock = Objects.requireNonNull(nanoClock, "nanoClock");
    }

    String identifier() {
        return identifier;
    }

    /** Identificador del tunel vivo; si no hay, lo levanta. */
    String acquire(Map<String, String> args, TunnelLog log) throws TunnelUnavailableException {
        // Con el tunel vivo no se toca el lock: los casos no se esperan entre si
        if (isRunning()) {
            return reused(log);
        }
        Failure failureBeforeWaiting = lastFailure;
        if (isCoolingDown(failureBeforeWaiting)) {
            throw new TunnelUnavailableException(unavailable("last start failed less than "
                    + describe(settings.failureCooldown()) + " ago: " + failureBeforeWaiting.message));
        }
        if (lock.isLocked()) {
            log.info("BrowserStack local connection with id " + identifier + " being started by another case: waiting");
        }
        lockWithinWaitTimeout();
        try {
            if (isRunning()) {
                return reused(log);
            }
            // Fallo el arranque que esperaba: repetirlo ahora solo alargaria la espera de todos
            Failure failure = lastFailure;
            if (failure != failureBeforeWaiting) {
                throw new TunnelUnavailableException(failure.message);
            }
            return start(args, log);
        } finally {
            lock.unlock();
        }
    }

    boolean isRunning() {
        LocalTunnelProcess current = running.get();
        return current != null && current.isAlive();
    }

    private String reused(TunnelLog log) {
        log.info("BrowserStack local connection reused with id " + identifier);
        return identifier;
    }

    /** Casos esperando a que otro levante el tunel. */
    int waitingCases() {
        return lock.getQueueLength();
    }

    /** Hook de la JVM: para el tunel y el que se este levantando, y no deja levantar mas. */
    void shutdown(TunnelLog log) {
        // closed antes de leer running: start publica running antes de releer closed (Dekker)
        closed = true;
        boolean locked = false;
        boolean interrupted = Thread.interrupted();
        try {
            locked = lock.tryLock(settings.shutdownTimeout().toMillis(), TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            interrupted = true;
        }
        try {
            Starting inFlight = starting;
            if (inFlight != null) {
                inFlight.process().abort();
                stopCandidate(inFlight.process(), inFlight.args(), log);
                log.info("BrowserStack local connection being started with id " + identifier + " stopped on shutdown");
            }
            LocalTunnelProcess current = running.getAndSet(null);
            if (current != null) {
                stopQuietly(current, log);
                log.info("BrowserStack local connection with id " + identifier + " stopped on shutdown");
            }
        } finally {
            if (locked) {
                lock.unlock();
            }
            if (interrupted) {
                Thread.currentThread().interrupt();
            }
        }
    }

    private boolean isCoolingDown(Failure failure) {
        return failure != null && nanoClock.getAsLong() - failure.atNanos < settings.failureCooldown().toNanos();
    }

    private void lockWithinWaitTimeout() throws TunnelUnavailableException {
        try {
            if (!lock.tryLock(settings.waitTimeout().toMillis(), TimeUnit.MILLISECONDS)) {
                throw new TunnelUnavailableException(unavailable("another case has been starting it for more than "
                        + describe(settings.waitTimeout())));
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new TunnelUnavailableException(unavailable("interrupted while waiting for it"));
        }
    }

    private String start(Map<String, String> requestedArgs, TunnelLog log) throws TunnelUnavailableException {
        registerShutdownHookOnce();
        Map<String, String> args = new HashMap<>(requestedArgs);
        args.put(LOCAL_IDENTIFIER_ARG, identifier);
        for (int attempt = 1;; attempt++) {
            throwIfClosed();
            // Solo se llega con el anterior muerto, pero se le manda parar: su daemon puede seguir registrado
            stopCurrent(log);
            long startNanos = nanoClock.getAsLong();
            LocalTunnelProcess candidate;
            try {
                candidate = launchOnce(args, log);
            } catch (TunnelUnavailableException e) {
                throw e;
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                // No es un fallo del tunel: los que esperan lo intentaran por su cuenta
                throw new TunnelUnavailableException(unavailable("interrupted while starting it"));
            } catch (Exception e) {
                String message = "BrowserStack local connection failed (id " + identifier + ") [attempt " + attempt + "/"
                        + settings.maxAttempts() + "]: " + reasonOf(e);
                LOG.warn(message, e);
                if (attempt >= settings.maxAttempts()) {
                    lastFailure = new Failure(message, nanoClock.getAsLong());
                    throw new TunnelUnavailableException(message);
                }
                log.warn(message);
                pauseBeforeRetry();
                continue;
            }
            running.set(candidate);
            // Releer closed despues de publicar: o el hook ve el tunel o el arranque ve el cierre
            if (closed) {
                if (running.compareAndSet(candidate, null)) {
                    stopCandidate(candidate, args, log);
                }
                throwIfClosed();
            }
            log.info("BrowserStack local connection started with id " + identifier + " (pid " + candidate.pid() + ", "
                    + TimeUnit.NANOSECONDS.toMillis(nanoClock.getAsLong() - startNanos) + " ms)");
            return identifier;
        }
    }

    /** Un intento: si no llega a conectar, su binario se para aqui pase lo que pase (Error incluido). */
    private LocalTunnelProcess launchOnce(Map<String, String> args, TunnelLog log) throws Exception {
        LocalTunnelProcess candidate = null;
        boolean connected = false;
        try {
            candidate = processFactory.get();
            starting = new Starting(candidate, args);
            // Releer closed tras publicar starting: o el hook ve el intento o el intento ve el cierre
            throwIfClosed();
            LocalTunnelProcess launching = candidate;
            runWithin(settings.startTimeout(), "not connected", () -> launching.start(args), launching);
            if (!candidate.isAlive()) {
                throw new IllegalStateException("Connection is not running");
            }
            connected = true;
            return candidate;
        } finally {
            starting = null;
            if (!connected) {
                stopCandidate(candidate, args, log);
            }
        }
    }

    private static String reasonOf(Exception e) {
        return e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
    }

    private void throwIfClosed() throws TunnelUnavailableException {
        if (closed) {
            throw new TunnelUnavailableException(unavailable("the process is shutting down"));
        }
    }

    private void pauseBeforeRetry() throws TunnelUnavailableException {
        try {
            sleeper.sleep(settings.retryBackoff());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new TunnelUnavailableException(unavailable("interrupted while starting it"));
        }
    }

    private void registerShutdownHookOnce() {
        if (shutdownHookRegistered) {
            return;
        }
        try {
            shutdownHooks.accept(new Thread(() -> shutdown(PROCESS_LOG), "browserstack-local-shutdown"));
            shutdownHookRegistered = true;
        } catch (IllegalStateException alreadyShuttingDown) {
            closed = true;
        }
    }

    private void stopCurrent(TunnelLog log) {
        LocalTunnelProcess current = running.getAndSet(null);
        if (current != null) {
            stopQuietly(current, log);
            log.info("BrowserStack local connection with id " + identifier + " stopped");
        }
    }

    private void stopQuietly(LocalTunnelProcess process, TunnelLog log) {
        stopWithin(process, process::stop, log);
    }

    /** Un intento que no ha llegado a dar servicio se para por identificador: puede no tener pid. */
    private void stopCandidate(LocalTunnelProcess candidate, Map<String, String> args, TunnelLog log) {
        if (candidate != null) {
            stopWithin(candidate, () -> candidate.stopByIdentifier(args), log);
        }
    }

    // La parada corre en otro hilo con plazo: ni un daemon colgado ni una interrupcion la dejan a medias
    private void stopWithin(LocalTunnelProcess process, ThrowingAction stop, TunnelLog log) {
        boolean interrupted = Thread.interrupted();
        try {
            runWithin(settings.stopTimeout(), "not stopped", stop, process);
        } catch (InterruptedException e) {
            interrupted = true;
            log.error("BrowserStack local connection failed to stop: interrupted");
        } catch (Exception e) {
            log.error("BrowserStack local connection failed to stop: " + e.getMessage());
        } finally {
            if (interrupted) {
                Thread.currentThread().interrupt();
            }
        }
    }

    /** Local no pone plazo a sus procesos: si vence el nuestro, se mata lo que este lanzando. */
    private void runWithin(Duration timeout, String timeoutReason, ThrowingAction action, LocalTunnelProcess process)
            throws Exception {
        FutureTask<Void> task = new FutureTask<>(action.asCallable());
        Thread worker = new Thread(task, "browserstack-local-" + identifier);
        // Un binario colgado no puede impedir que la JVM se cierre
        worker.setDaemon(true);
        worker.start();
        try {
            task.get(timeout.toMillis(), TimeUnit.MILLISECONDS);
        } catch (TimeoutException e) {
            process.abort();
            task.cancel(true);
            throw new IllegalStateException(timeoutReason + " after " + describe(timeout));
        } catch (InterruptedException e) {
            process.abort();
            task.cancel(true);
            throw e;
        } catch (ExecutionException e) {
            if (e.getCause() instanceof Error error) {
                throw error;
            }
            throw (Exception) e.getCause();
        }
    }

    @FunctionalInterface
    private interface ThrowingAction {
        void run() throws Exception;

        default Callable<Void> asCallable() {
            return () -> {
                run();
                return null;
            };
        }
    }

    static String describe(Duration duration) {
        long millis = duration.toMillis();
        return millis % 1000 == 0 ? millis / 1000 + " s" : millis + " ms";
    }

    private String unavailable(String reason) {
        return "BrowserStack local connection not available (id " + identifier + "): " + reason;
    }
}
