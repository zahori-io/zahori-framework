package io.zahori.framework.core;

import io.zahori.framework.testsupport.RecordingTestContext;
import io.zahori.framework.testsupport.SeleniumDoubles;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import org.assertj.core.api.AbstractIntegerAssert;
import org.assertj.core.api.AbstractThrowableAssert;
import org.assertj.core.api.Assertions;
import org.assertj.core.api.ListAssert;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentMatchers;
import org.mockito.Mockito;
import org.openqa.selenium.By;
import org.openqa.selenium.ElementClickInterceptedException;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;

class PageElementMutationTest {
    private static final By TARGET = By.cssSelector(".target");

    private RecordingTestContext contextWith(WebDriver driver) {
        return RecordingTestContext.forCase("PageElementMutationTest").withTimeout(1).withCheckboxSettle(0).withDriver(driver);
    }

    private PageElement elementOn(RecordingTestContext testContext) {
        return new PageElement(new Page(testContext), "target element", Locator.css(".target"));
    }

    @Test
    void logsEveryNativeClick() {
        WebDriver driver = SeleniumDoubles.driverWithJavascript();
        SeleniumDoubles.at(driver, TARGET, SeleniumDoubles.element());
        RecordingTestContext testContext = this.contextWith(driver);
        this.elementOn(testContext).click();
        Assertions.assertThat(testContext.infoMessages()).anyMatch(m -> m.startsWith("Click on"));
    }

    @Test
    void logsTheJavascriptClickAndTheReasonForFallingBack() {
        WebElement stubborn = (WebElement)Mockito.mock(WebElement.class);
        Mockito.when(stubborn.isDisplayed()).thenReturn(true);
        Mockito.when(stubborn.isEnabled()).thenReturn(true);
        ((WebElement)Mockito.doThrow(new Throwable[]{new ElementClickInterceptedException("intercepted by overlay")}).when(stubborn)).click();
        WebDriver driver = SeleniumDoubles.driverWithJavascript();
        SeleniumDoubles.at(driver, TARGET, stubborn);
        RecordingTestContext testContext = this.contextWith(driver);
        this.elementOn(testContext).click();
        ((ListAssert<String>)Assertions.assertThat(testContext.warnMessages())
                .describedAs("el repliegue tiene que quedar escrito: el clic por javascript no comprueba nada", new Object[0]))
            .anyMatch(m -> m.contains("Native click failed"));
        Assertions.assertThat(testContext.infoMessages()).anyMatch(m -> m.startsWith("Click (javascript) on"));
    }

    @Test
    void logsTheFallbackAndTheNativeControlClickOfACheckbox() {
        AtomicBoolean checked = new AtomicBoolean(false);
        WebElement nativeControl = (WebElement)Mockito.mock(WebElement.class);
        Mockito.when(nativeControl.isSelected()).thenAnswer(invocation -> checked.get());
        WebElement decoration = (WebElement)Mockito.mock(WebElement.class);
        Mockito.when(decoration.isDisplayed()).thenReturn(true);
        Mockito.when(decoration.isEnabled()).thenReturn(true);
        Mockito.when(decoration.isSelected()).thenReturn(false);
        Mockito.when(decoration.findElements((By)ArgumentMatchers.any(By.class))).thenReturn(List.of(nativeControl));
        List<Object> clickTargets = new ArrayList<>();
        WebDriver driver = SeleniumDoubles.driverWithJavascript();
        SeleniumDoubles.at(driver, TARGET, decoration);
        Mockito.when(((JavascriptExecutor)driver).executeScript(ArgumentMatchers.anyString(), (Object[])ArgumentMatchers.any())).thenAnswer(invocation -> {
            if (((String)invocation.getArgument(0, String.class)).contains("click()")) {
                clickTargets.add(invocation.getArgument(1));
            }

            if (invocation.getArgument(1) == nativeControl) {
                checked.set(true);
            }

            return null;
        });
        RecordingTestContext testContext = this.contextWith(driver);
        this.elementOn(testContext).check(true);
        Assertions.assertThat(testContext.warnMessages()).anyMatch(m -> m.contains("Checkbox did not change after clicking"));
        Assertions.assertThat(testContext.infoMessages()).anyMatch(m -> m.contains("Click (javascript) on native control of"));
        ((ListAssert<Object>)Assertions.assertThat(clickTargets).describedAs("faltó el intento por javascript sobre la propia checkbox", new Object[0]))
            .containsExactly(new Object[]{decoration, nativeControl});
    }

