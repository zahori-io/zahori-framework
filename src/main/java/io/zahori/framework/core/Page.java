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
import io.appium.java_client.AppiumDriver;
import static io.zahori.framework.core.PageElement.ERROR;
import io.zahori.framework.utils.Chronometer;
import io.zahori.framework.utils.Pause;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import org.apache.commons.lang3.StringUtils;
import org.openqa.selenium.*;
import org.openqa.selenium.interactions.PointerInput;
import org.openqa.selenium.interactions.Sequence;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;

public class Page implements Serializable {

    private static final long serialVersionUID = 7043344065990287228L;

    public TestContext testContext;
    private WebDriver driver;
    public String name;
    public String url;

    public String pageHandleId;

    public Page() {
    }

    public Page(TestContext testContext) {
        this.testContext = testContext;
        driver = testContext.driver;
        name = getClass().getSimpleName();
    }

    public Page(TestContext testContext, String url) {
        this(testContext);

        this.url = url;
    }

    @Override
    public String toString() {
        return "Page: " + this.name;
    }

    public String getPageTitle() {
        return this.testContext.driver.getTitle();
    }

    public void switchToFrame(String frameId) {
        switchToFrame(Locator.id(frameId));
    }

    public void switchToFrame(Locator locator) {
        PageElement frame = new PageElement(this, "iFrame temporal webElement", locator);
        Chronometer crono = new Chronometer();
        this.switchToFrameWebElement(crono, frame);
    }

    public void switchToFrame(PageElement iframeElement) {
        // if (iframeElement.webElement == null) {
        // iframeElement.initWebElement();
        // }
        // driver.switchTo().frame(iframeElement.webElement);
        Chronometer crono = new Chronometer();
        this.switchToFrameWebElement(crono, iframeElement);
    }

    private void switchToFrameWebElement(Chronometer crono, PageElement frameElement) {
        try {
            if (crono.getElapsedSeconds() < this.testContext.timeoutFindElement.intValue()) {
                if (frameElement.webElement == null) {
                    frameElement.initWebElement();
                }
                this.driver.switchTo().frame(frameElement.webElement);
            }
        } catch (Exception e) {
            this.switchToFrameWebElement(crono, frameElement);
        }
    }

    public void switchToDefaultContent() {
        this.driver.switchTo().defaultContent();
    }

    public boolean existModalWindow(String title, int maxWaitSeconds) {
        try {
            Thread.sleep(5L);
        } catch (InterruptedException e) {
            testContext.logInfo("Error checking modal window: " + e.getMessage());
        }
        int secondsWaiting = 1;
        pageHandleId = this.driver.getWindowHandle();
        while (secondsWaiting <= maxWaitSeconds) {
            for (String winHandle : this.driver.getWindowHandles()) {
                this.driver.switchTo().window(winHandle);

                if (this.driver.getTitle().trim().toLowerCase().contains(title.trim().toLowerCase())) {
                    this.driver.switchTo().window(this.pageHandleId);
                    return true;
                }
            }
            Pause.pause(1);
            secondsWaiting++;
        }

        this.driver.switchTo().window(this.pageHandleId);
        return false;
    }

    public int numberOfModalWindowsOpen(int waitSeconds) {
        Pause.pause(waitSeconds);
        Set<String> modalWindows = this.driver.getWindowHandles();
        return modalWindows.size() - 1;
    }

    public boolean existModalWindow() {
        String parentWindowHandler = this.driver.getWindowHandle();
        boolean existChildWindow = false;
        for (String winHandle : this.driver.getWindowHandles()) {
            if (!"".equals(winHandle) && !parentWindowHandler.equals(winHandle)) {
                existChildWindow = true;
                this.driver.switchTo().window(winHandle); // switch to popup
                // window
                this.driver.switchTo().window(parentWindowHandler);
            }
        }
        return existChildWindow;
    }

    public void switchToModalWindow(String title) {
        int maxWaitSeconds = this.testContext.timeoutFindElement.intValue();
        int secondsWaiting = 1;
        pageHandleId = this.driver.getWindowHandle();
        while (secondsWaiting <= maxWaitSeconds) {
            for (String winHandle : this.driver.getWindowHandles()) {
                this.driver.switchTo().window(winHandle);

                if (this.driver.getTitle().trim().toLowerCase().contains(title.trim().toLowerCase())) {
                    return;
                }
            }
            Pause.pause(1);
            secondsWaiting++;
        }

        throw new RuntimeException("Modal window " + title + " not found on " + this);
    }

    public void switchToModalWindow(String[] titles) {
        int maxWaitSeconds = this.testContext.timeoutFindElement.intValue();
        int secondsWaiting = 1;
        pageHandleId = this.driver.getWindowHandle();
        StringBuilder string = new StringBuilder();
        while (secondsWaiting <= maxWaitSeconds) {
            for (String winHandle : this.driver.getWindowHandles()) {
                this.driver.switchTo().window(winHandle);
                for (String title : titles) {
                    string.append(" ").append(title);
                    if (this.driver.getTitle().trim().toLowerCase().contains(title.trim().toLowerCase())) {
                        return;
                    }
                }
            }
            Pause.pause(1);
            secondsWaiting++;
        }

        throw new RuntimeException("Modal window " + string + " not found on " + this);
    }

    public void switchToMainWindow() {
        if (this.pageHandleId != null) {
            this.driver.switchTo().window(this.pageHandleId);
        }
    }

    private Alert switchToAlert() {
        return this.driver.switchTo().alert();
    }

    public void acceptAlert() {
        this.switchToAlert().accept();
    }

    public void closeAlert() {
        this.switchToAlert().dismiss();
    }

    public String getAlertText() {
        return this.switchToAlert().getText();
    }

    public void writeTextToAlert(String text) {
        this.switchToAlert().sendKeys(text);
    }

    public void setDriver(WebDriver driver) {
        this.driver = driver;
    }

    public WebDriver getDriver() {
        return driver;
    }

    public List<PageElement> getPageElements(Locator locator) {
        List<PageElement> elementsList = new ArrayList<>();

        WebElement element = null;
        try {
            element = (WebElement) new WebDriverWait(driver, Duration.ofSeconds(testContext.timeoutFindElement.longValue())).until(ExpectedConditions.presenceOfElementLocated(locator.getBy()));

        } catch (NoSuchElementException | TimeoutException e) {
            return elementsList;
        }

        List<WebElement> wdElements = this.driver.findElements(locator.getBy());

        for (WebElement each : wdElements) {
            PageElement pageElement = new PageElement(this, "pageElement", locator);
            pageElement.webElement = each;
            elementsList.add(pageElement);
        }

        return elementsList;
    }

    private void readObject(ObjectInputStream aInputStream) throws ClassNotFoundException, IOException {
        aInputStream.defaultReadObject();
    }

    private void writeObject(ObjectOutputStream aOutputStream) throws IOException {
        aOutputStream.defaultWriteObject();
    }

    public void waitStaticPageLoad(int waitSeconds) {
        if (waitSeconds > 0) {
            try {
                Thread.sleep(waitSeconds);
            } catch (InterruptedException e) {
                throw new RuntimeException(
                        "Static wait duration isn't defined properly: " + waitSeconds, e);
            }
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
