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
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 * #L%
 */
import com.google.common.collect.ImmutableMap;
import io.appium.java_client.AppiumDriver;
import io.zahori.framework.robot.UtilsRobot;
import io.zahori.framework.utils.Chronometer;
import io.zahori.framework.utils.Pause;
import io.zahori.framework.utils.Waits;
import io.zahori.framework.utils.WebdriverUtils;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.apache.commons.lang3.StringUtils;
import org.openqa.selenium.*;
import org.openqa.selenium.interactions.Action;
import org.openqa.selenium.interactions.Actions;
import org.openqa.selenium.interactions.PointerInput;
import org.openqa.selenium.interactions.Sequence;
import org.openqa.selenium.support.ui.ExpectedCondition;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.Select;
import org.openqa.selenium.support.ui.WebDriverWait;

public class PageElement {

    public static final String ERROR = "\nError: ";
    public static final String ON = "\" on ";
    public static final String ELEMENT_IS_NOT_EDITABLE = "Element is not editable: ";
    public static final String VALUE = "value";
    public static final String GET_TEXT = "Get text \"";
    public static final String FROM = "\" from ";

    /** A partir de aqui, un "no se ve" ha costado bastante como para contar por que. */
    private static final long DIAGNOSTIC_THRESHOLD_MILLIS = 1000L;

    public String name;
    public Locator locator;
    public Page page;
    public TestContext testContext;
    private WebDriver driver;

    public WebElement webElement;

    public PageElement(Page page, String name, Locator locator) {
        this.name = name;
        this.locator = locator;
        this.page = page;
        this.testContext = page.testContext;
        this.driver = this.testContext.driver;
    }

    @Override
    public String toString() {
        return "\"" + name + "\" [" + locator.getLocatorType() + ": " + locator.getLocatorText() + "] [page: " + page.name + "] [test: "
                + testContext.testCaseName + "]";
    }

    public void setDriver(WebDriver driver) {
        this.driver = driver;
    }

    /**
     * El localizador con el que preguntar al driver AHORA.
     *
     * <p>"id" significa dos cosas distintas según donde mire el driver. En un movil en contexto
     * nativo es el <b>resource-id</b> de la vista -documentado por el driver UiAutomator2: la
     * estrategia {@code id} mapea a {@code By.res}- y ahi es justo lo que se quiere. En un webview
     * el comando acaba en chromedriver, que sólo admite las cinco estrategias del W3C: css
     * selector, link text, partial link text, tag name y xpath. "id" no esta entre ellas y la
     * respuesta es {@code invalid argument: invalid locator}.
     *
     * <p>Peor aun, esa respuesta no es una {@code NotFoundException}, asi que tumbaba la espera
     * entera en el primer sondeo en vez de reintentar. Medido el 22/09/2026 en Android contra STG.
     *
     * <p>Se traduce por selector de atributo y no por {@code #id}: un id que empiece por digito o
     * lleve dos puntos -Angular Material los genera así- necesitaria escape en la forma corta.
     */
    private By effectiveBy() {
        if ((locator.getLocatorType() != Locator.LocatorTypes.id) || testContext.isNativeContext()
                || !(driver instanceof AppiumDriver)) {
            return locator.getBy();
        }
        return By.cssSelector("[id=\"" + locator.getLocatorText().replace("\"", "\\\"") + "\"]");
    }

    /**
     * Espera con el intervalo de sondeo del framework. Selenium sondea cada 500 ms si no se le
     * indica otra cosa, y con comandos de ~46 ms eso es medio segundo dormido por comprobación.
     */
    private WebDriverWait waitOf(long seconds) {
        return new WebDriverWait(driver, Duration.ofSeconds(seconds), Duration.ofMillis(Waits.POLLING_MILLIS));
    }

    public void click() {
        click(false, false);
    }

    public void clickNonVisible() {
        initWebElement();
        try {
            webElement.click();
            testContext.logInfo("Click on " + this);
        } catch (Exception e) {
            throw new RuntimeException("Unable to click on " + this + getErrorMessage(e));
        }
    }

    public void clickWithScroll() {
        click(true, false);
    }

    public boolean isclickable() {
        Chronometer chrono = new Chronometer();
        webElement = findElement();
        try {
            scroll();

            WebDriverWait wait = waitOf(testContext.timeoutFindElement - chrono.getElapsedSeconds());
            wait.until(ExpectedConditions.elementToBeClickable(webElement));
            return true;
        } catch (final Exception e) {
            return false;
        }
    }

    public boolean waitAndClick() {
        Chronometer chrono = new Chronometer();
        webElement = findElement();
        try {
            WebDriverWait wait = waitOf(testContext.timeoutFindElement - chrono.getElapsedSeconds());
            wait.until(ExpectedConditions.elementToBeClickable(webElement));
            webElement.click();
            testContext.logInfo("Click on " + this);
            return true;
        } catch (final Exception e) {
            return false;
        }
    }

    public void scroll() {
        webElement = findElementPresent();
        try {
            JavascriptExecutor executor = (JavascriptExecutor) driver;
            executor.executeScript("arguments[0].scrollIntoView({block: \"center\", behavior: \"auto\"});", webElement);
        } catch (Exception e) {
            throw new RuntimeException("Unable to scroll on " + this + getErrorMessage(e));
        }
    }

