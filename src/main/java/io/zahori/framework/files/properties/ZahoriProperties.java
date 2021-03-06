package io.zahori.framework.files.properties;

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

import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;
import java.util.Set;

import org.apache.commons.lang3.EnumUtils;
import org.apache.commons.lang3.StringUtils;

import io.zahori.framework.evidences.Evidences.ZahoriLogLevel;
import io.zahori.framework.exception.ZahoriException;
import io.zahori.framework.security.ZahoriCipher;
import io.zahori.framework.utils.BooleanUtils;

/**
 * The type Zahori properties.
 */
public class ZahoriProperties {

	private static final String ZAHORI_TEST_HEADERS_ADD = "zahori.test.headers.add";
	private static final String ZAHORI_TEST_BLACKLIST_ADD = "zahori.test.blacklist.reqpattern.add";
	private static final String ZAHORI_TEST_BROWSERPREFS_ADD = "zahori.test.browser.preferences.add";
	private static final String ZAHORI_TEST_CAPABILITIES_ADD = "zahori.test.capabilities.add";
	private static final String DOT = ".";

	private final Properties prop;

	private static final String TXT_PROP_TESTCASE_TIMEOUT = "zahori.test.execution.timeout.testcase";

	/**
	 * Instantiates a new Zahori properties.
	 */
	public ZahoriProperties() {

		prop = new Properties();
		InputStream input = null;
		try {

			input = SystemPropertiesUtils.class.getClassLoader().getResourceAsStream("zahori.properties");

			// load properties file
			prop.load(input);

			// display list of properties
			/*
			 * if (LOG.isDebugEnabled()) { Enumeration e = prop.propertyNames();
			 * LOG.debug("Project properties:"); while (e.hasMoreElements()) { String key =
			 * e.nextElement().toString(); LOG.debug("- " + key + "=" +
			 * prop.getProperty(key)); } }
			 */

		} catch (Exception e) {
			throw new RuntimeException("ERROR loading zahorí properties file: " + e.getMessage());
		} finally {
			if (input != null) {
				try {
					input.close();
				} catch (IOException e) {
					throw new RuntimeException("ERROR loading zahorí properties file: " + e.getMessage());
				}
			}
		}
	}

	private String getProperty(String propertyName) {
		return StringUtils.trim(prop.getProperty(propertyName));
	}

	/**
	 * Gets ie driver name.
	 *
	 * @return the ie driver name
	 */
	public String getIEDriverName() {
		return getProperty("zahori.webdriver.ie.driver.name");
	}

	/**
	 * Gets ie driver path.
	 *
	 * @return the ie driver path
	 */
	public String getIEDriverPath() {
		return getProperty("zahori.webdriver.ie.driver.path");
	}

	/**
	 * Gets chrome driver.
	 *
	 * @return the chrome driver
	 */
	public String getChromeDriver() {
		return getProperty("zahori.webdriver.chrome.driver");
	}

	/**
	 * Gets test url.
	 *
	 * @return the test url
	 */
	public String getTestUrl() {
		return getProperty("zahori.test.url");
	}

	/**
	 * Gets timeout find element.
	 *
	 * @return the timeout find element
	 */
	public Integer getTimeoutFindElement() {
		return Integer.valueOf(getProperty("zahori.test.execution.timeout.findElement"));
	}

	/**
	 * Gets results dir.
	 *
	 * @return the results dir
	 */
// ***** Directories *****
	public String getResultsDir() {
		return getProperty("zahori.test.results.dir");
	}

	// ***** Evidences *****

	/**
	 * Get languages string [ ].
	 *
	 * @return the string [ ]
	 */
// Languages
	public String[] getLanguages() {
		String languages = getProperty("zahori.test.results.evidence.languages");

		String[] defaultLang = { "EN" };

		// if no languages found in zahori.properties
		if (StringUtils.isBlank(languages)) {
			return defaultLang;
		}

		String[] langs = StringUtils.split(StringUtils.remove(languages, " ").toUpperCase(), ",");
		if (langs.length > 0) {
			return langs;
		}

		return defaultLang;
	}

