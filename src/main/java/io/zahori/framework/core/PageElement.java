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

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

import org.apache.commons.lang3.StringUtils;
import org.openqa.selenium.InvalidElementStateException;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.Keys;
import org.openqa.selenium.NoSuchElementException;
import org.openqa.selenium.Point;
import org.openqa.selenium.StaleElementReferenceException;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.interactions.Action;
import org.openqa.selenium.interactions.Actions;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.Select;
import org.openqa.selenium.support.ui.WebDriverWait;

import io.zahori.framework.robot.UtilsRobot;
import io.zahori.framework.utils.Chronometer;
import io.zahori.framework.utils.Pause;
import io.zahori.framework.utils.WebdriverUtils;

public class PageElement {

    public static final String ERROR = "\nError: ";
    public static final String ON = "\" on ";
    public static final String ELEMENT_IS_NOT_EDITABLE = "Element is not editable: ";
    public static final String VALUE = "value";
    public static final String GET_TEXT = "Get text \"";
    public static final String FROM = "\" from ";
    public String name;
    public Locator locator;
    public Page page;
    public TestContext testContext;
    private WebDriver driver;
    private final Locator[] frameLocators;

    public WebElement webElement;

    public PageElement(Page page, String name, Locator locator) {
        this.name = name;
        this.locator = locator;
        this.page = page;
        this.testContext = page.testContext;
        this.driver = this.testContext.driver;
        this.frameLocators = null;
    }

    public PageElement(Page page, Locator[] frameLocators, String name, Locator locator) {
        this.name = name;
        this.locator = locator;
        this.page = page;
        this.testContext = page.testContext;
        this.driver = this.testContext.driver;
        this.frameLocators = frameLocators;
    }

    @Override
    public String toString() {
        return "\"" + name + "\" [" + locator.getLocatorType() + ": " + locator.getLocatorText() + "] [page: " + page.name + "] [test: "
                + testContext.testCaseName + "]";
    }

    public void setDriver(WebDriver driver) {
        this.driver = driver;
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

            WebDriverWait wait = new WebDriverWait(driver, testContext.timeoutFindElement - chrono.getElapsedSeconds());
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
            WebDriverWait wait = new WebDriverWait(driver, testContext.timeoutFindElement - chrono.getElapsedSeconds());
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
            if (scroll) {
                scroll();
            }

            WebDriverWait wait = new WebDriverWait(driver, testContext.timeoutFindElement - chrono.getElapsedSeconds());
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
            testContext.logInfo("Click (through javascript) on " + this);
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
        webElement = findElement();
        final Actions action = new Actions(driver);
        action.moveToElement(webElement).click(webElement).perform();
    }