    public void scrollMiddle() {
        webElement = findElementPresent();
        try {
            String scrollElementIntoMiddle = "var viewPortHeight = Math.max(document.documentElement.clientHeight, window.innerHeight || 0);"
                    + "var elementTop = arguments[0].getBoundingClientRect().top;" + "window.scrollBy(0, elementTop-(viewPortHeight/2));";
            JavascriptExecutor executor = (JavascriptExecutor) driver;
            executor.executeScript(scrollElementIntoMiddle, webElement);
        } catch (Exception e) {
            throw new RuntimeException("Unable to scroll on " + this + getErrorMessage(e));
        }
    }

    private void click(boolean scroll, boolean retry) {
        Chronometer chrono = new Chronometer();
        webElement = findElement();
        try {
            // En contexto nativo no: el scroll se hace con javascript y ahi no hay motor que lo
            // ejecute. UiAutomator2 contesta "Method is not implemented" -111 ms y un aviso por
            // click- a una pregunta que no había que hacerle.
            if (scroll && !testContext.isNativeContext()) {
                try {
                    scroll();
                } catch (final Exception ex) {
                    testContext.logWarn("Unable to scroll to {}: {}", this.toString(), getErrorMessage(ex));
                }
            }

            WebDriverWait wait = waitOf(testContext.timeoutFindElement - chrono.getElapsedSeconds());
            wait.until(ExpectedConditions.elementToBeClickable(webElement));

            webElement.click();
            testContext.logInfo("Click on " + this);
        } catch (final Exception e) {
            if (retry) {
                throw new RuntimeException("Unable to click on " + this + getErrorMessage(e));
            } else {
                click(true, true);
            }
        }
    }

    public void clickJavascript() {
        initWebElement();
        try {
            JavascriptExecutor executor = (JavascriptExecutor) driver;
            executor.executeScript("arguments[0].scrollIntoView({block: \"center\", behavior: \"auto\"});", webElement);
            executor.executeScript("arguments[0].click()", webElement);
            testContext.logInfo("Click (javascript) on " + this);
        } catch (final Exception e) {
            throw new RuntimeException("Unable to click on " + this + getErrorMessage(e));
        }
    }

    public void doubleClick() {
        webElement = findElement();
        try {
            final Actions builder = new Actions(driver);
            builder.moveToElement(webElement).doubleClick().perform();
            testContext.logInfo("Double click on " + this);
        } catch (final Exception e) {
            throw new RuntimeException("Unable to double click on " + this + getErrorMessage(e));
        }
    }

    public void physicalClick() {
        webElement = findElement();
        final int coordenate_X = Objects.requireNonNull(WebdriverUtils.getElementCenter(driver, webElement)).getX();
        final int coordenate_Y = Objects.requireNonNull(WebdriverUtils.getElementCenter(driver, webElement)).getY();
        final int coordenate_Y2 = coordenate_Y - 15;
        final UtilsRobot robot = new UtilsRobot();
        robot.moveMousePointer(coordenate_X, coordenate_Y2);
        robot.click();
    }

    public void clickAtWebElementPosition() {
        webElement = findElementPresent();
        final Actions action = new Actions(driver);
        action.moveToElement(webElement).click(webElement).perform();
    }

    public void writeNotValidateValue(String text) {
        Chronometer chrono = new Chronometer();
        webElement = findElement();
        try {
            WebDriverWait wait = waitOf(testContext.timeoutFindElement - chrono.getElapsedSeconds());
            wait.until(ExpectedConditions.elementToBeClickable(webElement));
            while (!webElement.isEnabled() && (chrono.getElapsedSeconds() < testContext.timeoutFindElement)) {
                Pause.pause();
                initWebElement();
            }
            webElement.clear();
            webElement.sendKeys(text);
            testContext.logInfo("Write text \"" + text + ON + this);
        } catch (StaleElementReferenceException e) {
            throw new RuntimeException(ELEMENT_IS_NOT_EDITABLE + this);
        }

    }

    public void write(String text) {
        try {
            writeNoLog(text, false);
            testContext.logInfo("Write text \"" + text + ON + this);
        } catch (StaleElementReferenceException e) {
            webElement = findElement();
            write(text);
        }

    }

    public void writeHiddenElement(String text) {
        initWebElement();
        try {
            webElement.sendKeys(text);
            testContext.logInfo("Write (hidden element)  text: '" + text + "' on " + this);
        } catch (final Exception e) {
            throw new RuntimeException("Unable to write on " + this + getErrorMessage(e));
        }
    }

    public void writeCharByChar(String text) {
        initWebElement();
        try {
            if (!StringUtils.isEmpty(text)) {
                for (int i = 0; i < text.length(); i++) {
                    webElement.sendKeys(String.valueOf(text.charAt(i)));
                    // Pause.shortPause();
                }
            }

            testContext.logInfo("Write (char by char)  text: '" + text + "' on " + this);
        } catch (final Exception e) {
            throw new RuntimeException("Unable to write on " + this + getErrorMessage(e));
        }
    }

