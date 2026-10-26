package io.zahori.framework.core;

import static io.zahori.framework.core.FakeBinaries.FAILURE;
import static io.zahori.framework.core.FakeBinaries.args;
import static io.zahori.framework.core.FakeBinaries.awaitUntil;
import static io.zahori.framework.core.FakeBinaries.runAll;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;

import io.zahori.framework.core.BrowserStackLocalTunnel.Settings;
import io.zahori.framework.core.BrowserStackLocalTunnel.Sleeper;
import io.zahori.framework.core.BrowserStackLocalTunnel.TunnelUnavailableException;
import io.zahori.framework.core.FakeBinaries.Behaviour;
import io.zahori.framework.core.FakeBinaries.RecordingLog;
import io.zahori.framework.core.FakeBinaries.SettingsBuilder;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.mockito.ArgumentCaptor;

@Timeout(60)
class BrowserStackLocalTunnelTest {

    private static final String ID = "zahori-host-4242-1";
    private static final int PARALLEL_CASES = 8;
    private static final int MAX_ATTEMPTS = 3;

    private final FakeBinaries binaries = new FakeBinaries();
    private final RecordingLog log = new RecordingLog();
    @SuppressWarnings("unchecked")
    private final Consumer<Thread> hooks = mock(Consumer.class);
    private final Sleeper sleeper = mock(Sleeper.class);
    // Reloj lejos de cero: una resta cambiada por suma no puede dar el mismo resultado
    private final AtomicLong clock = new AtomicLong(Duration.ofHours(1).toNanos());
    private BrowserStackLocalTunnel tunnel;

    @AfterEach
    void shutdownTheTunnel() {
        binaries.releaseAll();
        if (tunnel != null) {
            tunnel.shutdown(new RecordingLog());
        }
    }

    @Nested
    class CasesInParallel {

        @Test
        void casesStartingAtOnceShareASingleTunnel() throws Exception {
            tunnel = tunnel(settings());

            List<Outcome> outcomes = acquireAtOnce(PARALLEL_CASES);

            assertThat(outcomes).describedAs("ningun caso puede fallar por el tunel").allMatch(Outcome::acquired);
            assertThat(outcomes).extracting(Outcome::identifier).containsOnly(ID);
            assertThat(binaries.connected.get()).describedAs("un solo tunel para todo el proceso").isEqualTo(1);
            assertThat(binaries.alive.get()).describedAs("binarios vivos que ya nadie puede parar").isEqualTo(1);
            assertThat(log.infos).describedAs("los que esperaron dejan en sus evidencias con que tunel van")
                    .filteredOn(("BrowserStack local connection reused with id " + ID)::equals)
                    .hasSize(PARALLEL_CASES - 1);
        }

        @Test
        void aCaseThatHasToWaitSaysSoInItsEvidences() throws Exception {
            binaries.script(Behaviour.BLOCKS);
            tunnel = tunnel(settings());
            ExecutorService pool = Executors.newFixedThreadPool(2);
            try {
                Future<String> starter = pool.submit(() -> tunnel.acquire(args("key"), log));
                binaries.awaitStarted(1);
                Future<String> waiter = pool.submit(() -> tunnel.acquire(args("key"), log));
                awaitUntil(() -> tunnel.waitingCases() == 1, "un caso esperando");

                binaries.release.countDown();

                assertThat(waiter.get(5, TimeUnit.SECONDS)).isEqualTo(starter.get(5, TimeUnit.SECONDS));
                assertThat(log.infos).contains("BrowserStack local connection with id " + ID + " being started by another case: waiting");
            } finally {
                pool.shutdownNow();
            }
        }

        @Test
        void reusingAnAliveTunnelChecksItOnceWithoutTakingTheLock() throws Exception {
            tunnel = tunnel(settings());
            tunnel.acquire(args("key"), log);
            int checksBefore = binaries.aliveChecks.get();

            tunnel.acquire(args("key"), log);

            assertThat(binaries.aliveChecks.get() - checksBefore).isEqualTo(1);
        }

        @Test
        void casesWaitingForAFailedStartShareItsFailureInsteadOfRetryingOneAfterAnother() throws Exception {
            binaries.script(Behaviour.BLOCKS_THEN_FAILS);
            tunnel = tunnel(settings());
            ExecutorService pool = Executors.newFixedThreadPool(PARALLEL_CASES);
            try {
                List<Future<Outcome>> outcomes = new ArrayList<>();
                outcomes.add(pool.submit(BrowserStackLocalTunnelTest.this::acquireOutcome));
                binaries.awaitStarted(1);
                for (int i = 1; i < PARALLEL_CASES; i++) {
                    outcomes.add(pool.submit(BrowserStackLocalTunnelTest.this::acquireOutcome));
                }
                awaitUntil(() -> tunnel.waitingCases() == PARALLEL_CASES - 1, "todos esperando");

                binaries.release.countDown();

                String failure = "BrowserStack local connection failed (id " + ID + ") [attempt 3/3]: " + FAILURE;
                for (Future<Outcome> outcome : outcomes) {
                    assertThat(outcome.get(5, TimeUnit.SECONDS).failure()).isEqualTo(failure);
                }
                assertThat(binaries.created.get()).describedAs("un arranque con sus intentos, no uno por caso").isEqualTo(MAX_ATTEMPTS);
            } finally {
                pool.shutdownNow();
            }
        }

