package io.zahori.framework.core;

import static io.zahori.framework.core.FakeBinaries.args;
import static io.zahori.framework.core.FakeBinaries.runAll;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.zahori.framework.core.BrowserStackLocalTunnel.TunnelUnavailableException;
import io.zahori.framework.core.FakeBinaries.RecordingLog;
import io.zahori.framework.core.FakeBinaries.SettingsBuilder;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;

@Timeout(60)
class BrowserStackLocalTunnelRegistryTest {

    private static final String ACCOUNT_A = "key-a";
    private static final String ACCOUNT_B = "key-b";

    private final FakeBinaries binaries = new FakeBinaries();
    private final RecordingLog log = new RecordingLog();
    private final List<BrowserStackLocalTunnel> created = Collections.synchronizedList(new ArrayList<>());
    private final BrowserStackLocalTunnelRegistry registry = new BrowserStackLocalTunnelRegistry(identifier -> {
        BrowserStackLocalTunnel tunnel = new BrowserStackLocalTunnel(identifier, binaries::next,
                new SettingsBuilder().maxAttempts(2).build(), hook -> { }, duration -> { }, System::nanoTime);
        created.add(tunnel);
        return tunnel;
    }, "zahori-host-4242");

    @AfterEach
    void shutdownEveryTunnel() {
        binaries.releaseAll();
        created.forEach(tunnel -> tunnel.shutdown(new RecordingLog()));
    }

    @Test
    void casesOfDifferentExecutionsWithTheSameConfigurationShareTheTunnel() {
        Map<String, String> fromOneExecution = args(ACCOUNT_A);
        fromOneExecution.put(BrowserStackLocalTunnel.LOCAL_IDENTIFIER_ARG, "12006-1");
        Map<String, String> fromAnother = args(ACCOUNT_A);
        fromAnother.put(BrowserStackLocalTunnel.LOCAL_IDENTIFIER_ARG, "12007-9");

        assertThat(registry.tunnelFor(fromOneExecution)).isSameAs(registry.tunnelFor(fromAnother)).isSameAs(registry.tunnelFor(args(ACCOUNT_A)));
        assertThat(created).hasSize(1);
        assertThat(fromOneExecution).describedAs("los argumentos del caso no se tocan").containsEntry(BrowserStackLocalTunnel.LOCAL_IDENTIFIER_ARG, "12006-1");
    }

    @Test
    void eachConfigurationGetsItsOwnTunnelAndNumberedIdentifier() {
        Map<String, String> otherOptions = args(ACCOUNT_A);
        otherOptions.put("forcelocal", "false");

        assertThat(registry.tunnelFor(args(ACCOUNT_A)).identifier()).isEqualTo("zahori-host-4242-1");
        assertThat(registry.tunnelFor(args(ACCOUNT_B)).identifier()).isEqualTo("zahori-host-4242-2");
        assertThat(registry.tunnelFor(otherOptions).identifier()).isEqualTo("zahori-host-4242-3");
    }

    @Test
    void acceptsAMissingAccessKey() {
        assertThat(registry.tunnelFor(args(null))).isSameAs(registry.tunnelFor(args(null)));
    }

    @Test
    void casesAskingForTheSameConfigurationAtOnceGetASingleTunnel() throws Exception {
        CountDownLatch ready = new CountDownLatch(8);
        List<Callable<BrowserStackLocalTunnel>> tasks = new ArrayList<>();
        for (int i = 0; i < 8; i++) {
            tasks.add(() -> {
                ready.countDown();
                ready.await();
                return registry.tunnelFor(args(ACCOUNT_A));
            });
        }

        List<BrowserStackLocalTunnel> results = runAll(tasks);

        assertThat(results).containsOnly(results.get(0));
        assertThat(created).hasSize(1);
    }