    public void writeOrSelectByTag(String text) {
        webElement = findElement();
        if ("select".equals(webElement.getTagName())) {
            selectOptionText(text);
        } else {
            write(text);
        }
    }

    public void writePassword(String password) {
        writeNoLog(password, false);
        testContext.logInfo("Write text \"********\" on " + this);
    }

    public void clear() {
        webElement = findElement();
        try {
            webElement.clear();
        } catch (final InvalidElementStateException e) {
            throw new RuntimeException(ELEMENT_IS_NOT_EDITABLE + this);
        }
    }

    /**
     * Clears an input field by javascript and then triggers the input event
     */
    public void clearJavascript() {
        initWebElement();

        JavascriptExecutor jsExecutor = (JavascriptExecutor) testContext.driver;
        jsExecutor.executeScript("arguments[0].value = ''", this.webElement);
        jsExecutor.executeScript("arguments[0].dispatchEvent(new Event('input'))", this.webElement);
    }

    public void triggerInputEvent() {
        initWebElement();

        JavascriptExecutor jsExecutor = (JavascriptExecutor) testContext.driver;
        jsExecutor.executeScript("arguments[0].dispatchEvent(new Event('input'))", this.webElement);
    }

    public void triggerChangeEvent() {
        initWebElement();

        JavascriptExecutor jsExecutor = (JavascriptExecutor) testContext.driver;
        jsExecutor.executeScript("arguments[0].dispatchEvent(new Event('change'))", this.webElement);
    }

    public void triggerBlurEvent() {
        initWebElement();

        JavascriptExecutor jsExecutor = (JavascriptExecutor) testContext.driver;
        jsExecutor.executeScript("arguments[0].dispatchEvent(new Event('blur'))", this.webElement);
    }

    public void triggerFocusEvent() {
        initWebElement();

        JavascriptExecutor jsExecutor = (JavascriptExecutor) testContext.driver;
        jsExecutor.executeScript("arguments[0].dispatchEvent(new Event('focus'))", this.webElement);
    }

    /**
     * Use clear() method instead of clearInput(). clearInput will be removed in
     * future versions
     */
    @Deprecated()
    public void clearInput() {
        webElement = findElement();
        try {
            webElement.clear();
        } catch (final InvalidElementStateException e) {
            throw new RuntimeException(ELEMENT_IS_NOT_EDITABLE + this);
        }
    }

    public void mouseOver() {
        webElement = findElement();
        try {

            final Actions builder = new Actions(this.driver);
            builder.moveToElement(webElement).perform();

            testContext.logInfo("Mouse over " + this);
        } catch (final InvalidElementStateException e) {
            throw new RuntimeException(ELEMENT_IS_NOT_EDITABLE + this);
        }
    }

    public void mouseOverNonVisible() {
        webElement = findElementPresent();
        try {

            final Actions builder = new Actions(this.driver);
            builder.moveToElement(webElement).perform();

            testContext.logInfo("Mouse over " + this);
        } catch (final InvalidElementStateException e) {
            throw new RuntimeException(ELEMENT_IS_NOT_EDITABLE + this);
        }
    }

    public void selectOptionValue(String value) {
        selectOptionValue(value, true);
    }

    public void selectOptionText(String text) {
        selectOptionText(text, true);
    }

    public void selectOptionIndex(int index) {
        selectOptionIndex(index, true);
    }

    public void selectOptionValueNonVisible(String value) {
        selectOptionValue(value, false);
    }

    public void selectOptionTextNonVisible(String text) {
        selectOptionText(text, false);
    }

    public void selectOptionIndexNonVisible(int index) {
        selectOptionIndex(index, false);
    }

    private void selectOptionValue(String value, boolean forceVisibility) {
        final Select select = forceVisibility ? new Select(findElement()) : new Select(findElementPresent());
        try {
            select.selectByValue(value);
            testContext.logInfo("Select option by value '" + value + "' " + this);
        } catch (final Exception e) {
            throw new RuntimeException("Select option not found: option :" + value + " elemnt :" + this);
        }
    }

    private void selectOptionText(String text, boolean forceVisibility) {
        final Select select = forceVisibility ? new Select(findElement()) : new Select(findElementPresent());
        try {
            select.selectByVisibleText(text);
            testContext.logInfo("Select option by text '" + text + "' " + this);
        } catch (final Exception e) {
            StringBuilder stringBuffer = new StringBuilder();
            for (WebElement selectOption : select.getOptions()) {
                stringBuffer.append("{").append(selectOption.getText()).append("}");
            }
            throw new RuntimeException("Select option not found: option :" + text + " elemnt :" + this + " with values :" + stringBuffer.toString());
        }
    }

    private void selectOptionIndex(int index, boolean forceVisibility) {
        final Select select = forceVisibility ? new Select(findElement()) : new Select(findElementPresent());
        try {
            select.selectByIndex(index);
            testContext.logInfo("Select option by index '" + index + "' " + this);
        } catch (final Exception e) {
            throw new RuntimeException("Select option not found: " + this);
        }
    }

    public List<String> getSelectOptionsText() {
        return getSelectOptionsText(true);
    }

    public List<String> getSelectOptionsTextNonVisible() {
        return getSelectOptionsText(false);
    }

