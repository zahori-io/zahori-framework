package io.zahori.framework.core;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.browserstack.local.Local;
import io.zahori.framework.core.BrowserStackLocalProcess.TrackedLocal;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.LongFunction;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;

@Timeout(60)
class BrowserStackLocalProcessTest {

    static final String JAVA = Paths.get(System.getProperty("java.home"), "bin", "java").toString();
    private static final Field PID = BrowserStackLocalProcess.pidField(Local.class, "pid");
    private static final Map<String, String> ARGS = Map.of("key", "access-key", "localIdentifier", "zahori-host-1-1");

    @TempDir
    Path temp;

    private final RecordingLocal local = new RecordingLocal();
    private final List<RecordingLocal> helpers = Collections.synchronizedList(new ArrayList<>());
    private final AtomicInteger lookups = new AtomicInteger();
    private final List<Process> launched = new ArrayList<>();

    @AfterEach
    void killWhatWasLaunched() {
        launched.forEach(Process::destroyForcibly);
    }

    @Test
    void preparesTheBinaryWithoutLaunchingItBeforeStartingAndStopsThroughTheLibrary() throws Exception {
        BrowserStackLocalProcess process = process(ProcessHandle::of);

        process.start(ARGS);
        process.stop();

        assertThat(helpers.get(0).started).containsEntry("onlyCommand", "true").containsEntry("key", "access-key");
        assertThat(local.started).isEqualTo(ARGS);
        assertThat(local.stopped).isTrue();
    }

    @Test
    void isAliveWhileTheDaemonThatConnectedIsAliveAndNotByLocalIsRunning() throws Exception {
        BrowserStackLocalProcess process = process(this::lookUp);
        assertThat(process.isAlive()).describedAs("sin conectar, aunque Local.isRunning() diga que si").isFalse();

        local.pidOnStart = ProcessHandle.current().pid();
        process.start(ARGS);

        assertThat(process.isAlive()).isTrue();
        assertThat(process.pid()).isEqualTo(ProcessHandle.current().pid());
        assertThat(lookups.get()).describedAs("el handle se busca una vez, al conectar").isEqualTo(1);
    }

    @Test
    void aDaemonThatDiedIsNotAliveEvenIfItsPidIsReused() throws Exception {
        Process finished = start(JAVA, "-version");
        assertThat(finished.waitFor(30, TimeUnit.SECONDS)).isTrue();
        BrowserStackLocalProcess process = process(pid -> Optional.of(finished.toHandle()));
        local.pidOnStart = finished.pid();

        process.start(ARGS);

        assertThat(process.isAlive()).isFalse();
    }

    @Test
    void aDaemonWhosePidIsGoneIsNotAliveNorRecorded() throws Exception {
        BrowserStackLocalProcess process = process(pid -> Optional.empty());
        local.pidOnStart = 4242;

        process.start(ARGS);

        assertThat(process.isAlive()).isFalse();
        assertThat(entries()).isEmpty();
    }

    @Test
    void recordsTheConnectedDaemonAndForgetsItWhenStopped() throws Exception {
        BrowserStackLocalProcess process = process(ProcessHandle::of);
        local.pidOnStart = ProcessHandle.current().pid();

        process.start(ARGS);
        assertThat(entries()).containsExactly("zahori-host-1-1.tunnel");

        process.stop();
        assertThat(entries()).isEmpty();
    }

    @Test
    void stoppingBeforeStartingForgetsNothing() throws Exception {
        BrowserStackLocalProcess process = process(ProcessHandle::of);

        assertThatCode(process::stop).doesNotThrowAnyException();
    }

    @Test
    void withoutAReadablePidItFallsBackToTheLibrary() {
        BrowserStackLocalProcess process = new BrowserStackLocalProcess(local, RecordingLocal::new, ProcessHandle::of, null, ledger(),
                temp.resolve("lock"));

        assertThat(process.isAlive()).isTrue();
        assertThat(process.pid()).isZero();

        local.runningByLibrary = false;
        assertThat(process.isAlive()).isFalse();

        local.runningByLibrary = true;
        local.isRunningFails = true;
        assertThat(process.isAlive()).isFalse();
    }

