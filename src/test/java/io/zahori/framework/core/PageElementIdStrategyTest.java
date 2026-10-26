package io.zahori.framework.core;

import io.appium.java_client.AppiumDriver;
import io.zahori.framework.testsupport.RecordingTestContext;
import io.zahori.framework.testsupport.SeleniumDoubles;
import java.util.ArrayList;
import java.util.List;
import org.assertj.core.api.Assertions;
import org.assertj.core.api.ListAssert;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentMatchers;
import org.mockito.Mockito;
import org.openqa.selenium.By;
import org.openqa.selenium.WebDriver;

class PageElementIdStrategyTest {
    private static final String ID = "flight-searcher-departure";

    private static WebDriver recordingDriver(List<By> queries, WebDriver base) {
        Mockito.when(base.findElement((By)ArgumentMatchers.any(By.class))).thenAnswer(invocation -> {
            queries.add((By)invocation.getArgument(0));
            return SeleniumDoubles.element();
        });
        return base;
    }

    private PageElement inputOn(RecordingTestContext testContext) {
        return new PageElement(new Page(testContext), "Airport input", Locator.id("flight-searcher-departure"));
    }

    @Test
    void asksByCssSelectorInAWebviewInsteadOfTheIdStrategy() {
        List<By> queries = new ArrayList<>();
        RecordingTestContext testContext = RecordingTestContext.forCase("PageElementIdStrategyTest")
            .withTimeout(1)
            .withDriver(recordingDriver(queries, SeleniumDoubles.androidWebDriver()));
        this.inputOn(testContext).findElement();
        ((ListAssert)Assertions.assertThat(queries).describedAs("chromedriver rechaza la estrategia id con 'invalid locator'", new Object[0]))
            .containsExactly(new By[]{By.cssSelector("[id=\"flight-searcher-departure\"]")});
    }

    @Test
    void keepsTheIdStrategyInNativeContext() {
        List<By> queries = new ArrayList<>();
        RecordingTestContext testContext = RecordingTestContext.forCase("PageElementIdStrategyTest")
            .withTimeout(1)
            .withDriver(recordingDriver(queries, SeleniumDoubles.androidDriver()));
        testContext.switchToNativeContext();
        this.inputOn(testContext).findElement();
        ((ListAssert)Assertions.assertThat(queries).describedAs("el resource-id nativo no se traduce", new Object[0]))
            .containsExactly(new By[]{By.id("flight-searcher-departure")});
    }

    @Test
    void leavesDesktopLocatorsUntouched() {
        List<By> queries = new ArrayList<>();
        RecordingTestContext testContext = RecordingTestContext.forCase("PageElementIdStrategyTest")
            .withTimeout(1)
            .withDriver(recordingDriver(queries, SeleniumDoubles.driver()));
        this.inputOn(testContext).findElement();
        Assertions.assertThat(queries).containsExactly(new By[]{By.id("flight-searcher-departure")});
    }

    @Test
    void onlyTranslatesIdLocators() {
        List<By> queries = new ArrayList<>();
        RecordingTestContext testContext = RecordingTestContext.forCase("PageElementIdStrategyTest")
            .withTimeout(1)
            .withDriver(recordingDriver(queries, SeleniumDoubles.androidDriver()));
        new PageElement(new Page(testContext), "Boton", Locator.css(".ae-btn")).findElement();
        Assertions.assertThat(queries).containsExactly(new By[]{By.cssSelector(".ae-btn")});
    }

    @Test
    void escapesQuotesInTheId() {
        List<By> queries = new ArrayList<>();
        RecordingTestContext testContext = RecordingTestContext.forCase("PageElementIdStrategyTest")
            .withTimeout(1)
            .withDriver(recordingDriver(queries, SeleniumDoubles.androidWebDriver()));
        new PageElement(new Page(testContext), "raro", Locator.id("a\"b")).findElement();
        Assertions.assertThat(queries).containsExactly(new By[]{By.cssSelector("[id=\"a\\\"b\"]")});
    }

    @Test
    void theMobileDoubleIsAnAppiumDriver() {
        Assertions.assertThat(SeleniumDoubles.androidDriver()).isInstanceOf(AppiumDriver.class);
        Assertions.assertThat(SeleniumDoubles.driver()).isNotInstanceOf(AppiumDriver.class);
        Assertions.assertThat((WebDriver)Mockito.mock(WebDriver.class)).isNotNull();
    }
}