    public List<String> getSelectOptionsValue() {
        return getSelectOptionsValue(true);
    }

    public List<String> getSelectOptionsValueNonVisible() {
        return getSelectOptionsValue(false);
    }

    public String getSelectedOptionValue() {
        return getSelectedOptionValue(true);
    }

    public String getSelectedOptionValueNonVisible() {
        return getSelectedOptionValue(false);
    }

    private List<String> getSelectOptionsText(boolean forceVisibility) {
        try {
            final Select select = forceVisibility ? new Select(findElement()) : new Select(findElementPresent());
            final List<String> list = new ArrayList<>();
            for (final WebElement selectOption : select.getOptions()) {
                list.add(selectOption.getText());
            }
            return list;
        } catch (final Exception e) {
            return new ArrayList<>();
        }

    }

    private List<String> getSelectOptionsValue(boolean forceVisibility) {
        try {
            final Select select = forceVisibility ? new Select(findElement()) : new Select(findElementPresent());
            final List<String> list = new ArrayList<>();
            for (final WebElement selectOption : select.getOptions()) {
                list.add(selectOption.getAttribute("Value"));
            }
            return list;
        } catch (final Exception e) {
            return new ArrayList<>();
        }

    }

    private String getSelectedOptionValue(boolean forceVisibility) {
        final Select select = forceVisibility ? new Select(findElement()) : new Select(findElementPresent());
        final WebElement option = select.getFirstSelectedOption();
        final String selectedValue = option.getAttribute(VALUE);
        testContext.logInfo("Value: '" + selectedValue + "' of selected option of element " + this);
        return option.getAttribute(VALUE);
    }

    public void check(boolean checked) {
        webElement = findElement();
        if (checked && !webElement.isSelected()) {
            click();
        }

        if (!checked && webElement.isSelected()) {
            click();
        }
    }

    public boolean isSelected() {
        webElement = findElement();
        return webElement.isSelected();
    }

    public String getText() {
        webElement = findElement();
        try {
            final String text = webElement.getText().trim();
            testContext.logInfo(GET_TEXT + text + FROM + this);
            return text;
        } catch (StaleElementReferenceException e) {
            initWebElement();
            return getText();
        } catch (final Exception e) {
            throw new RuntimeException("Unable to get element text: " + this + getErrorMessage(e));
        }
    }

    public String getText(boolean mustBeVisible) {
        if (mustBeVisible) {
            return getText();
        } else {
            initWebElement();
            try {
                final String text = webElement.getText().trim();
                testContext.logInfo(GET_TEXT + text + FROM + this);
                return text;
            } catch (final Exception e) {
                throw new RuntimeException(e);
            }
        }
    }

    public String getTextByTag() {
        webElement = findElement();
        try {
            String text;
            switch (webElement.getTagName()) {
                case "select":
                    final Select select = new Select(webElement);
                    text = select.getFirstSelectedOption().getText().trim();
                    break;
                case "input":
                    text = webElement.getAttribute(VALUE);
                    break;
                default:
                    text = webElement.getText().trim();
                    break;
            }

            testContext.logInfo(GET_TEXT + text + FROM + this);
            return text;
        } catch (final Exception e) {
            throw new RuntimeException("Unable to get element text: " + this);
        }
    }

    public String getAttributeValue(String attribute) {
        return getAttributeValue(attribute, true);
    }

    public String getAttributeValueWithoutPrint(String attribute) {
        return getAttributeValue(attribute, false);
    }

    private String getAttributeValue(String attribute, boolean print) {
        webElement = findElementPresent();
        String value = "";
        try {
            value = webElement.getAttribute(attribute).trim();
        } catch (final Exception e) {
            //
        }
        if (print) {
            testContext.logInfo("Get '" + attribute + "' attribute [value='" + value + "'] for " + this);
        } else {
            testContext.logInfo("Get '" + attribute + "' attribute for " + this);
        }

        return value;
    }

    public boolean isAttributePresent(String attribute) {
        webElement = findElementPresent();
        try {
            return webElement.getAttribute(attribute) != null;
        } catch (final Exception e) {
            return false;
        }
    }

    public String getInputValue() {
        webElement = findElement();
        try {
            final String value = webElement.getDomProperty(VALUE);
            testContext.logInfo("Get DOM property 'value': \"" + value + FROM + this);
            return value;
        } catch (final Exception e) {
            throw new RuntimeException("Unable to get input value " + this + ": " + e.getMessage());
        }
    }

    public String getDomProperty(String property) {
        webElement = findElement();
        try {
            final String value = webElement.getDomProperty(property);
            testContext.logInfo("Get DOM property '" + property + "': \"" + value + FROM + this);
            return value;
        } catch (final Exception e) {
            throw new RuntimeException("Unable to get DOM property '" + property + "' " + this + ": " + e.getMessage());
        }
    }

    public String getDomAttribute(String attribute) {
        webElement = findElement();
        try {
            final String value = webElement.getDomAttribute(attribute);
            testContext.logInfo("Get DOM attribute '" + attribute + "': \"" + value + FROM + this);
            return value;
        } catch (final Exception e) {
            throw new RuntimeException("Unable to get DOM attribute '" + attribute + "' " + this + ": " + e.getMessage());
        }
    }

