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
import io.zahori.model.process.Configuration;

public class ZahoriProperties {

    private static final String RESULTS_DIR = "target/test-results/";
    private static final String ZAHORI_TEST_HEADERS_ADD = "zahori.test.headers.add";
    private static final String ZAHORI_TEST_BLACKLIST_ADD = "zahori.test.blacklist.reqpattern.add";
    private static final String ZAHORI_TEST_BROWSERPREFS_ADD = "zahori.test.browser.preferences.add";
    private static final String ZAHORI_TEST_CAPABILITIES_ADD = "zahori.test.capabilities.add";
    private static final String DOT = ".";

    private final Properties prop;
    private Configuration configuration;

    private static final String TXT_PROP_TESTCASE_TIMEOUT = "zahori.test.execution.timeout.testcase";

    public ZahoriProperties(Configuration configuration) {
        this();
        this.configuration = configuration;
    }

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

    public String getIEDriverName() {
        return getProperty("zahori.webdriver.ie.driver.name");
    }

    public String getIEDriverPath() {
        return getProperty("zahori.webdriver.ie.driver.path");
    }

    public String getChromeDriver() {
        return getProperty("zahori.webdriver.chrome.driver");
    }

    public String getTestUrl() {
        return getProperty("zahori.test.url");
    }

    public Integer getTimeoutFindElement() {
        if (configuration != null) {
            return (int) configuration.getTimeout();
        }
        return Integer.valueOf(getProperty("zahori.test.execution.timeout.findElement"));
    }

    // ***** Directories *****
    public String getResultsDir() {
        return RESULTS_DIR;
    }

    // ***** Evidences *****

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

    // Timeout
    public int getExecutionTimeout() {
        try {
            return Integer.parseInt(getProperty(TXT_PROP_TESTCASE_TIMEOUT));
        } catch (NumberFormatException e) {
            throw new ZahoriException("TC", "zahori.error.timeout.value", TXT_PROP_TESTCASE_TIMEOUT, getProperty(TXT_PROP_TESTCASE_TIMEOUT));
        }
    }

    // LogFile
    public boolean isLogFileGenerationEnabled() {
        if (configuration != null) {
            return configuration.getGenerateEvidencesTypes().stream().anyMatch(Configuration.LOG::equalsIgnoreCase);
        }
        return BooleanUtils.getBoolean(getProperty("zahori.test.results.evidence.generateLogFile"));
    }

    public ZahoriLogLevel getLogLevel() {
        String propLogLevel = getProperty("zahori.test.results.evidence.logFileLevel");
        return EnumUtils.isValidEnum(ZahoriLogLevel.class, propLogLevel) ? ZahoriLogLevel.valueOf(propLogLevel) : null;
    }

    // HarLog File
    public boolean isHarLogFileEnabled() {
        if (configuration != null) {
            return configuration.getGenerateEvidencesTypes().stream().anyMatch(Configuration.HAR::equalsIgnoreCase);
        }
        return BooleanUtils.getBoolean(getProperty("zahori.test.results.evidence.generateHarLogFile"));
    }

    public boolean isHarRequestCookiesEnabled() {
        return BooleanUtils.getBoolean(getProperty("zahori.test.results.evidence.harlog.include.request.cookies"));
    }

    public boolean isHarRequestHeadersEnabled() {
        return BooleanUtils.getBoolean(getProperty("zahori.test.results.evidence.harlog.include.request.headers"));
    }

    public boolean isHarRequestContentEnabled() {
        return BooleanUtils.getBoolean(getProperty("zahori.test.results.evidence.harlog.include.request.content"));
    }

    public boolean isHarRequestBinaryContentEnabled() {
        return BooleanUtils.getBoolean(getProperty("zahori.test.results.evidence.harlog.include.request.binaryContent"));
    }

    public boolean isHarResponseCookiesEnabled() {
        return BooleanUtils.getBoolean(getProperty("zahori.test.results.evidence.harlog.include.response.cookies"));
    }

    public boolean isHarResponseHeadersEnabled() {
        return BooleanUtils.getBoolean(getProperty("zahori.test.results.evidence.harlog.include.response.headers"));
    }

