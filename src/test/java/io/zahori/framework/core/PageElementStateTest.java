package io.zahori.framework.core;

import io.zahori.framework.testsupport.RecordingTestContext;
import io.zahori.framework.testsupport.SeleniumDoubles;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentMatchers;
import org.mockito.Mockito;
import org.openqa.selenium.By;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.Keys;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;

class PageElementStateTest {
    private static final By TARGET = By.cssSelector(".field");

    private RecordingTestContext contextWith(WebDriver driver) {
        return RecordingTestContext.forCase("PageElementStateTest").withTimeout(1).withDriver(driver);
    }

    private PageElement fieldOn(WebDriver driver) {
        return new PageElement(new Page(this.contextWith(driver)), "input field", Locator.css(".field"));
    }

    @Test
    void initWebElementResolvesTheElement() {
        WebElement field = SeleniumDoubles.element();
        PageElement target = this.fieldOn(SeleniumDoubles.driverSupplying(() -> field));
        target.initWebElement();
        Assertions.assertThat(target.webElement).isSameAs(field);
    }

    @Test
    void initWebElementPropagatesWhenTheElementIsNotThere() {
        PageElement target = this.fieldOn(SeleniumDoubles.driverSupplying(() -> null));
        Assertions.assertThatThrownBy(target::initWebElement).hasMessageContaining("Element is not present in DOM").hasMessageContaining("input field");
    }

    @Test
    void tryInitWebElementReportsTheAbsenceInsteadOfPropagating() {
        PageElement missing = this.fieldOn(SeleniumDoubles.driverSupplying(() -> null));
        PageElement present = this.fieldOn(SeleniumDoubles.driverSupplying(() -> SeleniumDoubles.element()));
        Assertions.assertThat(missing.tryInitWebElement()).isFalse();
        Assertions.assertThat(missing.webElement).isNull();
        Assertions.assertThat(present.tryInitWebElement()).isTrue();
    }

    @Test
    void initWebElementResolvesAHiddenElementToo() {
        WebElement hidden = SeleniumDoubles.hiddenElement();
        PageElement target = this.fieldOn(SeleniumDoubles.driverSupplying(() -> hidden));
        target.initWebElement();
        Assertions.assertThat(target.webElement).isSameAs(hidden);
    }

    @Test
    void getInputValueReadsTheDomPropertyAndNotTheAttribute() {
        WebElement field = SeleniumDoubles.element();
        Mockito.when(field.getAttribute("value")).thenReturn("");
        Mockito.when(field.getDomProperty("value")).thenReturn("4444 3333 2222 1111");
        WebDriver driver = SeleniumDoubles.driverWithJavascript();
        SeleniumDoubles.at(driver, TARGET, field);
        RecordingTestContext testContext = this.contextWith(driver);
        PageElement target = new PageElement(new Page(testContext), "input field", Locator.css(".field"));
        Assertions.assertThat(target.getInputValue()).isEqualTo("4444 3333 2222 1111");
        Assertions.assertThat(testContext.infoMessages()).anyMatch(m -> m.contains("4444 3333 2222 1111"));
    }

    @Test
    void getInputValueFailsWithTheElementInTheMessage() {
        WebElement field = SeleniumDoubles.element();
        Mockito.when(field.getDomProperty("value")).thenThrow(new Throwable[]{new IllegalStateException("stale")});
        WebDriver driver = SeleniumDoubles.driverWithJavascript();
        SeleniumDoubles.at(driver, TARGET, field);
        Assertions.assertThatThrownBy(() -> this.fieldOn(driver).getInputValue())
            .hasMessageContaining("Unable to get input value")
            .hasMessageContaining("input field");
    }

    @Test
    void getDomPropertyReturnsTheLiveValue() {
        WebElement field = SeleniumDoubles.element();
        Mockito.when(field.getDomProperty("checked")).thenReturn("true");
        WebDriver driver = SeleniumDoubles.driverWithJavascript();
        SeleniumDoubles.at(driver, TARGET, field);
        RecordingTestContext testContext = this.contextWith(driver);
        PageElement target = new PageElement(new Page(testContext), "input field", Locator.css(".field"));
        Assertions.assertThat(target.getDomProperty("checked")).isEqualTo("true");
        Assertions.assertThat(testContext.infoMessages()).anyMatch(m -> m.contains("checked"));
    }

