package io.zahori.framework.core;

import io.zahori.framework.testsupport.RecordingTestContext;
import io.zahori.framework.testsupport.SeleniumDoubles;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.openqa.selenium.By;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;

class TestContextInitialContextTest {
    private static final String ID = "guestUserAccountFragment";
    private static final By ID_BY_CSS = By.cssSelector("[id=\"" + ID + "\"]");

    private RecordingTestContext contextWith(WebDriver webDriver) {
        return RecordingTestContext.forCase("TestContextInitialContextTest").withTimeout(1).withDriver(webDriver);
    }

    private PageElement accountButton(RecordingTestContext testContext) {
        return new PageElement(new Page(testContext), "My account button", Locator.id(ID));
    }

    @Test
    @DisplayName("una app android nativa esta en contexto nativo sin haber cambiado de contexto")
    void aNativeAndroidAppIsInNativeContextFromTheStart() {
        Assertions.assertThat(this.contextWith(SeleniumDoubles.androidDriver()).isNativeContext()).isTrue();
    }

    @Test
    void aNativeIosAppIsInNativeContextFromTheStart() {
        Assertions.assertThat(this.contextWith(SeleniumDoubles.iosDriver()).isNativeContext()).isTrue();
    }

    @Test
    void mobileWebIsNotInNativeContext() {
        Assertions.assertThat(this.contextWith(SeleniumDoubles.androidWebDriver()).isNativeContext()).isFalse();
    }

    @Test
    void aDesktopDriverIsNotInNativeContext() {
        Assertions.assertThat(this.contextWith(SeleniumDoubles.driver()).isNativeContext()).isFalse();
    }

    @Test
    void aMobileWebSessionSwitchedToNativeIsInNativeContext() {
        RecordingTestContext testContext = this.contextWith(SeleniumDoubles.androidWebDriver());
        testContext.switchToNativeContext();
        Assertions.assertThat(testContext.isNativeContext()).isTrue();
    }

    @Test
    @DisplayName("el contexto fijado a mano manda sobre el del tipo de driver")
    void anExplicitSwitchToAWebviewWinsOverTheDriverType() {
        RecordingTestContext testContext = this.contextWith(SeleniumDoubles.androidDriver());
        testContext.switchToWebContext("CHROMIUM");
        Assertions.assertThat(testContext.isNativeContext()).isFalse();
    }

    @Test
    @DisplayName("al recrear el driver en un reintento no se arrastra el contexto del anterior")
    void aNewDriverDoesNotKeepTheContextSetOnThePreviousOne() {
        RecordingTestContext testContext = this.contextWith(SeleniumDoubles.androidDriver());
        testContext.switchToWebContext("CHROMIUM");
        testContext.withDriver(SeleniumDoubles.androidDriver());
        Assertions.assertThat(testContext.isNativeContext()).isTrue();
    }

    @Test
    @DisplayName("un id en una app nativa se busca por id y no se traduce a CSS")
    void aNativeAppLooksUpAnIdByIdAndNotByCss() {
        WebDriver driver = SeleniumDoubles.androidDriver();
        WebElement element = SeleniumDoubles.element();
        SeleniumDoubles.at(driver, By.id(ID), element);
        RecordingTestContext testContext = this.contextWith(driver);

        this.accountButton(testContext).click();

        Mockito.verify(element).click();
    }

    @Test
    void aWebviewLooksUpAnIdByCss() {
        WebDriver driver = SeleniumDoubles.androidWebDriver();
        WebElement element = SeleniumDoubles.element();
        SeleniumDoubles.at(driver, ID_BY_CSS, element);
        RecordingTestContext testContext = this.contextWith(driver);

        this.accountButton(testContext).click();

        Mockito.verify(element).click();
    }

    @Test
    void aNativeAppSwitchedToAWebviewLooksUpAnIdByCss() {
        WebDriver driver = SeleniumDoubles.androidDriver();
        WebElement element = SeleniumDoubles.element();
        SeleniumDoubles.at(driver, ID_BY_CSS, element);
        RecordingTestContext testContext = this.contextWith(driver);
        testContext.switchToWebContext("CHROMIUM");

        this.accountButton(testContext).click();

        Mockito.verify(element).click();
    }
}