	/**
	 * Gets execution timeout.
	 *
	 * @return the execution timeout
	 */
// Timeout
	public int getExecutionTimeout() {
		try {
			return Integer.parseInt(getProperty(TXT_PROP_TESTCASE_TIMEOUT));
		} catch (NumberFormatException e) {
			throw new ZahoriException("TC", "zahori.error.timeout.value", TXT_PROP_TESTCASE_TIMEOUT, getProperty(TXT_PROP_TESTCASE_TIMEOUT));
		}
	}

	/**
	 * Is log file generation enabled boolean.
	 *
	 * @return the boolean
	 */
// LogFile
	public boolean isLogFileGenerationEnabled() {
		return BooleanUtils.getBoolean(getProperty("zahori.test.results.evidence.generateLogFile"));
	}

	/**
	 * Gets log level.
	 *
	 * @return the log level
	 */
	public ZahoriLogLevel getLogLevel() {
		String propLogLevel = getProperty("zahori.test.results.evidence.logFileLevel");
		return EnumUtils.isValidEnum(ZahoriLogLevel.class, propLogLevel) ? ZahoriLogLevel.valueOf(propLogLevel) : null;
	}

	/**
	 * Is har log file enabled boolean.
	 *
	 * @return the boolean
	 */
// HarLog File
	public boolean isHarLogFileEnabled() {
		return BooleanUtils.getBoolean(getProperty("zahori.test.results.evidence.generateHarLogFile"));
	}

	/**
	 * Is har request cookies enabled boolean.
	 *
	 * @return the boolean
	 */
	public boolean isHarRequestCookiesEnabled() {
		return BooleanUtils.getBoolean(getProperty("zahori.test.results.evidence.harlog.include.request.cookies"));
	}

	/**
	 * Is har request headers enabled boolean.
	 *
	 * @return the boolean
	 */
	public boolean isHarRequestHeadersEnabled() {
		return BooleanUtils.getBoolean(getProperty("zahori.test.results.evidence.harlog.include.request.headers"));
	}

	/**
	 * Is har request content enabled boolean.
	 *
	 * @return the boolean
	 */
	public boolean isHarRequestContentEnabled() {
		return BooleanUtils.getBoolean(getProperty("zahori.test.results.evidence.harlog.include.request.content"));
	}

	/**
	 * Is har request binary content enabled boolean.
	 *
	 * @return the boolean
	 */
	public boolean isHarRequestBinaryContentEnabled() {
		return BooleanUtils.getBoolean(getProperty("zahori.test.results.evidence.harlog.include.request.binaryContent"));
	}

	/**
	 * Is har response cookies enabled boolean.
	 *
	 * @return the boolean
	 */
	public boolean isHarResponseCookiesEnabled() {
		return BooleanUtils.getBoolean(getProperty("zahori.test.results.evidence.harlog.include.response.cookies"));
	}

	/**
	 * Is har response headers enabled boolean.
	 *
	 * @return the boolean
	 */
	public boolean isHarResponseHeadersEnabled() {
		return BooleanUtils.getBoolean(getProperty("zahori.test.results.evidence.harlog.include.response.headers"));
	}

	/**
	 * Is har response content enabled boolean.
	 *
	 * @return the boolean
	 */
	public boolean isHarResponseContentEnabled() {
		return BooleanUtils.getBoolean(getProperty("zahori.test.results.evidence.harlog.include.response.content"));
	}

	/**
	 * Is har response binary content enabled boolean.
	 *
	 * @return the boolean
	 */
	public boolean isHarResponseBinaryContentEnabled() {
		return BooleanUtils.getBoolean(getProperty("zahori.test.results.evidence.harlog.include.response.binaryContent"));
	}

	/**
	 * Gets har filter by url pattern.
	 *
	 * @return the har filter by url pattern
	 */
	public String getHarFilterByUrlPattern() {
		return getProperty("zahori.test.results.evidence.harlog.filterPattern.url");
	}

	/**
	 * Gets har filter by request method.
	 *
	 * @return the har filter by request method
	 */
	public String getHarFilterByRequestMethod() {
		return getProperty("zahori.test.results.evidence.harlog.filter.method");
	}