    @Test
    void getDomPropertyNamesThePropertyWhenItFails() {
        WebElement field = SeleniumDoubles.element();
        Mockito.when(field.getDomProperty("checked")).thenThrow(new Throwable[]{new IllegalStateException("gone")});
        WebDriver driver = SeleniumDoubles.driverWithJavascript();
        SeleniumDoubles.at(driver, TARGET, field);
        Assertions.assertThatThrownBy(() -> this.fieldOn(driver).getDomProperty("checked")).hasMessageContaining("Unable to get DOM property 'checked'");
    }

    @Test
    void isAttributePresentTellsAnEmptyAttributeApartFromAMissingOne() {
        WebElement field = SeleniumDoubles.element();
        Mockito.when(field.getAttribute("disabled")).thenReturn("");
        Mockito.when(field.getAttribute("readonly")).thenReturn(null);
        WebDriver driver = SeleniumDoubles.driverWithJavascript();
        SeleniumDoubles.at(driver, TARGET, field);
        PageElement target = this.fieldOn(driver);
        Assertions.assertThat(target.isAttributePresent("disabled")).isTrue();
        Assertions.assertThat(target.isAttributePresent("readonly")).isFalse();
    }

    @Test
    void getTextTrimsWhatTheElementShows() {
        WebDriver driver = SeleniumDoubles.driverSupplying(() -> SeleniumDoubles.elementWithText("  153,06  "));
        RecordingTestContext testContext = this.contextWith(driver);
        PageElement target = new PageElement(new Page(testContext), "input field", Locator.css(".field"));
        Assertions.assertThat(target.getText()).isEqualTo("153,06");
        Assertions.assertThat(testContext.infoMessages()).anyMatch(m -> m.contains("153,06"));
    }

    @Test
    void validateTextFailsShowingBothTexts() {
        WebDriver driver = SeleniumDoubles.driverSupplying(() -> SeleniumDoubles.elementWithText("185,06"));
        Assertions.assertThatThrownBy(() -> this.fieldOn(driver).validateText("153,06")).hasMessageContaining("153,06").hasMessageContaining("185,06");
    }

    @Test
    void validateTextLeavesTraceWhenItMatches() {
        WebDriver driver = SeleniumDoubles.driverSupplying(() -> SeleniumDoubles.elementWithText("153,06"));
        RecordingTestContext testContext = this.contextWith(driver);
        new PageElement(new Page(testContext), "input field", Locator.css(".field")).validateText("153,06");
        Assertions.assertThat(testContext.infoMessages()).anyMatch(m -> m.startsWith("Validated text"));
    }

    @Test
    void sendKeysForwardsTheKeyToTheElement() {
        WebElement field = SeleniumDoubles.element();
        WebDriver driver = SeleniumDoubles.driverWithJavascript();
        SeleniumDoubles.at(driver, TARGET, field);
        this.fieldOn(driver).sendKeys(Keys.ENTER);
        ((WebElement)Mockito.verify(field)).sendKeys(new CharSequence[]{Keys.ENTER});
    }

    @Test
    void scrollCentersTheElementInTheViewport() {
        WebElement field = SeleniumDoubles.element();
        WebDriver driver = SeleniumDoubles.driverWithJavascript();
        SeleniumDoubles.at(driver, TARGET, field);
        this.fieldOn(driver).scroll();
        ((JavascriptExecutor)Mockito.verify((JavascriptExecutor)driver))
            .executeScript(ArgumentMatchers.contains("scrollIntoView"), new Object[]{ArgumentMatchers.eq(field)});
    }

    @Test
    void scrollFailsNamingTheElement() {
        Assertions.assertThatThrownBy(() -> this.fieldOn(SeleniumDoubles.driverSupplying(() -> null)).scroll()).hasMessageContaining("input field");
    }
}
