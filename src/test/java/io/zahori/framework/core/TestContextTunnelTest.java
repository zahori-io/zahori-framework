package io.zahori.framework.core;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.zahori.framework.core.FakeBinaries.Behaviour;
import io.zahori.framework.core.FakeBinaries.SettingsBuilder;
import io.zahori.framework.driver.browserfactory.Browsers;
import io.zahori.framework.driver.gridprovider.BrowserStackGridProvider;
import io.zahori.framework.driver.gridprovider.SelenoidGridProvider;
import io.zahori.framework.files.properties.ZahoriProperties;
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
    void aCaseThatNeedsTheTunnelAndHasNoneFailsWithoutAskingForTheSession() {
        List<String> sessions = new ArrayList<>();

        assertThatThrownBy(() -> context.createBrowser(() -> {
            sessions.add("asked");
            return null;
        }, true)).isInstanceOf(StepFailed.class);

        assertThat(context.failures).containsExactly(TestContext.MISSING_TUNNEL);
        assertThat(sessions).describedAs("la sesion iria con la plantilla del caso, que no levanta nadie").isEmpty();
    }

    @Test
    void aCaseThatDoesNotNeedTheTunnelAsksForTheSessionWithoutIt() {
        List<String> sessions = new ArrayList<>();

        assertThat(context.createBrowser(() -> {
            sessions.add("asked");
            return null;
        }, false)).isNull();

        assertThat(sessions).containsExactly("asked");
        assertThat(context.failures).isEmpty();
    }

    @Test
    void aTunnelThatBrowserStackSeesAsNotConnectedIsReplacedBeforeTheCaseGivesUp() {
        context.startRemoteTunnel(registry, "access-key", null);
        List<String> sessions = new ArrayList<>();

        context.createBrowser(() -> {
            sessions.add("asked");
            if (sessions.size() == 1) {
                throw new IllegalStateException("[BROWSERSTACK_LOCAL_CONNECTION_FAILED] Issues with the local testing configurations");
            }
            return null;
        }, true);

        assertThat(sessions).hasSize(2);
        assertThat(binaries.created.get()).isEqualTo(2);
        assertThat(context.isTunnelRunning()).isTrue();
        assertThat(context.getTunnelLocalIdentifier()).isEqualTo("zahori-host-1-1");
        assertThat(context.warnings).containsExactly("BrowserStack local connection with id zahori-host-1-1 recycled: "
                + BrowserStackLocalFailures.CONNECTION_FAILED_REASON);
    }

    @Test
    void aReplacementThatCannotStartFailsTheCaseWithTheReasonAndLeavesItWithoutTunnel() {
        context.startRemoteTunnel(registry, "access-key", null);
        binaries.script(Behaviour.CONNECTS, Behaviour.FAILS);

        assertThatThrownBy(() -> context.createBrowser(() -> {
            throw new IllegalStateException("[BROWSERSTACK_LOCAL_CONNECTION_FAILED]");
        }, true)).isInstanceOf(StepFailed.class);

        assertThat(context.failures).singleElement().asString().contains(FakeBinaries.FAILURE);
        assertThat(context.getTunnelLocalIdentifier()).isEmpty();
        assertThat(context.isTunnelRunning()).isFalse();
    }

    @Test
    void withoutPropertiesNoTunnelIsRequired() {
        assertThat(TestContext.localTunnelRequired(null, new BrowserStackGridProvider(), "linux")).isFalse();
    }

    @Test
    void theProcessSettingAsksForTheTunnelOnBrowserStack() {
        ZahoriProperties properties = mock(ZahoriProperties.class);
        when(properties.getProperty("zahori.test.execution.browserStackLocal.local")).thenReturn("true");

        assertThat(TestContext.localTunnelRequired(properties, new BrowserStackGridProvider(), "linux")).isTrue();
        assertThat(TestContext.localTunnelRequired(properties, new SelenoidGridProvider(), "linux")).isFalse();
    }

    @Test
    void theSessionComesBackToTheCase() {
        io.zahori.framework.core.Browser session = mock(io.zahori.framework.core.Browser.class);

        assertThat(context.createBrowser(() -> session, false)).isSameAs(session);
    }

    @Test
    void theSessionAskedAgainComesBackToTheCase() {
        context.startRemoteTunnel(registry, "access-key", null);
        io.zahori.framework.core.Browser session = mock(io.zahori.framework.core.Browser.class);
        List<String> sessions = new ArrayList<>();

        assertThat(context.createBrowser(() -> {
            sessions.add("asked");
            if (sessions.size() == 1) {
                throw new IllegalStateException("[BROWSERSTACK_LOCAL_CONNECTION_FAILED]");
            }
            return session;
        }, true)).isSameAs(session);
    }

    @Test
    void aCaseWithBrowserGetsItsDriverInitialized() {
        io.zahori.framework.core.Browser session = mock(io.zahori.framework.core.Browser.class);
        context.browserName = "chrome";
        context.nextBrowser = session;

        context.createDriver();

        assertThat(context.getBrowser()).isSameAs(session);
        assertThat(context.infos).contains("Driver initialized!");
    }

    @Test
    void aCaseWithoutBrowserGetsNone() {
        context.browserName = "";
        context.nextBrowser = mock(io.zahori.framework.core.Browser.class);

        context.createDriver();

        assertThat(context.getBrowser()).isNull();
        assertThat(context.infos).doesNotContain("Driver initialized!");
    }

    @Test
    void aGridThatDoesNotAskForTheTunnelLeavesTheCaseWithoutIt() {
        context.zahoriProperties = mock(ZahoriProperties.class);

        context.startRemoteTunnel();

        assertThat(context.getTunnelLocalIdentifier()).isEmpty();
        assertThat(context.failures).isEmpty();
    }

    @Test
    void aRemoteBrowserNeverGetsTheBrowserMobProxy() {
        ZahoriProperties properties = mock(ZahoriProperties.class);
        when(properties.isAddHeadersEnabled()).thenReturn(true);
        context.zahoriProperties = properties;
        context.remote = Browsers.REMOTE_YES;

        assertThat(context.createBrowserMobProxy()).isNull();

        assertThat(context.infos).containsExactly(
                "Remote browser on grid provider '{}': BrowserMobProxy only runs with a local browser, skipping it.");
    }

    @Test
    void withNothingToDoThereIsNoBrowserMobProxy() {
        context.zahoriProperties = mock(ZahoriProperties.class);
        context.remote = Browsers.REMOTE_NO;

        assertThat(context.createBrowserMobProxy()).isNull();

        assertThat(context.infos).isEmpty();
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

        private io.zahori.framework.core.Browser nextBrowser;

        RecordingContext() {
            super(caseExecution(), null);
        }

        @Override
        io.zahori.framework.core.Browser newBrowser() {
            return nextBrowser;
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
