package io.zahori.framework.core;

/*-
 * #%L
 * zahori-framework
 * $Id:$
 * $HeadURL:$
 * %%
 * Copyright (C) 2021 PANEL SISTEMAS INFORMATICOS,S.L
 * %%
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public
 * License along with this program.  If not, see
 * <http://www.gnu.org/licenses/agpl-3.0.html>.
 * #L%
 */

import static io.zahori.framework.testsupport.SeleniumDoubles.disabledElement;
import static io.zahori.framework.testsupport.SeleniumDoubles.driver;
import static io.zahori.framework.testsupport.SeleniumDoubles.driverSupplying;
import static io.zahori.framework.testsupport.SeleniumDoubles.element;
import static io.zahori.framework.testsupport.SeleniumDoubles.elementWithText;
import static io.zahori.framework.testsupport.SeleniumDoubles.hiddenElement;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.zahori.framework.testsupport.RecordingTestContext;
import io.zahori.framework.testsupport.SeleniumDoubles;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.openqa.selenium.By;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;

/**
 * Contract of the dynamic waits added to {@link PageElement}.
 *
 * <p>Why they exist: across the Zahori consumer projects there were 36 places hand rolling
 * "wait until a condition holds" as {@code while (...) { Pause.pauseMillis(500); }}, because the
 * framework only knew how to wait for something to <b>appear</b> ({@code isVisible(int)},
 * {@code isPresent(int)}). Waiting for something to <b>disappear</b>, to become <b>enabled</b>, or
 * for an <b>attribute</b> to reach a value had no primitive, so everyone wrote their own — with no
 * cap, or a cap never checked, or an exception read as "still there".
 *
 * <p>These are also the first tests {@link PageElement} has ever had: 1000+ lines called by every
 * consumer of the framework, with no coverage at all until now.
 *
 * <p><b>Mutation status (PIT, profile {@code mutation}):</b> 10 killed, 2 survived on the new
 * methods. Both survivors are <b>equivalent mutants</b> and cannot be killed: PIT's boolean-return
 * mutators rewrite {@code return true} as {@code return true} and {@code return false} as
 * {@code return false} in {@code waitUntil}'s happy and catch paths. Do not chase them — the test
 * that would kill them cannot be written, because the mutated code is the original code.
 */
class PageElementWaitsTest {

    private static final By SPINNER = By.cssSelector(".spinner");

    private PageElement elementOn(WebDriver webDriver, By locator) {
        RecordingTestContext testContext = RecordingTestContext.forCase("PageElementWaitsTest")
                .withTimeout(1)
                .withDriver(webDriver);
        return new PageElement(new Page(testContext), "element under test", Locator.css(cssOf(locator)));
    }

    private static String cssOf(By locator) {
        // "By.cssSelector: .spinner" -> ".spinner"
        return String.valueOf(locator).replace("By.cssSelector: ", "");
    }

    // ---------------------------------------------------------------- isNotVisible

    @Test
    void isNotVisibleIsTrueWhenTheElementWasNeverThere() {
        PageElement spinner = elementOn(driverSupplying(() -> null), SPINNER);

        assertTrue(spinner.isNotVisible(1));
    }

    @Test
    void isNotVisibleWaitsUntilTheElementDisappears() {
        AtomicInteger lookups = new AtomicInteger();
        // Visible en las dos primeras consultas y ausente despues: si isNotVisible no esperase,
        // esto daria false.
        PageElement spinner = elementOn(
                driverSupplying(() -> lookups.getAndIncrement() < 2 ? element() : null), SPINNER);

        assertTrue(spinner.isNotVisible(5));
    }

    @Test
    void isNotVisibleIsTrueWhenTheElementIsPresentButHidden() {
        PageElement spinner = elementOn(driverSupplying(SeleniumDoubles::hiddenElement), SPINNER);

        assertTrue(spinner.isNotVisible(1));
    }

    @Test
    void isNotVisibleIsFalseWhenTheElementNeverGoesAway() {
        WebElement stuck = element();
        PageElement spinner = elementOn(driverSupplying(() -> stuck), SPINNER);

        assertFalse(spinner.isNotVisible(1));
    }

    /**
     * La sobrecarga sin argumentos usa el timeout del caso, que aqui es 1 segundo. Se comprueban
     * los DOS desenlaces: con solo el negativo, sustituir su return por false pasaba desapercibido
     * (mutante superviviente en PIT).
     */
    @Test
    void isNotVisibleWithNoArgumentsIsFalseWhenTheElementIsStillThere() {
        WebElement stuck = element();
        PageElement spinner = elementOn(driverSupplying(() -> stuck), SPINNER);

        assertFalse(spinner.isNotVisible());
    }