    public void sendKeys(Keys keys) {
        webElement = findElement();
        webElement.sendKeys(keys);
    }

    public void validateText(String expectedText) {
        final String realText = getText();
        if (realText.equals(expectedText)) {
            testContext.logInfo("Validated text is \"" + realText + ON + this);
        } else {
            throw new RuntimeException("Expected text \"" + expectedText + "\" is not equal to real text \"" + realText + ON + this);
        }
    }

    public void validateInputText(String expectedText) {
        final String realText = getInputValue();
        if (realText.equals(expectedText)) {
            testContext.logInfo("Validated input value is \"" + realText + ON + this);
        } else {
            throw new RuntimeException("Expected input value \"" + expectedText + "\" is not equal to real input value \"" + realText + ON + this);
        }
    }

    public void validateIsPresent() {
        try {
            webElement = findElementPresent();
            testContext.logInfo("Validated is present in DOM: " + this);
        } catch (final Exception e) {
            throw new RuntimeException("Element is not present in DOM: " + this, e);
        }
    }

    public boolean isPresent() {
        try {
            webElement = findElementPresent();
            return webElement != null;
        } catch (final Exception e) {
            return false;
        }
    }

    public boolean isPresent(int maxSecondsWaiting) {
        // Sondeo explicito y no espera implicita: esta sondea y aquella bloquea. Con la implicita
        // puesta, una busqueda contra un elemento que aun no esta no devuelve el control hasta
        // agotarla, así que la espera de fuera no llega a dar un segundo intento.
        return Waits.until(this::isPresentWithoutWait, maxSecondsWaiting);
    }

    public boolean isPresentWithoutWait() {
        try {
            testContext.applyImplicitWait(Duration.ZERO);
            return driver.findElement(effectiveBy()) != null;
        } catch (final Exception e) {
            return false;
        }
    }

    public void validateIsVisible() {
        validateIsPresent();

        try {
            findElementVisible();
            testContext.logInfo("Validated is visible: " + this);
        } catch (final Exception e) {
            throw new RuntimeException("Element is present in DOM but not visible: " + this);
        }
    }

    public boolean isVisible() {
        final Chronometer chrono = new Chronometer();
        try {
            findElementVisible();
            return true;
        } catch (final Exception e) {
            // Un "no se ve" que ha costado segundos trae dentro por que -cuantos sondeos, que
            // contesto el driver- y aquí se perdia al reducirlo a un booleano. Solo se cuenta
            // cuando la espera consumió tiempo: como predicado se usa en todas partes y responde
            // en milisegundos, así que registrarlo siempre sería ruido.
            if (chrono.getElapsedTime() > DIAGNOSTIC_THRESHOLD_MILLIS) {
                testContext.logInfo("Not visible after waiting: " + getErrorMessage(e));
            }
            return false;
        }
    }

    public boolean isVisible(int maxSecondsWaiting) {
        // Sondeo del framework y no medio segundo dormido por comprobación: con comandos de ~46 ms,
        // la pausa era el 90 % del tiempo de espera.
        return Waits.until(this::isVisibleWithoutWait, maxSecondsWaiting);
    }

    public boolean isVisibleWithoutWait() {
        try {
            // "Sin espera" tiene que serlo de verdad: al conmutar a un webview el driver deja de
            // conservar los timeouts, y sin esto cada comprobación pasaba a costar el plazo entero.
            testContext.applyImplicitWait(Duration.ZERO);
            webElement = driver.findElement(effectiveBy());
            return webElement.isDisplayed();
        } catch (final Exception e) {
            return false;
        }
    }

    /**
     * Waits until this element is no longer visible, or is gone from the DOM.
     *
     * <p>This is the counterpart of {@link #isVisible(int)} and it did not exist before: every
     * consumer that needed to wait for a spinner, a loader or an overlay to disappear was hand
     * rolling {@code while (element.isVisibleWithoutWait()) { Pause.pauseMillis(500); }}. That loop
     * is easy to get subtly wrong — no cap, a cap that is never checked, or an exception that is
     * read as "still there".
     *
     * @param maxSecondsWaiting how long to wait at most
     * @return {@code true} if it became invisible (or was never there) within the time, {@code false} otherwise
     */
    public boolean isNotVisible(int maxSecondsWaiting) {
        return waitUntil(maxSecondsWaiting, ExpectedConditions.invisibilityOfElementLocated(effectiveBy()));
    }

    /**
     * Waits until this element is no longer visible, using the case timeout.
     *
     * @return {@code true} if it became invisible (or was never there) within the time
     */
    public boolean isNotVisible() {
        return isNotVisible(testContext.timeoutFindElement);
    }

    /**
     * Waits until this element is present and enabled.
     *
     * <p>Different from {@link #isclickable()}, which also requires visibility: a control can be
     * enabled and still be scrolled out of view. Use this one when what you are waiting for is the
     * application enabling the control — a submit button that a form validation unlocks, typically.
     *
     * @param maxSecondsWaiting how long to wait at most
     * @return {@code true} if it became enabled within the time, {@code false} otherwise
     */
    public boolean isEnabled(int maxSecondsWaiting) {
        return waitUntil(maxSecondsWaiting, webDriver -> webDriver.findElement(effectiveBy()).isEnabled());
    }

