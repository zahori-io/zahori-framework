package io.zahori.framework.core;

import static org.assertj.core.api.Assertions.assertThat;

import io.zahori.framework.driver.browserfactory.Browsers;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

class TestContextBrowserMobProxyTest {

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {Browsers.REMOTE_NO, "no"})
    void aLocalBrowserReachesTheProxyOnThisMachine(String remote) {
        assertThat(TestContext.browserMobProxyHost(remote)).isEqualTo("localhost");
    }

    @ParameterizedTest
    @ValueSource(strings = {Browsers.REMOTE_YES, "yes"})
    void aRemoteBrowserGetsNoProxy(String remote) {
        // En la maquina de AE el flujo no avanzaba con Selenoid pasando por el proxy de este proceso
        assertThat(TestContext.browserMobProxyHost(remote)).isNull();
    }
}
