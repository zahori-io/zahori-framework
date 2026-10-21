package io.zahori.framework.core;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;

@Timeout(60)
class LocalTunnelLedgerTest {

    private static final Duration GRACE = Duration.ofMillis(200);

    @TempDir
    Path directory;

    private final List<Process> launched = new ArrayList<>();

    @AfterEach
    void killWhatWasLaunched() {
        launched.forEach(Process::destroyForcibly);
    }

    @Test
    void stopsTheDaemonOfAJvmThatDiedAndForgetsIt() throws Exception {
        Process daemon = sleeper();
        LocalTunnelLedger deadJvmLedger = new LocalTunnelLedger(directory, finishedProcess(), ProcessHandle::of);
        deadJvmLedger.record("zahori-host-1-1", daemon.toHandle());

        int reaped = ledgerOfThisJvm().reapOrphans();

        assertThat(reaped).isEqualTo(1);
        assertThat(daemon.waitFor(10, TimeUnit.SECONDS)).describedAs("el daemon huerfano muere").isTrue();
        assertThat(entries()).isEmpty();
    }

    @Test
    void leavesTheDaemonsOfLiveJvmsAlone() throws Exception {
        Process daemon = sleeper();
        ledgerOfThisJvm().record("zahori-host-1-1", daemon.toHandle());

        int reaped = ledgerOfThisJvm().reapOrphans();

        assertThat(reaped).isZero();
        assertThat(daemon.isAlive()).isTrue();
        assertThat(entries()).containsExactly("zahori-host-1-1.tunnel");
    }

    @Test
    void aPidReusedByAnotherProcessDoesNotMakeAnOldJvmLookAlive() throws Exception {
        Process daemon = sleeper();
        new LocalTunnelLedger(directory, ProcessHandle.current(), ProcessHandle::of).record("zahori-host-1-1", daemon.toHandle());
        rewrite("zahori-host-1-1.tunnel", "jvmStart=.*", "jvmStart=2000-01-01T00\\\\:00\\\\:00Z");

        int reaped = ledgerOfThisJvm().reapOrphans();

        assertThat(reaped).describedAs("misma JVM por pid, pero arrancada en otro momento").isEqualTo(1);
        assertThat(daemon.waitFor(10, TimeUnit.SECONDS)).isTrue();
    }

    @Test
    void aDaemonPidReusedByAnotherProcessIsNotKilled() throws Exception {
        Process stranger = sleeper();
        new LocalTunnelLedger(directory, finishedProcess(), ProcessHandle::of).record("zahori-host-1-1", stranger.toHandle());
        rewrite("zahori-host-1-1.tunnel", "daemonStart=.*", "daemonStart=2000-01-01T00\\\\:00\\\\:00Z");

        int reaped = ledgerOfThisJvm().reapOrphans();

        assertThat(reaped).isZero();
        assertThat(stranger.isAlive()).describedAs("el pid ya es de otro proceso: no se toca").isTrue();
        assertThat(entries()).describedAs("la entrada muerta se borra igualmente").isEmpty();
    }

    @Test
    void aDaemonThatSurvivesItsTerminationIsNotCountedButItsEntryIsForgotten() throws Exception {
        Process daemon = sleeper();
        new LocalTunnelLedger(directory, finishedProcess(), ProcessHandle::of).record("zahori-host-1-1", daemon.toHandle());

        int reaped = new LocalTunnelLedger(directory, ProcessHandle.current(), ProcessHandle::of, handle -> false).reapOrphans();

        assertThat(reaped).isZero();
        assertThat(entries()).isEmpty();
    }

    @Test
    void rejectsMissingCollaborators() {
        assertThatThrownBy(() -> new LocalTunnelLedger(null, ProcessHandle.current(), ProcessHandle::of)).hasMessage("directory");
        assertThatThrownBy(() -> new LocalTunnelLedger(directory, null, ProcessHandle::of)).hasMessage("jvm");
        assertThatThrownBy(() -> new LocalTunnelLedger(directory, ProcessHandle.current(), null)).hasMessage("processes");
        assertThatThrownBy(() -> new LocalTunnelLedger(directory, ProcessHandle.current(), ProcessHandle::of, null)).hasMessage("terminator");
    }