    /**
     * Waits until an attribute of this element reaches an expected value.
     *
     * <p>Meant to replace polling {@code getAttributeValue(...)} in a loop, which besides being
     * hand rolled writes one line into the evidences on every turn.
     *
     * @param attribute         attribute name
     * @param expectedValue     value to wait for
     * @param maxSecondsWaiting how long to wait at most
     * @return {@code true} if the attribute reached that value within the time, {@code false} otherwise
     */
    public boolean hasAttribute(String attribute, String expectedValue, int maxSecondsWaiting) {
        return waitUntil(maxSecondsWaiting, ExpectedConditions.attributeToBe(effectiveBy(), attribute, expectedValue));
    }

    /**
     * Waits until this element's text contains the expected fragment.
     *
     * @param expectedText      fragment to wait for
     * @param maxSecondsWaiting how long to wait at most
     * @return {@code true} if the text appeared within the time, {@code false} otherwise
     */
    public boolean containsText(String expectedText, int maxSecondsWaiting) {
        return waitUntil(maxSecondsWaiting,
                ExpectedConditions.textToBePresentInElementLocated(effectiveBy(), expectedText));
    }

    /**
     * Runs an explicit wait with the implicit wait temporarily disabled.
     *
     * <p>That dance is not decoration. Selenium's own documentation is explicit about it: <i>"It is
     * strongly advised not to mix implicit and explicit waits, as this can lead to unpredictable
     * wait times. For instance, setting an implicit wait of 10 seconds and an explicit wait of 15
     * seconds could result in a timeout occurring after 20 seconds"</i>. This framework sets a
     * global implicit wait of {@code testContext.timeoutFindElement}, so any explicit wait added on
     * top has to zero it first and restore it afterwards — which is exactly what
     * {@link #isVisibleWithoutWait()} already does for the same reason.
     *
     * @param maxSecondsWaiting how long to wait at most
     * @param condition         the condition to wait for
     * @return {@code true} if the condition held within the time, {@code false} on timeout or error
     */
    private boolean waitUntil(int maxSecondsWaiting, ExpectedCondition<Boolean> condition) {
        try {
            WebDriverWait wait = waitOf(maxSecondsWaiting);
            // until() never returns false: FluentWait treats false and null as "not yet" and keeps
            // polling, so it either returns something truthy or throws TimeoutException. Checking
            // its return value would be a branch that cannot be taken.
            wait.until(condition);
            return true;
        } catch (final Exception e) {
            return false;
        }
    }

    public void validateIsNotPresent() {
        try {
            webElement = findElementPresent();
            testContext.logInfo("Validated is not present in DOM: " + this);
            throw new RuntimeException("Element is present but it is not expected: " + this);
        } catch (final Exception e) {
            testContext.logInfo("Validated that not exists " + this);
        }
    }

    public void initWebElement() {
        try {
            this.webElement = findElementPresent();
        } catch (final NullPointerException | NoSuchElementException e) {
            this.webElement = null;
        } catch (final StaleElementReferenceException e) {
            this.webElement = null;
            initWebElement();
        }

    }

    /**
     * Initialises the web element and reports whether it was found. Use it when the element may
     * legitimately be absent: {@link #initWebElement()} propagates instead of leaving it null.
     *
     * @return {@code true} if the element was found and stored, {@code false} otherwise
     */
    public boolean tryInitWebElement() {
        try {
            initWebElement();
        } catch (final RuntimeException e) {
            this.webElement = null;
        }
        return this.webElement != null;
    }

    protected WebElement findElement() {
        try {
            if ((webElement != null) && webElement.isDisplayed()) {
                return webElement;
            }

            // Una sola espera: se preguntaba con isVisible() -que agota el plazo, descarta el
            // elemento que acaba de encontrar y se come la causa- para después volver a buscarlo.
            // El doble trabajo se pagaba en el camino bueno; en el malo quedaba un "no es visible"
            // sin decir por que.
            return findElementVisible();
        } catch (final StaleElementReferenceException staleException) {
            webElement = null;
            return findElement();
        }
    }

    public boolean waitElementVisible() {
        final Chronometer crono = new Chronometer();
        return waitElementVisible_initiated(crono);
    }

    private boolean waitElementVisible_initiated(Chronometer crono) {
        boolean found = false;
        try {
            while (!found && (crono.getElapsedSeconds() < testContext.timeoutFindElement)) {
                final WebElement element = driver.findElement(effectiveBy());
                found = (element != null) && element.isDisplayed();
            }
        } catch (final Exception e) {
            return waitElementVisible_initiated(crono);
        }

        return found;
    }

    private WebElement findElementPresent() {

        try {
            final WebDriverWait wait = waitOf(testContext.timeoutFindElement.longValue());
            return (WebElement) wait.until(ExpectedConditions.presenceOfElementLocated(effectiveBy()));
        } catch (final Exception e) {
            throw new RuntimeException("Element is not present in DOM: " + this, e);
        }
    }