    public void writeNotValidateValue(String text) {
        Chronometer chrono = new Chronometer();
        webElement = findElement();
        try {
            WebDriverWait wait = new WebDriverWait(driver, testContext.timeoutFindElement - chrono.getElapsedSeconds());
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

    public void clearInput() {
        final WebElement webElement = findElement();
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
            for (WebElement webElement : select.getOptions()) {
                stringBuffer.append("{").append(webElement.getText()).append("}");
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
            for (final WebElement webElement : select.getOptions()) {
                list.add(webElement.getText());
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
            for (final WebElement webElement : select.getOptions()) {
                list.add(webElement.getAttribute("Value"));
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
            final String value = webElement.getAttribute(VALUE).trim();
            testContext.logInfo("Get input value \"" + value + FROM + this);
            return value;
        } catch (final Exception e) {
            throw new RuntimeException("Unable to get input value: " + this);
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
            findElementPresent();
            testContext.logInfo("Validated is present in DOM: " + this);
        } catch (final Exception e) {
            throw new RuntimeException("Element is not present in DOM: " + this);
        }
    }

    public boolean isPresent() {
        try {
            switchWithLocators();
            driver.manage().timeouts().implicitlyWait(0L, TimeUnit.SECONDS);
            final WebDriverWait wait = new WebDriverWait(driver, 0L);
            wait.until(ExpectedConditions.presenceOfElementLocated(locator.getBy()));
            return driver.findElement(locator.getBy()) != null;
        } catch (final Exception e) {
            return false;
        }
    }

    private void switchWithLocators() {
        if (frameLocators != null) {
            page.switchToDefaultContent();
            for (final Locator actualFrame : frameLocators) {
                page.switchToFrame(new PageElement(page, "frameElement", actualFrame));
            }
        } else {
            page.switchToDefaultContent();
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
        try {
            findElementVisible();
            return true;
        } catch (final Exception e) {
            return false;
        }
    }

    public boolean isVisibleWithoutWait() {
        try {
            switchWithLocators();
            driver.manage().timeouts().implicitlyWait(0L, TimeUnit.SECONDS);
            webElement = driver.findElement(locator.getBy());
            return webElement.isDisplayed();
        } catch (final Exception e) {
            return false;
        }
    }

    public void validateIsNotPresent() {
        try {
            findElementPresent();
            testContext.logInfo("Validated is not present in DOM: " + this);
            throw new RuntimeException("Element is present but it is not expected: " + this);
        } catch (final Exception e) {
            testContext.logInfo("Validated that not exists " + this);
        }
    }

    public void initWebElement() {
        try {
            switchWithLocators();
            driver.manage().timeouts().implicitlyWait(0L, TimeUnit.SECONDS);
            this.webElement = driver.findElement(locator.getBy());
        } catch (final NullPointerException | NoSuchElementException e) {
            this.webElement = null;
        } catch (final StaleElementReferenceException e) {
            this.webElement = null;
            initWebElement();
        }

    }

    protected WebElement findElement() {
        try {
            if ((webElement != null) && webElement.isDisplayed()) {
                return webElement;
            }

            // if (this.waitElementVisible()) {
            if (this.isVisible()) {
                return findElementVisible();
            } else {
                throw new RuntimeException("Element is not visible: " + this);
            }
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
        switchWithLocators();
        boolean found = false;
        try {
            while (!found && (crono.getElapsedSeconds() < testContext.timeoutFindElement)) {
                final WebElement element = driver.findElement(locator.getBy());
                found = (element != null) && element.isDisplayed();
            }
        } catch (final Exception e) {
            return waitElementVisible_initiated(crono);
        }

        return found;
    }

    private WebElement findElementPresent() {
        try {
            switchWithLocators();
            final WebDriverWait wait = new WebDriverWait(driver, testContext.timeoutFindElement.longValue());
            return (WebElement) wait.until(ExpectedConditions.presenceOfElementLocated(locator.getBy()));
        } catch (final Exception e) {
            throw new RuntimeException("Element is not present in DOM: " + this);
        }
    }

    private WebElement findElementVisible() {
        try {
            switchWithLocators();
            final WebDriverWait wait = new WebDriverWait(driver, testContext.timeoutFindElement.longValue());
            return (WebElement) wait.until(ExpectedConditions.visibilityOfElementLocated(locator.getBy()));
        } catch (final Exception e) {
            throw new RuntimeException("Element is not visible: " + this);
        }
    }

    private void writeNoLog(String text, boolean retry) {
        Chronometer chrono = new Chronometer();
        final WebElement webElement = findElement();
        try {
            while (!StringUtils.equals(webElement.getAttribute(VALUE), text) && (chrono.getElapsedSeconds() < testContext.timeoutFindElement)) {
                WebDriverWait wait = new WebDriverWait(driver, testContext.timeoutFindElement - chrono.getElapsedSeconds());
                wait.until(ExpectedConditions.elementToBeClickable(webElement));
                while (!webElement.isEnabled() && (chrono.getElapsedSeconds() < testContext.timeoutFindElement)) {
                    Pause.pause();
                    initWebElement();
                }
                webElement.clear();
                webElement.sendKeys(text);
            }
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
            final WebElement webElement = findElement();
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
        webElement = findElementPresent();
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
