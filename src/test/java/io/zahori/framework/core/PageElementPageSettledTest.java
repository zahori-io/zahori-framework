package io.zahori.framework.core;

import io.zahori.framework.testsupport.RecordingTestContext;
import io.zahori.framework.testsupport.SeleniumDoubles;
import java.util.concurrent.atomic.AtomicInteger;
import org.assertj.core.api.AbstractIntegerAssert;
import org.assertj.core.api.AbstractLongAssert;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;
import org.openqa.selenium.By;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;

class PageElementPageSettledTest {
    private static final By TARGET = By.cssSelector(".btn");

    private PageElement buttonOn(WebDriver driver, RecordingTestContext testContext) {
        return new PageElement(new Page(testContext), "continue button", Locator.css(".btn"));
    }

    private RecordingTestContext contextWith(WebDriver driver) {
        return RecordingTestContext.forCase("PageElementPageSettledTest").withTimeout(1).withDriver(driver);
    }

    @Test
    void waitsAfterClickingUntilThePageIsSettled() {
        WebElement button = SeleniumDoubles.element();
        WebDriver driver = SeleniumDoubles.driverWithJavascript();
        SeleniumDoubles.at(driver, TARGET, button);
        RecordingTestContext testContext = this.contextWith(driver);
        AtomicInteger checks = new AtomicInteger();
        testContext.setPageSettledCheck(() -> checks.incrementAndGet() >= 3);
        this.buttonOn(driver, testContext).click();
        ((AbstractIntegerAssert)Assertions.assertThat(checks.get()).describedAs("seguia cargando: habia que volver a mirar", new Object[0]))
            .isGreaterThanOrEqualTo(3);
    }

    @Test
    void theJavascriptClickWaitsToo() {
        WebElement button = SeleniumDoubles.element();
        WebDriver driver = SeleniumDoubles.driverWithJavascript();
        SeleniumDoubles.at(driver, TARGET, button);
        RecordingTestContext testContext = this.contextWith(driver);
        AtomicInteger checks = new AtomicInteger();
        testContext.setPageSettledCheck(() -> checks.incrementAndGet() >= 2);
        this.buttonOn(driver, testContext).clickJavascript();
        Assertions.assertThat(checks.get()).isGreaterThanOrEqualTo(2);
    }

    @Test
    void withoutACheckRegisteredNothingChanges() {
        WebElement button = SeleniumDoubles.element();
        WebDriver driver = SeleniumDoubles.driverWithJavascript();
        SeleniumDoubles.at(driver, TARGET, button);
        RecordingTestContext testContext = this.contextWith(driver);
        long start = System.nanoTime();
        this.buttonOn(driver, testContext).click();
        long milliseconds = (System.nanoTime() - start) / 1000000L;
        ((AbstractLongAssert)Assertions.assertThat(milliseconds).describedAs("sin check no hay nada que esperar", new Object[0])).isLessThan(1000L);
    }

    @Test
    void aCheckThatNeverPassesDoesNotThrow() {
        WebElement button = SeleniumDoubles.element();
        WebDriver driver = SeleniumDoubles.driverWithJavascript();
        SeleniumDoubles.at(driver, TARGET, button);
        RecordingTestContext testContext = this.contextWith(driver);
        testContext.setPageSettledCheck(() -> false);
        this.buttonOn(driver, testContext).click();
        Assertions.assertThat(testContext.infoMessages()).anyMatch(m -> m.startsWith("Click on"));
    }
}
