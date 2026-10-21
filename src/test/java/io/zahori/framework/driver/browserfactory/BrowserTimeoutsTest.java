package io.zahori.framework.driver.browserfactory;

import io.zahori.framework.testsupport.SeleniumDoubles;
import java.time.Duration;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebDriver.Timeouts;

class BrowserTimeoutsTest {
    private static final long CASE_TIMEOUT_SECONDS = 60L;

    private Browsers browsers() {
        return new Browsers().withPageLoadTimeout(60L).withImplicitlyWait(0L).withScriptTimeout(60L);
    }

    @Test
    void theScriptTimeoutIsNotTheImplicitWait() {
        WebDriver webDriver = SeleniumDoubles.driver();
        Timeouts timeouts = webDriver.manage().timeouts();
        new WebDriverBrowserSelenium(this.browsers()).setProperties(webDriver, this.browsers());
        ((Timeouts)Mockito.verify(timeouts)).scriptTimeout(Duration.ofSeconds(60L));
        ((Timeouts)Mockito.verify(timeouts)).implicitlyWait(Duration.ZERO);
        ((Timeouts)Mockito.verify(timeouts, Mockito.never())).scriptTimeout(Duration.ZERO);
    }

    @Test
    void thePageLoadTimeoutIsTheCaseTimeout() {
        WebDriver webDriver = SeleniumDoubles.driver();
        new WebDriverBrowserSelenium(this.browsers()).setProperties(webDriver, this.browsers());
        ((Timeouts)Mockito.verify(webDriver.manage().timeouts())).pageLoadTimeout(Duration.ofSeconds(60L));
    }

    @Test
    void androidOnlyGetsTheImplicitWait() {
        WebDriver webDriver = SeleniumDoubles.androidDriver();
        Timeouts timeouts = webDriver.manage().timeouts();
        new WebDriverBrowserSelenium(this.browsers()).setProperties(webDriver, this.browsers());
        ((Timeouts)Mockito.verify(timeouts)).implicitlyWait(Duration.ZERO);
        ((Timeouts)Mockito.verify(timeouts, Mockito.never())).scriptTimeout(Duration.ofSeconds(60L));
        ((Timeouts)Mockito.verify(timeouts, Mockito.never())).pageLoadTimeout(Duration.ofSeconds(60L));
    }

    @Test
    void withoutADriverItDoesNothing() {
        new WebDriverBrowserSelenium(this.browsers()).setProperties(null, this.browsers());
    }

    @Test
    void fallsBackToThePageLoadTimeoutWhenNobodySetsIt() {
        Browsers withoutScriptTimeout = new Browsers().withPageLoadTimeout(60L).withImplicitlyWait(0L);
        Assertions.assertThat(withoutScriptTimeout.getScriptTimeout()).isEqualTo(60L);
    }

    @Test
    void anExplicitScriptTimeoutWins() {
        Assertions.assertThat(this.browsers().withScriptTimeout(15L).getScriptTimeout()).isEqualTo(15L);
    }
}
