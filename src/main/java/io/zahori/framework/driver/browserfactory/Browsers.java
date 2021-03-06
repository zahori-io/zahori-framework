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

import org.openqa.selenium.remote.BrowserType;

/**
 * The enum Browsers.
 */
/*Clase Browsers.java*/
public enum Browsers {

	/**
	 * Explorer browsers.
	 */
	EXPLORER(BrowserType.IE, Browsers.BITS_32, "org.openqa.selenium.ie.InternetExplorerDriver", Browsers.REMOTE_NO),
	/**
	 * Firefox browsers.
	 */
	FIREFOX(BrowserType.FIREFOX, Browsers.BITS_32, "org.openqa.selenium.firefox.FirefoxDriver", Browsers.REMOTE_NO),
	/**
	 * Chrome browsers.
	 */
	CHROME(BrowserType.CHROME, Browsers.BITS_32, "org.openqa.selenium.chrome.ChromeDriver", Browsers.REMOTE_NO),
	/**
	 * Safari browsers.
	 */
	SAFARI(BrowserType.SAFARI, Browsers.BITS_32, "org.openqa.selenium.safari.SafariDriver", Browsers.REMOTE_NO),
	/**
	 * Edge browsers.
	 */
	EDGE(BrowserType.EDGE, Browsers.BITS_32, "org.openqa.selenium.edge.EdgeDriver", Browsers.REMOTE_NO),
	/**
	 * Opera browsers.
	 */
	OPERA(BrowserType.OPERA, Browsers.BITS_32, "org.openqa.selenium.opera.OperaDriver", Browsers.REMOTE_NO),
	/**
	 * The Nullbrowser.
	 */
	NULLBROWSER(null, Browsers.BITS_32, "no browser", Browsers.REMOTE_NO);

	private String name;

	private String bits;

	private String packageWebdrivername;

	private String platform = "";

	private String version = "";

	private String remote;

	private String remoteUrl;

	private String downloadPath;

	private String testName;
	
	private String caseExecutionId;

	/**
	 * The constant BITS_32.
	 */
	public static final String BITS_32 = "32";

	/**
	 * The constant BITS_64.
	 */
	public static final String BITS_64 = "64";

	/**
	 * The constant VERSION_8.
	 */
	public static final String VERSION_8 = "8";

	/**
	 * The constant VERSION_9.
	 */
	public static final String VERSION_9 = "9";

	/**
	 * The constant VERSION_10.
	 */
	public static final String VERSION_10 = "10";

	/**
	 * The constant VERSION_11.
	 */
	public static final String VERSION_11 = "11";

	/**
	 * The constant VERSION_28.
	 */
	public static final String VERSION_28 = "28";

	/**
	 * The constant VERSION_36.
	 */
	public static final String VERSION_36 = "36";

	/**
	 * The constant VERSION_38.
	 */
	public static final String VERSION_38 = "38";

	/**
	 * The constant EXPLORER_8_32.
	 */
	public static final String EXPLORER_8_32 = BrowserType.IEXPLORE + VERSION_8 + BITS_32;

	/**
	 * The constant EXPLORER_8_64.
	 */
	public static final String EXPLORER_8_64 = BrowserType.IEXPLORE + VERSION_8 + BITS_64;

	/**
	 * The constant EXPLORER_9_32.
	 */
	public static final String EXPLORER_9_32 = BrowserType.IEXPLORE + VERSION_9 + BITS_32;

	/**
	 * The constant EXPLORER_9_64.
	 */
	public static final String EXPLORER_9_64 = BrowserType.IEXPLORE + VERSION_9 + BITS_64;

	/**
	 * The constant EXPLORER_10_32.
	 */
	public static final String EXPLORER_10_32 = BrowserType.IEXPLORE + VERSION_10 + BITS_32;

	/**
	 * The constant EXPLORER_10_64.
	 */
	public static final String EXPLORER_10_64 = BrowserType.IEXPLORE + VERSION_10 + BITS_64;

	/**
	 * The constant EXPLORER_11_32.
	 */
	public static final String EXPLORER_11_32 = BrowserType.IEXPLORE + VERSION_11 + BITS_32;

	/**
	 * The constant EXPLORER_11_64.
	 */
	public static final String EXPLORER_11_64 = BrowserType.IEXPLORE + VERSION_11 + BITS_64;

	/**
	 * The constant FIREFOX_28_32.
	 */
	public static final String FIREFOX_28_32 = BrowserType.FIREFOX + VERSION_28 + BITS_32;

	/**
	 * The constant CHROME_38_32.
	 */
	public static final String CHROME_38_32 = BrowserType.CHROME + VERSION_38 + BITS_32;

	/**
	 * The constant NAVEGADORCHROME.
	 */
	/* Constantes nombre navegador. */
	public static final String NAVEGADORCHROME = "chrome";

	/**
	 * The constant NAVEGADORIE.
	 */
	public static final String NAVEGADORIE = "internet explorer";

