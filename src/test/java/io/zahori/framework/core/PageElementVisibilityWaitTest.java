package io.zahori.framework.core;

import io.zahori.framework.testsupport.RecordingTestContext;
import io.zahori.framework.testsupport.SeleniumDoubles;
import io.zahori.framework.utils.Chronometer;
import java.util.concurrent.atomic.AtomicInteger;
import org.assertj.core.api.AbstractLongAssert;
import org.assertj.core.api.AbstractThrowableAssert;
import org.assertj.core.api.Assertions;
import org.assertj.core.api.ObjectAssert;
import org.junit.jupiter.api.Test;
import org.openqa.selenium.InvalidSelectorException;
import org.openqa.selenium.TimeoutException;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebDriverException;
import org.openqa.selenium.WebElement;

class PageElementVisibilityWaitTest {
    private static final int TIMEOUT_SECONDS = 2;
    private static final int SE_RINDIO_AL_INSTANTE_MILLIS = 500;

    private PageElement elementOf(WebDriver driver) {
        RecordingTestContext testContext = RecordingTestContext.forCase("PageElementVisibilityWaitTest").withTimeout(2).withDriver(driver);
        return new PageElement(new Page(testContext), "input del buscador", Locator.id("flight-searcher-departure"));
    }

    @Test
    void survivesATransientDriverErrorWhileTheDomMoves() {
        AtomicInteger sondeos = new AtomicInteger();
        WebDriver driver = SeleniumDoubles.driverSupplying(() -> {
            if (sondeos.incrementAndGet() < 3) {
                throw new WebDriverException("javascript error: document unloaded while waiting for result");
            } else {
                return SeleniumDoubles.element();
            }
        });
        WebElement encontrado = this.elementOf(driver).findElement();
        ((ObjectAssert)Assertions.assertThat(encontrado).describedAs("la espera se rendia en el primer sondeo", new Object[0])).isNotNull();
        Assertions.assertThat(sondeos.get()).isGreaterThanOrEqualTo(3);
    }

    @Test
    void usesTheWholeTimeoutWhenTheElementNeverShows() {
        WebDriver driver = SeleniumDoubles.driverSupplying(PageElementVisibilityWaitTest.HiddenDouble::hidden);
        Chronometer chrono = new Chronometer();
        Assertions.assertThatThrownBy(() -> this.elementOf(driver).findElement()).hasMessageContaining("Element is not visible");
        ((AbstractLongAssert)Assertions.assertThat(chrono.getElapsedTime()).describedAs("se rindió antes de agotar el plazo del caso", new Object[0]))
            .isGreaterThanOrEqualTo(2000L);
    }

    @Test
    void theFailureReportsHowManyTimesItAsked() {
        WebDriver driver = SeleniumDoubles.driverSupplying(PageElementVisibilityWaitTest.HiddenDouble::hidden);
        Assertions.assertThatThrownBy(() -> this.elementOf(driver).findElement())
            .hasMessageContaining("Sondeos:")
            .matches(
                error -> error.getMessage().matches(".*Sondeos: [1-9]\\d* en \\d+[,.]\\d+ s.*"),
                "el mensaje debe traer el número de sondeos y el tiempo consumido"
            );
    }

    @Test
    void theFailureCarriesItsCause() {
        WebDriver driver = SeleniumDoubles.driverSupplying(PageElementVisibilityWaitTest.HiddenDouble::hidden);
        Assertions.assertThatThrownBy(() -> this.elementOf(driver).findElement())
            .hasMessageContaining("flight-searcher-departure")
            .hasCauseInstanceOf(TimeoutException.class);
    }

    @Test
    void reportsWhatTheDriverAnsweredOnEveryAttempt() {
        WebDriver driver = SeleniumDoubles.driverSupplying(() -> {
            throw new WebDriverException("disconnected: not connected to DevTools\nBuild info: ...");
        });
        ((AbstractThrowableAssert)Assertions.assertThatThrownBy(() -> this.elementOf(driver).findElement())
                .describedAs("el plazo se agotaba sin decir que contesto el driver", new Object[0]))
            .hasMessageContaining("disconnected: not connected to DevTools")
            .hasCauseInstanceOf(TimeoutException.class);
    }

    @Test
    void doesNotBlameTheDriverWhenTheElementWasJustHidden() {
        WebDriver driver = SeleniumDoubles.driverSupplying(PageElementVisibilityWaitTest.HiddenDouble::hidden);
        Assertions.assertThatThrownBy(() -> this.elementOf(driver).findElement()).hasMessageNotContaining("El driver respondio con error");
    }

    @Test
    void failsImmediatelyOnAnInvalidSelector() {
        WebDriver driver = SeleniumDoubles.driverSupplying(() -> {
            throw new InvalidSelectorException("invalid selector: Compound class names not permitted");
        });
        Chronometer chrono = new Chronometer();
        Assertions.assertThatThrownBy(() -> this.elementOf(driver).findElement()).hasCauseInstanceOf(InvalidSelectorException.class);
        ((AbstractLongAssert)Assertions.assertThat(chrono.getElapsedTime()).describedAs("un selector invalido consumia el plazo entero", new Object[0]))
            .isLessThan(500L);
    }

    private static final class HiddenDouble {
        static WebElement hidden() {
            return SeleniumDoubles.hiddenElement();
        }
    }
}
