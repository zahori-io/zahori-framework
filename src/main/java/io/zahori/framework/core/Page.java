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
import static io.zahori.framework.core.PageElement.ERROR;
import io.zahori.framework.utils.Chronometer;
import io.zahori.framework.utils.Pause;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.openqa.selenium.*;

public class Page implements Serializable {

    private static final long serialVersionUID = 7043344065990287228L;
    private static final Logger LOG = LogManager.getLogger(Page.class);

    public TestContext testContext;
    private transient WebDriver driver;
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
        if (crono.getElapsedSeconds() < this.testContext.timeoutFindElement) {
            try {
                if (frameElement.webElement == null) {
                    frameElement.initWebElement();
                }
                this.driver.switchTo().frame(frameElement.webElement);
            } catch (Exception e) {
                this.switchToFrameWebElement(crono, frameElement);
            }
        } else {
            throw new RuntimeException("iframe not found: " + frameElement);
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
            Pause.sleep(Duration.ofSeconds(1));
            secondsWaiting++;
        }

        this.driver.switchTo().window(this.pageHandleId);
        return false;
    }

    public int numberOfModalWindowsOpen(int waitSeconds) {
        Pause.sleep(Duration.ofSeconds(waitSeconds));
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
        int secondsWaiting = 1;
        pageHandleId = this.driver.getWindowHandle();
        while (secondsWaiting <= this.testContext.timeoutFindElement) {
            for (String winHandle : this.driver.getWindowHandles()) {
                this.driver.switchTo().window(winHandle);

                if (this.driver.getTitle().trim().toLowerCase().contains(title.trim().toLowerCase())) {
                    return;
                }
            }
            Pause.sleep(Duration.ofSeconds(1));
            secondsWaiting++;
        }

        throw new RuntimeException("Modal window " + title + " not found on " + this);
    }

    public void switchToModalWindow(String[] titles) {
        int maxWaitSeconds = this.testContext.timeoutFindElement;
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
            Pause.sleep(Duration.ofSeconds(1));
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

    /**
     * Use getPageElements(String name, Locator locator) instead of this method
     */
    @Deprecated
    public List<PageElement> getPageElements(Locator locator) {
        return getPageElements(null, locator);
    }

    public List<PageElement> getPageElements(String name, Locator locator) {
        List<PageElement> pageElements = new ArrayList<>();

        try {
            List<WebElement> webElements = this.driver.findElements(locator.getBy());
            if (webElements == null) {
                return pageElements;
            }

            for (int i = 0; i < webElements.size(); i++) {
                WebElement webElement = webElements.get(i);
                String pageElementName = (name != null ? name : "pageElement") + " (" + (i + 1) + ")";
                PageElement pageElement = new PageElement(this, pageElementName, locator);
                pageElement.webElement = webElement;
                pageElements.add(pageElement);
            }
            return pageElements;
        } catch (Exception e) {
            testContext.logWarn("Error getting pageElements with locator {}: {}", locator.getLocatorText(), e.getMessage());
            return pageElements;
        }
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
        SwipeHelper.swipeVertical(driver, x, startY, endY, this.toString());
    }

    public void swipeHorizontal(int y, int startX, int endX) {
        SwipeHelper.swipeHorizontal(driver, y, startX, endX, this.toString());
    }

    // ==================== New Window API (Selenium 4) ====================

    /**
     * Abre una nueva ventana del navegador (Selenium 4).
     * El foco se cambia automaticamente a la nueva ventana.
     *
     * @return handle de la nueva ventana
     */
    public String openNewWindow() {
        pageHandleId = this.driver.getWindowHandle();
        this.driver.switchTo().newWindow(WindowType.WINDOW);
        String newWindowHandle = this.driver.getWindowHandle();
        LOG.info("Nueva ventana abierta: {}", newWindowHandle);
        testContext.logInfo("Nueva ventana abierta");
        return newWindowHandle;
    }

    /**
     * Abre una nueva pestana del navegador (Selenium 4).
     * El foco se cambia automaticamente a la nueva pestana.
     *
     * @return handle de la nueva pestana
     */
    public String openNewTab() {
        pageHandleId = this.driver.getWindowHandle();
        this.driver.switchTo().newWindow(WindowType.TAB);
        String newTabHandle = this.driver.getWindowHandle();
        LOG.info("Nueva pestana abierta: {}", newTabHandle);
        testContext.logInfo("Nueva pestana abierta");
        return newTabHandle;
    }

    /**
     * Cierra la ventana/pestana actual y vuelve a la ventana principal.
     */
    public void closeCurrentWindow() {
        String currentHandle = this.driver.getWindowHandle();
        this.driver.close();
        LOG.info("Ventana cerrada: {}", currentHandle);

        // Volver a la ventana principal si existe
        if (pageHandleId != null && !pageHandleId.equals(currentHandle)) {
            this.driver.switchTo().window(pageHandleId);
            testContext.logInfo("Volviendo a ventana principal");
        } else {
            // Si no hay ventana principal, cambiar a la primera disponible
            Set<String> handles = this.driver.getWindowHandles();
            if (!handles.isEmpty()) {
                this.driver.switchTo().window(handles.iterator().next());
            }
        }
    }

    /**
     * Obtiene el numero de ventanas/pestanas abiertas.
     *
     * @return numero de handles de ventana
     */
    public int getWindowCount() {
        return this.driver.getWindowHandles().size();
    }

    /**
     * Cambia a una ventana por su indice (0-based).
     *
     * @param index indice de la ventana (0 = primera)
     */
    public void switchToWindowByIndex(int index) {
        Set<String> handles = this.driver.getWindowHandles();
        if (index >= handles.size()) {
            throw new RuntimeException("Indice de ventana fuera de rango: " + index + " (total: " + handles.size() + ")");
        }
        String targetHandle = handles.toArray(new String[0])[index];
        this.driver.switchTo().window(targetHandle);
        LOG.info("Cambiado a ventana indice {}: {}", index, targetHandle);
    }

    // ==================== Full Page Screenshot (Selenium 4) ====================

    /**
     * Captura screenshot de la pagina completa incluyendo scroll (Selenium 4).
     * Solo funciona en Firefox con geckodriver.
     *
     * @return bytes del screenshot en formato PNG
     */
    public byte[] takeFullPageScreenshot() {
        try {
            // Para Firefox, usar metodo especifico de full page; otros navegadores igual
            if (driver instanceof TakesScreenshot && driver.getClass().getSimpleName().contains("Firefox")) {
                return ((TakesScreenshot) driver).getScreenshotAs(OutputType.BYTES);
            }
            // Fallback: screenshot normal
            return ((TakesScreenshot) driver).getScreenshotAs(OutputType.BYTES);
        } catch (Exception e) {
            throw new RuntimeException("No se pudo capturar screenshot de pagina completa" + getErrorMessage(e));
        }
    }

    // ==================== Private Methods ====================

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