        @Test
        void aCaseWaitingGetsTheFailureOfTheStartItWaitedForEvenWithTheSameMessage() throws Exception {
            binaries.script(Behaviour.FAILS, Behaviour.FAILS, Behaviour.FAILS, Behaviour.BLOCKS_THEN_FAILS);
            tunnel = tunnel(settings());
            assertThatThrownBy(() -> tunnel.acquire(args("key"), log)).hasMessageEndingWith(FAILURE);
            ExecutorService pool = Executors.newFixedThreadPool(2);
            try {
                Future<Outcome> starter = pool.submit(BrowserStackLocalTunnelTest.this::acquireOutcome);
                binaries.awaitStarted(MAX_ATTEMPTS + 1);
                Future<Outcome> waiter = pool.submit(BrowserStackLocalTunnelTest.this::acquireOutcome);
                awaitUntil(() -> tunnel.waitingCases() == 1, "un caso esperando");

                binaries.release.countDown();

                assertThat(starter.get(5, TimeUnit.SECONDS).acquired()).isFalse();
                assertThat(waiter.get(5, TimeUnit.SECONDS).acquired()).isFalse();
                assertThat(binaries.created.get()).describedAs("el que esperaba no repite el arranque").isEqualTo(2 * MAX_ATTEMPTS);
            } finally {
                pool.shutdownNow();
            }
        }

        @Test
        void casesFindingTheTunnelDeadAtOnceRestartItOnlyOnceAndKeepItsIdentifier() throws Exception {
            tunnel = tunnel(settings());
            tunnel.acquire(args("key"), log);
            binaries.killAll();

            List<Outcome> outcomes = acquireAtOnce(PARALLEL_CASES);

            assertThat(outcomes).extracting(Outcome::identifier).containsOnly(ID);
            assertThat(binaries.connected.get()).describedAs("el arranque inicial y un unico reinicio").isEqualTo(2);
            assertThat(binaries.stops.get()).describedAs("al muerto se le manda parar igualmente").isEqualTo(1);
            assertThat(binaries.alive.get()).isEqualTo(1);
            assertThat(log.infos).contains("BrowserStack local connection with id " + ID + " stopped");
        }

        @Test
        void aCaseGivesUpWaitingWhenAnotherTakesLongerThanTheLimitToStartIt() throws Exception {
            binaries.script(Behaviour.BLOCKS);
            tunnel = tunnel(settings().waitTimeout(Duration.ofMillis(200)));
            ExecutorService pool = Executors.newSingleThreadExecutor();
            try {
                Future<String> starter = pool.submit(() -> tunnel.acquire(args("key"), log));
                binaries.awaitStarted(1);

                assertThatThrownBy(() -> tunnel.acquire(args("key"), log))
                        .isInstanceOf(TunnelUnavailableException.class)
                        .hasMessage("BrowserStack local connection not available (id " + ID
                                + "): another case has been starting it for more than 200 ms");

                binaries.release.countDown();
                assertThat(starter.get(5, TimeUnit.SECONDS)).isEqualTo(ID);
            } finally {
                pool.shutdownNow();
            }
        }

        @Test
        void aCaseInterruptedWhileWaitingFailsAndKeepsItsInterruptFlag() throws Exception {
            binaries.script(Behaviour.BLOCKS);
            tunnel = tunnel(settings());
            ExecutorService pool = Executors.newFixedThreadPool(2);
            try {
                Future<String> starter = pool.submit(() -> tunnel.acquire(args("key"), log));
                binaries.awaitStarted(1);

                Future<Boolean> waiter = pool.submit(() -> {
                    Thread.currentThread().interrupt();
                    assertThatThrownBy(() -> tunnel.acquire(args("key"), log))
                            .hasMessage("BrowserStack local connection not available (id " + ID + "): interrupted while waiting for it");
                    return Thread.interrupted();
                });

                assertThat(waiter.get(5, TimeUnit.SECONDS)).describedAs("la interrupcion no se puede tragar").isTrue();
                binaries.release.countDown();
                assertThat(starter.get(5, TimeUnit.SECONDS)).isEqualTo(ID);
            } finally {
                pool.shutdownNow();
            }
        }

        @Test
        void whenTheStarterIsInterruptedTheWaitingCaseStartsTheTunnelItself() throws Exception {
            binaries.script(Behaviour.BLOCKS, Behaviour.CONNECTS);
            tunnel = tunnel(settings());
            ExecutorService starterPool = Executors.newSingleThreadExecutor();
            ExecutorService waiterPool = Executors.newSingleThreadExecutor();
            try {
                starterPool.submit(BrowserStackLocalTunnelTest.this::acquireOutcome);
                binaries.awaitStarted(1);
                Future<Outcome> waiter = waiterPool.submit(BrowserStackLocalTunnelTest.this::acquireOutcome);
                awaitUntil(() -> tunnel.waitingCases() == 1, "un caso esperando");

                starterPool.shutdownNow();

                assertThat(waiter.get(5, TimeUnit.SECONDS).identifier()).isEqualTo(ID);
                assertThat(binaries.created.get()).isEqualTo(2);
            } finally {
                starterPool.shutdownNow();
                waiterPool.shutdownNow();
            }
        }

