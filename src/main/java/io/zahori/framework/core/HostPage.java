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

import org.openqa.selenium.WebDriver;
import org.openqa.selenium.firefox.FirefoxDriver;
import org.openqa.selenium.firefox.FirefoxOptions;
import org.openqa.selenium.remote.DesiredCapabilities;

public class HostPage extends Page {

    private static final long serialVersionUID = 5112565167634493112L;

    private HostPageElement connectionInput;
    private HostPageElement connectButton;
    private HostPageElement disconnectButton;
    private HostPageElement keypadButton;
    private String emulatorURL;
    protected String logActions;

    private HostPageElement f1Button;
    private HostPageElement f2Button;
    private HostPageElement f3Button;
    private HostPageElement f4Button;
    private HostPageElement f5Button;
    private HostPageElement f6Button;
    private HostPageElement f7Button;
    private HostPageElement f8Button;
    private HostPageElement f9Button;
    private HostPageElement f10Button;
    private HostPageElement f11Button;
    private HostPageElement f12Button;

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

    protected void f1Button() {
        fxButton(this.f1Button, "F1");
    }

    protected void f2Button() {
        fxButton(this.f2Button, "F2");
    }

    protected void f3Button() {
        fxButton(this.f3Button, "F3");
    }

    protected void f4Button() {
        fxButton(this.f4Button, "F4");
    }

    protected void f5Button() {
        fxButton(this.f5Button, "F5");
    }

    protected void f6Button() {
        fxButton(this.f6Button, "F6");
    }

    protected void f7Button() {
        fxButton(this.f7Button, "F7");
    }

    protected void f8Button() {
        fxButton(this.f8Button, "F8");
    }

    protected void f9Button() {
        fxButton(this.f9Button, "F9");
    }

    protected void f10Button() {
        fxButton(this.f10Button, "F10");
    }

    protected void f11Button() {
        fxButton(this.f11Button, "F11");
    }

    protected void f12Button() {
        fxButton(this.f12Button, "F12");
    }

    private void initFButtons() {
        this.f1Button = new HostPageElement(this, "f1Button", Locator.name("pf1"));
        this.f2Button = new HostPageElement(this, "f2Button", Locator.name("pf2"));
        this.f3Button = new HostPageElement(this, "f3Button", Locator.name("pf3"));
        this.f4Button = new HostPageElement(this, "f45Button", Locator.name("pf4"));
        this.f5Button = new HostPageElement(this, "f5Button", Locator.name("pf5"));
        this.f6Button = new HostPageElement(this, "f6Button", Locator.name("pf6"));
        this.f7Button = new HostPageElement(this, "f7Button", Locator.name("pf7"));
        this.f8Button = new HostPageElement(this, "f8Button", Locator.name("pf8"));
        this.f9Button = new HostPageElement(this, "f9Button", Locator.name("pf9"));
        this.f10Button = new HostPageElement(this, "f10Button", Locator.name("pf10"));
        this.f11Button = new HostPageElement(this, "f11Button", Locator.name("pf11"));
        this.f12Button = new HostPageElement(this, "f12Button", Locator.name("pf12"));
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