    @Test
    void readsThePidFieldOnlyIfTheLibraryHasIt() throws Exception {
        // Recien calculado: el static del test se resolvio antes de cualquier mutante
        Field pid = BrowserStackLocalProcess.pidField(Local.class, "pid");

        assertThat(pid).describedAs("si la libreria cambia el campo, este test avisa").isNotNull();
        assertThat(BrowserStackLocalProcess.pidField(Local.class, "missing")).isNull();
        assertThat(BrowserStackLocalProcess.readPid(Local.class.getDeclaredField("pid"), local))
                .describedAs("campo sin abrir: no hay pid").isZero();
        pid.setInt(local, 4242);
        assertThat(BrowserStackLocalProcess.readPid(pid, local)).isEqualTo(4242);
    }

    @Test
    void stopsByIdentifierWithAFreshInstanceEachTimeAndForgetsTheDaemon() throws Exception {
        BrowserStackLocalProcess process = process(ProcessHandle::of);
        ledger().record("zahori-host-1-1", ProcessHandle.current());

        process.stopByIdentifier(ARGS);
        process.stopByIdentifier(ARGS);

        assertThat(helpers).hasSize(2).allMatch(helper -> ARGS.equals(helper.stoppedBy));
        assertThat(helpers.get(0)).isNotSameAs(helpers.get(1)).isNotSameAs(local);
        assertThat(entries()).isEmpty();
    }

    @Test
    void abortingStopsTheLauncherAndEveryHelper() throws Exception {
        BrowserStackLocalProcess process = process(ProcessHandle::of);
        process.stopByIdentifier(ARGS);
        process.stopByIdentifier(ARGS);

        process.abort();

        assertThat(local.isAborted()).isTrue();
        assertThat(helpers).hasSize(2).allMatch(RecordingLocal::isAborted);
        assertThatCode(() -> new BrowserStackLocalProcess(ledger()).abort()).doesNotThrowAnyException();
    }

    @Test
    void theBinaryIsPreparedOneAtATimeAcrossTunnels() throws Exception {
        AtomicInteger preparing = new AtomicInteger();
        AtomicInteger mostAtOnce = new AtomicInteger();
        CountDownLatch ready = new CountDownLatch(4);
        List<Callable<Void>> tasks = new ArrayList<>();
        for (int i = 0; i < 4; i++) {
            RecordingLocal own = new RecordingLocal();
            BrowserStackLocalProcess process = new BrowserStackLocalProcess(own, () -> {
                RecordingLocal helper = new RecordingLocal();
                helper.preparing = preparing;
                helper.mostAtOnce = mostAtOnce;
                return helper;
            }, ProcessHandle::of, PID, ledger(), temp.resolve("BrowserStackLocal.lock"));
            tasks.add(() -> {
                ready.countDown();
                ready.await();
                process.start(Map.of("key", "key-" + own.hashCode(), "localIdentifier", "zahori-host-1-" + own.hashCode()));
                return null;
            });
        }

        FakeBinaries.runAll(tasks);

        assertThat(mostAtOnce.get()).describedAs("descargas a la vez del mismo binario").isEqualTo(1);
        assertThat(temp.resolve("BrowserStackLocal.lock")).exists();
    }

    @Test
    void aCaseInterruptedWhileWaitingToPrepareTheBinaryStops() {
        BrowserStackLocalProcess process = process(ProcessHandle::of);
        Thread.currentThread().interrupt();

        assertThatThrownBy(() -> process.start(ARGS)).isInstanceOf(InterruptedException.class);
        assertThat(local.started).describedAs("ni se prepara ni se lanza").isNull();
    }

