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
import io.zahori.framework.core.ExecutionTarget;
import io.zahori.framework.evidences.Evidences.ZahoriLogLevel;
import io.zahori.framework.exception.ZahoriException;
import io.zahori.framework.security.ZahoriCipher;
import io.zahori.framework.utils.BooleanUtils;
import io.zahori.model.process.Configuration;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import org.apache.commons.lang3.EnumUtils;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ZahoriProperties {

    private static final Logger LOG = LoggerFactory.getLogger(ZahoriProperties.class);

    private static final String RESULTS_DIR = "target/test-results/";
    private static final String ZAHORI_TEST_BROWSERPREFS_ADD = "zahori.test.browser.preferences.add";
    private static final String ZAHORI_TEST_CAPABILITIES_ADD = "zahori.test.capabilities.add";
    private static final String DOT = ".";
    private static final String BASE_PROPERTIES_FILE = "zahori.properties";
    private static final String ENVIRONMENT_PROPERTIES_PREFIX = "zahori-";
    private static final String ENVIRONMENT_PROPERTIES_SUFFIX = ".properties";

    private final Properties prop;
    private Configuration configuration;

    private static final String TXT_PROP_TESTCASE_TIMEOUT = "zahori.test.execution.timeout.testcase";

    /**
     * Loads zahori.properties and, if the configuration carries an environment name, overlays
     * any matching zahori-&lt;environment&gt;.properties found in the classpath on top of it.
     * Platform-specific overrides are not applied through this constructor (no platform is
     * known here) — use {@link #ZahoriProperties(ExecutionTarget)} when the platform is
     * available.
     * <p>
     * This is orthogonal to the grid provider (BrowserStack bstack:options / Selenoid
     * selenoid:options / local Appium) dimension already resolved by key prefixing in
     * {@code getExtraCapabilities()} / {@code getBrowserPreferencesToBeAdded()}: the environment
     * overlay only decides which VALUE a key has, never which provider it applies to.
     */
    public ZahoriProperties(Configuration configuration) {
        this(ExecutionTarget.of(configuration == null ? null : configuration.getEnvironmentName(), null));
        this.configuration = configuration;
    }

    /**
     * Loads zahori.properties (common, environment-agnostic properties) and, if present in the
     * classpath, overlays environment specific files on top of it. No platform-specific overlay
     * is applied through this constructor — use {@link #ZahoriProperties(ExecutionTarget)} when
     * the platform is available.
     */
    public ZahoriProperties(String environmentName) {
        this(ExecutionTarget.of(environmentName, null));
    }

    /**
     * Loads zahori.properties (common, environment-agnostic properties) and, if present in the
     * classpath, overlays environment- and platform-specific files on top of it, following the
     * same "most specific wins" convention Spring Boot uses for profiles:
     * <ol>
     * <li>zahori-&lt;generic environment id&gt;.properties (e.g. environment "STG - Web" -&gt;
     * zahori-stg.properties)</li>
     * <li>zahori-&lt;full environment id&gt;.properties (e.g. zahori-stg-web.properties)</li>
     * <li>zahori-&lt;full environment id&gt;-&lt;platform&gt;.properties (e.g.
     * zahori-stg-web-android.properties) — only when a platform is known</li>
     * </ol>
     * All three files are optional; when none exist (e.g. processes that don't opt into
     * per-environment properties), behavior is identical to {@link #ZahoriProperties()} — full
     * backward compatibility with existing processes that ship a single zahori.properties. Only
     * the keys that actually change for that environment/platform need to be declared in the
     * overlay file.
     */
    public ZahoriProperties(ExecutionTarget target) {
        prop = new Properties();
        loadPropertiesFile(BASE_PROPERTIES_FILE, true);
        loadEnvironmentOverrides(target == null ? ExecutionTarget.of(null, null) : target);
    }

    public ZahoriProperties() {
        this((String) null);
    }

    /**
     * Associates a {@link Configuration} so that the handful of getters that prefer values
     * coming from it (timeout, TMS credentials...) over the properties file can use it. Optional
     * — callers that only need the merged properties (e.g. capability/preference lookups keyed
     * off an {@link ExecutionTarget}) can leave this unset.
     */
    public void setConfiguration(Configuration configuration) {
        this.configuration = configuration;
    }

    private void loadEnvironmentOverrides(ExecutionTarget target) {
        String environmentId = target.environment();
        if (StringUtils.isBlank(environmentId)) {
            return;
        }

        String genericId = StringUtils.substringBefore(environmentId, "-");
        if (!StringUtils.equals(genericId, environmentId)) {
            loadPropertiesFile(ENVIRONMENT_PROPERTIES_PREFIX + genericId + ENVIRONMENT_PROPERTIES_SUFFIX, false);
        }
        loadPropertiesFile(ENVIRONMENT_PROPERTIES_PREFIX + environmentId + ENVIRONMENT_PROPERTIES_SUFFIX, false);

        String platform = target.platform();
        if (StringUtils.isNotBlank(platform)) {
            loadPropertiesFile(ENVIRONMENT_PROPERTIES_PREFIX + environmentId + "-" + platform + ENVIRONMENT_PROPERTIES_SUFFIX, false);
        }
    }

    /**
     * Loads a properties file from the classpath into {@link #prop}, merging it on top of
     * whatever was already loaded (later values win, same semantics as calling
     * {@link Properties#load(InputStream)} repeatedly on the same instance).
     *
     * @param mandatory when {@code true}, a missing file throws (used for the base
     * zahori.properties, same behavior as before this change); when {@code false}, a missing
     * file is silently skipped (used for optional per-environment overlays).
     */
    private void loadPropertiesFile(String fileName, boolean mandatory) {
        try (InputStream input = SystemPropertiesUtils.class.getClassLoader().getResourceAsStream(fileName)) {
            if (input == null) {
                if (mandatory) {
                    throw new RuntimeException(
                            "ERROR loading zahorí properties file: Can't find file " + fileName + " in classpath. Please create it using one of the templates");
                }
                return;
            }
            prop.load(input);
            if (!mandatory) {
                LOG.info("Loaded environment properties overrides from {}", fileName);
            }
        } catch (IOException e) {
            throw new RuntimeException("ERROR loading zahorí properties file '" + fileName + "': " + e.getMessage());
        }
    }

    public String getProperty(String propertyName) {
        String propertyValue = prop.getProperty(propertyName);

        if (StringUtils.isBlank(propertyValue)) {
            return "";
        }
        String propertyValueTrimmed = propertyValue.trim();

        // Verify if the value contains the syntax that indicates the value should be read from an environment variable: ${VAR_NAME}
        if (propertyValueTrimmed.startsWith("${") && propertyValueTrimmed.endsWith("}")) {
            String environmentVariableName = propertyValueTrimmed.substring(2, propertyValueTrimmed.length() - 1);

            String environmentVariableValue = System.getenv(environmentVariableName);

            if (StringUtils.isBlank(environmentVariableValue)) {
                return "";
            }
            return environmentVariableValue;
        }

        return propertyValueTrimmed;
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

        String[] defaultLang = {"EN"};

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

    // BrowserMob Proxy - Add headers
    public boolean isAddHeadersEnabled() {
        return BooleanUtils.getBoolean(getProperty("zahori.test.browserMobProxy.headers.enabled"));
    }

    public Map<String, String> getHeadersToBeAdded() {
        return getMapFromProps("zahori.test.browserMobProxy.headers.add");
    }

    // BrowserMob Proxy - BlackList
    public boolean isBlackListEnabled() {
        return BooleanUtils.getBoolean(getProperty("zahori.test.browserMobProxy.blacklist.enabled"));
    }

    public Map<String, String> getBlackList() {
        return getMapFromProps("zahori.test.browserMobProxy.blacklist.add");
    }

    // BrowserMob Proxy - Har
    public boolean isHarEnabled() {
        if (configuration != null) {
            return configuration.getGenerateEvidencesTypes().stream().anyMatch(Configuration.HAR::equalsIgnoreCase);
        }
        return false;
    }

    public String getHarFilterByUrls() {
        return getProperty("zahori.test.browserMobProxy.har.filter.urls");
    }

    public String getHarFilterByRequestMethods() {
        return getProperty("zahori.test.browserMobProxy.har.filter.requestMethods");
    }

    public String getHarFilterByResponseContentTypes() {
        return getProperty("zahori.test.browserMobProxy.har.filter.responseContentTypes");
    }

    public boolean isHarRequestHeadersEnabled() {
        return BooleanUtils.getBoolean(getProperty("zahori.test.browserMobProxy.har.include.request.headers"));
    }

    public boolean isHarRequestCookiesEnabled() {
        return BooleanUtils.getBoolean(getProperty("zahori.test.browserMobProxy.har.include.request.cookies"));
    }

    public boolean isHarRequestContentEnabled() {
        return BooleanUtils.getBoolean(getProperty("zahori.test.browserMobProxy.har.include.request.content"));
    }

    public boolean isHarRequestBinaryContentEnabled() {
        return BooleanUtils.getBoolean(getProperty("zahori.test.browserMobProxy.har.include.request.binaryContent"));
    }

    public boolean isHarResponseHeadersEnabled() {
        return BooleanUtils.getBoolean(getProperty("zahori.test.browserMobProxy.har.include.response.headers"));
    }

    public boolean isHarResponseCookiesEnabled() {
        return BooleanUtils.getBoolean(getProperty("zahori.test.browserMobProxy.har.include.response.cookies"));
    }

    public boolean isHarResponseContentEnabled() {
        return BooleanUtils.getBoolean(getProperty("zahori.test.browserMobProxy.har.include.response.content"));
    }

    public boolean isHarResponseBinaryContentEnabled() {
        return BooleanUtils.getBoolean(getProperty("zahori.test.browserMobProxy.har.include.response.binaryContent"));
    }

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
        if (configuration != null && configuration.getTms() != null) {
            return configuration.getTms().isUploadResults();
        }
        return BooleanUtils.getBoolean(getProperty("zahori.test.results.tms.enabled"));
    }

    public String getTMS() {
        if (configuration != null && configuration.getTms() != null) {
            return configuration.getTms().getName();
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
        if (configuration != null) {
            return configuration.getTms().getUrl();
        }
        return getProperty("zahori.test.results.tms.testLink.url");
    }

    public String getTestLinkApiKey() {
        ZahoriCipher cipher = new ZahoriCipher();
        if (configuration != null) {
            return cipher.decode(configuration.getTms().getPassword());
        }
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
        if (configuration != null) {
            return configuration.getTms().getUrl();
        }
        return getProperty("zahori.test.results.tms.alm.url");
    }

    public String getALMUsername() {
        if (configuration != null) {
            return configuration.getTms().getUser();
        }
        return getProperty("zahori.test.results.tms.alm.username");
    }

    public String getALMPassword() {
        ZahoriCipher cipher = new ZahoriCipher();
        if (configuration != null) {
            return cipher.decode(configuration.getTms().getPassword());
        }
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
        if (configuration != null) {
            return configuration.getTms().getUrl();
        }
        return getProperty("zahori.test.results.tms.xray.url");
    }

    public String getXrayUser() {
        if (configuration != null) {
            return configuration.getTms().getUser();
        }
        return getProperty("zahori.test.results.tms.xray.username");
    }

    public String getXrayPassword() {
        ZahoriCipher cipher = new ZahoriCipher();
        if (configuration != null) {
            return cipher.decode(configuration.getTms().getPassword());
        }
        return getProperty("zahori.test.results.tms.xray.password");
    }

    public String getXrayTestPlanId() {
        return getProperty("zahori.test.results.tms.xray.tplan.id");
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
