package io.zahori.framework.driver;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import io.appium.java_client.android.AndroidDriver;
import io.appium.java_client.ios.IOSDriver;
import io.zahori.framework.driver.browserfactory.Browsers;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import org.openqa.selenium.Dimension;
import org.openqa.selenium.Point;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.remote.LocalFileDetector;
import org.openqa.selenium.remote.RemoteWebDriver;

class RemoteDriverConfigurationTest {

    private final RemoteDriver remoteDriver = new RemoteDriver();

    @Test
    void aBrowserSessionUploadsLocalFilesAndTakesTheWholeScreen() {
        RemoteWebDriver session = mock(RemoteWebDriver.class, RETURNS_DEEP_STUBS);
        WebDriver.Window window = session.manage().window();

        remoteDriver.configureWebDriver(session, new Browsers().withScreenResolution("1920x1080"));

        verify(session).setFileDetector(any(LocalFileDetector.class));
        verify(window).setPosition(new Point(0, 0));
        verify(window).setSize(new Dimension(1920, 1080));
        verify(window).maximize();
    }

    @Test
    void anAndroidSessionIsLeftAsTheGridCreatedIt() {
        AndroidDriver session = mock(AndroidDriver.class);

        remoteDriver.configureWebDriver(session, new Browsers().withScreenResolution("1920x1080"));

        verifyNoInteractions(session);
    }

    @Test
    void anIosSessionIsLeftAsTheGridCreatedIt() {
        IOSDriver session = mock(IOSDriver.class);

        remoteDriver.configureWebDriver(session, new Browsers().withScreenResolution("1920x1080"));

        verifyNoInteractions(session);
    }

    @Test
    void asksTheGridForTheConfiguredBrowserVersion() {
        Browsers browsers = new Browsers().withName("CHROME").withPlatform("LINUX").withVersion("128.0");

        assertThat(remoteDriver.getOptions(browsers, null).getBrowserVersion()).isEqualTo("128.0");
    }

    @Test
    void theGridProviderAddsItsOwnCapabilities() {
        Browsers browsers = new Browsers().withName("CHROME").withPlatform("LINUX").withTestName("TCRAR")
                .withRemoteUrl("http://selenoid.test:4444/wd/hub");

        Object selenoidOptions = remoteDriver.getOptions(browsers, null).getCapability("selenoid:options");

        assertThat(selenoidOptions).asInstanceOf(org.assertj.core.api.InstanceOfAssertFactories.MAP).containsEntry("testName", "TCRAR");
    }

    @Test
    void aMalformedGridAddressIsReportedBeforeAnyRequest() {
        Browsers browsers = new Browsers().withName("CHROME").withPlatform("LINUX").withRemoteUrl("grid sin protocolo");

        assertThatThrownBy(() -> remoteDriver.createWebDriver(browsers, null))
                .hasMessageStartingWith("Error parsing URL 'grid sin protocolo': ");
    }

    @Test
    void resolvesAProviderWithoutAnExecution() {
        assertThat(remoteDriver.gridProvider(null)).isNotNull();
    }

    @Test
    void printsTheAppiumCapabilitiesItSends() {
        PrintStream original = System.out;
        ByteArrayOutputStream printed = new ByteArrayOutputStream();
        System.setOut(new PrintStream(printed, true, StandardCharsets.UTF_8));
        try {
            remoteDriver.getAppiumCapabilities(new Browsers().withPlatform("ANDROID").withEnvironmentUrl("https://stg.example/"));
        } finally {
            System.setOut(original);
        }

        assertThat(printed.toString(StandardCharsets.UTF_8)).contains("- Appium capabilities: ").contains("Test Device");
    }
}