    public boolean isHarResponseContentEnabled() {
        return BooleanUtils.getBoolean(getProperty("zahori.test.results.evidence.harlog.include.response.content"));
    }

    public boolean isHarResponseBinaryContentEnabled() {
        return BooleanUtils.getBoolean(getProperty("zahori.test.results.evidence.harlog.include.response.binaryContent"));
    }

    public String getHarFilterByUrlPattern() {
        return getProperty("zahori.test.results.evidence.harlog.filterPattern.url");
    }

    public String getHarFilterByRequestMethod() {
        return getProperty("zahori.test.results.evidence.harlog.filter.method");
    }

    // Video
    public boolean isVideoGenerationEnabledWhenPassed() {
        return BooleanUtils.getBoolean(getProperty("zahori.test.results.evidence.generateVideo.passed"));
    }

    public boolean isVideoGenerationEnabledWhenFailed() {
        return BooleanUtils.getBoolean(getProperty("zahori.test.results.evidence.generateVideo.failed"));
    }

    // Doc
    public boolean isDocGenerationEnabled() {
        if (configuration != null) {
            return configuration.getGenerateEvidencesTypes().stream().anyMatch(Configuration.DOC::equalsIgnoreCase);
        }
        return BooleanUtils.getBoolean(getProperty("zahori.test.results.evidence.generateDoc"));
    }

    // Screenshots
    public boolean isScreenshotsGenerationEnabled() {
        if (configuration != null) {
            return configuration.getGenerateEvidencesTypes().stream().anyMatch(Configuration.SCREENSHOT::equalsIgnoreCase);
        }
        return BooleanUtils.getBoolean(getProperty("zahori.test.results.evidence.generateScreenshots"));
    }

    // ***** TMS *****

    public boolean isTMSEnabled() {
        if (configuration != null) {
            return configuration.isUploadResults();
        }
        return BooleanUtils.getBoolean(getProperty("zahori.test.results.tms.enabled"));
    }

    public String getTMS() {
        if (configuration != null && configuration.isUploadResults()) {
            return configuration.getUploadRepositoryName();
        }
        return getProperty("zahori.test.results.tms");
    }

    // TMS Options

    // LogFile
    public boolean uploadEvidenceLogFileWhenPassed() {
        return BooleanUtils.getBoolean(getProperty("zahori.test.results.tms.uploadEvidence.logFile.passed"));
    }

    public boolean uploadEvidenceLogFileWhenFailed() {
        return BooleanUtils.getBoolean(getProperty("zahori.test.results.tms.uploadEvidence.logFile.failed"));
    }

    // HarLogFile
    public boolean uploadEvidenceHarLogFileWhenPassed() {
        return BooleanUtils.getBoolean(getProperty("zahori.test.results.tms.uploadEvidence.harlogFile.passed"));
    }

    public boolean uploadEvidenceHarLogFileWhenFailed() {
        return BooleanUtils.getBoolean(getProperty("zahori.test.results.tms.uploadEvidence.harlogFile.failed"));
    }

    // Video
    public boolean uploadEvidenceVideoWhenPassed() {
        return BooleanUtils.getBoolean(getProperty("zahori.test.results.tms.uploadEvidence.video.passed"));
    }

    public boolean uploadEvidenceVideoWhenFailed() {
        return BooleanUtils.getBoolean(getProperty("zahori.test.results.tms.uploadEvidence.video.failed"));
    }

    // Doc
    public boolean uploadEvidenceDocWhenPassed() {
        return BooleanUtils.getBoolean(getProperty("zahori.test.results.tms.uploadEvidence.doc.passed"));
    }

    public boolean uploadEvidenceDocWhenFailed() {
        return BooleanUtils.getBoolean(getProperty("zahori.test.results.tms.uploadEvidence.doc.failed"));
    }

    // TEST LINK

    public String getTestLinkUrl() {
        return getProperty("zahori.test.results.tms.testLink.url");
    }

    public String getTestLinkApiKey() {
        return getProperty("zahori.test.results.tms.testLink.apiKey");
    }