        @Test
        void whenTheStarterHitsAnErrorTheWaitingCaseStartsTheTunnelItself() throws Exception {
            binaries.script(Behaviour.BLOCKS_THEN_ERROR, Behaviour.CONNECTS);
            tunnel = tunnel(settings());
            ExecutorService pool = Executors.newFixedThreadPool(2);
            try {
                Future<Outcome> starter = pool.submit(BrowserStackLocalTunnelTest.this::acquireOutcome);
                binaries.awaitStarted(1);
                Future<Outcome> waiter = pool.submit(BrowserStackLocalTunnelTest.this::acquireOutcome);
                awaitUntil(() -> tunnel.waitingCases() == 1, "un caso esperando");

                binaries.release.countDown();

                assertThatThrownBy(() -> starter.get(5, TimeUnit.SECONDS)).hasRootCauseInstanceOf(LinkageError.class);
                assertThat(waiter.get(5, TimeUnit.SECONDS).identifier()).isEqualTo(ID);
            } finally {
                pool.shutdownNow();
            }
        }

        @Test
        void shuttingDownWhileCasesAreStartingNeverLeavesAnOrphan() throws Exception {
            tunnel = tunnel(settings().shutdownTimeout(Duration.ZERO));
            CountDownLatch ready = new CountDownLatch(PARALLEL_CASES + 1);
            List<Callable<Void>> tasks = new ArrayList<>();
            for (int i = 0; i < PARALLEL_CASES; i++) {
                tasks.add(() -> {
                    ready.countDown();
                    ready.await();
                    acquireOutcome();
                    return null;
                });
            }
            tasks.add(() -> {
                ready.countDown();
                ready.await();
                Thread.sleep(FakeBinaries.CONNECT_MILLIS / 2);
                tunnel.shutdown(log);
                return null;
            });

            runAll(tasks);

            assertThat(binaries.alive.get()).describedAs("o lo paro el hook o lo paro el arranque").isZero();
            assertThat(tunnel.isRunning()).isFalse();
        }
    }

    @Nested
    class StartingTheTunnel {

        @Test
        void startsOnceAndReusesItLoggingBothInTheCaseEvidences() throws Exception {
            tunnel = new BrowserStackLocalTunnel(ID, binaries::next, settings().build(), hooks, sleeper, System::nanoTime);

            assertThat(tunnel.isRunning()).isFalse();
            assertThat(tunnel.acquire(args("key"), log)).isEqualTo(ID);
            assertThat(tunnel.acquire(args("key"), log)).isEqualTo(ID);

            assertThat(binaries.created.get()).isEqualTo(1);
            assertThat(tunnel.isRunning()).isTrue();
            assertThat(tunnel.identifier()).isEqualTo(ID);
            assertThat(log.warnings).isEmpty();
            assertThat(log.infos.get(0)).matches("BrowserStack local connection started with id " + ID + " \\(pid 1001, \\d+ ms\\)");
            long millis = Long.parseLong(log.infos.get(0).replaceAll(".*, (\\d+) ms\\)$", "$1"));
            assertThat(millis).isBetween(FakeBinaries.CONNECT_MILLIS / 2, 10_000L);
            assertThat(log.infos.get(1)).isEqualTo("BrowserStack local connection reused with id " + ID);
        }

        @Test
        void startsTheBinaryWithTheTunnelIdentifierOnADaemonThreadLeavingTheCallerArgumentsUntouched() throws Exception {
            tunnel = tunnel(settings());
            Map<String, String> args = args("access-key");
            args.put(BrowserStackLocalTunnel.LOCAL_IDENTIFIER_ARG, "12006-1");

            // Desde un hilo que no es daemon: el de arranque no puede heredarlo
            Thread caseThread = new Thread(() -> acquireOutcomeWith(args));
            caseThread.setDaemon(false);
            caseThread.start();
            caseThread.join(5_000);

            assertThat(binaries.all.get(0).startArgs).containsEntry(BrowserStackLocalTunnel.LOCAL_IDENTIFIER_ARG, ID)
                    .containsEntry("key", "access-key");
            assertThat(binaries.all.get(0).startedOnDaemonThread).describedAs("un arranque colgado no impide cerrar la JVM").isTrue();
            assertThat(args).containsEntry(BrowserStackLocalTunnel.LOCAL_IDENTIFIER_ARG, "12006-1");
        }

        @Test
        void retriesUntilItConnectsPausingBetweenAttemptsAndLoggingEachFailure() throws Exception {
            binaries.script(Behaviour.FAILS, Behaviour.FAILS, Behaviour.CONNECTS);
            tunnel = tunnel(settings());

            assertThat(tunnel.acquire(args("key"), log)).isEqualTo(ID);

            verify(sleeper, times(2)).sleep(Duration.ofMillis(150));
            verifyNoMoreInteractions(sleeper);
            assertThat(log.warnings).containsExactly(
                    "BrowserStack local connection failed (id " + ID + ") [attempt 1/3]: " + FAILURE,
                    "BrowserStack local connection failed (id " + ID + ") [attempt 2/3]: " + FAILURE);
        }

