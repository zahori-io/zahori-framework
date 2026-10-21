package io.zahori.framework.core;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.zahori.framework.files.properties.ZahoriProperties;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;

class TestContextTunnelPlatformTest {

    private static final String OWN = "zahori.test.execution.browserStackLocal.accessKey";
    private static final String WINDOWS = "zahori.test.capabilities.add.windows.bstack:options.accessKey";
    private static final String ANDROID = "zahori.test.capabilities.add.android.bstack:options.accessKey";

    @ParameterizedTest
    @CsvSource({"Windows 11,windows", "WINDOWS 10,windows", "Mac OS X,osx", "Darwin,osx", "Linux,linux", "FreeBSD,linux"})
    void processPlatform_followsTheOperatingSystem(String osName, String expected) {
        Assertions.assertThat(TestContext.processPlatform(osName)).isEqualTo(expected);
    }

    @ParameterizedTest
    @NullAndEmptySource
    void processPlatform_defaultsToLinux(String osName) {
        Assertions.assertThat(TestContext.processPlatform(osName)).isEqualTo("linux");
    }

    @Test
    void processPlatform_readsTheRunningJvm() {
        Assertions.assertThat(TestContext.processPlatform()).isEqualTo(TestContext.processPlatform(System.getProperty("os.name")));
    }

    @Test
    void tunnelSetting_prefersTheProcessOwnSetting() {
        ZahoriProperties properties = mock(ZahoriProperties.class);
        when(properties.getProperty(OWN)).thenReturn("own");
        when(properties.getProperty(WINDOWS)).thenReturn("windows");

        Assertions.assertThat(TestContext.tunnelSetting(properties, "accessKey", "windows")).isEqualTo("own");
    }

    @Test
    void tunnelSetting_usesTheProcessPlatformBlock_notTheCaseOne() {
        ZahoriProperties properties = mock(ZahoriProperties.class);
        when(properties.getProperty(WINDOWS)).thenReturn("windows");
        when(properties.getProperty(ANDROID)).thenReturn("android");

        Assertions.assertThat(TestContext.tunnelSetting(properties, "accessKey", "windows")).isEqualTo("windows");
    }

    @Test
    void tunnelSetting_ignoresTheCaseBlock_whenTheProcessDeclaresNothing() {
        ZahoriProperties properties = mock(ZahoriProperties.class);
        when(properties.getProperty(ANDROID)).thenReturn("android");

        Assertions.assertThat(TestContext.tunnelSetting(properties, "accessKey", "linux")).isNull();
    }

    @Test
    void tunnelSetting_skipsABlankOwnSetting() {
        ZahoriProperties properties = mock(ZahoriProperties.class);
        when(properties.getProperty(OWN)).thenReturn("  ");
        when(properties.getProperty(WINDOWS)).thenReturn("windows");

        Assertions.assertThat(TestContext.tunnelSetting(properties, "accessKey", "windows")).isEqualTo("windows");
    }
}