    private WebElement findElementVisible() {
        final AtomicReference<WebDriverException> lastDriverError = new AtomicReference<>();
        final AtomicInteger polls = new AtomicInteger();
        final Chronometer chrono = new Chronometer();
        try {
            return waitOf(testContext.timeoutFindElement.longValue()).until(visibleElement(lastDriverError, polls));
        } catch (final Exception e) {
            throw new RuntimeException("Element is not visible: " + this + driverErrorDetail(lastDriverError.get())
                    + pollingDetail(polls.get(), chrono.getElapsedTime()), e);
        }
    }

    /**
     * Cuantas veces se llego a preguntar y en cuanto tiempo.
     *
     * <p>Un plazo agotado no dice lo mismo si se sondeo trescientas veces que si se sondeo una:
     * lo primero es que el elemento no llego, lo segundo es que la espera no llego a esperar.
     * Distinguirlo a posteriori obliga a cruzar el log de comandos del grid, cuando el dato lo
     * tiene aquí el propio bucle. Anadido el 22/09/2026, tras una espera de la home que sólo
     * mando una petición en 24 s.
     */
    private String pollingDetail(int polls, long elapsedMillis) {
        return String.format(". Sondeos: %d en %.1f s", polls, elapsedMillis / 1000.0);
    }

    /**
     * Lo que el driver contesto la última vez, cuando contesto con un error.
     *
     * <p>Reintentar sin contarlo convierte un fallo permanente en un plazo agotado en silencio:
     * mismo sintoma para "no se ve todavia" que para "el driver no puede ni mirarlo", que son dos
     * averias distintas. El 22/09/2026 este elemento consumió 60 s sin una sola línea de log.
     */
    private String driverErrorDetail(WebDriverException lastDriverError) {
        if (lastDriverError == null) {
            return StringUtils.EMPTY;
        }
        return ". El driver respondio con error en el ultimo intento: "
                + StringUtils.substringBefore(lastDriverError.getMessage(), "\n");
    }

    /**
     * Visible, reintentando mientras el DOM se mueve.
     *
     * <p>Un modal en animacion hace que el driver conteste con error a {@code findElement} o a
     * {@code isDisplayed}, y {@code WebDriverWait} solo ignora {@code NotFoundException}: cualquier
     * otra abortaba la espera entera en el primer sondeo. Medido el 21/09/2026 en Android, el input
     * del buscador de aeropuertos se daba por no visible a los <b>154 ms</b> de un plazo de
     * <b>60 s</b>, con su dialogo todavia en {@code mdc-dialog--opening}.
     *
     * <p>Un selector mal escrito si tiene que fallar en el acto: esperar no lo va a arreglar.
     */
    private ExpectedCondition<WebElement> visibleElement(AtomicReference<WebDriverException> lastDriverError,
            AtomicInteger polls) {
        return driverToQuery -> {
            polls.incrementAndGet();
            try {
                WebElement candidate = driverToQuery.findElement(effectiveBy());
                return (candidate != null) && candidate.isDisplayed() ? candidate : null;
            } catch (final InvalidSelectorException invalidLocator) {
                throw invalidLocator;
            } catch (final RuntimeException fallaTransitoria) {
                // RuntimeException y no sólo WebDriverException: cuando el navegador va ahogado, la
                // llamada puede morir en el cliente HTTP con una excepcion que no es de Selenium, y
                // esa se escapaba del bucle y terminaba la espera en el primer intento. Medido el
                // 22/09/2026 con la red degradada: "Sondeos: 1 en 13,9 s" de un plazo de 60 s.
                lastDriverError.set(fallaTransitoria instanceof WebDriverException
                        ? (WebDriverException) fallaTransitoria
                        : new WebDriverException(fallaTransitoria));
                return null;
            }
        };
    }

    private void writeNoLog(String text, boolean retry) {
        webElement = findElement();
        try {
            webElement.sendKeys(text);
        } catch (final InvalidElementStateException e) {
            if (retry) {
                throw new RuntimeException(ELEMENT_IS_NOT_EDITABLE + this);
            } else {
                writeNoLog(text, true);
            }
        }
    }

    public boolean isEnabled() {
        try {
            webElement = findElement();
            return webElement.isEnabled();
        } catch (final Exception e) {
            return false;
        }

    }

    public void moveTo(PageElement destination) {
        webElement = findElement();
        try {
            destination.initWebElement();
            Actions action = new Actions(driver);
            if (destination.isVisibleWithoutWait()) {
                action.moveToElement(webElement).moveToElement(destination.webElement).build().perform();
            } else {
                action.moveToElement(webElement).perform();
            }

        } catch (final Exception e) {
            throw new RuntimeException("Cannot perform move action FROM: " + this + " TO: " + destination);
        }
    }

    public String getElementType() {
        webElement = findElement();
        try {
            return webElement.getTagName();
        } catch (final Exception e) {
            throw new RuntimeException("Cannot get the element type for element: " + this);
        }
    }

    public Point getElementPosition() {
        initWebElement();
        if (webElement == null) {
            return new Point(0, 0);
        }
        try {
            return webElement.getLocation();
        } catch (final Exception e) {
            throw new RuntimeException("Cannot get the position for element: " + this);
        }
    }