        @Test
        void triesExactlyTheConfiguredAttemptsEvenIfItIsOne() throws Exception {
            binaries.script(Behaviour.FAILS);
            tunnel = tunnel(settings().maxAttempts(1));

            assertThatThrownBy(() -> tunnel.acquire(args("key"), log))
                    .hasMessage("BrowserStack local connection failed (id " + ID + ") [attempt 1/1]: " + FAILURE);
            assertThat(binaries.created.get()).isEqualTo(1);
            verifyNoInteractions(sleeper);
        }

        @Test
        void aCaseArrivingAfterAFailedStartTriesAgainOnceTheCooldownIsOver() throws Exception {
            binaries.script(Behaviour.FAILS, Behaviour.FAILS, Behaviour.FAILS, Behaviour.CONNECTS);
            tunnel = tunnel(settings().failureCooldown(Duration.ofSeconds(60)));
            assertThatThrownBy(() -> tunnel.acquire(args("key"), log)).isInstanceOf(TunnelUnavailableException.class);

            clock.addAndGet(Duration.ofSeconds(59).toNanos());
            assertThatThrownBy(() -> tunnel.acquire(args("key"), log))
                    .describedAs("durante el enfriamiento falla al momento con el motivo")
                    .hasMessage("BrowserStack local connection not available (id " + ID + "): last start failed less than 60 s ago: "
                            + "BrowserStack local connection failed (id " + ID + ") [attempt 3/3]: " + FAILURE);
            assertThat(binaries.created.get()).isEqualTo(MAX_ATTEMPTS);

            clock.addAndGet(Duration.ofSeconds(1).toNanos());
            assertThat(tunnel.acquire(args("key"), log)).isEqualTo(ID);
        }

        @Test
        void aStartThatHangsIsAbortedAfterItsTimeoutAndCountsAsAFailedAttempt() throws Exception {
            binaries.script(Behaviour.BLOCKS, Behaviour.CONNECTS_IMMEDIATELY);
            tunnel = tunnel(settings().startTimeout(Duration.ofMillis(300)));

            assertThat(tunnel.acquire(args("key"), log)).isEqualTo(ID);

            assertThat(binaries.aborts.get()).describedAs("se mata el lanzador colgado").isEqualTo(1);
            assertThat(binaries.stopsByIdentifier.get()).isEqualTo(1);
            assertThat(log.warnings).containsExactly(
                    "BrowserStack local connection failed (id " + ID + ") [attempt 1/3]: not connected after 300 ms");
        }

        @Test
        void aStartInterruptedAbortsAndStopsTheBinaryKeepsTheFlagAndDoesNotFailTheNextCase() throws Exception {
            binaries.script(Behaviour.BLOCKS, Behaviour.CONNECTS);
            tunnel = tunnel(settings());
            ExecutorService pool = Executors.newSingleThreadExecutor();
            try {
                Future<Boolean> starter = pool.submit(() -> {
                    assertThatThrownBy(() -> tunnel.acquire(args("key"), log))
                            .hasMessage("BrowserStack local connection not available (id " + ID + "): interrupted while starting it");
                    return Thread.currentThread().isInterrupted();
                });
                binaries.awaitStarted(1);
                pool.shutdownNow();

                assertThat(starter.get(5, TimeUnit.SECONDS)).describedAs("la interrupcion no se puede tragar").isTrue();
            } finally {
                pool.shutdownNow();
            }
            assertThat(binaries.aborts.get()).isEqualTo(1);
            assertThat(binaries.stopsByIdentifier.get()).describedAs("parado aunque el hilo llegue interrumpido").isEqualTo(1);
            assertThat(binaries.created.get()).describedAs("interrumpido no se reintenta").isEqualTo(1);
            assertThat(acquireFromAnotherThread()).describedAs("no es un fallo del tunel: el siguiente lo levanta").isEqualTo(ID);
        }

        @Test
        void aCaseInterruptedDuringThePauseBetweenAttemptsStopsTrying() throws Exception {
            binaries.script(Behaviour.FAILS);
            doThrow(new InterruptedException()).when(sleeper).sleep(any());
            tunnel = tunnel(settings());

            assertThatThrownBy(() -> tunnel.acquire(args("key"), log))
                    .hasMessage("BrowserStack local connection not available (id " + ID + "): interrupted while starting it");
            assertThat(Thread.interrupted()).describedAs("la interrupcion no se puede tragar").isTrue();
            assertThat(binaries.created.get()).isEqualTo(1);
        }

        @Test
        void aFailedAttemptWhoseDaemonCameUpWithoutPidIsStoppedByIdentifier() {
            binaries.script(Behaviour.FAILS_WITH_DAEMON_UP);
            tunnel = tunnel(settings());

            assertThatThrownBy(() -> tunnel.acquire(args("key"), log)).hasMessageEndingWith("Could not parse the binary output");

            assertThat(binaries.alive.get()).describedAs("ningun intento deja su daemon vivo").isZero();
            assertThat(binaries.stopsByIdentifier.get()).isEqualTo(MAX_ATTEMPTS);
        }

        @Test
        void aBinaryThatConnectsButIsNotAliveIsAFailedAttempt() {
            binaries.script(Behaviour.CONNECTS_BUT_DEAD);
            tunnel = tunnel(settings());

            assertThatThrownBy(() -> tunnel.acquire(args("key"), log)).hasMessageEndingWith("[attempt 3/3]: Connection is not running");
            assertThat(binaries.alive.get()).isZero();
        }