	/**
	 * Is video generation enabled when passed boolean.
	 *
	 * @return the boolean
	 */
// Video
	public boolean isVideoGenerationEnabledWhenPassed() {
		return BooleanUtils.getBoolean(getProperty("zahori.test.results.evidence.generateVideo.passed"));
	}

	/**
	 * Is video generation enabled when failed boolean.
	 *
	 * @return the boolean
	 */
	public boolean isVideoGenerationEnabledWhenFailed() {
		return BooleanUtils.getBoolean(getProperty("zahori.test.results.evidence.generateVideo.failed"));
	}

	/**
	 * Is doc generation enabled boolean.
	 *
	 * @return the boolean
	 */
// Doc
	public boolean isDocGenerationEnabled() {
		return BooleanUtils.getBoolean(getProperty("zahori.test.results.evidence.generateDoc"));
	}

	/**
	 * Is screenshots generation enabled boolean.
	 *
	 * @return the boolean
	 */
// Screenshots
	public boolean isScreenshotsGenerationEnabled() {
		return BooleanUtils.getBoolean(getProperty("zahori.test.results.evidence.generateScreenshots"));
	}

	// ***** TMS *****

	/**
	 * Is tms enabled boolean.
	 *
	 * @return the boolean
	 */
	public boolean isTMSEnabled() {
		return BooleanUtils.getBoolean(getProperty("zahori.test.results.tms.enabled"));
	}

	/**
	 * Gets tms.
	 *
	 * @return the tms
	 */
	public String getTMS() {
		return getProperty("zahori.test.results.tms");
	}

	// TMS Options

	/**
	 * Upload evidence log file when passed boolean.
	 *
	 * @return the boolean
	 */
// LogFile
	public boolean uploadEvidenceLogFileWhenPassed() {
		return BooleanUtils.getBoolean(getProperty("zahori.test.results.tms.uploadEvidence.logFile.passed"));
	}

	/**
	 * Upload evidence log file when failed boolean.
	 *
	 * @return the boolean
	 */
	public boolean uploadEvidenceLogFileWhenFailed() {
		return BooleanUtils.getBoolean(getProperty("zahori.test.results.tms.uploadEvidence.logFile.failed"));
	}

	/**
	 * Upload evidence har log file when passed boolean.
	 *
	 * @return the boolean
	 */
// HarLogFile
	public boolean uploadEvidenceHarLogFileWhenPassed() {
		return BooleanUtils.getBoolean(getProperty("zahori.test.results.tms.uploadEvidence.harlogFile.passed"));
	}

	/**
	 * Upload evidence har log file when failed boolean.
	 *
	 * @return the boolean
	 */
	public boolean uploadEvidenceHarLogFileWhenFailed() {
		return BooleanUtils.getBoolean(getProperty("zahori.test.results.tms.uploadEvidence.harlogFile.failed"));
	}

	/**
	 * Upload evidence video when passed boolean.
	 *
	 * @return the boolean
	 */
// Video
	public boolean uploadEvidenceVideoWhenPassed() {
		return BooleanUtils.getBoolean(getProperty("zahori.test.results.tms.uploadEvidence.video.passed"));
	}

	/**
	 * Upload evidence video when failed boolean.
	 *
	 * @return the boolean
	 */
	public boolean uploadEvidenceVideoWhenFailed() {
		return BooleanUtils.getBoolean(getProperty("zahori.test.results.tms.uploadEvidence.video.failed"));
	}

	/**
	 * Upload evidence doc when passed boolean.
	 *
	 * @return the boolean
	 */
// Doc
	public boolean uploadEvidenceDocWhenPassed() {
		return BooleanUtils.getBoolean(getProperty("zahori.test.results.tms.uploadEvidence.doc.passed"));
	}

	/**
	 * Upload evidence doc when failed boolean.
	 *
	 * @return the boolean
	 */
	public boolean uploadEvidenceDocWhenFailed() {
		return BooleanUtils.getBoolean(getProperty("zahori.test.results.tms.uploadEvidence.doc.failed"));
	}

	// TEST LINK

	/**
	 * Gets test link url.
	 *
	 * @return the test link url
	 */
	public String getTestLinkUrl() {
		return getProperty("zahori.test.results.tms.testLink.url");
	}