    @Test
    void theBinaryLockSitsNextToTheConfiguredBinary() {
        BrowserStackLocalProcess process = process(ProcessHandle::of);
        Path binary = temp.resolve("BrowserStackLocal");

        assertThat(process.binaryLockOf(Map.of())).isEqualTo(temp.resolve("lock"));
        assertThat(process.binaryLockOf(Map.of("binarypath", " "))).isEqualTo(temp.resolve("lock"));
        assertThat(process.binaryLockOf(Map.of("binarypath", " " + binary + " "))).isEqualTo(temp.resolve("BrowserStackLocal.lock"));
    }

    @Test
    void runsTheLauncherAndExposesItsOutput() throws Exception {
        Local.LocalProcess process = new TrackedLocal().runCommand(List.of(JAVA, "-version"));

        assertThat(process.waitFor()).isZero();
        assertThat(read(process.getErrorStream())).contains("version");
        assertThat(read(process.getInputStream())).isEmpty();
    }

    @Test
    void abortingKillsAHungLauncher() throws Exception {
        TrackedLocal tracked = new TrackedLocal();
        Local.LocalProcess hung = tracked.runCommand(sleeperCommand());

        tracked.abort();

        assertThat(hung.waitFor()).isNotZero();
    }

    @Test
    void abortingKillsTheLauncherWithItsChildren() throws Exception {
        List<Process> started = new ArrayList<>();
        TrackedLocal tracked = new TrackedLocal() {
            @Override
            Process launch(List<String> command) throws IOException {
                Process process = super.launch(command);
                started.add(process);
                return process;
            }
        };
        Local.LocalProcess launcher = tracked.runCommand(spawnerCommand());
        ProcessHandle child = childOf(started.get(0).toHandle());

        tracked.abort();

        assertThat(launcher.waitFor()).isNotZero();
        assertThat(child.onExit().get(10, TimeUnit.SECONDS).isAlive()).describedAs("en Windows el hijo no muere con el padre").isFalse();
    }

    @Test
    void abortingAConnectedTunnelKillsTheDaemonWithItsFork() throws Exception {
        StubProcessHandle daemon = new StubProcessHandle(false, true);
        StubProcessHandle fork = new StubProcessHandle(false, true);
        daemon.forks.add(fork);
        BrowserStackLocalProcess process = process(pid -> Optional.of(daemon));
        local.pidOnStart = 4242;
        process.start(ARGS);

        process.abort();

        assertThat(daemon.isAlive()).isFalse();
        assertThat(fork.isAlive()).describedAs("en Windows el fork no muere con el daemon").isFalse();
    }

    @Test
    void onceAbortedItLaunchesNothingMore() {
        TrackedLocal tracked = new TrackedLocal();
        tracked.abort();

        assertThatThrownBy(() -> tracked.runCommand(List.of(JAVA, "-version")))
                .isInstanceOf(IOException.class).hasMessage("BrowserStack Local aborted");
    }

    @Test
    void anAbortArrivingWhileLaunchingKillsWhatWasLaunched() throws Exception {
        List<Process> started = new ArrayList<>();
        TrackedLocal tracked = new TrackedLocal() {
            @Override
            Process launch(List<String> command) throws IOException {
                Process process = super.launch(command);
                started.add(process);
                abort();
                return process;
            }
        };

        assertThatThrownBy(() -> tracked.runCommand(sleeperCommand())).isInstanceOf(IOException.class);
        assertThat(started.get(0).waitFor(10, TimeUnit.SECONDS)).describedAs("lo lanzado durante el aborto muere").isTrue();
    }

    private BrowserStackLocalProcess process(LongFunction<Optional<ProcessHandle>> processes) {
        return new BrowserStackLocalProcess(local, () -> {
            RecordingLocal helper = new RecordingLocal();
            helpers.add(helper);
            return helper;
        }, processes, PID, ledger(), temp.resolve("lock"));
    }