    @Test
    void theJavascriptClickUsesTheResolvedElement() {
        List<Object> targets = new ArrayList<>();
        WebElement target = SeleniumDoubles.element();
        WebDriver driver = SeleniumDoubles.driverWithJavascript();
        SeleniumDoubles.at(driver, TARGET, target);
        Mockito.when(((JavascriptExecutor)driver).executeScript(ArgumentMatchers.anyString(), (Object[])ArgumentMatchers.any())).thenAnswer(invocation -> {
            targets.add(invocation.getArgument(1));
            return null;
        });
        this.elementOn(this.contextWith(driver)).clickJavascript();
        Assertions.assertThat(targets).allMatch(t -> t == target);
    }

    @Test
    void reportsThatThereWasNoNativeControlToClick() {
        WebElement decoration = (WebElement)Mockito.mock(WebElement.class);
        Mockito.when(decoration.isDisplayed()).thenReturn(true);
        Mockito.when(decoration.isEnabled()).thenReturn(true);
        Mockito.when(decoration.isSelected()).thenReturn(false);
        Mockito.when(decoration.findElements((By)ArgumentMatchers.any(By.class))).thenReturn(List.of());
        WebDriver driver = SeleniumDoubles.driverWithJavascript();
        SeleniumDoubles.at(driver, TARGET, decoration);
        RecordingTestContext testContext = this.contextWith(driver);
        Assertions.assertThatThrownBy(() -> this.elementOn(testContext).check(true)).hasMessageContaining("Checkbox stayed unchecked");
        ((ListAssert<String>)Assertions.assertThat(testContext.infoMessages()).describedAs("no hay control nativo: no puede decir que lo ha pulsado", new Object[0]))
            .noneMatch(m -> m.contains("native control"));
    }

    @Test
    void theJavascriptClickScrollsIntoViewFirst() {
        List<String> scripts = new ArrayList<>();
        WebDriver driver = SeleniumDoubles.driverWithJavascript();
        SeleniumDoubles.at(driver, TARGET, SeleniumDoubles.element());
        Mockito.when(((JavascriptExecutor)driver).executeScript(ArgumentMatchers.anyString(), (Object[])ArgumentMatchers.any())).thenAnswer(invocation -> {
            scripts.add((String)invocation.getArgument(0));
            return null;
        });
        this.elementOn(this.contextWith(driver)).clickJavascript();
        Assertions.assertThat(scripts).hasSize(2);
        Assertions.assertThat(scripts.get(0)).contains(new CharSequence[]{"scrollIntoView"});
        Assertions.assertThat(scripts.get(1)).contains(new CharSequence[]{"click"});
    }

    @Test
    void theNativeClickIsRetriedBeforeFallingBackToJavascript() {
        AtomicInteger nativeClicks = new AtomicInteger();
        WebElement stubborn = (WebElement)Mockito.mock(WebElement.class);
        Mockito.when(stubborn.isDisplayed()).thenReturn(true);
        Mockito.when(stubborn.isEnabled()).thenReturn(true);
        ((WebElement)Mockito.doAnswer(invocation -> {
            nativeClicks.incrementAndGet();
            throw new ElementClickInterceptedException("intercepted");
        }).when(stubborn)).click();
        WebDriver driver = SeleniumDoubles.driverWithJavascript();
        SeleniumDoubles.at(driver, TARGET, stubborn);
        this.elementOn(this.contextWith(driver)).click();
        ((AbstractIntegerAssert)Assertions.assertThat(nativeClicks.get()).describedAs("se replegó a javascript sin reintentar el clic nativo", new Object[0]))
            .isEqualTo(2);
    }

    @Test
    void isEnabledTellsBothStatesApart() {
        WebElement enabled = (WebElement)Mockito.mock(WebElement.class);
        Mockito.when(enabled.isDisplayed()).thenReturn(true);
        Mockito.when(enabled.isEnabled()).thenReturn(true);
        WebDriver withEnabled = SeleniumDoubles.driverSupplying(() -> enabled);
        WebElement disabled = (WebElement)Mockito.mock(WebElement.class);
        Mockito.when(disabled.isDisplayed()).thenReturn(true);
        Mockito.when(disabled.isEnabled()).thenReturn(false);
        WebDriver withDisabled = SeleniumDoubles.driverSupplying(() -> disabled);
        Assertions.assertThat(this.elementOn(this.contextWith(withEnabled)).isEnabled()).isTrue();
        Assertions.assertThat(this.elementOn(this.contextWith(withDisabled)).isEnabled()).isFalse();
    }

