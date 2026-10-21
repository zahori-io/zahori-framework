package io.zahori.framework.core;

import io.zahori.framework.testsupport.RecordingTestContext;
import io.zahori.framework.testsupport.SeleniumDoubles;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentMatchers;
import org.mockito.Mockito;
import org.openqa.selenium.By;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;

class PageElementClickScrollTest {
    private static final String CSS = ".boton";

    private PageElement elementOn(WebDriver webDriver) {
        RecordingTestContext testContext = RecordingTestContext.forCase("PageElementClickScrollTest").withTimeout(2).withDriver(webDriver);
        return new PageElement(new Page(testContext), "boton bajo prueba", Locator.css(".boton"));
    }

    @Test
    void scrollsTheElementIntoViewBeforeTheFirstClick() {
        WebDriver driver = SeleniumDoubles.driverWithJavascript();
        WebElement boton = SeleniumDoubles.element();
        SeleniumDoubles.at(driver, By.cssSelector(".boton"), boton);
        this.elementOn(driver).click();
        ((JavascriptExecutor)Mockito.verify((JavascriptExecutor)driver))
            .executeScript(ArgumentMatchers.contains("scrollIntoView"), (Object[])ArgumentMatchers.any());
        ((WebElement)Mockito.verify(boton)).click();
    }
}
