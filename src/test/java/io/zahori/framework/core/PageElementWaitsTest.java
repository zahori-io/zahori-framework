package io.zahori.framework.core;

import io.zahori.framework.testsupport.RecordingTestContext;
import io.zahori.framework.testsupport.SeleniumDoubles;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentMatchers;
import org.mockito.Mockito;
import org.openqa.selenium.By;
import org.openqa.selenium.TimeoutException;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.WebDriver.Timeouts;

class PageElementWaitsTest {
    private static final By SPINNER = By.cssSelector(".spinner");

    private PageElement elementOn(WebDriver webDriver, By locator) {
        RecordingTestContext testContext = RecordingTestContext.forCase("PageElementWaitsTest").withTimeout(1).withDriver(webDriver);
        return new PageElement(new Page(testContext), "element under test", Locator.css(cssOf(locator)));
    }

    private static String cssOf(By locator) {
        return String.valueOf(locator).replace("By.cssSelector: ", "");
    }

    @Test
    void isNotVisibleIsTrueWhenTheElementWasNeverThere() {
        PageElement spinner = this.elementOn(SeleniumDoubles.driverSupplying(() -> null), SPINNER);
        Assertions.assertTrue(spinner.isNotVisible(1));
    }

    @Test
    void isNotVisibleWaitsUntilTheElementDisappears() {
        AtomicInteger lookups = new AtomicInteger();
        PageElement spinner = this.elementOn(SeleniumDoubles.driverSupplying(() -> lookups.getAndIncrement() < 2 ? SeleniumDoubles.element() : null), SPINNER);
        Assertions.assertTrue(spinner.isNotVisible(5));
    }

    @Test
    void isNotVisibleIsTrueWhenTheElementIsPresentButHidden() {
        PageElement spinner = this.elementOn(SeleniumDoubles.driverSupplying(SeleniumDoubles::hiddenElement), SPINNER);
        Assertions.assertTrue(spinner.isNotVisible(1));
    }

    @Test
    void isNotVisibleIsFalseWhenTheElementNeverGoesAway() {
        WebElement stuck = SeleniumDoubles.element();
        PageElement spinner = this.elementOn(SeleniumDoubles.driverSupplying(() -> stuck), SPINNER);
        Assertions.assertFalse(spinner.isNotVisible(1));
    }

    @Test
    void isNotVisibleWithNoArgumentsIsFalseWhenTheElementIsStillThere() {
        WebElement stuck = SeleniumDoubles.element();
        PageElement spinner = this.elementOn(SeleniumDoubles.driverSupplying(() -> stuck), SPINNER);
        Assertions.assertFalse(spinner.isNotVisible());
    }

    @Test
    void isNotVisibleWithNoArgumentsIsTrueWhenTheElementIsNotThere() {
        PageElement spinner = this.elementOn(SeleniumDoubles.driverSupplying(() -> null), SPINNER);
        Assertions.assertTrue(spinner.isNotVisible());
    }

    @Test
    void isEnabledIsFalseWhileTheControlStaysDisabled() {
        WebElement button = SeleniumDoubles.disabledElement();
        PageElement payButton = this.elementOn(SeleniumDoubles.driverSupplying(() -> button), By.cssSelector("#pay"));
        Assertions.assertFalse(payButton.isEnabled(1));
    }

    @Test
    void isEnabledWaitsUntilTheApplicationUnlocksTheControl() {
        AtomicInteger lookups = new AtomicInteger();
        PageElement payButton = this.elementOn(
            SeleniumDoubles.driverSupplying(() -> lookups.getAndIncrement() < 2 ? SeleniumDoubles.disabledElement() : SeleniumDoubles.element()),
            By.cssSelector("#pay")
        );
        Assertions.assertTrue(payButton.isEnabled(5));
    }

