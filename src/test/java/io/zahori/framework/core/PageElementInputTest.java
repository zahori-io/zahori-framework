package io.zahori.framework.core;

import io.zahori.framework.testsupport.RecordingTestContext;
import io.zahori.framework.testsupport.SeleniumDoubles;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.ArgumentMatchers;
import org.mockito.Mockito;
import org.openqa.selenium.By;
import org.openqa.selenium.InvalidElementStateException;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.WebDriver.Timeouts;

class PageElementInputTest {
    private static final By TARGET = By.cssSelector(".field");

    private RecordingTestContext contextWith(WebDriver driver) {
        return RecordingTestContext.forCase("PageElementInputTest").withTimeout(1).withDriver(driver);
    }

    private PageElement fieldOn(WebDriver driver) {
        return new PageElement(new Page(this.contextWith(driver)), "input field", Locator.css(".field"));
    }

    @Test
    void isVisibleWithoutWaitIsTrueForAVisibleElement() {
        Assertions.assertThat(this.fieldOn(SeleniumDoubles.driverSupplying(() -> SeleniumDoubles.element())).isVisibleWithoutWait()).isTrue();
    }

    @Test
    void isVisibleWithoutWaitIsFalseForAPresentButHiddenElement() {
        Assertions.assertThat(this.fieldOn(SeleniumDoubles.driverSupplying(() -> SeleniumDoubles.hiddenElement())).isVisibleWithoutWait()).isFalse();
    }

    @Test
    void isVisibleWithoutWaitIsFalseWhenTheElementIsNotThere() {
        Assertions.assertThat(this.fieldOn(SeleniumDoubles.driverSupplying(() -> null)).isVisibleWithoutWait()).isFalse();
    }

    @Test
    void isVisibleWithoutWaitGuaranteesTheImplicitWaitIsZero() {
        WebDriver driver = SeleniumDoubles.driverSupplying(() -> null);
        Timeouts timeouts = driver.manage().timeouts();
        this.fieldOn(driver).isVisibleWithoutWait();
        ((Timeouts)Mockito.verify(timeouts)).implicitlyWait(Duration.ZERO);
    }

    @Test
    void isPresentWithoutWaitGuaranteesItToo() {
        WebDriver driver = SeleniumDoubles.driverSupplying(() -> null);
        Timeouts timeouts = driver.manage().timeouts();
        this.fieldOn(driver).isPresentWithoutWait();
        ((Timeouts)Mockito.verify(timeouts)).implicitlyWait(Duration.ZERO);
    }

    @Test
    void isVisibleWithoutWaitAsksTheDriverExactlyOnce() {
        AtomicInteger searches = new AtomicInteger();
        WebDriver driver = SeleniumDoubles.driverSupplying(() -> {
            searches.incrementAndGet();
            return null;
        });
        this.fieldOn(driver).isVisibleWithoutWait();
        Assertions.assertThat(searches.get()).isEqualTo(1);
    }

    @Test
    void writeSendsTheTextAndLeavesTraceOfIt() {
        WebElement field = SeleniumDoubles.element();
        WebDriver driver = SeleniumDoubles.driverWithJavascript();
        SeleniumDoubles.at(driver, TARGET, field);
        RecordingTestContext testContext = this.contextWith(driver);
        new PageElement(new Page(testContext), "input field", Locator.css(".field")).write("MAD");
        ((WebElement)Mockito.verify(field)).sendKeys(new CharSequence[]{"MAD"});
        Assertions.assertThat(testContext.infoMessages()).anyMatch(m -> m.contains("Write text \"MAD\""));
    }

    @Test
    void writeFailsWhenTheFieldIsNotEditable() {
        WebElement readOnly = SeleniumDoubles.element();
        ((WebElement)Mockito.doThrow(new Throwable[]{new InvalidElementStateException("not editable")}).when(readOnly)).sendKeys(new CharSequence[]{"MAD"});
        WebDriver driver = SeleniumDoubles.driverWithJavascript();
        SeleniumDoubles.at(driver, TARGET, readOnly);
        Assertions.assertThatThrownBy(() -> this.fieldOn(driver).write("MAD")).hasMessageContaining("Element is not editable");
    }

    @Test
    void writeCharByCharSendsOneKeystrokePerCharacter() {
        WebElement field = SeleniumDoubles.element();
        WebDriver driver = SeleniumDoubles.driverWithJavascript();
        SeleniumDoubles.at(driver, TARGET, field);
        this.fieldOn(driver).writeCharByChar("MAD");
        ArgumentCaptor<CharSequence> keys = ArgumentCaptor.forClass(CharSequence.class);
        ((WebElement)Mockito.verify(field, Mockito.times(3))).sendKeys(new CharSequence[]{(CharSequence)keys.capture()});
        Assertions.assertThat(keys.getAllValues()).containsExactly(new CharSequence[]{"M", "A", "D"});
    }

    @Test
    void writeCharByCharLeavesTraceOfTheText() {
        WebDriver driver = SeleniumDoubles.driverWithJavascript();
        SeleniumDoubles.at(driver, TARGET, SeleniumDoubles.element());
        RecordingTestContext testContext = this.contextWith(driver);
        new PageElement(new Page(testContext), "input field", Locator.css(".field")).writeCharByChar("MAD");
        Assertions.assertThat(testContext.infoMessages()).anyMatch(m -> m.contains("MAD"));
    }

    @Test
    void writeCharByCharSendsNothingForAnEmptyText() {
        WebElement field = SeleniumDoubles.element();
        WebDriver driver = SeleniumDoubles.driverWithJavascript();
        SeleniumDoubles.at(driver, TARGET, field);
        this.fieldOn(driver).writeCharByChar("");
        ((WebElement)Mockito.verify(field, Mockito.never())).sendKeys(new CharSequence[]{ArgumentMatchers.anyString()});
    }