    public void slide(int xOffset, int yOffset) {
        webElement = findElement();
        try {
            Actions move = new Actions(driver);
            Action action = (Action) move.moveToElement(webElement, xOffset, yOffset).click().build();
            action.perform();
            testContext.logInfo("Slide action has been done on " + this);
        } catch (StaleElementReferenceException e) {
            initWebElement();
            slide(xOffset, yOffset);
        } catch (final Exception e) {
            throw new RuntimeException("Unable to slide: " + this + getErrorMessage(e));
        }
    }

    public void swipeDownUntilVisible() {
        int retry = 1;
        while (retry < 20 && !isVisibleWithoutWait()) { // TODO cuando parar?
            retry++;
            swipeVertical(100, 200, 420);
        }
    }

    public void swipeUpUntilVisible() {
        int retry = 1;
        while (retry < 20 && !isVisibleWithoutWait()) { // TODO cuando parar?
            retry++;
            swipeVertical(100, 420, 200);
        }
    }

    public void swipeVerticalUntilVisible(int x, int startY, int endY) {
        int retry = 1;
        while (retry < 20 && !isVisibleWithoutWait()) { // TODO cuando parar?
            retry++;
            swipeVertical(x, startY, endY);
        }
    }

    public void swipeVertical(int x, int startY, int endY) {
        try {
            PointerInput finger = new PointerInput(PointerInput.Kind.TOUCH, "finger");
            Sequence seqSwipe = new Sequence(finger, 1);

            seqSwipe.addAction(finger.createPointerMove(Duration.ofSeconds(0), PointerInput.Origin.viewport(), x, startY));
            seqSwipe.addAction(finger.createPointerDown(0));

            seqSwipe.addAction(finger.createPointerMove(Duration.ofMillis(500), PointerInput.Origin.viewport(), x, endY));
            seqSwipe.addAction(finger.createPointerUp(0));
            ((AppiumDriver) driver).perform(Arrays.asList(seqSwipe));
        } catch (Exception e) {
            throw new RuntimeException("Unable to swipe: " + this + getErrorMessage(e));
        }
    }

    public void swipeLeftUntilVisible() {
        int retry = 1;
        while (retry < 20 && !isVisibleWithoutWait()) { // TODO cuando parar?
            retry++;
            swipeHorizontal(400, 200, 50);
        }
    }

    public void swipeRightUntilVisible() {
        int retry = 1;
        while (retry < 20 && !isVisibleWithoutWait()) { // TODO cuando parar?
            retry++;
            swipeHorizontal(400, 50, 200);
        }
    }

    public void swipeHorizontalUntilVisible(int y, int startX, int endX) {
        int retry = 1;
        while (retry < 20 && !isVisibleWithoutWait()) { // TODO cuando parar?
            retry++;
            swipeHorizontal(y, startX, endX);
        }
    }

    public void swipeHorizontal(int y, int startX, int endX) {
        try {
            PointerInput finger = new PointerInput(PointerInput.Kind.TOUCH, "finger");
            Sequence seqSwipe = new Sequence(finger, 1);

            seqSwipe.addAction(finger.createPointerMove(Duration.ofSeconds(0), PointerInput.Origin.viewport(), startX, y));
            seqSwipe.addAction(finger.createPointerDown(0));

            seqSwipe.addAction(finger.createPointerMove(Duration.ofMillis(500), PointerInput.Origin.viewport(), endX, y));
            seqSwipe.addAction(finger.createPointerUp(0));
            ((AppiumDriver) driver).perform(Arrays.asList(seqSwipe));
        } catch (Exception e) {
            throw new RuntimeException("Unable to swipe: " + this + getErrorMessage(e));
        }
    }

    public void scrollUp() {
        JavascriptExecutor js = (JavascriptExecutor) driver;
        js.executeScript("mobile:scroll", ImmutableMap.of("direction", "up"));
    }

    public void scrollDown() {
        JavascriptExecutor js = (JavascriptExecutor) driver;
        js.executeScript("mobile:scroll", ImmutableMap.of("direction", "down"));
    }

    public void moveToElement() {
        try {
            initWebElement();
            if (webElement != null && !isVisibleWithoutWait()) {
                Actions action = new Actions(driver);
                action.moveToElement(webElement).perform();
            }
        } catch (final Exception e) {
            throw new RuntimeException("Cannot perform move action");
        }
    }

    public void printElementPosition() {
        Point point = getElementPosition();
        System.out.println("Element position: x=" + point.x + " y=" + point.y);
        System.out.println("Element position: x=" + driver.manage().window().getPosition().x + " y=" + driver.manage().window().getPosition().y);
    }

    public void printWindowPosition() {
        Point point = driver.manage().window().getPosition();
        System.out.println("Window position: x=" + point.x + " y=" + point.y);
    }

    private String getErrorMessage(Exception exception) {
        return ERROR + removeSeleniumBuildInfo(removeSeleniumSessionInfo(exception.getMessage()));
    }

    private String removeSeleniumSessionInfo(String error) {
        return StringUtils.substringBefore(error, "(Session info:");
    }

    private String removeSeleniumBuildInfo(String error) {
        return StringUtils.substringBefore(error, "Build info:");
    }

}