    @Test
    void hasAttributeWaitsUntilTheAttributeReachesTheValue() {
        AtomicInteger lookups = new AtomicInteger();
        PageElement field = this.elementOn(SeleniumDoubles.driverSupplying(() -> {
            WebElement webElement = (WebElement)Mockito.mock(WebElement.class);
            Mockito.when(webElement.isDisplayed()).thenReturn(true);
            Mockito.when(webElement.getAttribute("aria-expanded")).thenReturn(lookups.getAndIncrement() < 2 ? "false" : "true");
            return webElement;
        }), By.cssSelector("#selector"));
        Assertions.assertTrue(field.hasAttribute("aria-expanded", "true", 5));
    }

    @Test
    void hasAttributeIsFalseWhenTheValueNeverArrives() {
        PageElement field = this.elementOn(SeleniumDoubles.driverSupplying(SeleniumDoubles::disabledElement), By.cssSelector("#pay"));
        Assertions.assertFalse(field.hasAttribute("disabled", "false", 1));
    }

    @Test
    void containsTextWaitsUntilTheTextAppears() {
        AtomicInteger lookups = new AtomicInteger();
        PageElement total = this.elementOn(
            SeleniumDoubles.driverSupplying(
                () -> lookups.getAndIncrement() < 2 ? SeleniumDoubles.elementWithText("36,53 EUR") : SeleniumDoubles.elementWithText("44,40 USD")
            ),
            By.cssSelector(".total")
        );
        Assertions.assertTrue(total.containsText("USD", 5));
    }

    @Test
    void containsTextIsFalseWhenTheTextNeverAppears() {
        PageElement total = this.elementOn(SeleniumDoubles.driverSupplying(() -> SeleniumDoubles.elementWithText("36,53 EUR")), By.cssSelector(".total"));
        Assertions.assertFalse(total.containsText("USD", 1));
    }

    @Test
    void waitsDoNotSpendASingleCommandOnTimeouts() {
        WebDriver webDriver = SeleniumDoubles.driver();
        WebElement visible = SeleniumDoubles.element();
        Mockito.when(webDriver.findElement((By)ArgumentMatchers.any(By.class))).thenReturn(visible);
        Timeouts timeouts = webDriver.manage().timeouts();
        PageElement spinner = this.elementOn(webDriver, SPINNER);
        spinner.isNotVisible(1);
        ((Timeouts)Mockito.verify(timeouts, Mockito.never())).implicitlyWait((Duration)ArgumentMatchers.any(Duration.class));
    }

    @Test
    void anExhaustedWaitKeepsTheSeleniumCause() {
        PageElement missing = this.elementOn(SeleniumDoubles.driverSupplying(() -> null), SPINNER);
        RuntimeException error = (RuntimeException)Assertions.assertThrows(RuntimeException.class, missing::initWebElement);
        Assertions.assertNotNull(error.getCause(), "la causa original de Selenium se ha perdido por el camino");
        Assertions.assertInstanceOf(TimeoutException.class, error.getCause());
        Assertions.assertTrue(error.getMessage().contains("element under test"), error.getMessage());
        Assertions.assertTrue(error.getMessage().contains(".spinner"), error.getMessage());
    }

    @Test
    void initWebElementPropagatesInsteadOfLeavingTheElementNull() {
        PageElement missing = this.elementOn(SeleniumDoubles.driverSupplying(() -> null), SPINNER);
        Assertions.assertThrows(RuntimeException.class, missing::initWebElement);
    }

    @Test
    void tryInitWebElementReportsTheAbsenceInsteadOfThrowing() {
        PageElement missing = this.elementOn(SeleniumDoubles.driverSupplying(() -> null), SPINNER);
        Assertions.assertFalse(missing.tryInitWebElement());
        Assertions.assertNull(missing.webElement);
    }

    @Test
    void tryInitWebElementStoresTheElementWhenItIsThere() {
        PageElement present = this.elementOn(SeleniumDoubles.driverSupplying(SeleniumDoubles::element), SPINNER);
        Assertions.assertTrue(present.tryInitWebElement());
        Assertions.assertNotNull(present.webElement);
    }
}