    @Test
    void executionsOfTwoAccountsRunningAtOnceEachUseTheirOwnTunnel() throws Exception {
        List<String> outcomes = acquireAtOnce(ACCOUNT_A, ACCOUNT_B, ACCOUNT_A, ACCOUNT_B, ACCOUNT_A, ACCOUNT_B);

        String tunnelA = registry.tunnelFor(args(ACCOUNT_A)).identifier();
        String tunnelB = registry.tunnelFor(args(ACCOUNT_B)).identifier();
        assertThat(binaries.connected.get()).describedAs("un tunel por cuenta").isEqualTo(2);
        assertThat(outcomes).containsExactly(tunnelA, tunnelB, tunnelA, tunnelB, tunnelA, tunnelB);
    }

    @Test
    void anAccountThatCannotConnectDoesNotAffectTheOther() throws Exception {
        binaries.unreachableKey = ACCOUNT_B;

        List<String> outcomes = acquireAtOnce(ACCOUNT_A, ACCOUNT_B, ACCOUNT_A, ACCOUNT_B);

        String tunnelA = registry.tunnelFor(args(ACCOUNT_A)).identifier();
        assertThat(outcomes).containsExactly(tunnelA, "failed", tunnelA, "failed");
        assertThat(registry.tunnelFor(args(ACCOUNT_A)).isRunning()).isTrue();
    }