    @Test
    void anEntryWhoseDaemonAlreadyDiedIsJustForgotten() throws Exception {
        new LocalTunnelLedger(directory, finishedProcess(), ProcessHandle::of).record("zahori-host-1-1", finishedProcess());

        assertThat(ledgerOfThisJvm().reapOrphans()).isZero();
        assertThat(entries()).isEmpty();
    }

    @Test
    void aStartInstantTheSystemDoesNotGiveIsNotRequired() throws Exception {
        Process daemon = sleeper();
        new LocalTunnelLedger(directory, finishedProcess(), ProcessHandle::of).record("zahori-host-1-1", daemon.toHandle());
        rewrite("zahori-host-1-1.tunnel", "daemonStart=.*", "daemonStart=");

        assertThat(ledgerOfThisJvm().reapOrphans()).isEqualTo(1);
    }

    @Test
    void aCorruptOrForeignEntryDoesNotStopTheOthers() throws Exception {
        Process daemon = sleeper();
        new LocalTunnelLedger(directory, finishedProcess(), ProcessHandle::of).record("zahori-host-1-2", daemon.toHandle());
        Files.writeString(directory.resolve("zahori-host-1-1.tunnel"), "jvmPid=not-a-number");
        Files.createDirectory(directory.resolve("zahori-host-1-0.tunnel"));

        assertThat(ledgerOfThisJvm().reapOrphans()).isEqualTo(1);
        assertThat(daemon.waitFor(10, TimeUnit.SECONDS)).isTrue();
    }

    @Test
    void withoutEntriesThereIsNothingToReap() {
        assertThat(new LocalTunnelLedger(directory.resolve("missing"), ProcessHandle.current(), ProcessHandle::of).reapOrphans()).isZero();
        assertThat(ledgerOfThisJvm().reapOrphans()).isZero();
    }

    @Test
    void forgettingRemovesTheEntryAndToleratesAMissingOne() throws Exception {
        LocalTunnelLedger ledger = ledgerOfThisJvm();
        ledger.record("zahori-host-1-1", ProcessHandle.current());

        ledger.forget("zahori-host-1-1");
        ledger.forget("zahori-host-1-1");

        assertThat(entries()).isEmpty();
    }

    @Test
    void anEntryThatCannotBeWrittenOrDeletedDoesNotBreakTheTunnel() throws Exception {
        Path notADirectory = Files.writeString(directory.resolve("file"), "x");
        LocalTunnelLedger ledger = new LocalTunnelLedger(notADirectory, ProcessHandle.current(), ProcessHandle::of);
        Files.createDirectories(directory.resolve("busy.tunnel").resolve("child"));

        assertThatCode(() -> ledger.record("zahori-host-1-1", ProcessHandle.current())).doesNotThrowAnyException();
        assertThatCode(() -> ledgerOfThisJvm().forget("busy")).doesNotThrowAnyException();
        assertThat(Files.isDirectory(directory.resolve("busy.tunnel"))).isTrue();
    }

    @Test
    void recordsTheDaemonAndItsJvmByPidAndStartInstant() throws Exception {
        Process daemon = sleeper();

        ledgerOfThisJvm().record("zahori-host-1-1", daemon.toHandle());

        Properties entry = new Properties();
        try (var in = Files.newInputStream(directory.resolve("zahori-host-1-1.tunnel"))) {
            entry.load(in);
        }
        assertThat(entry).containsEntry("daemonPid", String.valueOf(daemon.pid()))
                .containsEntry("daemonStart", daemon.toHandle().info().startInstant().orElseThrow().toString())
                .containsEntry("jvmPid", String.valueOf(ProcessHandle.current().pid()))
                .containsEntry("jvmStart", ProcessHandle.current().info().startInstant().orElseThrow().toString());
    }

    @Test
    void aDaemonThatStopsWhenAskedIsNotForced() {
        StubProcessHandle daemon = new StubProcessHandle(true, true);

        assertThat(LocalTunnelLedger.terminate(daemon, GRACE)).isTrue();
        assertThat(daemon.forced).describedAs("se le deja desconectarse de BrowserStack").isZero();
    }

