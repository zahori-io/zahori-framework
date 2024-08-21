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

/*Clase Browsers.java*/
public class Browsers {

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

    private Long executionId;

    // TODO: remove. This is a temporal solution for mobile testing.
    // This url is used to indicate the id of the app artifact uploaded in the cloud farm (browserstack, ...)
    private String environmentUrl;
    private String environmentName;

    public static final String REMOTE_YES = "YES";

    public static final String REMOTE_NO = "NO";

    public Browsers() {
    }

    public Browsers withName(String name) {
        this.name = name;
        return this;
    }
    
    public Browsers withPlatform(String platform) {
        this.platform = platform;
        return this;
    }

    public Browsers withVersion(String version) {
        this.version = version;
        return this;
    }

    public Browsers withScreenResolution(String screenResolution) {
        this.screenResolution = screenResolution;
        return this;
    }

    public Browsers withBits(String bits) {
        this.bits = bits;
        return this;
    }

    public Browsers withRemote(String remote) {
        this.remote = remote;
        return this;
    }

    public Browsers withRemoteUrl(String remoteUrl) {
        this.remoteUrl = remoteUrl;
        return this;
    }

    public Browsers withDownloadPath(String downloadPath) {
        this.downloadPath = downloadPath;
        return this;
    }

    public Browsers withTestName(String testName) {
        this.testName = testName;
        return this;
    }

    public Browsers withCaseExecution(String caseExecutionId) {
        this.caseExecutionId = caseExecutionId;
        return this;
    }

    public Browsers withExecution(Long executionId) {
        this.executionId = executionId;
        return this;
    }

    public Browsers withEnvironmentUrl(String environmentUrl) {
        this.environmentUrl = environmentUrl;
        return this;
    }

    public Browsers withEnvironmentName(String environmentName) {
        this.environmentName = environmentName;
        return this;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getPackageWebdrivername() {
        return packageWebdrivername;
    }

    public void setPackageWebdrivername(String packageWebdrivername) {
        this.packageWebdrivername = packageWebdrivername;
    }

    public String getBits() {
        return bits;
    }

    public void setBits(String bits) {
        this.bits = bits;
    }

    public String getPlatform() {
        return platform;
    }

    public void setPlatform(String platform) {
        this.platform = platform;
    }

    public String getVersion() {
        return version;
    }

    public void setVersion(String version) {
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

    public void setRemote(String remote) {
        this.remote = remote;
    }

    public String getRemoteUrl() {
        return remoteUrl;
    }

    public void setRemoteUrl(String remoteUrl) {
        this.remoteUrl = remoteUrl;
    }

    public String getDownloadPath() {
        return downloadPath;
    }

    public void setDownloadPath(String downloadPath) {
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

    public Long getExecutionId() {
        return executionId;
    }

    public void setExecutionId(Long executionId) {
        this.executionId = executionId;
    }

    public String getEnvironmentUrl() {
        return environmentUrl;
    }

    public void setEnvironmentUrl(String environmentUrl) {
        this.environmentUrl = environmentUrl;
    }

    public String getEnvironmentName() {
        return environmentName;
    }

    public void setEnvironmentName(String environmentName) {
        this.environmentName = environmentName;
    }

}