        @Test
        void aFailedAttemptThatCannotBeStoppedIsLoggedAndTheNextAttemptGoesOn() {
            binaries.script(Behaviour.FAILS);
            binaries.stopFails = true;
            tunnel = tunnel(settings());

            assertThatThrownBy(() -> tunnel.acquire(args("key"), log)).isInstanceOf(TunnelUnavailableException.class);

            assertThat(binaries.created.get()).isEqualTo(MAX_ATTEMPTS);
            assertThat(log.errors).hasSize(MAX_ATTEMPTS).containsOnly("BrowserStack local connection failed to stop: daemon not responding");
            binaries.stopFails = false;
        }

        @Test
        void aStopThatHangsIsAbortedWithinItsTimeoutAndTheRestartGoesOn() throws Exception {
            tunnel = tunnel(settings().stopTimeout(Duration.ofMillis(200)));
            tunnel.acquire(args("key"), log);
            binaries.killAll();
            binaries.stopHangs = true;

            long start = System.nanoTime();
            assertThat(tunnel.acquire(args("key"), log)).isEqualTo(ID);

            assertThat(Duration.ofNanos(System.nanoTime() - start)).isLessThan(Duration.ofSeconds(5));
            assertThat(log.errors).containsExactly("BrowserStack local connection failed to stop: not stopped after 200 ms");
        }

        @Test
        void aFailureWithoutMessageIsReportedByItsType() {
            binaries.script(Behaviour.FAILS_WITHOUT_MESSAGE);
            tunnel = tunnel(settings().maxAttempts(1));

            assertThatThrownBy(() -> tunnel.acquire(args("key"), log))
                    .hasMessage("BrowserStack local connection failed (id " + ID + ") [attempt 1/1]: NullPointerException");
        }

        @Test
        void aFactoryThatFailsCountsAsAFailedAttempt() throws Exception {
            binaries.factoryFailures = 1;
            tunnel = tunnel(settings());

            assertThat(tunnel.acquire(args("key"), log)).isEqualTo(ID);
            assertThat(log.warnings).containsExactly("BrowserStack local connection failed (id " + ID + ") [attempt 1/3]: no binary");
        }

        @Test
        void anErrorWhileStartingStopsTheBinaryReleasesTheLockAndPropagates() throws Exception {
            binaries.script(Behaviour.THROWS_ERROR, Behaviour.CONNECTS);
            tunnel = tunnel(settings().waitTimeout(Duration.ofMillis(500)));

            assertThatThrownBy(() -> tunnel.acquire(args("key"), log)).isInstanceOf(LinkageError.class).hasMessage("broken binary");

            assertThat(binaries.stopsByIdentifier.get()).isEqualTo(1);
            assertThat(acquireFromAnotherThread()).isEqualTo(ID);
        }
    }

    @Nested
    class ShuttingDownTheJvm {

        @Test
        void registersASingleHookPerTunnelThatStopsIt() throws Exception {
            tunnel = tunnel(settings());
            tunnel.acquire(args("key"), log);
            binaries.killAll();
            tunnel.acquire(args("key"), log);

            ArgumentCaptor<Thread> hook = ArgumentCaptor.forClass(Thread.class);
            verify(hooks, times(1).description("uno por tunel, aunque se reinicie")).accept(hook.capture());
            hook.getValue().run();
            assertThat(binaries.alive.get()).describedAs("el hook para el tunel").isZero();
        }

        @Test
        void afterShutdownTheTunnelIsStoppedAndNoOtherIsStarted() throws Exception {
            tunnel = tunnel(settings().waitTimeout(Duration.ofMillis(500)));
            tunnel.acquire(args("key"), log);

            tunnel.shutdown(log);

            assertThat(binaries.alive.get()).isZero();
            assertThat(log.infos).contains("BrowserStack local connection with id " + ID + " stopped on shutdown");
            assertThatThrownBy(BrowserStackLocalTunnelTest.this::acquireFromAnotherThread)
                    .isInstanceOf(ExecutionException.class)
                    .hasRootCauseMessage("BrowserStack local connection not available (id " + ID + "): the process is shutting down");
            assertThat(binaries.created.get()).isEqualTo(1);
        }

        @Test
        void shutdownAbortsAndStopsByIdentifierATunnelBeingStartedWithoutWaitingForIt() throws Exception {
            binaries.script(Behaviour.BLOCKS_WITH_DAEMON_UP);
            binaries.abortIgnored = true;
            tunnel = tunnel(settings().shutdownTimeout(Duration.ofMillis(200)));
            ExecutorService pool = Executors.newSingleThreadExecutor();
            try {
                pool.submit(BrowserStackLocalTunnelTest.this::acquireOutcome);
                binaries.awaitStarted(1);

                long start = System.nanoTime();
                tunnel.shutdown(log);

                assertThat(Duration.ofNanos(System.nanoTime() - start)).isLessThan(Duration.ofSeconds(2));
                assertThat(binaries.aborts.get()).isEqualTo(1);
                assertThat(binaries.stopsByIdentifier.get()).isEqualTo(1);
                assertThat(binaries.alive.get()).describedAs("el hook no puede esperar a que el lanzador vuelva").isZero();
                assertThat(log.infos).contains("BrowserStack local connection being started with id " + ID + " stopped on shutdown");
            } finally {
                pool.shutdownNow();
            }
        }

