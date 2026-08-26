package io.zahori.framework.testsupport;

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

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.withSettings;

import io.appium.java_client.android.AndroidDriver;
import io.appium.java_client.ios.IOSDriver;
import java.time.Duration;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;
import org.openqa.selenium.Alert;
import org.openqa.selenium.By;
import org.openqa.selenium.ImmutableCapabilities;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.NoSuchElementException;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.remote.RemoteWebDriver;

/**
 * Selenium and Appium test doubles for unit-testing page objects without a browser.
 *
 * <p>«Doubles» y no «Mocks»: casi todo esto son <i>stubs</i> (meten datos hacia dentro), no mocks
 * (verifican llamadas hacia fuera, {@code verify}). Confundirlos lleva a asertar sobre stubs, que
 * es un test que no puede fallar.
 *
 * <p>This class lives in the framework on purpose. The doubles it builds encode knowledge of how
 * {@link io.zahori.framework.core.PageElement} and {@link io.zahori.framework.core.Page} talk to
 * the driver — that {@code isVisibleWithoutWait()} calls
 * {@code driver.manage().timeouts().implicitlyWait(...)} before searching, that a
 * {@code findElement} returning {@code null} reads as "not visible", that
 * {@code switchToFrame(...)} goes through {@code driver.switchTo().frame(...)} and <b>retries on
 * exception until the timeout</b>. Kept in a consumer project, that knowledge rots silently the day
 * the framework changes. Kept here, the framework's own tests break first.
 *
 * <p>It is published as a {@code test-jar}, so any Zahori consumer can depend on it:
 *
 * <pre>{@code
 * <dependency>
 *   <groupId>io.zahori</groupId>
 *   <artifactId>zahori-framework</artifactId>
 *   <version>${zahori-framework.version}</version>
 *   <type>test-jar</type>
 *   <scope>test</scope>
 * </dependency>
 * }</pre>
 *
 * <p><b>Three traps this class exists to remove.</b>
 *
 * <ul>
 *   <li>The {@code manage().timeouts()} chain has to be stubbed by hand. Mockito's
 *       {@code RETURNS_DEEP_STUBS} looks like the obvious shortcut, but with deep stubs the
 *       explicit {@code findElement} stub does not take effect and every element silently reads as
 *       "not visible".</li>
 *   <li>An {@code Answer} for a <b>void</b> method — {@code click()}, for example — must return
 *       {@code null}. Returning a value makes Mockito throw at invocation time, and the failure
 *       surfaces far away, in the page object's next step.</li>
 *   <li>{@code switchTo()} must never return {@code null}. {@code Page.switchToFrameWebElement}
 *       catches the exception and <b>recurses</b>, so an unstubbed {@code switchTo()} does not fail
 *       fast: it spins until {@code timeoutFindElement} and then throws "iframe not found", which
 *       reads as a locator problem and is not.</li>
 * </ul>
 */
public final class SeleniumDoubles {

    private static final String BROWSER_NAME = "browserName";

    private SeleniumDoubles() {
    }

    // ---------------------------------------------------------------- drivers

    /**
     * Desktop web driver with an empty DOM: {@code findElement} returns {@code null}, which
     * {@code PageElement} reads as "not visible". Populate it with
     * {@link #at(WebDriver, By, WebElement)}.
     *
     * @return a mock WebDriver, fully wired
     */
    public static WebDriver driver() {
        return wire(mock(WebDriver.class));
    }

    /**
     * Same as {@link #driver()} but the double also implements {@code JavascriptExecutor}, like the
     * real driver does. Page objects that run {@code (JavascriptExecutor) testContext.driver} throw
     * {@code ClassCastException} against a plain WebDriver mock.
     *
     * @return a mock WebDriver that can be cast to JavascriptExecutor
     */
    public static WebDriver driverWithJavascript() {
        return wire(mock(WebDriver.class, withSettings().extraInterfaces(JavascriptExecutor.class)));
    }

    /**
     * Android driver running a <b>native</b> app.
     *
     * <p>Returned as a real {@code AndroidDriver} mock and not as a WebDriver with a stubbed
     * predicate, because {@code TestContext.isAndroidDriver()} is literally
     * {@code driver instanceof AndroidDriver}. Handing this to a
     * {@link RecordingTestContext} makes the production platform branching run for real:
     * {@code isMobileDriver()} true, {@code isMobileNativeApp()} true.
     *
     * @return a mock AndroidDriver with no browserName capability
     */
    public static WebDriver androidDriver() {
        return mobileDriver(mock(AndroidDriver.class), null);
    }

