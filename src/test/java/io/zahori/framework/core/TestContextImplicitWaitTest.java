package io.zahori.framework.core;

import io.zahori.framework.testsupport.RecordingTestContext;
import io.zahori.framework.testsupport.SeleniumDoubles;
import java.time.Duration;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentMatchers;
import org.mockito.Mockito;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebDriver.Timeouts;

class TestContextImplicitWaitTest {
    private RecordingTestContext contextWith(WebDriver webDriver) {
        return RecordingTestContext.forCase("TestContextImplicitWaitTest").withTimeout(60).withDriver(webDriver);
    }

    @Test
    void appliesTheImplicitWaitOnlyOnceWhileTheValueDoesNotChange() {
        WebDriver webDriver = SeleniumDoubles.driver();
        Timeouts timeouts = webDriver.manage().timeouts();
        RecordingTestContext testContext = this.contextWith(webDriver);
        testContext.applyImplicitWait(Duration.ZERO);
        testContext.applyImplicitWait(Duration.ZERO);
        testContext.applyImplicitWait(Duration.ZERO);
        ((Timeouts)Mockito.verify(timeouts, Mockito.times(1))).implicitlyWait(Duration.ZERO);
    }

    @Test
    void appliesItAgainWhenTheValueChanges() {
        WebDriver webDriver = SeleniumDoubles.driver();
        Timeouts timeouts = webDriver.manage().timeouts();
        RecordingTestContext testContext = this.contextWith(webDriver);
        testContext.applyImplicitWait(Duration.ZERO);
        testContext.applyImplicitWait(Duration.ofSeconds(60L));
        ((Timeouts)Mockito.verify(timeouts, Mockito.times(1))).implicitlyWait(Duration.ZERO);
        ((Timeouts)Mockito.verify(timeouts, Mockito.times(1))).implicitlyWait(Duration.ofSeconds(60L));
    }

    @Test
    void forgetsTheAppliedValueAfterSwitchingContext() {
        WebDriver webDriver = SeleniumDoubles.androidDriver();
        Timeouts timeouts = webDriver.manage().timeouts();
        RecordingTestContext testContext = this.contextWith(webDriver);
        testContext.applyImplicitWait(Duration.ZERO);
        testContext.switchToNativeContext();
        testContext.applyImplicitWait(Duration.ZERO);
        ((Timeouts)Mockito.verify(timeouts, Mockito.times(2))).implicitlyWait(Duration.ZERO);
    }

    @Test
    void aDesktopDriverIsNeverInNativeContext() {
        WebDriver webDriver = SeleniumDoubles.driver();
        Timeouts timeouts = webDriver.manage().timeouts();
        Assertions.assertThat(this.contextWith(webDriver).isNativeContext()).isFalse();
        ((Timeouts)Mockito.verify(timeouts, Mockito.times(0))).implicitlyWait((Duration)ArgumentMatchers.any(Duration.class));
    }
}