	/**
	 * Gets test link api key.
	 *
	 * @return the test link api key
	 */
	public String getTestLinkApiKey() {
		return getProperty("zahori.test.results.tms.testLink.apiKey");
	}

	/**
	 * Gets test link project name.
	 *
	 * @return the test link project name
	 */
	public String getTestLinkProjectName() {
		return getProperty("zahori.test.results.tms.testLink.projectName");
	}

	/**
	 * Gets test link plan name.
	 *
	 * @return the test link plan name
	 */
	public String getTestLinkPlanName() {
		return getProperty("zahori.test.results.tms.testLink.planName");
	}

	/**
	 * Gets test link build name.
	 *
	 * @return the test link build name
	 */
	public String getTestLinkBuildName() {
		return getProperty("zahori.test.results.tms.testLink.buildName");
	}

	/**
	 * Is test link using platform boolean.
	 *
	 * @return the boolean
	 */
	public boolean isTestLinkUsingPlatform() {
		return BooleanUtils.getBoolean(getProperty("zahori.test.results.tms.testLink.usePlatform"));
	}

	// HOST 3270 EMULATION

	/**
	 * Gets host emulator url.
	 *
	 * @return the host emulator url
	 */
	public String getHostEmulatorURL() {
		return getProperty("zahori.test.host.emulator.url");
	}

	// ALM

	/**
	 * Gets alm url.
	 *
	 * @return the alm url
	 */
	public String getALMUrl() {
		return getProperty("zahori.test.results.tms.alm.url");
	}

	/**
	 * Gets alm username.
	 *
	 * @return the alm username
	 */
	public String getALMUsername() {
		return getProperty("zahori.test.results.tms.alm.username");
	}

	/**
	 * Gets alm password.
	 *
	 * @return the alm password
	 */
	public String getALMPassword() {
		ZahoriCipher cipher = new ZahoriCipher();
		return cipher.decode(getProperty("zahori.test.results.tms.alm.password"));
	}

	/**
	 * Gets alm domain.
	 *
	 * @return the alm domain
	 */
	public String getALMDomain() {
		return getProperty("zahori.test.results.tms.alm.domain");
	}

	/**
	 * Gets alm project.
	 *
	 * @return the alm project
	 */
	public String getALMProject() {
		return getProperty("zahori.test.results.tms.alm.project");
	}

	/**
	 * Gets alm test set.
	 *
	 * @return the alm test set
	 */
	public String getALMTestSet() {
		return getProperty("zahori.test.results.tms.alm.testSet");
	}

	/**
	 * Gets defined retries.
	 *
	 * @return the defined retries
	 */
	public int getDefinedRetries() {
		try {
			return Integer.parseInt(getProperty("zahori.test.execution.maxtestretries"));
		} catch (NumberFormatException | NullPointerException e) {
			return 0;
		}
	}

	/**
	 * Gets headers to be added.
	 *
	 * @return the headers to be added
	 */
	public Map<String, String> getHeadersToBeAdded() {
		return getMapFromProps(ZAHORI_TEST_HEADERS_ADD);
	}

	/**
	 * Gets black list patterns to be added.
	 *
	 * @return the black list patterns to be added
	 */
	public Map<String, String> getBlackListPatternsToBeAdded() {
		return getMapFromProps(ZAHORI_TEST_BLACKLIST_ADD);
	}

	/**
	 * Gets browser preferences to be added.
	 *
	 * @param browserName the browser name
	 * @return the browser preferences to be added
	 */
	public Map<String, String> getBrowserPreferencesToBeAdded(String browserName) {
		return getMapFromProps(ZAHORI_TEST_BROWSERPREFS_ADD + DOT + StringUtils.lowerCase(browserName));
	}

	/**
	 * Gets extra capabilities.
	 *
	 * @return the extra capabilities
	 */
	public Map<String, String> getExtraCapabilities() {
		return getMapFromProps(ZAHORI_TEST_CAPABILITIES_ADD);
	}

	private Map<String, String> getMapFromProps(String propertyPrefix) {
		Map<String, String> properties = new HashMap<>();
		Set<Object> keys = prop.keySet();
		for (Object currentKey : keys) {
			String currentProperty = (String) currentKey;
			if (StringUtils.startsWithIgnoreCase(currentProperty, propertyPrefix)) {
				properties.put(currentProperty.replaceFirst(propertyPrefix + DOT, StringUtils.EMPTY), getProperty(currentProperty));
			}
		}

		return properties;
	}

