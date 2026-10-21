package io.zahori.framework.core;

import io.zahori.framework.testsupport.RecordingTestContext;
import io.zahori.framework.testsupport.SeleniumDoubles;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import org.assertj.core.api.Assertions;
import org.assertj.core.api.AtomicBooleanAssert;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentMatchers;
import org.mockito.Mockito;
import org.openqa.selenium.By;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebDriverException;
import org.openqa.selenium.WebElement;

class PageElementCheckTest {
    private static final By CHECKBOX = By.cssSelector(".check-terms-privacy input");

    private static WebElement checkbox(AtomicBoolean checked, boolean reactsToTheClick) {
        WebElement element = (WebElement)Mockito.mock(WebElement.class);
        Mockito.when(element.isDisplayed()).thenReturn(true);
        Mockito.when(element.isEnabled()).thenReturn(true);
        Mockito.when(element.isSelected()).thenAnswer(invocation -> checked.get());
        Mockito.when(element.findElements((By)ArgumentMatchers.any(By.class))).thenReturn(List.of());
        ((WebElement)Mockito.doAnswer(invocation -> {
            if (reactsToTheClick) {
                checked.set(!checked.get());
            }

            return null;
        }).when(element)).click();
        return element;
    }

    private PageElement checkboxOn(WebDriver driver) {
        RecordingTestContext testContext = RecordingTestContext.forCase("PageElementCheckTest").withTimeout(1).withCheckboxSettle(0).withDriver(driver);
        return new PageElement(new Page(testContext), "privacy accepted check", Locator.css(".check-terms-privacy input"));
    }

    @Test
    void checksAnUncheckedBox() {
        AtomicBoolean checked = new AtomicBoolean(false);
        WebDriver driver = SeleniumDoubles.driverWithJavascript();
        SeleniumDoubles.at(driver, CHECKBOX, checkbox(checked, true));
        this.checkboxOn(driver).check(true);
        Assertions.assertThat(checked).isTrue();
    }

    @Test
    void leavesAnAlreadyCheckedBoxAlone() {
        AtomicBoolean checked = new AtomicBoolean(true);
        WebElement element = checkbox(checked, true);
        WebDriver driver = SeleniumDoubles.driverWithJavascript();
        SeleniumDoubles.at(driver, CHECKBOX, element);
        this.checkboxOn(driver).check(true);
        Assertions.assertThat(checked).isTrue();
        ((WebElement)Mockito.verify(element, Mockito.never())).click();
    }

    @Test
    void fallsBackToJavascriptWhenTheClickChangesNothing() {
        AtomicBoolean checked = new AtomicBoolean(false);
        WebDriver driver = SeleniumDoubles.driverWithJavascript();
        SeleniumDoubles.at(driver, CHECKBOX, checkbox(checked, false));
        Mockito.when(((JavascriptExecutor)driver).executeScript(ArgumentMatchers.anyString(), (Object[])ArgumentMatchers.any())).thenAnswer(invocation -> {
            checked.set(true);
            return null;
        });
        this.checkboxOn(driver).check(true);
        ((AtomicBooleanAssert)Assertions.assertThat(checked).describedAs("la casilla se quedó sin marcar y nadie lo notó", new Object[0])).isTrue();
    }

    @Test
    void failsWhenTheBoxCannotBeCheckedAtAll() {
        AtomicBoolean checked = new AtomicBoolean(false);
        WebDriver driver = SeleniumDoubles.driverWithJavascript();
        SeleniumDoubles.at(driver, CHECKBOX, checkbox(checked, false));
        Assertions.assertThatThrownBy(() -> this.checkboxOn(driver).check(true))
            .hasMessageContaining("Checkbox stayed unchecked")
            .hasMessageContaining("privacy accepted check");
    }

    @Test
    void readsTheStateFromTheNativeControlInside() {
        AtomicBoolean checked = new AtomicBoolean(false);
        WebElement nativeControl = (WebElement)Mockito.mock(WebElement.class);
        Mockito.when(nativeControl.isSelected()).thenAnswer(invocation -> checked.get());
        WebElement decoration = (WebElement)Mockito.mock(WebElement.class);
        Mockito.when(decoration.isDisplayed()).thenReturn(true);
        Mockito.when(decoration.isEnabled()).thenReturn(true);
        Mockito.when(decoration.isSelected()).thenReturn(false);
        Mockito.when(decoration.findElements((By)ArgumentMatchers.any(By.class))).thenReturn(List.of(nativeControl));
        ((WebElement)Mockito.doAnswer(invocation -> {
            checked.set(true);
            return null;
        }).when(decoration)).click();
        WebDriver driver = SeleniumDoubles.driverWithJavascript();
        SeleniumDoubles.at(driver, CHECKBOX, decoration);
        this.checkboxOn(driver).check(true);
        ((AtomicBooleanAssert)Assertions.assertThat(checked).describedAs("el estado vive en el input de dentro, no en el nodo visible", new Object[0]))
            .isTrue();
    }

