package io.zahori.framework.core;

import io.zahori.framework.testsupport.RecordingTestContext;
import io.zahori.framework.testsupport.SeleniumDoubles;
import java.util.concurrent.atomic.AtomicInteger;
import org.assertj.core.api.AbstractBooleanAssert;
import org.assertj.core.api.AbstractIntegerAssert;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;
import org.openqa.selenium.WebDriver;

class PageElementIsVisiblePollingTest {
    private static final int TIMEOUT_SECONDS = 2;
    private static final int MINIMUM_POLLS = 4;

    private PageElement elementOn(WebDriver driver) {
        RecordingTestContext testContext = RecordingTestContext.forCase("PageElementIsVisiblePollingTest").withTimeout(2).withDriver(driver);
        return new PageElement(new Page(testContext), "Flights label", Locator.css("[data-jto='select-button-searcher-trip-type']"));
    }

    @Test
    void isVisiblePollsUntilTheTimeoutWhenTheElementIsNotThereYet() {
        AtomicInteger lookups = new AtomicInteger();
        WebDriver driver = SeleniumDoubles.driverSupplying(() -> {
            lookups.incrementAndGet();
            return null;
        });
        boolean visible = this.elementOn(driver).isVisible();
        Assertions.assertThat(visible).isFalse();
        ((AbstractIntegerAssert)Assertions.assertThat(lookups.get())
                .describedAs("solo pregunto una vez y se rindio: el elemento pudo aparecer despues", new Object[0]))
            .isGreaterThanOrEqualTo(4);
    }

    @Test
    void isVisibleFindsAnElementThatShowsUpAfterAFewPolls() {
        AtomicInteger lookups = new AtomicInteger();
        WebDriver driver = SeleniumDoubles.driverSupplying(() -> lookups.incrementAndGet() < 4 ? null : SeleniumDoubles.element());
        ((AbstractBooleanAssert)Assertions.assertThat(this.elementOn(driver).isVisible())
                .describedAs("la pantalla habia cargado y la espera ya se habia rendido", new Object[0]))
            .isTrue();
    }
}