    private LocalTunnelLedger ledger() {
        return new LocalTunnelLedger(temp.resolve("ledger"), ProcessHandle.current(), ProcessHandle::of);
    }

    private List<String> entries() throws IOException {
        Path ledger = temp.resolve("ledger");
        if (!Files.isDirectory(ledger)) {
            return List.of();
        }
        try (var files = Files.list(ledger)) {
            return files.map(file -> file.getFileName().toString()).toList();
        }
    }

    private Optional<ProcessHandle> lookUp(long pid) {
        lookups.incrementAndGet();
        return ProcessHandle.of(pid);
    }

    private Process start(String... command) throws IOException {
        Process process = new ProcessBuilder(command).start();
        launched.add(process);
        return process;
    }

    // Classpath minimo: el completo de surefire roza el limite de la linea de comandos de Windows
    private static List<String> sleeperCommand() throws Exception {
        String classes = Paths.get(Sleeper.class.getProtectionDomain().getCodeSource().getLocation().toURI()).toString();
        return List.of(JAVA, "-cp", classes, Sleeper.class.getName());
    }

    private static String read(InputStream stream) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        stream.transferTo(out);
        return out.toString(StandardCharsets.UTF_8);
    }

    /** Lanzador que se cuelga. */
    public static final class Sleeper {
        public static void main(String[] args) throws InterruptedException {
            Thread.sleep(60_000);
        }
    }

    /** Como el daemon BrowserStackLocal: deja un hijo (su fork) y sigue vivo. */
    public static final class Spawner {
        public static void main(String[] args) throws Exception {
            new ProcessBuilder(JAVA, "-cp", System.getProperty("java.class.path"), Sleeper.class.getName())
                    .redirectOutput(ProcessBuilder.Redirect.DISCARD)
                    .redirectError(ProcessBuilder.Redirect.DISCARD)
                    .start();
            Thread.sleep(60_000);
        }
    }

    static List<String> spawnerCommand() throws Exception {
        String classes = Paths.get(Spawner.class.getProtectionDomain().getCodeSource().getLocation().toURI()).toString();
        return List.of(JAVA, "-cp", classes, Spawner.class.getName());
    }

    /** Espera a que el proceso haya lanzado su hijo y lo devuelve. */
    static ProcessHandle childOf(ProcessHandle parent) throws InterruptedException {
        long deadline = System.currentTimeMillis() + 20_000;
        while (System.currentTimeMillis() < deadline) {
            Optional<ProcessHandle> child = parent.children().findFirst();
            if (child.isPresent()) {
                return child.get();
            }
            Thread.sleep(50);
        }
        throw new AssertionError("el proceso no lanzo su hijo");
    }

    /** Como Local, sin binario; isRunning() responde que si, como en Linux y macOS. */
    private static class RecordingLocal extends TrackedLocal {
        private Map<String, String> started;
        private Map<String, String> stoppedBy;
        private boolean stopped;
        private long pidOnStart;
        private boolean isRunningFails;
        private boolean runningByLibrary = true;
        private AtomicInteger preparing;
        private AtomicInteger mostAtOnce;

        @Override
        public void start(Map<String, String> options) throws Exception {
            started = options;
            if (preparing != null) {
                mostAtOnce.accumulateAndGet(preparing.incrementAndGet(), Math::max);
                Thread.sleep(50);
                preparing.decrementAndGet();
            }
            PID.setInt(this, (int) pidOnStart);
        }

        @Override
        public void stop() {
            stopped = true;
        }

        @Override
        public void stop(Map<String, String> options) {
            stoppedBy = options;
        }

        @Override
        public boolean isRunning() {
            if (isRunningFails) {
                throw new IllegalStateException("ps not available");
            }
            return runningByLibrary;
        }

        boolean isAborted() {
            try {
                runCommand(List.of(JAVA, "-version")).waitFor();
                return false;
            } catch (Exception e) {
                return true;
            }
        }
    }
}