    @Test
    void aDaemonThatIgnoresTheStopIsForced() {
        StubProcessHandle daemon = new StubProcessHandle(false, true);

        assertThat(LocalTunnelLedger.terminate(daemon, GRACE)).isTrue();
        assertThat(daemon.destroys).isEqualTo(1);
        assertThat(daemon.forced).isEqualTo(1);
    }

    @Test
    void theForkOfTheDaemonIsStoppedToo() {
        StubProcessHandle daemon = new StubProcessHandle(true, true);
        StubProcessHandle fork = new StubProcessHandle(false, true);
        daemon.forks.add(fork);

        assertThat(LocalTunnelLedger.terminate(daemon, GRACE)).isTrue();
        assertThat(fork.forced).describedAs("en Windows el fork no muere con el daemon").isEqualTo(1);
    }

    @Test
    void aDaemonWhoseForkSurvivesIsNotCountedAsStopped() {
        StubProcessHandle daemon = new StubProcessHandle(true, true);
        daemon.forks.add(new StubProcessHandle(false, false));

        assertThat(LocalTunnelLedger.terminate(daemon, GRACE)).isFalse();
    }

    @Test
    void aDaemonThatSurvivesEverythingIsNotCountedAsStopped() {
        assertThat(LocalTunnelLedger.terminate(new StubProcessHandle(false, false), GRACE)).isFalse();
        assertThat(LocalTunnelLedger.terminator(GRACE).test(new StubProcessHandle(false, false))).isFalse();
        assertThat(LocalTunnelLedger.terminator(GRACE).test(new StubProcessHandle(true, true))).isTrue();
    }

    @Test
    void aDaemonWhoseExitCannotBeObservedIsJudgedByWhetherItIsAlive() {
        StubProcessHandle daemon = new StubProcessHandle(false, false);
        daemon.exit.completeExceptionally(new IllegalStateException("no exit"));

        assertThat(LocalTunnelLedger.terminate(daemon, GRACE)).describedAs("ya no esta vivo").isTrue();
    }

    @Test
    void anInterruptedReaperDoesNotWaitAndKeepsTheFlag() {
        StubProcessHandle daemon = new StubProcessHandle(false, false);
        Thread.currentThread().interrupt();

        boolean stopped = LocalTunnelLedger.terminate(daemon, Duration.ofSeconds(30));

        assertThat(stopped).isFalse();
        assertThat(Thread.interrupted()).describedAs("la interrupcion no se puede tragar").isTrue();
    }

    @Test
    void theJvmLedgerLivesInTheTemporaryDirectory() {
        assertThatCode(() -> LocalTunnelLedger.forThisJvm().reapOrphans()).doesNotThrowAnyException();
    }

    private LocalTunnelLedger ledgerOfThisJvm() {
        return new LocalTunnelLedger(directory, ProcessHandle.current(), ProcessHandle::of);
    }

    private List<String> entries() throws IOException {
        try (var files = Files.list(directory)) {
            return files.map(file -> file.getFileName().toString()).filter(name -> name.endsWith(".tunnel")).toList();
        }
    }

    private void rewrite(String entry, String regex, String replacement) throws IOException {
        Path file = directory.resolve(entry);
        Files.writeString(file, Files.readString(file).replaceAll(regex, replacement));
    }

    private Process sleeper() throws Exception {
        String classes = Paths.get(BrowserStackLocalProcessTest.Sleeper.class.getProtectionDomain().getCodeSource().getLocation().toURI())
                .toString();
        Process process = new ProcessBuilder(BrowserStackLocalProcessTest.JAVA, "-cp", classes,
                BrowserStackLocalProcessTest.Sleeper.class.getName()).start();
        launched.add(process);
        return process;
    }

    private static ProcessHandle finishedProcess() throws Exception {
        Process process = new ProcessBuilder(BrowserStackLocalProcessTest.JAVA, "-version").start();
        assertThat(process.waitFor(30, TimeUnit.SECONDS)).isTrue();
        return process.toHandle();
    }
}