    @Test
    void anAccountWhoseStartHangsDoesNotBlockTheOther() throws Exception {
        binaries.hangingKey = ACCOUNT_B;
        ExecutorService pool = Executors.newSingleThreadExecutor();
        try {
            pool.submit(() -> registry.tunnelFor(args(ACCOUNT_B)).acquire(args(ACCOUNT_B), log));
            binaries.awaitStarted(1);

            long start = System.nanoTime();
            String tunnelA = registry.tunnelFor(args(ACCOUNT_A)).acquire(args(ACCOUNT_A), log);

            assertThat(tunnelA).isEqualTo(registry.tunnelFor(args(ACCOUNT_A)).identifier());
            assertThat(Duration.ofNanos(System.nanoTime() - start)).isLessThan(Duration.ofSeconds(2));
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    void buildsTheBinaryArgumentsOfAnAccount() {
        assertThat(BrowserStackLocalTunnelRegistry.argsFor("access-key", null))
                .containsOnly(Map.entry("key", "access-key"), Map.entry("forcelocal", "true"), Map.entry("onlyAutomate", "true"));
        assertThat(BrowserStackLocalTunnelRegistry.argsFor("access-key", " ")).doesNotContainKey("binarypath");
        assertThat(BrowserStackLocalTunnelRegistry.argsFor("access-key", " /opt/browserstack/BrowserStackLocal "))
                .containsEntry("binarypath", "/opt/browserstack/BrowserStackLocal");
    }

    @Test
    void theIdentifierIsUniqueInTheAccountAndUsesOnlySafeCharacters() {
        Instant start = Instant.ofEpochSecond(1_760_000_000L);

        assertThat(BrowserStackLocalTunnelRegistry.identifierPrefix("aeazahori01.globalia.local", 4242, start))
                .isEqualTo("zahori-aeazahori01-globalia-local-4242-1760000000");
        assertThat(BrowserStackLocalTunnelRegistry.identifierPrefix("MAC_book Pro", 7, start)).isEqualTo("zahori-MAC-book-Pro-7-1760000000");
        assertThat(BrowserStackLocalTunnelRegistry.identifierPrefix(null, 7, start)).isEqualTo("zahori-host-7-1760000000");
        assertThat(BrowserStackLocalTunnelRegistry.identifierPrefix(" ", 7, start)).isEqualTo("zahori-host-7-1760000000");
        assertThat(BrowserStackLocalTunnelRegistry.identifierPrefix("a".repeat(60), 7, start))
                .describedAs("un FQDN largo no alarga sin limite el identificador").isEqualTo("zahori-" + "a".repeat(32) + "-7-1760000000");
        assertThat(BrowserStackLocalTunnelRegistry.localHostName(() -> "aeazahori01")).isEqualTo("aeazahori01");
        assertThat(BrowserStackLocalTunnelRegistry.localHostName(() -> {
            throw new UnknownHostException("no dns");
        })).isNull();
    }

    @Test
    void theProcessTunnelsAreNamedAfterThisMachineAndJvm() {
        ProcessHandle jvm = ProcessHandle.current();
        // Mismo calculo que produccion: sin DNS ambos dan "host" y el test no depende de la red
        String expected = BrowserStackLocalTunnelRegistry.identifierPrefix(
                BrowserStackLocalTunnelRegistry.localHostName(() -> InetAddress.getLocalHost().getHostName()), jvm.pid(),
                jvm.info().startInstant().orElseThrow());

        assertThat(BrowserStackLocalTunnelRegistry.forThisProcess().tunnelFor(args(ACCOUNT_A)).identifier()).isEqualTo(expected + "-1");
    }

    @Test
    void theProcessRegistryStopsTheDaemonsLeftByADeadZahoriProcessFirst() throws Exception {
        Process orphan = new ProcessBuilder(BrowserStackLocalProcessTest.JAVA, "-cp",
                Paths.get(BrowserStackLocalProcessTest.Sleeper.class.getProtectionDomain().getCodeSource().getLocation().toURI()).toString(),
                BrowserStackLocalProcessTest.Sleeper.class.getName()).start();
        try {
            Process deadJvm = new ProcessBuilder(BrowserStackLocalProcessTest.JAVA, "-version").start();
            assertThat(deadJvm.waitFor(30, TimeUnit.SECONDS)).isTrue();
            String identifier = "zahori-test-" + orphan.pid();
            new LocalTunnelLedger(Path.of(System.getProperty("java.io.tmpdir"), "zahori-browserstack-local"), deadJvm.toHandle(),
                    ProcessHandle::of).record(identifier, orphan.toHandle());

            BrowserStackLocalTunnelRegistry.forThisProcess();

            assertThat(orphan.waitFor(15, TimeUnit.SECONDS)).describedAs("el daemon huerfano muere al arrancar").isTrue();
        } finally {
            orphan.destroyForcibly();
        }
    }

    @Test
    void theRealTunnelsDriveTheRealBinary(@TempDir Path directory) {
        LocalTunnelLedger ledger = new LocalTunnelLedger(directory, ProcessHandle.current(), ProcessHandle::of);

        assertThat(BrowserStackLocalTunnelRegistry.realBinaries(ledger).get()).isInstanceOf(BrowserStackLocalProcess.class);
    }

    @Test
    void reapingOrphansNeverFailsTheProcess(@TempDir Path directory) throws Exception {
        Files.createDirectories(directory.resolve("broken.tunnel"));

        assertThatCode(() -> BrowserStackLocalTunnelRegistry.reapOrphans(
                new LocalTunnelLedger(directory, ProcessHandle.current(), ProcessHandle::of))).doesNotThrowAnyException();
    }

    @Test
    void rejectsMissingCollaborators() {
        assertThatThrownBy(() -> new BrowserStackLocalTunnelRegistry(null, "zahori-host-1")).hasMessage("tunnelFactory");
        assertThatThrownBy(() -> new BrowserStackLocalTunnelRegistry(identifier -> null, null)).hasMessage("identifierPrefix");
    }

    private List<String> acquireAtOnce(String... accounts) throws Exception {
        CountDownLatch ready = new CountDownLatch(accounts.length);
        List<Callable<String>> tasks = new ArrayList<>();
        for (String account : accounts) {
            tasks.add(() -> {
                ready.countDown();
                ready.await();
                try {
                    return registry.tunnelFor(args(account)).acquire(args(account), log);
                } catch (TunnelUnavailableException e) {
                    return "failed";
                }
            });
        }
        return runAll(tasks);
    }
}