    /**
     * Android driver running <b>mobile web</b> (Chrome), so {@code isMobileWebApp()} is true.
     *
     * @return a mock AndroidDriver with browserName=chrome
     */
    public static WebDriver androidWebDriver() {
        return mobileDriver(mock(AndroidDriver.class), "chrome");
    }

    /**
     * iOS driver running a <b>native</b> app, so {@code isMobileNativeApp()} is true.
     *
     * @return a mock IOSDriver with no browserName capability
     */
    public static WebDriver iosDriver() {
        return mobileDriver(mock(IOSDriver.class), null);
    }

    /**
     * iOS driver running <b>mobile web</b> (Safari), so {@code isMobileWebApp()} is true.
     *
     * @return a mock IOSDriver with browserName=safari
     */
    public static WebDriver iosWebDriver() {
        return mobileDriver(mock(IOSDriver.class), "safari");
    }

    // ---------------------------------------------------------------- DOM

    /**
     * Places an element at a given locator.
     *
     * @param driver  the driver to populate
     * @param locator the locator the page object will search for
     * @param element the element to return, or {@code null} for "not there"
     */
    public static void at(WebDriver driver, By locator, WebElement element) {
        when(driver.findElement(locator)).thenReturn(element);
    }

    /**
     * Driver whose DOM changes between lookups: the supplier decides, on every
     * {@code findElement}, whether the element is still there ({@code null} means gone, and then
     * {@code NoSuchElementException} is thrown, like Selenium does). This is what "wait until this
     * disappears" conditions need — a spinner, a loader, an overlay.
     *
     * @param elementSupplier called on every lookup
     * @return a mock WebDriver driven by the supplier
     */
    public static WebDriver driverSupplying(Supplier<WebElement> elementSupplier) {
        WebDriver driver = driver();
        when(driver.findElement(any(By.class))).thenAnswer(invocation -> {
            WebElement element = elementSupplier.get();
            if (element == null) {
                throw new NoSuchElementException("no longer in the DOM");
            }
            return element;
        });
        when(driver.findElements(any(By.class))).thenAnswer(invocation -> {
            WebElement element = elementSupplier.get();
            return element == null ? List.of() : List.of(element);
        });
        return driver;
    }

    /**
     * Driver that serves a list of elements regardless of the locator: {@code findElements} returns
     * the whole list and {@code findElement} hands them out in order — {@code PageElement.getText()}
     * re-locates the element before reading it — repeating the last one once exhausted.
     *
     * @param elements the elements to serve, in order
     * @return a mock WebDriver serving those elements
     */
    public static WebDriver driverServing(List<WebElement> elements) {
        WebDriver driver = driver();
        when(driver.findElements(any(By.class))).thenReturn(elements);

        if (elements.isEmpty()) {
            when(driver.findElement(any(By.class)))
                    .thenThrow(new NoSuchElementException("no elements in this test"));
            return driver;
        }

        Deque<WebElement> pending = new ArrayDeque<>(elements);
        when(driver.findElement(any(By.class))).thenAnswer(invocation -> {
            WebElement next = pending.poll();
            return next != null ? next : elements.get(elements.size() - 1);
        });
        return driver;
    }

    // ---------------------------------------------------------------- elements

    /**
     * @return a visible, enabled element with no text
     */
    public static WebElement element() {
        return element(null, null);
    }

    /**
     * @param text the text {@code getText()} will return
     * @return a visible, enabled element with that text
     */
    public static WebElement elementWithText(String text) {
        return element(text, null);
    }

    /**
     * @param text      the text {@code getText()} will return
     * @param ariaLabel the value of its {@code aria-label} attribute, or {@code null}
     * @return a visible, enabled element
     */
    public static WebElement element(String text, String ariaLabel) {
        WebElement element = mock(WebElement.class);
        when(element.isDisplayed()).thenReturn(true);
        when(element.isEnabled()).thenReturn(true);
        when(element.getText()).thenReturn(text == null ? "" : text);
        when(element.getAttribute("aria-label")).thenReturn(ariaLabel);
        return element;
    }