    public String getTestLinkProjectName() {
        return getProperty("zahori.test.results.tms.testLink.projectName");
    }

    public String getTestLinkPlanName() {
        return getProperty("zahori.test.results.tms.testLink.planName");
    }

    public String getTestLinkBuildName() {
        return getProperty("zahori.test.results.tms.testLink.buildName");
    }

    public boolean isTestLinkUsingPlatform() {
        return BooleanUtils.getBoolean(getProperty("zahori.test.results.tms.testLink.usePlatform"));
    }

    // HOST 3270 EMULATION

    public String getHostEmulatorURL() {
        return getProperty("zahori.test.host.emulator.url");
    }

    // ALM

    public String getALMUrl() {
        return getProperty("zahori.test.results.tms.alm.url");
    }

    public String getALMUsername() {
        return getProperty("zahori.test.results.tms.alm.username");
    }

    public String getALMPassword() {
        ZahoriCipher cipher = new ZahoriCipher();
        return cipher.decode(getProperty("zahori.test.results.tms.alm.password"));
    }

    public String getALMDomain() {
        return getProperty("zahori.test.results.tms.alm.domain");
    }

    public String getALMProject() {
        return getProperty("zahori.test.results.tms.alm.project");
    }

    public String getALMTestSet() {
        return getProperty("zahori.test.results.tms.alm.testSet");
    }

    public int getDefinedRetries() {
        try {
            if (configuration != null) {
                return configuration.getRetries();
            }
            return Integer.parseInt(getProperty("zahori.test.execution.maxtestretries"));
        } catch (NumberFormatException | NullPointerException e) {
            return 0;
        }
    }

    public Map<String, String> getHeadersToBeAdded() {
        return getMapFromProps(ZAHORI_TEST_HEADERS_ADD);
    }

    public Map<String, String> getBlackListPatternsToBeAdded() {
        return getMapFromProps(ZAHORI_TEST_BLACKLIST_ADD);
    }

    public Map<String, String> getBrowserPreferencesToBeAdded(String browserName) {
        return getMapFromProps(ZAHORI_TEST_BROWSERPREFS_ADD + DOT + StringUtils.lowerCase(browserName));
    }

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

    public String getProxyIP() {
        return getProperty("zahori.test.execution.proxy.ip");
    }

    public int getProxyPort() {
        try {
            return Integer.parseInt(getProperty("zahori.test.execution.proxy.port"));
        } catch (NullPointerException | NumberFormatException e) {
            return -1;
        }
    }

    public String getBMPIP() {
        return getProperty("zahori.test.execution.bmp.ip");
    }

    public String getProxyUser() {
        return getProperty("zahori.test.execution.proxy.user");
    }

    public String getProxyEncodedPassword() {
        return getProperty("zahori.test.execution.proxy.password");
    }

    public String getDownloadPath() {
        return getProperty("webdriver.downloadpath");
    }

    public String getXrayUrl() {
        return getProperty("zahori.test.results.tms.xray.url");
    }

    public String getXrayUser() {
        return getProperty("zahori.test.results.tms.xray.username");
    }

    public String getXrayPassword() {
        return getProperty("zahori.test.results.tms.xray.password");
    }

    public String getXrayTestExecDescription() {
        return getProperty("zahori.test.results.tms.xray.texec.description");
    }

    public String getXrayTestExecPriorityId() {
        return getProperty("zahori.test.results.tms.xray.texec.priorityid");
    }

    public String[] getXrayTestExecLabels() {
        return getCommaSeparatedValuesArray("zahori.test.results.tms.xray.texec.labels");
    }

    public String[] getXrayTestExecComponents() {
        return getCommaSeparatedValuesArray("zahori.test.results.tms.xray.texec.components");
    }

    public String getXrayTestExecAssignee() {
        return getProperty("zahori.test.results.tms.xray.texec.assignee");
    }

    public String getXrayProjectKey() {
        return getProperty("zahori.test.results.tms.xray.projectkey");
    }

    public String getXrayTestExecSummary() {
        return getProperty("zahori.test.results.tms.xray.texec.summary");
    }

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
