package io.zahori.framework.driver.browserfactory;

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

import org.openqa.selenium.remote.Browser;

/*Clase Browsers.java*/
public enum Browsers {

    EXPLORER(Browser.IE.browserName(), Browsers.BITS_32, "org.openqa.selenium.ie.InternetExplorerDriver", Browsers.REMOTE_NO),
    FIREFOX(Browser.FIREFOX.browserName(), Browsers.BITS_32, "org.openqa.selenium.firefox.FirefoxDriver", Browsers.REMOTE_NO),
    CHROME(Browser.CHROME.browserName(), Browsers.BITS_32, "org.openqa.selenium.chrome.ChromeDriver", Browsers.REMOTE_NO),
    SAFARI(Browser.SAFARI.browserName(), Browsers.BITS_32, "org.openqa.selenium.safari.SafariDriver", Browsers.REMOTE_NO),
    EDGE(Browser.EDGE.browserName(), Browsers.BITS_32, "org.openqa.selenium.edge.EdgeDriver", Browsers.REMOTE_NO),
    OPERA(Browser.OPERA.browserName(), Browsers.BITS_32, "org.openqa.selenium.opera.OperaDriver", Browsers.REMOTE_NO),
    NULLBROWSER(null, Browsers.BITS_32, "no browser", Browsers.REMOTE_NO);

    private String name;

    private String bits;

    private String packageWebdrivername;

    private String platform = "";

    private String version = "";

    private String screenResolution = "";

    private String remote;

    private String remoteUrl;

    private String downloadPath;

    private String testName;

    private String caseExecutionId;

    public static final String BITS_32 = "32";

    public static final String BITS_64 = "64";

    public static final String VERSION_8 = "8";

    public static final String VERSION_9 = "9";

    public static final String VERSION_10 = "10";

    public static final String VERSION_11 = "11";

    public static final String VERSION_28 = "28";

    public static final String VERSION_36 = "36";

    public static final String VERSION_38 = "38";

    public static final String EXPLORER_8_32 = Browser.IE.browserName() + VERSION_8 + BITS_32;

    public static final String EXPLORER_8_64 =Browser.IE.browserName() + VERSION_8 + BITS_64;

    public static final String EXPLORER_9_32 =Browser.IE.browserName() + VERSION_9 + BITS_32;

    public static final String EXPLORER_9_64 =Browser.IE.browserName() + VERSION_9 + BITS_64;

    public static final String EXPLORER_10_32 =Browser.IE.browserName() + VERSION_10 + BITS_32;

    public static final String EXPLORER_10_64 =Browser.IE.browserName() + VERSION_10 + BITS_64;

    public static final String EXPLORER_11_32 =Browser.IE.browserName() + VERSION_11 + BITS_32;

    public static final String EXPLORER_11_64 =Browser.IE.browserName() + VERSION_11 + BITS_64;

    public static final String FIREFOX_28_32 = Browser.FIREFOX.browserName() + VERSION_28 + BITS_32;

    public static final String CHROME_38_32 = Browser.CHROME.browserName() + VERSION_38 + BITS_32;

    public static final String REMOTE_YES = "YES";

    public static final String REMOTE_NO = "NO";

    Browsers(final String nameWebdriver, final String bits, final String packageWebdrivername, final String remote) {
        this.name = nameWebdriver;
        this.bits = bits;
        this.packageWebdrivername = packageWebdrivername;
        this.remote = remote;
        this.remoteUrl = null;
    }

    Browsers(final String nameWebdriver, final String bits, final String packageWebdrivername, final String remote, final String remoteUrl) {
        this.name = nameWebdriver;
        this.bits = bits;
        this.packageWebdrivername = packageWebdrivername;
        this.remote = remote;
        this.remoteUrl = remoteUrl;
    }

    public Browsers withPlatform(final String platform) {
        this.platform = platform;
        return this;
    }

    public Browsers withVersion(final String version) {
        this.version = version;
        return this;
    }

    public Browsers withScreenResolution(final String screenResolution) {
        this.screenResolution = screenResolution;
        return this;
    }

    public Browsers withBits(final String bits) {
        this.bits = bits;
        return this;
    }

    public Browsers withRemote(final String remote) {
        this.remote = remote;
        return this;
    }

    public Browsers withRemoteUrl(final String remoteUrl) {
        this.remoteUrl = remoteUrl;
        return this;
    }

    public Browsers withDownloadPath(final String downloadPath) {
        this.downloadPath = downloadPath;
        return this;
    }

    public Browsers withTestName(final String testName) {
        this.testName = testName;
        return this;
    }

    public Browsers withCaseExecution(final String caseExecutionId) {
        this.caseExecutionId = caseExecutionId;
        return this;
    }

    public String getName() {
        return name;
    }

    public void setName(final String name) {
        this.name = name;
    }

    public String getPackageWebdrivername() {
        return packageWebdrivername;
    }

    public void setPackageWebdrivername(final String packageWebdrivername) {
        this.packageWebdrivername = packageWebdrivername;
    }

    public String getBits() {
        return bits;
    }

    public void setBits(final String bits) {
        this.bits = bits;
    }

    public String getPlatform() {
        return platform;
    }

    public void setPlatform(final String platform) {
        this.platform = platform;
    }

    public String getVersion() {
        return version;
    }

    public void setVersion(final String version) {
        this.version = version;
    }

    public String getScreenResolution() {
        return screenResolution;
    }

    public void setScreenResolution(String screenResolution) {
        this.screenResolution = screenResolution;
    }

    public String getRemote() {
        return remote;
    }

    public void setRemote(final String remote) {
        this.remote = remote;
    }

    public String getRemoteUrl() {
        return remoteUrl;
    }

    public void setRemoteUrl(final String remoteUrl) {
        this.remoteUrl = remoteUrl;
    }

    public String getDownloadPath() {
        return downloadPath;
    }

    public void setDownloadPath(final String downloadPath) {
        this.downloadPath = downloadPath;
    }

    public String getTestName() {
        return this.testName;
    }

    public void setTestName(String testName) {
        this.testName = testName;
    }

    public String getCaseExecutionId() {
        return caseExecutionId;
    }

    public void setCaseExecutionId(String caseExecutionId) {
        this.caseExecutionId = caseExecutionId;
    }

}