	/**
	 * Gets proxy ip.
	 *
	 * @return the proxy ip
	 */
	public String getProxyIP() {
		return getProperty("zahori.test.execution.proxy.ip");
	}

	/**
	 * Gets proxy port.
	 *
	 * @return the proxy port
	 */
	public int getProxyPort() {
		try {
			return Integer.parseInt(getProperty("zahori.test.execution.proxy.port"));
		} catch (NullPointerException | NumberFormatException e) {
			return -1;
		}
	}

	/**
	 * Gets bmpip.
	 *
	 * @return the bmpip
	 */
	public String getBMPIP() {
		return getProperty("zahori.test.execution.bmp.ip");
	}

	/**
	 * Gets proxy user.
	 *
	 * @return the proxy user
	 */
	public String getProxyUser() {
		return getProperty("zahori.test.execution.proxy.user");
	}

	/**
	 * Gets proxy encoded password.
	 *
	 * @return the proxy encoded password
	 */
	public String getProxyEncodedPassword() {
		return getProperty("zahori.test.execution.proxy.password");
	}

	/**
	 * Gets download path.
	 *
	 * @return the download path
	 */
	public String getDownloadPath() {
		return getProperty("webdriver.downloadpath");
	}

	/**
	 * Gets xray url.
	 *
	 * @return the xray url
	 */
	public String getXrayUrl() {
		return getProperty("zahori.test.results.tms.xray.url");
	}

	/**
	 * Gets xray user.
	 *
	 * @return the xray user
	 */
	public String getXrayUser() {
		return getProperty("zahori.test.results.tms.xray.username");
	}

	/**
	 * Gets xray password.
	 *
	 * @return the xray password
	 */
	public String getXrayPassword() {
		return getProperty("zahori.test.results.tms.xray.password");
	}

	/**
	 * Gets xray test exec description.
	 *
	 * @return the xray test exec description
	 */
	public String getXrayTestExecDescription() {
		return getProperty("zahori.test.results.tms.xray.texec.description");
	}

	/**
	 * Gets xray test exec priority id.
	 *
	 * @return the xray test exec priority id
	 */
	public String getXrayTestExecPriorityId() {
		return getProperty("zahori.test.results.tms.xray.texec.priorityid");
	}

	/**
	 * Get xray test exec labels string [ ].
	 *
	 * @return the string [ ]
	 */
	public String[] getXrayTestExecLabels() {
		return getCommaSeparatedValuesArray("zahori.test.results.tms.xray.texec.labels");
	}

	/**
	 * Get xray test exec components string [ ].
	 *
	 * @return the string [ ]
	 */
	public String[] getXrayTestExecComponents() {
		return getCommaSeparatedValuesArray("zahori.test.results.tms.xray.texec.components");
	}

	/**
	 * Gets xray test exec assignee.
	 *
	 * @return the xray test exec assignee
	 */
	public String getXrayTestExecAssignee() {
		return getProperty("zahori.test.results.tms.xray.texec.assignee");
	}

	/**
	 * Gets xray project key.
	 *
	 * @return the xray project key
	 */
	public String getXrayProjectKey() {
		return getProperty("zahori.test.results.tms.xray.projectkey");
	}

	/**
	 * Gets xray test exec summary.
	 *
	 * @return the xray test exec summary
	 */
	public String getXrayTestExecSummary() {
		return getProperty("zahori.test.results.tms.xray.texec.summary");
	}

	/**
	 * Enable selenium log performance boolean.
	 *
	 * @return the boolean
	 */
	public boolean enableSeleniumLogPerformance() {
		return BooleanUtils.getBoolean(getProperty("zahori.test.capabilities.logperformance"));
	}

	private String[] getCommaSeparatedValuesArray(String propertyName) {
		String text = getProperty(propertyName);
		String[] array = text.split(",");
		for (int i = 0; i < array.length; i++) {
			array[i] = StringUtils.trim(array[i]);
		}
		return array;
	}

}