	/**
	 * The constant REMOTE_YES.
	 */
	public static final String REMOTE_YES = "YES";

	/**
	 * The constant REMOTE_NO.
	 */
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

	/**
	 * With platform browsers.
	 *
	 * @param platform the platform
	 * @return the browsers
	 */
	public Browsers withPlatform(final String platform) {
		this.platform = platform;
		return this;
	}

	/**
	 * With version browsers.
	 *
	 * @param version the version
	 * @return the browsers
	 */
	public Browsers withVersion(final String version) {
		this.version = version;
		return this;
	}

	/**
	 * With bits browsers.
	 *
	 * @param bits the bits
	 * @return the browsers
	 */
	public Browsers withBits(final String bits) {
		this.bits = bits;
		return this;
	}

	/**
	 * With remote browsers.
	 *
	 * @param remote the remote
	 * @return the browsers
	 */
	public Browsers withRemote(final String remote) {
		this.remote = remote;
		return this;
	}

	/**
	 * With remote url browsers.
	 *
	 * @param remoteUrl the remote url
	 * @return the browsers
	 */
	public Browsers withRemoteUrl(final String remoteUrl) {
		this.remoteUrl = remoteUrl;
		return this;
	}

	/**
	 * With download path browsers.
	 *
	 * @param downloadPath the download path
	 * @return the browsers
	 */
	public Browsers withDownloadPath(final String downloadPath) {
		this.downloadPath = downloadPath;
		return this;
	}

	/**
	 * With test name browsers.
	 *
	 * @param testName the test name
	 * @return the browsers
	 */
	public Browsers withTestName(final String testName) {
		this.testName = testName;
		return this;
	}

	/**
	 * With case execution browsers.
	 *
	 * @param caseExecutionId the case execution id
	 * @return the browsers
	 */
	public Browsers withCaseExecution(final String caseExecutionId) {
		this.caseExecutionId = caseExecutionId;
		return this;
	}

	/**
	 * Gets name.
	 *
	 * @return the name
	 */
	public String getName() {
		return name;
	}

	/**
	 * Sets name.
	 *
	 * @param name the name
	 */
	public void setName(final String name) {
		this.name = name;
	}

	/**
	 * Gets package webdrivername.
	 *
	 * @return the package webdrivername
	 */
	public String getPackageWebdrivername() {
		return packageWebdrivername;
	}

	/**
	 * Sets package webdrivername.
	 *
	 * @param packageWebdrivername the package webdrivername
	 */
	public void setPackageWebdrivername(final String packageWebdrivername) {
		this.packageWebdrivername = packageWebdrivername;
	}

	/**
	 * Gets bits.
	 *
	 * @return the bits
	 */
	public String getBits() {
		return bits;
	}

	/**
	 * Sets bits.
	 *
	 * @param bits the bits
	 */
	public void setBits(final String bits) {
		this.bits = bits;
	}

	/**
	 * Gets platform.
	 *
	 * @return the platform
	 */
	public String getPlatform() {
		return platform;
	}

	/**
	 * Sets platform.
	 *
	 * @param platform the platform
	 */
	public void setPlatform(final String platform) {
		this.platform = platform;
	}

	/**
	 * Gets version.
	 *
	 * @return the version
	 */
	public String getVersion() {
		return version;
	}

	/**
	 * Sets version.
	 *
	 * @param version the version
	 */
	public void setVersion(final String version) {
		this.version = version;
	}

	/**
	 * Gets remote.
	 *
	 * @return the remote
	 */
	public String getRemote() {
		return remote;
	}

	/**
	 * Sets remote.
	 *
	 * @param remote the remote
	 */
	public void setRemote(final String remote) {
		this.remote = remote;
	}

	/**
	 * Gets remote url.
	 *
	 * @return the remote url
	 */
	public String getRemoteUrl() {
		return remoteUrl;
	}

	/**
	 * Sets remote url.
	 *
	 * @param remoteUrl the remote url
	 */
	public void setRemoteUrl(final String remoteUrl) {
		this.remoteUrl = remoteUrl;
	}

	/**
	 * Gets download path.
	 *
	 * @return the download path
	 */
	public String getDownloadPath() {
		return downloadPath;
	}

	/**
	 * Sets download path.
	 *
	 * @param downloadPath the download path
	 */
	public void setDownloadPath(final String downloadPath) {
		this.downloadPath = downloadPath;
	}

	/**
	 * Gets test name.
	 *
	 * @return the test name
	 */
	public String getTestName() {
		return this.testName;
	}

	/**
	 * Sets test name.
	 *
	 * @param testName the test name
	 */
	public void setTestName(String testName) {
		this.testName = testName;
	}

	/**
	 * Gets case execution id.
	 *
	 * @return the case execution id
	 */
	public String getCaseExecutionId() {
		return caseExecutionId;
	}

	/**
	 * Sets case execution id.
	 *
	 * @param caseExecutionId the case execution id
	 */
	public void setCaseExecutionId(String caseExecutionId) {
		this.caseExecutionId = caseExecutionId;
	}

}