    @Test
    void writeRetriesOnceBeforeGivingUp() {
        WebElement readOnly = SeleniumDoubles.element();
        ((WebElement)Mockito.doThrow(new Throwable[]{new InvalidElementStateException("not editable")}).when(readOnly)).sendKeys(new CharSequence[]{"MAD"});
        WebDriver driver = SeleniumDoubles.driverWithJavascript();
        SeleniumDoubles.at(driver, TARGET, readOnly);
        Assertions.assertThatThrownBy(() -> this.fieldOn(driver).write("MAD")).hasMessageContaining("Element is not editable");
        ((WebElement)Mockito.verify(readOnly, Mockito.times(2))).sendKeys(new CharSequence[]{"MAD"});
    }

    @Test
    void getAttributeValueWithoutPrintStillRecordsThatItWasRead() {
        WebElement field = SeleniumDoubles.element();
        Mockito.when(field.getAttribute("class")).thenReturn("ae-btn");
        WebDriver driver = SeleniumDoubles.driverWithJavascript();
        SeleniumDoubles.at(driver, TARGET, field);
        RecordingTestContext testContext = this.contextWith(driver);
        new PageElement(new Page(testContext), "input field", Locator.css(".field")).getAttributeValueWithoutPrint("class");
        Assertions.assertThat(testContext.infoMessages()).anyMatch(m -> m.contains("Get 'class' attribute for"));
    }

    @Test
    void clearEmptiesTheField() {
        WebElement field = SeleniumDoubles.element();
        WebDriver driver = SeleniumDoubles.driverWithJavascript();
        SeleniumDoubles.at(driver, TARGET, field);
        this.fieldOn(driver).clear();
        ((WebElement)Mockito.verify(field)).clear();
    }

    @Test
    void clearFailsWhenTheFieldIsNotEditable() {
        WebElement readOnly = SeleniumDoubles.element();
        ((WebElement)Mockito.doThrow(new Throwable[]{new InvalidElementStateException("not editable")}).when(readOnly)).clear();
        WebDriver driver = SeleniumDoubles.driverWithJavascript();
        SeleniumDoubles.at(driver, TARGET, readOnly);
        Assertions.assertThatThrownBy(() -> this.fieldOn(driver).clear()).hasMessageContaining("Element is not editable");
    }

    @Test
    void getAttributeValueReturnsWhatTheElementHas() {
        WebElement field = SeleniumDoubles.element();
        Mockito.when(field.getAttribute("data-jto")).thenReturn("input-searcher-departure");
        WebDriver driver = SeleniumDoubles.driverWithJavascript();
        SeleniumDoubles.at(driver, TARGET, field);
        RecordingTestContext testContext = this.contextWith(driver);
        PageElement target = new PageElement(new Page(testContext), "input field", Locator.css(".field"));
        Assertions.assertThat(target.getAttributeValue("data-jto")).isEqualTo("input-searcher-departure");
        Assertions.assertThat(testContext.infoMessages()).anyMatch(m -> m.contains("input-searcher-departure"));
    }

    @Test
    void getAttributeValueWithoutPrintReturnsTheSameValueWithoutLogging() {
        WebElement field = SeleniumDoubles.element();
        Mockito.when(field.getAttribute("class")).thenReturn("ae-btn");
        WebDriver driver = SeleniumDoubles.driverWithJavascript();
        SeleniumDoubles.at(driver, TARGET, field);
        RecordingTestContext testContext = this.contextWith(driver);
        PageElement target = new PageElement(new Page(testContext), "input field", Locator.css(".field"));
        Assertions.assertThat(target.getAttributeValueWithoutPrint("class")).isEqualTo("ae-btn");
        Assertions.assertThat(testContext.infoMessages()).noneMatch(m -> m.contains("ae-btn"));
    }

    @Test
    void validateIsVisibleLeavesTraceWhenTheElementIsThere() {
        WebDriver driver = SeleniumDoubles.driverSupplying(() -> SeleniumDoubles.element());
        RecordingTestContext testContext = this.contextWith(driver);
        new PageElement(new Page(testContext), "input field", Locator.css(".field")).validateIsVisible();
        Assertions.assertThat(testContext.infoMessages()).anyMatch(m -> m.startsWith("Validated is visible"));
        Assertions.assertThat(testContext.infoMessages()).anyMatch(m -> m.startsWith("Validated is present"));
    }

    @Test
    void validateIsVisibleTellsPresentButHiddenApartFromMissing() {
        Assertions.assertThatThrownBy(() -> this.fieldOn(SeleniumDoubles.driverSupplying(() -> SeleniumDoubles.hiddenElement())).validateIsVisible())
            .hasMessageContaining("Element is present in DOM but not visible");
    }

    @Test
    void isPresentIsTrueEvenForAHiddenElement() {
        Assertions.assertThat(this.fieldOn(SeleniumDoubles.driverSupplying(() -> SeleniumDoubles.hiddenElement())).isPresentWithoutWait()).isTrue();
    }

    @Test
    void isPresentIsFalseWhenTheElementNeverArrives() {
        Assertions.assertThat(this.fieldOn(SeleniumDoubles.driverSupplying(() -> null)).isPresent(1)).isFalse();
    }

    @Test
    void isPresentWaitsUntilTheElementShowsUp() {
        AtomicInteger lookups = new AtomicInteger();
        WebDriver driver = SeleniumDoubles.driverSupplying(() -> lookups.getAndIncrement() < 2 ? null : SeleniumDoubles.hiddenElement());
        Assertions.assertThat(this.fieldOn(driver).isPresent(5)).isTrue();
    }
}
