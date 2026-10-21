package io.zahori.framework.core;

import io.zahori.framework.testsupport.RecordingTestContext;
import io.zahori.framework.testsupport.SeleniumDoubles;
import org.assertj.core.api.AbstractBooleanAssert;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentMatchers;
import org.mockito.Mockito;
import org.openqa.selenium.By;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.WebDriver;

class PageElementNativeScrollTest {
    private static final By BUTTON = By.xpath("(//android.widget.FrameLayout//android.widget.Button)[1]");

    private PageElement nativeButton(RecordingTestContext testContext) {
        return new PageElement(new Page(testContext), "Boton rechazar notificaciones", Locator.xpath(BUTTON.toString().replace("By.xpath: ", "")));
    }

    @Test
    @DisplayName("en contexto nativo no se intenta un scroll por javascript")
    void doesNotTryAJavascriptScrollInNativeContext() {
        WebDriver driver = SeleniumDoubles.androidDriver();
        SeleniumDoubles.at(driver, BUTTON, SeleniumDoubles.element());
        RecordingTestContext testContext = RecordingTestContext.forCase("PageElementNativeScrollTest").withTimeout(1).withDriver(driver);
        testContext.switchToNativeContext();
        this.nativeButton(testContext).click();
        ((JavascriptExecutor)Mockito.verify((JavascriptExecutor)driver, Mockito.never()))
            .executeScript(ArgumentMatchers.anyString(), (Object[])ArgumentMatchers.any());
    }

    @Test
    void restoresTheScrollWhenBackInWebContext() {
        WebDriver driver = SeleniumDoubles.androidDriver();
        RecordingTestContext testContext = RecordingTestContext.forCase("PageElementNativeScrollTest").withTimeout(1).withDriver(driver);
        testContext.switchToNativeContext();
        Assertions.assertThat(testContext.isNativeContext()).isTrue();
        testContext.switchToWebContext("CHROMIUM");
        ((AbstractBooleanAssert)Assertions.assertThat(testContext.isNativeContext())
                .describedAs("el contexto se quedaba marcado como nativo tras volver al webview", new Object[0]))
            .isFalse();
    }

    @Test
    void aDesktopDriverIsNeverInNativeContext() {
        RecordingTestContext testContext = RecordingTestContext.forCase("PageElementNativeScrollTest").withTimeout(1).withDriver(SeleniumDoubles.driver());
        Assertions.assertThat(testContext.isNativeContext()).isFalse();
    }
}