    @Test
    void isNotVisibleWithNoArgumentsIsTrueWhenTheElementIsNotThere() {
        PageElement spinner = elementOn(driverSupplying(() -> null), SPINNER);

        assertTrue(spinner.isNotVisible());
    }

    // ---------------------------------------------------------------- isEnabled

    @Test
    void isEnabledIsFalseWhileTheControlStaysDisabled() {
        WebElement button = disabledElement();
        PageElement payButton = elementOn(driverSupplying(() -> button), By.cssSelector("#pay"));

        assertFalse(payButton.isEnabled(1));
    }

    @Test
    void isEnabledWaitsUntilTheApplicationUnlocksTheControl() {
        AtomicInteger lookups = new AtomicInteger();
        PageElement payButton = elementOn(
                driverSupplying(() -> lookups.getAndIncrement() < 2 ? disabledElement() : element()),
                By.cssSelector("#pay"));

        assertTrue(payButton.isEnabled(5));
    }

    // ---------------------------------------------------------------- hasAttribute

    @Test
    void hasAttributeWaitsUntilTheAttributeReachesTheValue() {
        AtomicInteger lookups = new AtomicInteger();
        PageElement field = elementOn(driverSupplying(() -> {
            WebElement webElement = mock(WebElement.class);
            when(webElement.isDisplayed()).thenReturn(true);
            when(webElement.getAttribute("aria-expanded"))
                    .thenReturn(lookups.getAndIncrement() < 2 ? "false" : "true");
            return webElement;
        }), By.cssSelector("#selector"));

        assertTrue(field.hasAttribute("aria-expanded", "true", 5));
    }

    @Test
    void hasAttributeIsFalseWhenTheValueNeverArrives() {
        PageElement field = elementOn(driverSupplying(SeleniumDoubles::disabledElement), By.cssSelector("#pay"));

        assertFalse(field.hasAttribute("disabled", "false", 1));
    }

    // ---------------------------------------------------------------- containsText

    @Test
    void containsTextWaitsUntilTheTextAppears() {
        AtomicInteger lookups = new AtomicInteger();
        PageElement total = elementOn(
                driverSupplying(() -> lookups.getAndIncrement() < 2
                        ? elementWithText("36,53 EUR")
                        : elementWithText("44,40 USD")),
                By.cssSelector(".total"));

        assertTrue(total.containsText("USD", 5));
    }

    @Test
    void containsTextIsFalseWhenTheTextNeverAppears() {
        PageElement total = elementOn(driverSupplying(() -> elementWithText("36,53 EUR")), By.cssSelector(".total"));

        assertFalse(total.containsText("USD", 1));
    }

    // ---------------------------------------------------------------- implicit vs explicit wait

    /**
     * Selenium's own documentation: <i>"It is strongly advised not to mix implicit and explicit
     * waits, as this can lead to unpredictable wait times. For instance, setting an implicit wait
     * of 10 seconds and an explicit wait of 15 seconds could result in a timeout occurring after 20
     * seconds"</i>. This framework sets a global implicit wait, so the new waits zero it while they
     * run and restore it afterwards. This test pins that, because it is invisible from the outside
     * and the day someone "simplifies" it the only symptom will be waits taking longer than they
     * say.
     */
    @Test
    void theExplicitWaitDisablesTheImplicitWaitWhileItRuns() {
        WebDriver webDriver = driver();
        // El elemento se construye ANTES del when: element() crea su propio mock con sus stubs
        // dentro, y hacerlo en el argumento de thenReturn() abre un stubbing anidado que Mockito
        // rechaza con UnfinishedStubbing.
        WebElement visible = element();
        when(webDriver.findElement(any(By.class))).thenReturn(visible);
        // Y el mock de timeouts se resuelve FUERA del verify, por el mismo motivo.
        WebDriver.Timeouts timeouts = webDriver.manage().timeouts();
        PageElement spinner = elementOn(webDriver, SPINNER);

        spinner.isNotVisible(1);

        verify(timeouts, atLeastOnce()).implicitlyWait(Duration.ofSeconds(0L));
        verify(timeouts, atLeastOnce()).implicitlyWait(Duration.ofSeconds(1L));
    }
}
