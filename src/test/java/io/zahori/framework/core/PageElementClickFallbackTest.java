package io.zahori.framework.core;

import io.zahori.framework.testsupport.RecordingTestContext;
import io.zahori.framework.testsupport.SeleniumDoubles;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentMatchers;
import org.mockito.Mockito;
import org.openqa.selenium.By;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.TimeoutException;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;

class PageElementClickFallbackTest {
    private static final String CSS = ".boton";
    private RecordingTestContext testContext;

    private PageElement elementOn(WebDriver webDriver) {
        this.testContext = RecordingTestContext.forCase("PageElementClickFallbackTest").withTimeout(2).withDriver(webDriver);
        return new PageElement(new Page(this.testContext), "boton bajo prueba", Locator.css(".boton"));
    }

    @Test
    void fallsBackToJavascriptWhenTheNativeClickDoesNotAnswer() {
        WebDriver driver = SeleniumDoubles.driverWithJavascript();
        WebElement boton = SeleniumDoubles.element();
        ((WebElement)Mockito.doThrow(new Throwable[]{new TimeoutException("safaridriver no contesta")}).when(boton)).click();
        SeleniumDoubles.at(driver, By.cssSelector(".boton"), boton);
        this.elementOn(driver).click();
        ((JavascriptExecutor)Mockito.verify((JavascriptExecutor)driver)).executeScript(ArgumentMatchers.contains("click"), (Object[])ArgumentMatchers.any());
    }

    @Test
    void recordsInTheLogThatItFellBack() {
        WebDriver driver = SeleniumDoubles.driverWithJavascript();
        WebElement boton = SeleniumDoubles.element();
        ((WebElement)Mockito.doThrow(new Throwable[]{new TimeoutException("safaridriver no contesta")}).when(boton)).click();
        SeleniumDoubles.at(driver, By.cssSelector(".boton"), boton);
        this.elementOn(driver).click();
        Assertions.assertTrue(
            this.testContext.infoMessages().stream().anyMatch(m -> m.toLowerCase().contains("javascript")),
            "el repliegue a javascript tiene que quedar en el log: " + this.testContext.infoMessages()
        );
    }

    @Test
    void doesNotFallBackToJavascriptWhenTheNativeClickWorks() {
        WebDriver driver = SeleniumDoubles.driverWithJavascript();
        WebElement boton = SeleniumDoubles.element();
        SeleniumDoubles.at(driver, By.cssSelector(".boton"), boton);
        this.elementOn(driver).click();
        ((WebElement)Mockito.verify(boton)).click();
    }
}