        @Test
        void aRetryThatOutlivesTheHookStartsNoBinary() throws Exception {
            tunnel = tunnel(settings().stopTimeout(Duration.ofSeconds(1)).shutdownTimeout(Duration.ofMillis(100)));
            tunnel.acquire(args("key"), log);
            binaries.killAll();
            binaries.stopHangs = true;
            ExecutorService pool = Executors.newSingleThreadExecutor();
            try {
                Future<Outcome> restart = pool.submit(BrowserStackLocalTunnelTest.this::acquireOutcome);
                awaitUntil(() -> binaries.hungStops.get() == 1, "parando el tunel muerto");

                tunnel.shutdown(log);

                assertThat(restart.get(5, TimeUnit.SECONDS).failure()).endsWith("the process is shutting down");
                assertThat(binaries.started.get()).describedAs("tras el hook no se lanza ningun binario").isEqualTo(1);
            } finally {
                pool.shutdownNow();
            }
        }

        @Test
        void aTunnelThatConnectsAfterTheShutdownIsStoppedAtOnce() throws Exception {
            binaries.script(Behaviour.BLOCKS);
            binaries.abortIgnored = true;
            tunnel = tunnel(settings().shutdownTimeout(Duration.ofMillis(200)));
            ExecutorService pool = Executors.newSingleThreadExecutor();
            try {
                Future<Outcome> starter = pool.submit(BrowserStackLocalTunnelTest.this::acquireOutcome);
                binaries.awaitStarted(1);
                tunnel.shutdown(log);

                binaries.release.countDown();

                assertThat(starter.get(5, TimeUnit.SECONDS).failure()).endsWith("the process is shutting down");
                assertThat(binaries.connected.get()).isEqualTo(1);
                assertThat(binaries.alive.get()).describedAs("lo que conecto tarde no puede quedar vivo").isZero();
            } finally {
                pool.shutdownNow();
            }
        }

        @Test
        void aJvmThatIsAlreadyShuttingDownStartsNoTunnel() {
            doThrow(new IllegalStateException("Shutdown in progress")).when(hooks).accept(any());
            tunnel = tunnel(settings());

            assertThatThrownBy(() -> tunnel.acquire(args("key"), log))
                    .hasMessage("BrowserStack local connection not available (id " + ID + "): the process is shutting down");
            assertThat(binaries.created.get()).isZero();
        }

        @Test
        void shutdownFromAnInterruptedThreadStillStopsTheTunnelAndKeepsTheFlag() throws Exception {
            tunnel = tunnel(settings());
            tunnel.acquire(args("key"), log);
            ExecutorService pool = Executors.newSingleThreadExecutor();
            try {
                Future<Boolean> interrupted = pool.submit(() -> {
                    Thread.currentThread().interrupt();
                    tunnel.shutdown(log);
                    return Thread.interrupted();
                });

                assertThat(interrupted.get(5, TimeUnit.SECONDS)).isTrue();
                assertThat(binaries.alive.get()).describedAs("parado aunque el hook llegue interrumpido").isZero();
                assertThat(log.errors).isEmpty();
            } finally {
                pool.shutdownNow();
            }
        }

        @Test
        void aShutdownWhoseStopHangsEndsWithinItsTimeouts() throws Exception {
            tunnel = tunnel(settings().stopTimeout(Duration.ofMillis(200)).shutdownTimeout(Duration.ofMillis(200)));
            tunnel.acquire(args("key"), log);
            binaries.stopHangs = true;

            long start = System.nanoTime();
            tunnel.shutdown(log);

            assertThat(Duration.ofNanos(System.nanoTime() - start)).isLessThan(Duration.ofSeconds(2));
            assertThat(log.errors).containsExactly("BrowserStack local connection failed to stop: not stopped after 200 ms");
        }

        @Test
        void aShutdownInterruptedWhileStoppingGivesUpLoggingItAndKeepsTheFlag() throws Exception {
            tunnel = tunnel(settings().stopTimeout(Duration.ofSeconds(30)));
            tunnel.acquire(args("key"), log);
            binaries.stopHangs = true;
            ExecutorService pool = Executors.newSingleThreadExecutor();
            try {
                Future<Boolean> hook = pool.submit(() -> {
                    tunnel.shutdown(log);
                    return Thread.interrupted();
                });
                awaitUntil(() -> binaries.hungStops.get() == 1, "parada en curso");

                pool.shutdownNow();

                assertThat(hook.get(5, TimeUnit.SECONDS)).describedAs("la interrupcion no se puede tragar").isTrue();
                assertThat(log.errors).containsExactly("BrowserStack local connection failed to stop: interrupted");
                assertThat(binaries.aborts.get()).describedAs("se mata la parada colgada").isEqualTo(1);
            } finally {
                pool.shutdownNow();
            }
        }

        @Test
        void shutdownWithNothingStartedDoesNothing() {
            tunnel = tunnel(settings());

            tunnel.shutdown(log);

            assertThat(log.infos).isEmpty();
            assertThat(log.errors).isEmpty();
        }
    }

