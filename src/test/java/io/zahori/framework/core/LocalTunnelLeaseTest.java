package io.zahori.framework.core;

import static io.zahori.framework.core.FakeBinaries.args;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.zahori.framework.core.BrowserStackLocalTunnel.TunnelUnavailableException;
import io.zahori.framework.core.FakeBinaries.Behaviour;
import io.zahori.framework.core.FakeBinaries.RecordingLog;
import io.zahori.framework.core.FakeBinaries.SettingsBuilder;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.function.Supplier;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

@Timeout(60)
class LocalTunnelLeaseTest {

    private static final String ID = "zahori-host-1-1";
    private static final String NOT_CONNECTED = "[browserstack.local] is set to true but local testing through BrowserStack is not connected.";

    private final FakeBinaries binaries = new FakeBinaries();
    private final RecordingLog log = new RecordingLog();
    private final Map<String, String> args = args("key");
    private final BrowserStackLocalTunnel tunnel = new BrowserStackLocalTunnel(ID, binaries::next,
            new SettingsBuilder().maxAttempts(1).build(), hook -> { }, duration -> { }, System::nanoTime);

    @AfterEach
    void shutdownTheTunnel() {
        binaries.releaseAll();
        tunnel.shutdown(new RecordingLog());
    }

    @Test
    void aSessionThatStartsDoesNotTouchTheTunnel() throws Exception {
        LocalTunnelLease lease = LocalTunnelLease.acquire(tunnel, args, log);

        assertThat(lease.create(() -> "session")).isEqualTo("session");

        assertThat(binaries.created.get()).isEqualTo(1);
        assertThat(binaries.stops.get()).isZero();
        assertThat(log.warnings).isEmpty();
    }

    @Test
    void aFailureThatIsNotOfTheTunnelReachesTheCaseAsIs() throws Exception {
        LocalTunnelLease lease = LocalTunnelLease.acquire(tunnel, args, log);
        IllegalStateException other = new IllegalStateException("Invalid platformName");

        assertThatThrownBy(() -> lease.create(() -> {
            throw other;
        })).isSameAs(other);

        assertThat(binaries.stops.get()).isZero();
        assertThat(tunnel.isRunning()).isTrue();
    }

    @Test
    void aTunnelThatBrowserStackSeesAsNotConnectedIsReplacedAndTheSessionAskedAgain() throws Exception {
        LocalTunnelLease lease = LocalTunnelLease.acquire(tunnel, args, log);
        LocalTunnelProcess failed = tunnel.current();
        Deque<Supplier<String>> attempts = new ArrayDeque<>();
        attempts.add(() -> {
            throw new RuntimeException(NOT_CONNECTED);
        });
        attempts.add(() -> "session");

        assertThat(lease.create(() -> attempts.pop().get())).isEqualTo("session");

        assertThat(binaries.stops.get()).isEqualTo(1);
        assertThat(tunnel.current()).isNotNull().isNotSameAs(failed);
        assertThat(tunnel.isRunning()).isTrue();
        assertThat(log.warnings).containsExactly("BrowserStack local connection with id " + ID + " recycled: "
                + BrowserStackLocalFailures.NOT_CONNECTED_REASON);
    }

    @Test
    void theSessionIsAskedAgainOnlyOnce() throws Exception {
        LocalTunnelLease lease = LocalTunnelLease.acquire(tunnel, args, log);
        RuntimeException second = new RuntimeException("again: " + NOT_CONNECTED);
        Deque<RuntimeException> failures = new ArrayDeque<>();
        failures.add(new RuntimeException(NOT_CONNECTED));
        failures.add(second);

        assertThatThrownBy(() -> lease.create(() -> {
            throw failures.pop();
        })).isSameAs(second);

        assertThat(binaries.stops.get()).isEqualTo(1);
        assertThat(binaries.created.get()).isEqualTo(2);
    }

    @Test
    void theSecondFailureIsJudgedAgainstTheReplacementTunnel() throws Exception {
        LocalTunnelLease lease = LocalTunnelLease.acquire(tunnel, args, log);
        Deque<Supplier<String>> attempts = new ArrayDeque<>();
        attempts.add(() -> {
            throw new RuntimeException(NOT_CONNECTED);
        });
        attempts.add(() -> "session");
        lease.create(() -> attempts.pop().get());
        LocalTunnelProcess replacement = tunnel.current();
        attempts.add(() -> {
            throw new RuntimeException(NOT_CONNECTED);
        });
        attempts.add(() -> "second session");

        assertThat(lease.create(() -> attempts.pop().get())).isEqualTo("second session");

        assertThat(binaries.stops.get()).describedAs("el sustituto tambien se releva").isEqualTo(2);
        assertThat(tunnel.current()).isNotSameAs(replacement);
    }

    @Test
    void aCaseLateToTheFailureReusesTheTunnelAnotherCaseAlreadyReplaced() throws Exception {
        LocalTunnelLease late = LocalTunnelLease.acquire(tunnel, args, log);
        LocalTunnelLease first = LocalTunnelLease.acquire(tunnel, args, log);
        Deque<Supplier<String>> attempts = new ArrayDeque<>();
        attempts.add(() -> {
            throw new RuntimeException(NOT_CONNECTED);
        });
        attempts.add(() -> "first");
        first.create(() -> attempts.pop().get());
        LocalTunnelProcess replacement = tunnel.current();
        attempts.add(() -> {
            throw new RuntimeException(NOT_CONNECTED);
        });
        attempts.add(() -> "late");

        assertThat(late.create(() -> attempts.pop().get())).isEqualTo("late");

        assertThat(tunnel.current()).describedAs("no para el que ya levanto el otro caso").isSameAs(replacement);
        assertThat(binaries.stops.get()).isEqualTo(1);
    }

    @Test
    void ifTheReplacementCannotStartTheCaseGetsTheReason() throws Exception {
        LocalTunnelLease lease = LocalTunnelLease.acquire(tunnel, args, log);
        binaries.script(Behaviour.CONNECTS, Behaviour.FAILS);

        assertThatThrownBy(() -> lease.create(() -> {
            throw new RuntimeException(NOT_CONNECTED);
        })).isInstanceOf(TunnelUnavailableException.class).hasMessageContaining(FakeBinaries.FAILURE);
    }

    @Test
    void aTunnelThatCannotBeAcquiredGivesNoLease() {
        binaries.script(Behaviour.FAILS);

        assertThatThrownBy(() -> LocalTunnelLease.acquire(tunnel, args, log)).isInstanceOf(TunnelUnavailableException.class);
    }
}
