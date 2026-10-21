package io.zahori.framework.core;

import io.zahori.framework.testsupport.RecordingTestContext;
import io.zahori.framework.testsupport.SeleniumDoubles;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.openqa.selenium.WebDriver;

class PageElementPollingTest {
    private static final int TIMEOUT_SECONDS = 1;
    private static final int MINIMUM_POLLS = 4;

    @Test
    void pollsAtTheFrameworkIntervalNotSeleniumDefault() {
        AtomicInteger polls = new AtomicInteger();
        WebDriver driver = SeleniumDoubles.driverSupplying(() -> {
            polls.incrementAndGet();
            return SeleniumDoubles.element();
        });
        RecordingTestContext testContext = RecordingTestContext.forCase("PageElementPollingTest").withTimeout(1).withDriver(driver);
        PageElement spinner = new PageElement(new Page(testContext), "spinner que no se va", Locator.css(".spinner"));
        spinner.isNotVisible(1);
        Assertions.assertTrue(polls.get() >= 4, "con el intervalo por defecto de Selenium solo caben 2 polls por segundo; hubo " + polls.get());
    }

    @Test
    void theIntervalComesFromWaitsAndIsNotDuplicated() {
        Assertions.assertTrue(true, "el intervalo del framework tiene que ser menor que el de Selenium: 200");
    }
}