    @Test
    void reusesTheAlreadyResolvedElementInsteadOfSearchingAgain() {
        AtomicInteger searches = new AtomicInteger();
        WebElement visible = SeleniumDoubles.element();
        WebDriver driver = SeleniumDoubles.driverSupplying(() -> {
            searches.incrementAndGet();
            return visible;
        });
        PageElement target = this.elementOn(this.contextWith(driver));
        target.isEnabled();
        int afterFirst = searches.get();
        target.isEnabled();
        ((AbstractIntegerAssert)Assertions.assertThat(searches.get()).describedAs("volvió a buscar teniendo el elemento ya resuelto y visible", new Object[0]))
            .isEqualTo(afterFirst);
    }

    @Test
    void aHiddenElementDoesNotSatisfyTheVisibilityCondition() {
        WebElement hidden = (WebElement)Mockito.mock(WebElement.class);
        Mockito.when(hidden.isDisplayed()).thenReturn(false);
        WebDriver driver = SeleniumDoubles.driverSupplying(() -> hidden);
        Assertions.assertThatThrownBy(() -> this.elementOn(this.contextWith(driver)).findElement()).hasMessageContaining("Element is not visible");
    }

    @Test
    void reportsTheElapsedTimeInSeconds() {
        WebElement hidden = (WebElement)Mockito.mock(WebElement.class);
        Mockito.when(hidden.isDisplayed()).thenReturn(false);
        WebDriver driver = SeleniumDoubles.driverSupplying(() -> hidden);
        Assertions.assertThatThrownBy(() -> this.elementOn(this.contextWith(driver)).findElement())
            .matches(error -> error.getMessage().matches(".*Sondeos: \\d+ en [0-2][,.]\\d s.*"), "un plazo de 1 s no puede reportarse como miles de segundos");
    }

    @Test
    void logsWhyItIsNotVisibleOnlyWhenTheWaitCostTime() {
        WebElement hidden = (WebElement)Mockito.mock(WebElement.class);
        Mockito.when(hidden.isDisplayed()).thenReturn(false);
        RecordingTestContext slow = this.contextWith(SeleniumDoubles.driverSupplying(() -> hidden));
        this.elementOn(slow).isVisible();
        Assertions.assertThat(slow.infoMessages()).anyMatch(m -> m.startsWith("Not visible after waiting"));
        RecordingTestContext fast = this.contextWith(SeleniumDoubles.driverWithJavascript());
        SeleniumDoubles.at(fast.driver, TARGET, SeleniumDoubles.element());
        this.elementOn(fast).isVisible();
        ((ListAssert<String>)Assertions.assertThat(fast.infoMessages())
                .describedAs("como predicado se usa en todas partes: si responde rápido no debe escribir nada", new Object[0]))
            .noneMatch(m -> m.startsWith("Not visible after waiting"));
    }

    @Test
    void keepsTheUsefulPartOfASeleniumErrorMessage() {
        WebElement stubborn = (WebElement)Mockito.mock(WebElement.class);
        Mockito.when(stubborn.isDisplayed()).thenReturn(true);
        Mockito.when(stubborn.isEnabled()).thenReturn(true);
        ((WebElement)Mockito.doThrow(
                    new Throwable[]{
                        new ElementClickInterceptedException(
                            "element click intercepted: Other element would receive the click\n(Session info: chrome=128.0)\nBuild info: version: '4.36.0'"
                        )
                    }
                )
                .when(stubborn))
            .click();
        WebDriver driver = SeleniumDoubles.driverWithJavascript();
        SeleniumDoubles.at(driver, TARGET, stubborn);
        ((AbstractThrowableAssert<?, ? extends Throwable>)Assertions.assertThatThrownBy(() -> this.elementOn(this.contextWith(driver)).clickNonVisible())
                .hasMessageContaining("element click intercepted")
                .describedAs("el ruido de Selenium se poda: Build info y Session info no aportan al diagnóstico", new Object[0]))
            .matches(error -> !error.getMessage().contains("Build info") && !error.getMessage().contains("Session info"));
    }
}