    /**
     * A disabled element, for testing waits on controls the application unlocks.
     *
     * @return a visible but disabled element, whose {@code disabled} attribute reads "true"
     */
    public static WebElement disabledElement() {
        WebElement element = mock(WebElement.class);
        when(element.isDisplayed()).thenReturn(true);
        when(element.isEnabled()).thenReturn(false);
        when(element.getText()).thenReturn("");
        when(element.getAttribute("disabled")).thenReturn("true");
        return element;
    }

    /**
     * An element that is in the DOM but not displayed.
     *
     * @return a present, non-visible element
     */
    public static WebElement hiddenElement() {
        WebElement element = mock(WebElement.class);
        when(element.isDisplayed()).thenReturn(false);
        when(element.isEnabled()).thenReturn(true);
        when(element.getText()).thenReturn("");
        return element;
    }

    // ---------------------------------------------------------------- windows and alerts

    /**
     * Makes the driver report a set of open window handles, plus the current one.
     *
     * <p>Needed by anything that opens a third-party payment window — PayPal, Bizum, Trustly — and
     * then waits for {@code getWindowHandles().size()} to change.
     *
     * @param driver  the driver to configure
     * @param current the handle {@code getWindowHandle()} returns
     * @param handles all open handles, in order
     */
    public static void withWindows(WebDriver driver, String current, String... handles) {
        Set<String> allHandles = new LinkedHashSet<>(List.of(handles));
        allHandles.add(current);
        when(driver.getWindowHandle()).thenReturn(current);
        when(driver.getWindowHandles()).thenReturn(allHandles);
    }

    /**
     * Makes the driver report a URL.
     *
     * @param driver the driver to configure
     * @param url    the URL {@code getCurrentUrl()} returns
     */
    public static void withUrl(WebDriver driver, String url) {
        when(driver.getCurrentUrl()).thenReturn(url);
    }

    /**
     * Attaches an alert to the driver, so {@code Page.acceptAlert()} and friends work.
     *
     * @param driver the driver to configure
     * @param text   the alert text
     * @return the alert mock, to verify {@code accept()} or {@code dismiss()} on it
     */
    public static Alert withAlert(WebDriver driver, String text) {
        Alert alert = mock(Alert.class);
        when(alert.getText()).thenReturn(text);
        when(driver.switchTo().alert()).thenReturn(alert);
        return alert;
    }

    // ---------------------------------------------------------------- internals

    /**
     * Wires the parts of the driver that {@code PageElement} and {@code Page} use unconditionally,
     * and that are a NullPointerException away from making every test look like a locator problem.
     */
    private static WebDriver wire(WebDriver driver) {
        WebDriver.Timeouts timeouts = mock(WebDriver.Timeouts.class);
        when(timeouts.implicitlyWait(any(Duration.class))).thenReturn(timeouts);
        WebDriver.Options options = mock(WebDriver.Options.class);
        when(options.timeouts()).thenReturn(timeouts);
        when(driver.manage()).thenReturn(options);

        // switchTo() nunca puede devolver null: Page.switchToFrameWebElement captura la excepcion
        // y RECURSE hasta agotar el timeout, asi que el sintoma no seria un NPE sino un
        // "iframe not found" varios segundos despues.
        WebDriver.TargetLocator targetLocator = mock(WebDriver.TargetLocator.class);
        when(targetLocator.frame(any(WebElement.class))).thenReturn(driver);
        when(targetLocator.frame(anyString())).thenReturn(driver);
        when(targetLocator.frame(anyInt())).thenReturn(driver);
        when(targetLocator.defaultContent()).thenReturn(driver);
        when(targetLocator.window(anyString())).thenReturn(driver);
        when(driver.switchTo()).thenReturn(targetLocator);

        when(driver.findElements(any(By.class))).thenReturn(List.of());
        when(driver.getWindowHandle()).thenReturn("window-0");
        when(driver.getWindowHandles()).thenReturn(Set.of("window-0"));
        return driver;
    }

    private static WebDriver mobileDriver(WebDriver driver, String browserName) {
        wire(driver);
        // isWebApp() lee las capabilities: con browserName presente es web movil, sin el es app
        // nativa. Se configuran aqui para que la logica de plataforma de TestContext corra de
        // verdad en el test, en vez de sobrescribir el predicado y acabar probando el doble.
        Map<String, Object> capabilities = browserName == null ? Map.of() : Map.of(BROWSER_NAME, browserName);
        when(((RemoteWebDriver) driver).getCapabilities()).thenReturn(new ImmutableCapabilities(capabilities));
        return driver;
    }
}
