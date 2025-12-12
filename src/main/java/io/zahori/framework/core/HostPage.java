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

import java.util.HashMap;
import java.util.Map;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.firefox.FirefoxDriver;
import org.openqa.selenium.firefox.FirefoxOptions;

public class HostPage extends Page {

    private static final long serialVersionUID = 5112565167634493112L;
    private static final int MAX_FUNCTION_KEY = 12;

    private HostPageElement connectionInput;
    private HostPageElement connectButton;
    private HostPageElement disconnectButton;
    private HostPageElement keypadButton;
    private String emulatorURL;
    protected String logActions;

    // Function buttons stored in Map instead of 12 individual fields
    private final Map<Integer, HostPageElement> functionButtons = new HashMap<>();

    protected HostPage(TestContext context) {
        super(context);
        this.testContext = context;
        this.emulatorURL = context.getHostEmulatorURL();
        this.logActions = "";
        initHostBrowser();
    }

    public void connectHost(String url) {
        this.getDriver().get(this.emulatorURL);

        if (!connectButton.isVisible()) {
            this.disconnectButton.click();
        }

        this.connectionInput.write(url);
        this.connectButton.click();

        keypadButton.click();
        this.logActions = this.logActions + "Connected to HOST url " + url + ".\n";
    }

    public void disconnect() {
        if (this.disconnectButton.isVisible()) {
            this.disconnectButton.click();
        }

        this.getDriver().quit();
        this.testContext.setHostDriver(null);
        this.setDriver(null);
    }

    public void resetActionsLog() {
        this.logActions = "";
    }

    public String getActionsLog() {
        return this.logActions;
    }

    /**
     * Press a function key (F1-F12).
     *
     * @param functionKey the function key number (1-12)
     * @throws IllegalArgumentException if functionKey is not between 1 and 12
     */
    protected void pressFunctionKey(int functionKey) {
        if (functionKey < 1 || functionKey > MAX_FUNCTION_KEY) {
            throw new IllegalArgumentException(
                "Invalid function key: " + functionKey + ". Must be between 1 and " + MAX_FUNCTION_KEY);
        }
        fxButton(functionButtons.get(functionKey), "F" + functionKey);
    }

    // Backward compatibility methods - delegate to pressFunctionKey
    protected void f1Button() { pressFunctionKey(1); }
    protected void f2Button() { pressFunctionKey(2); }
    protected void f3Button() { pressFunctionKey(3); }
    protected void f4Button() { pressFunctionKey(4); }
    protected void f5Button() { pressFunctionKey(5); }
    protected void f6Button() { pressFunctionKey(6); }
    protected void f7Button() { pressFunctionKey(7); }
    protected void f8Button() { pressFunctionKey(8); }
    protected void f9Button() { pressFunctionKey(9); }
    protected void f10Button() { pressFunctionKey(10); }
    protected void f11Button() { pressFunctionKey(11); }
    protected void f12Button() { pressFunctionKey(12); }

    private void initFButtons() {
        functionButtons.clear();
        for (int i = 1; i <= MAX_FUNCTION_KEY; i++) {
            functionButtons.put(i,
                new HostPageElement(this, "f" + i + "Button", Locator.name("pf" + i)));
        }
    }

    private void initHostBrowser() {
        WebDriver driver = this.testContext.getHostDriver();
        if (driver == null) {
            FirefoxOptions options = new FirefoxOptions();
            driver = new FirefoxDriver(options);
            driver.manage().window().maximize();
            this.testContext.setHostDriver(driver);
        }

        this.setDriver(driver);
        initFButtons();
        initHostHostPageElements();
    }

    private void initHostHostPageElements() {
        this.connectionInput = new HostPageElement(this, "connectionInput", Locator.name("hostname"));
        this.connectButton = new HostPageElement(this, "connectButton", Locator.name("connect"));
        this.disconnectButton = new HostPageElement(this, "disconnectButton", Locator.name("disconnect"));
        this.keypadButton = new HostPageElement(this, "keypadButton", Locator.name("keypad"));
    }

    private void fxButton(HostPageElement button, String txtButton) {
        if (!button.isVisible()) {
            initFButtons();
        }
        button.click();
        this.logActions = this.logActions + txtButton + " function key has been pressed.";
    }
}
