package io.zahori.framework.tms.xray.cloud.model;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
public class XrayCloudReportInfo {

    private String project;
    private String summary;
    private String description;
    private String version;
    private String user;
    private String revision;
    private String startDate;
    private String finishDate;
    private String testPlanKey;
    private String[] testEnvironments = {};

    public XrayCloudReportInfo() {
    }

    public XrayCloudReportInfo(String project, String testPlanKey) {
        this.project = project;
        this.testPlanKey = testPlanKey;
    }

    public String getProject() {
        return project;
    }

    public void setProject(String project) {
        this.project = project;
    }

    public String getSummary() {
        return summary;
    }

    public void setSummary(String summary) {
        this.summary = summary;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getVersion() {
        return version;
    }

    public void setVersion(String version) {
        this.version = version;
    }

    public String getUser() {
        return user;
    }

    public void setUser(String user) {
        this.user = user;
    }

    public String getRevision() {
        return revision;
    }

    public void setRevision(String revision) {
        this.revision = revision;
    }

    public String getStartDate() {
        return startDate;
    }

    public void setStartDate(String startDate) {
        this.startDate = startDate;
    }

    public String getFinishDate() {
        return finishDate;
    }

    public void setFinishDate(String finishDate) {
        this.finishDate = finishDate;
    }

    public String getTestPlanKey() {
        return testPlanKey;
    }

    public void setTestPlanKey(String testPlanKey) {
        this.testPlanKey = testPlanKey;
    }

    public String[] getTestEnvironments() {
        return testEnvironments;
    }

    public void setTestEnvironments(String[] testEnvironments) {
        this.testEnvironments = testEnvironments;
    }
}