    @Test
    void clicksTheNativeControlAsALastResort() {
        AtomicBoolean checked = new AtomicBoolean(false);
        WebElement nativeControl = (WebElement)Mockito.mock(WebElement.class);
        Mockito.when(nativeControl.isSelected()).thenAnswer(invocation -> checked.get());
        WebElement decoration = (WebElement)Mockito.mock(WebElement.class);
        Mockito.when(decoration.isDisplayed()).thenReturn(true);
        Mockito.when(decoration.isEnabled()).thenReturn(true);
        Mockito.when(decoration.isSelected()).thenReturn(false);
        Mockito.when(decoration.findElements((By)ArgumentMatchers.any(By.class))).thenReturn(List.of(nativeControl));
        WebDriver driver = SeleniumDoubles.driverWithJavascript();
        SeleniumDoubles.at(driver, CHECKBOX, decoration);
        Mockito.when(((JavascriptExecutor)driver).executeScript(ArgumentMatchers.anyString(), (Object[])ArgumentMatchers.any())).thenAnswer(invocation -> {
            if (invocation.getArgument(1) == nativeControl) {
                checked.set(true);
            }

            return null;
        });
        this.checkboxOn(driver).check(true);
        Assertions.assertThat(checked).isTrue();
    }

    @Test
    void readsStateFromAriaCheckedWhenThereIsNoNativeControl() {
        WebElement widget = (WebElement)Mockito.mock(WebElement.class);
        Mockito.when(widget.isDisplayed()).thenReturn(true);
        Mockito.when(widget.isEnabled()).thenReturn(true);
        Mockito.when(widget.isSelected()).thenReturn(false);
        Mockito.when(widget.findElements((By)ArgumentMatchers.any(By.class))).thenReturn(List.of());
        Mockito.when(widget.getAttribute("aria-checked")).thenReturn("true");
        WebDriver driver = SeleniumDoubles.driverWithJavascript();
        SeleniumDoubles.at(driver, CHECKBOX, widget);
        this.checkboxOn(driver).check(true);
        ((WebElement)Mockito.verify(widget, Mockito.never())).click();
    }

    @Test
    void readsTheStateFromTheDomPropertyWhenIsSelectedLiesAboutIt() {
        WebElement nativeControl = (WebElement)Mockito.mock(WebElement.class);
        Mockito.when(nativeControl.isSelected()).thenReturn(false);
        Mockito.when(nativeControl.getDomProperty("checked")).thenReturn("true");
        WebElement decoration = (WebElement)Mockito.mock(WebElement.class);
        Mockito.when(decoration.isDisplayed()).thenReturn(true);
        Mockito.when(decoration.isEnabled()).thenReturn(true);
        Mockito.when(decoration.isSelected()).thenReturn(false);
        Mockito.when(decoration.findElements((By)ArgumentMatchers.any(By.class))).thenReturn(List.of(nativeControl));
        WebDriver driver = SeleniumDoubles.driverWithJavascript();
        SeleniumDoubles.at(driver, CHECKBOX, decoration);
        this.checkboxOn(driver).check(true);
        ((WebElement)Mockito.verify(decoration, Mockito.never())).click();
    }

    @Test
    void survivesADriverThatThrowsInsteadOfAnsweringIsSelected() {
        WebElement nativeControl = (WebElement)Mockito.mock(WebElement.class);
        Mockito.when(nativeControl.isSelected()).thenThrow(new Throwable[]{new WebDriverException("Element is not selectable")});
        Mockito.when(nativeControl.getDomProperty("checked")).thenReturn("true");
        WebElement decoration = (WebElement)Mockito.mock(WebElement.class);
        Mockito.when(decoration.isDisplayed()).thenReturn(true);
        Mockito.when(decoration.isEnabled()).thenReturn(true);
        Mockito.when(decoration.isSelected()).thenThrow(new Throwable[]{new WebDriverException("Element is not selectable")});
        Mockito.when(decoration.findElements((By)ArgumentMatchers.any(By.class))).thenReturn(List.of(nativeControl));
        WebDriver driver = SeleniumDoubles.driverWithJavascript();
        SeleniumDoubles.at(driver, CHECKBOX, decoration);
        this.checkboxOn(driver).check(true);
        ((WebElement)Mockito.verify(decoration, Mockito.never())).click();
    }

    @Test
    void fallsBackToAriaCheckedWhenIsSelectedThrowsAndThereIsNoProperty() {
        WebElement widget = (WebElement)Mockito.mock(WebElement.class);
        Mockito.when(widget.isDisplayed()).thenReturn(true);
        Mockito.when(widget.isEnabled()).thenReturn(true);
        Mockito.when(widget.isSelected()).thenThrow(new Throwable[]{new WebDriverException("Element is not selectable")});
        Mockito.when(widget.findElements((By)ArgumentMatchers.any(By.class))).thenReturn(List.of());
        Mockito.when(widget.getDomProperty("checked")).thenReturn(null);
        Mockito.when(widget.getAttribute("aria-checked")).thenReturn("true");
        WebDriver driver = SeleniumDoubles.driverWithJavascript();
        SeleniumDoubles.at(driver, CHECKBOX, widget);
        this.checkboxOn(driver).check(true);
        ((WebElement)Mockito.verify(widget, Mockito.never())).click();
    }

    @Test
    void unchecksACheckedBox() {
        AtomicBoolean checked = new AtomicBoolean(true);
        WebDriver driver = SeleniumDoubles.driverWithJavascript();
        SeleniumDoubles.at(driver, CHECKBOX, checkbox(checked, true));
        this.checkboxOn(driver).check(false);
        Assertions.assertThat(checked).isFalse();
    }
}
