package io.zahori.framework.core;

import io.zahori.framework.driver.gridprovider.BrowserStackGridProvider;
import io.zahori.framework.driver.gridprovider.GenericGridProvider;
import io.zahori.framework.driver.gridprovider.GridProvider;
import io.zahori.framework.driver.gridprovider.SelenoidGridProvider;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

class TestContextLocalTunnelTest {
    private final GridProvider browserStack = new BrowserStackGridProvider();
    private final GridProvider selenoid = new SelenoidGridProvider();
    private final GridProvider generic = new GenericGridProvider();

    @Test
    void startsTheTunnel_whenTheGridIsRemoteAndTheExecutionAsksForIt() {
        Assertions.assertThat(TestContext.shouldStartLocalTunnel(this.browserStack, "true")).isTrue();
    }

    @Test
    void doesNotStartIt_onALocalGrid_evenThoughThePlatformDeclaresTheCapability() {
        Assertions.assertThat(TestContext.shouldStartLocalTunnel(this.selenoid, "true")).isFalse();
    }

    @Test
    void doesNotStartIt_onAGridWeKnowNothingAbout() {
        Assertions.assertThat(TestContext.shouldStartLocalTunnel(this.generic, "true")).isFalse();
    }

    @Test
    void doesNotStartIt_whenTheExecutionDidNotAskForIt() {
        Assertions.assertThat(TestContext.shouldStartLocalTunnel(this.browserStack, "false")).isFalse();
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(
        strings = {"   ", "no", "1", "yes"}
    )
    void doesNotStartIt_whenTheCapabilityIsAbsentOrNotATrue(String capability) {
        Assertions.assertThat(TestContext.shouldStartLocalTunnel(this.browserStack, capability)).isFalse();
    }

    @Test
    void doesNotStartIt_whenNoProviderWasResolved() {
        Assertions.assertThat(TestContext.shouldStartLocalTunnel(null, "true")).isFalse();
    }

    @Test
    void theProcessLogSaysWhichGridLeftTheCaseWithoutTunnel() {
        Assertions.assertThat(TestContext.tunnelNotStartedReason(this.selenoid, "true"))
                .isEqualTo("BrowserStack local connection not started: grid provider selenoid (no local tunnel), local setting 'true'");
    }

    @Test
    void theProcessLogSaysTheSettingDidNotAskForIt() {
        Assertions.assertThat(TestContext.tunnelNotStartedReason(this.browserStack, "false"))
                .isEqualTo("BrowserStack local connection not started: grid provider browserstack, local setting 'false'");
    }

    @Test
    void theProcessLogSaysNoGridWasResolved() {
        Assertions.assertThat(TestContext.tunnelNotStartedReason(null, null))
                .isEqualTo("BrowserStack local connection not started: grid provider none resolved, local setting 'null'");
    }
}