    @Nested
    class RecyclingTheTunnel {

        @Test
        void withoutTunnelThereIsNoProcessToRecycle() {
            tunnel = tunnel(settings());

            assertThat(tunnel.current()).isNull();
            assertThat(tunnel.recycle(null, "reason", log)).isFalse();
            assertThat(binaries.stops.get()).isZero();
        }

        @Test
        void theCaseThatSawTheTunnelFailStopsItAndTheNextAcquireStartsANewOne() throws Exception {
            tunnel = tunnel(settings());
            tunnel.acquire(args("key"), log);
            LocalTunnelProcess failed = tunnel.current();

            assertThat(tunnel.recycle(failed, "BrowserStack reports it as not connected", log)).isTrue();

            assertThat(tunnel.current()).isNull();
            assertThat(tunnel.isRunning()).isFalse();
            assertThat(binaries.stops.get()).isEqualTo(1);
            assertThat(log.warnings).containsExactly(
                    "BrowserStack local connection with id " + ID + " recycled: BrowserStack reports it as not connected");
            assertThat(tunnel.acquire(args("key"), log)).isEqualTo(ID);
            assertThat(tunnel.current()).isNotSameAs(failed).isNotNull();
            assertThat(binaries.connected.get()).isEqualTo(2);
            assertThat(binaries.alive.get()).describedAs("solo queda vivo el nuevo").isEqualTo(1);
        }

        @Test
        void aCaseThatSawAnOlderTunnelFailDoesNotStopTheOneAlreadyReplacingIt() throws Exception {
            tunnel = tunnel(settings());
            tunnel.acquire(args("key"), log);
            LocalTunnelProcess failed = tunnel.current();
            tunnel.recycle(failed, "first case", log);
            tunnel.acquire(args("key"), log);
            LocalTunnelProcess replacement = tunnel.current();

            assertThat(tunnel.recycle(failed, "second case", log)).isFalse();

            assertThat(tunnel.current()).isSameAs(replacement);
            assertThat(tunnel.isRunning()).isTrue();
            assertThat(binaries.stops.get()).isEqualTo(1);
            assertThat(log.warnings).noneMatch(message -> message.endsWith("second case"));
        }

        @Test
        void casesFailingAtOnceWithTheSameTunnelRecycleItOnlyOnce() throws Exception {
            tunnel = tunnel(settings());
            tunnel.acquire(args("key"), log);
            LocalTunnelProcess failed = tunnel.current();
            CountDownLatch ready = new CountDownLatch(PARALLEL_CASES);
            List<Callable<Boolean>> tasks = new ArrayList<>();
            for (int i = 0; i < PARALLEL_CASES; i++) {
                tasks.add(() -> {
                    ready.countDown();
                    ready.await();
                    return tunnel.recycle(failed, "not connected", log);
                });
            }

            List<Boolean> recycled = runAll(tasks);

            assertThat(recycled).containsOnlyOnce(true);
            assertThat(binaries.stops.get()).isEqualTo(1);
        }

        @Test
        void whileAnotherCaseIsStartingItTheRecycleGivesUpAfterTheWaitTimeout() throws Exception {
            tunnel = tunnel(settings().waitTimeout(Duration.ofMillis(200)));
            binaries.script(Behaviour.BLOCKS);
            ExecutorService starter = Executors.newSingleThreadExecutor();
            try {
                starter.submit(() -> tunnel.acquire(args("key"), log));
                binaries.awaitStarted(1);
                LocalTunnelProcess failed = mock(LocalTunnelProcess.class);

                long start = System.nanoTime();
                assertThat(tunnel.recycle(failed, "not connected", log)).isFalse();

                assertThat(Duration.ofNanos(System.nanoTime() - start)).isGreaterThanOrEqualTo(Duration.ofMillis(200));
                verifyNoInteractions(failed);
            } finally {
                binaries.releaseAll();
                starter.shutdownNow();
            }
        }

        @Test
        void anInterruptedRecycleKeepsTheInterruptionAndTouchesNothing() {
            tunnel = tunnel(settings());
            LocalTunnelProcess failed = mock(LocalTunnelProcess.class);

            Thread.currentThread().interrupt();
            boolean recycled = tunnel.recycle(failed, "not connected", log);

            assertThat(Thread.interrupted()).isTrue();
            assertThat(recycled).isFalse();
            verifyNoInteractions(failed);
        }
    }

    @Nested
    class Configuration {

        @Test
        void rejectsInvalidSettings() {
            Duration ok = Duration.ofSeconds(1);
            Duration negative = Duration.ofMillis(-1);
            assertThatThrownBy(() -> new Settings(0, ok, ok, ok, ok, ok, ok)).hasMessage("maxAttempts must be at least 1: 0");
            assertThatThrownBy(() -> new Settings(1, null, ok, ok, ok, ok, ok)).hasMessage("startTimeout");
            assertThatThrownBy(() -> new Settings(1, ok, negative, ok, ok, ok, ok)).hasMessage("stopTimeout must not be negative: PT-0.001S");
            assertThatThrownBy(() -> new Settings(1, ok, ok, negative, ok, ok, ok)).hasMessage("retryBackoff must not be negative: PT-0.001S");
            assertThatThrownBy(() -> new Settings(1, ok, ok, ok, negative, ok, ok)).hasMessage("waitTimeout must not be negative: PT-0.001S");
            assertThatThrownBy(() -> new Settings(1, ok, ok, ok, ok, negative, ok)).hasMessage("shutdownTimeout must not be negative: PT-0.001S");
            assertThatThrownBy(() -> new Settings(1, ok, ok, ok, ok, ok, negative)).hasMessage("failureCooldown must not be negative: PT-0.001S");
            Duration zero = Duration.ZERO;
            assertThat(new Settings(1, zero, zero, zero, zero, zero, zero).maxAttempts()).isEqualTo(1);
        }

