package io.zahori.framework.core;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.zahori.framework.core.FakeBinaries.Behaviour;
import io.zahori.framework.core.FakeBinaries.SettingsBuilder;
import io.zahori.model.process.Browser;
import io.zahori.model.process.Case;
import io.zahori.model.process.CaseExecution;
import io.zahori.model.process.Configuration;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

@Timeout(60)
class TestContextTunnelTest {

    private final FakeBinaries binaries = new FakeBinaries();
    private final List<BrowserStackLocalTunnel> created = new ArrayList<>();
    private final BrowserStackLocalTunnelRegistry registry = new BrowserStackLocalTunnelRegistry(identifier -> {
        BrowserStackLocalTunnel tunnel = new BrowserStackLocalTunnel(identifier, binaries::next,
                new SettingsBuilder().maxAttempts(1).build(), hook -> { }, duration -> { }, System::nanoTime);
        created.add(tunnel);
        return tunnel;
    }, "zahori-host-1");
    private final RecordingContext context = new RecordingContext();

    @AfterEach
    void shutdownEveryTunnel() {
        created.forEach(tunnel -> tunnel.shutdown(new FakeBinaries.RecordingLog()));
    }

    @Test
    void aCaseWithoutTunnelHasNoIdentifier() {
        assertThat(context.getTunnelLocalIdentifier()).isEmpty();
        assertThat(context.isTunnelRunning()).isFalse();
    }

    @Test
    void aCaseKeepsTheIdentifierOfItsTunnelAndLogsItInItsEvidences() {
        context.startRemoteTunnel(registry, "access-key", null);

        assertThat(context.getTunnelLocalIdentifier()).isEqualTo("zahori-host-1-1");
        assertThat(context.isTunnelRunning()).isTrue();
        assertThat(context.infos).anyMatch(m -> m.startsWith("BrowserStack local connection started with id zahori-host-1-1"));
        assertThat(binaries.all.get(0).startArgs).containsEntry("key", "access-key").containsEntry("onlyAutomate", "true")
                .containsEntry("forcelocal", "true").doesNotContainKey("binarypath");
    }

    @Test
    void theConfiguredBinaryIsUsedSoJvmsDoNotDownloadItAtOnce() {
        context.startRemoteTunnel(registry, "access-key", "/opt/browserstack/BrowserStackLocal");

        assertThat(binaries.all.get(0).startArgs).containsEntry("binarypath", "/opt/browserstack/BrowserStackLocal");
    }

    @Test
    void aCaseWithoutAccessKeyFailsWithoutAskingForATunnel() {
        assertThatThrownBy(() -> context.startRemoteTunnel(registry, " ", null)).isInstanceOf(StepFailed.class);

        assertThat(context.failures).containsExactly(
                "BrowserStack local connection is enabled in zahori.properties but the access key is not defined");
        assertThat(binaries.created.get()).isZero();
    }

    @Test
    void aCaseThatCannotGetTheTunnelFailsWithTheReasonAndKeepsNoTunnel() {
        binaries.script(Behaviour.FAILS);

        assertThatThrownBy(() -> context.startRemoteTunnel(registry, "access-key", null)).isInstanceOf(StepFailed.class);

        assertThat(context.failures).containsExactly(
                "BrowserStack local connection failed (id zahori-host-1-1) [attempt 1/1]: " + FakeBinaries.FAILURE);
        assertThat(context.getTunnelLocalIdentifier()).isEmpty();
        binaries.script(Behaviour.CONNECTS);
        new RecordingContext().startRemoteTunnel(registry, "access-key", null);
        assertThat(context.isTunnelRunning()).describedAs("aunque otro caso lo levante despues").isFalse();
    }

    @Test
    void aRetryThatCannotGetTheTunnelForgetsTheOneItHad() {
        context.startRemoteTunnel(registry, "access-key", null);
        binaries.killAll();
        binaries.script(Behaviour.FAILS);

        assertThatThrownBy(() -> context.startRemoteTunnel(registry, "access-key", null)).isInstanceOf(StepFailed.class);

        assertThat(context.getTunnelLocalIdentifier()).isEmpty();
        assertThat(context.isTunnelRunning()).isFalse();
    }

    @SuppressWarnings("removal")
    @Test
    void aCaseCannotStopTheTunnelOtherExecutionsAreUsing() {
        context.startRemoteTunnel(registry, "access-key", null);

        context.stopRemoteTunnel();

        assertThat(context.isTunnelRunning()).isTrue();
    }

    @Test
    void theTunnelLogGoesToTheCaseEvidences() {
        BrowserStackLocalTunnel.TunnelLog log = context.tunnelLog();

        log.info("info");
        log.warn("warn");
        log.error("error");

        assertThat(context.infos).containsExactly("info");
        assertThat(context.warnings).containsExactly("warn");
        assertThat(context.errors).containsExactly("error");
    }

    private static CaseExecution caseExecution() {
        Case cas = new Case();
        cas.setName("TestContextTunnelTest");
        Browser browser = new Browser();
        browser.setBrowserName("chrome");
        browser.setDefaultVersion("latest");
        Configuration configuration = new Configuration();
        configuration.setName("osx");
        configuration.setEnvironmentName("staging");
        configuration.setTimeout(1L);
        CaseExecution caseExecution = new CaseExecution();
        caseExecution.setCaseExecutionId(1L);
        caseExecution.setCas(cas);
        caseExecution.setBrowser(browser);
        caseExecution.setConfiguration(configuration);
        caseExecution.setScreenResolution("1920x1080");
        return caseExecution;
    }

    private static final class StepFailed extends RuntimeException {
        private static final long serialVersionUID = 1L;

        StepFailed(String message) {
            super(message);
        }
    }

    /** Como TestContext: un paso fallido corta el caso. */
    private static final class RecordingContext extends TestContext {
        private final List<String> infos = new ArrayList<>();
        private final List<String> warnings = new ArrayList<>();
        private final List<String> errors = new ArrayList<>();
        private final List<String> failures = new ArrayList<>();

        RecordingContext() {
            super(caseExecution(), null);
        }

        @Override
        public void logInfo(String text, String... textArgs) {
            infos.add(text);
        }

        @Override
        public void logWarn(String text, String... textArgs) {
            warnings.add(text);
        }

        @Override
        public void logError(String text, String... textArgs) {
            errors.add(text);
        }

        @Override
        public void logStepFailed(String description, String... descriptionArgs) {
            failures.add(description);
            throw new StepFailed(description);
        }
    }
}