        @Test
        void rejectsMissingCollaborators() {
            Settings settings = settings().build();
            Consumer<Thread> noHooks = hook -> { };
            assertThatThrownBy(() -> new BrowserStackLocalTunnel(null, binaries::next, settings, noHooks, sleeper, clock::get))
                    .hasMessage("identifier");
            assertThatThrownBy(() -> new BrowserStackLocalTunnel(ID, null, settings, noHooks, sleeper, clock::get))
                    .hasMessage("processFactory");
            assertThatThrownBy(() -> new BrowserStackLocalTunnel(ID, binaries::next, null, noHooks, sleeper, clock::get))
                    .hasMessage("settings");
            assertThatThrownBy(() -> new BrowserStackLocalTunnel(ID, binaries::next, settings, null, sleeper, clock::get))
                    .hasMessage("shutdownHooks");
            assertThatThrownBy(() -> new BrowserStackLocalTunnel(ID, binaries::next, settings, noHooks, null, clock::get))
                    .hasMessage("sleeper");
            assertThatThrownBy(() -> new BrowserStackLocalTunnel(ID, binaries::next, settings, noHooks, sleeper, null))
                    .hasMessage("nanoClock");
        }

        @Test
        void theDefaultsLetTheOthersWaitForEveryAttemptAndFitInTheSystemdStopTimeout() {
            Settings defaults = Settings.DEFAULTS;
            Duration everyAttempt = defaults.startTimeout().plus(defaults.retryBackoff()).plus(defaults.stopTimeout().multipliedBy(2))
                    .multipliedBy(defaults.maxAttempts());

            assertThat(defaults.maxAttempts()).isEqualTo(5);
            assertThat(defaults.waitTimeout()).isGreaterThan(everyAttempt);
            assertThat(defaults.shutdownTimeout().plus(defaults.stopTimeout().multipliedBy(2))).isLessThan(Duration.ofSeconds(90));
            assertThat(defaults.failureCooldown()).isPositive();
            assertThat(new BrowserStackLocalTunnel(ID, binaries::next).isRunning()).isFalse();
        }

        @Test
        void theDefaultPauseReallyWaits() throws Exception {
            long start = System.nanoTime();

            BrowserStackLocalTunnel.THREAD_SLEEP.sleep(Duration.ofMillis(100));

            assertThat(Duration.ofNanos(System.nanoTime() - start)).isGreaterThanOrEqualTo(Duration.ofMillis(90));
        }

        @Test
        void describesTimeoutsInTheUnitAPersonWouldUse() {
            assertThat(BrowserStackLocalTunnel.describe(Duration.ofMillis(200))).isEqualTo("200 ms");
            assertThat(BrowserStackLocalTunnel.describe(Duration.ofMinutes(2))).isEqualTo("120 s");
            assertThat(BrowserStackLocalTunnel.describe(Duration.ofMillis(1500))).isEqualTo("1500 ms");
        }
    }

    // --- soporte ---

    private static SettingsBuilder settings() {
        return new SettingsBuilder();
    }

    private BrowserStackLocalTunnel tunnel(SettingsBuilder settings) {
        return new BrowserStackLocalTunnel(ID, binaries::next, settings.build(), hooks, sleeper, clock::get);
    }

    private Outcome acquireOutcome() {
        return acquireOutcomeWith(args("key"));
    }

    private Outcome acquireOutcomeWith(Map<String, String> args) {
        try {
            return Outcome.ok(tunnel.acquire(args, log));
        } catch (TunnelUnavailableException e) {
            return Outcome.ko(e.getMessage());
        }
    }

    private String acquireFromAnotherThread() throws Exception {
        ExecutorService pool = Executors.newSingleThreadExecutor();
        try {
            return pool.submit(() -> tunnel.acquire(args("key"), log)).get(5, TimeUnit.SECONDS);
        } finally {
            pool.shutdownNow();
        }
    }

    private List<Outcome> acquireAtOnce(int cases) throws Exception {
        CountDownLatch ready = new CountDownLatch(cases);
        List<Callable<Outcome>> tasks = new ArrayList<>();
        for (int i = 0; i < cases; i++) {
            tasks.add(() -> {
                ready.countDown();
                ready.await();
                return acquireOutcome();
            });
        }
        return runAll(tasks);
    }

    private record Outcome(String identifier, String failure) {
        static Outcome ok(String identifier) {
            return new Outcome(identifier, null);
        }

        static Outcome ko(String failure) {
            return new Outcome(null, failure);
        }

        boolean acquired() {
            return identifier != null;
        }
    }
}
