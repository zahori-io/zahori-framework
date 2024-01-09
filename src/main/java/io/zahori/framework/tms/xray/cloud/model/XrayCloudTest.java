package io.zahori.framework.tms.xray.cloud.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import java.util.ArrayList;
import java.util.List;

@JsonInclude(Include.NON_EMPTY)
public class XrayCloudTest {

    private String testKey;     // The test issue key
    private String start;       // The start date for the test run
    private String finish;      // The finish date for the test run
    private String comment;     // The comment for the test run
    private String executedBy;  // The user id who executed the test run
    private String assignee;    // The user id for the assignee of the test run
    private String status;      // The test run status (PASSED, FAILED, EXECUTING, TODO, custom statuses ...)
    private List<XrayCloudStep> steps = new ArrayList<>();    // The step results (link)
    private String[] defects = {};   // An array of defect issue keys to associate with the test run
    private List<XrayCloudEvidence> evidences = new ArrayList<>();   // An array of evidence items of the test run (link)
    private String[] customFields = {}; // An array of custom fields for the test run (link)

    public XrayCloudTest() {
    }

    public String getTestKey() {
        return testKey;
    }

    public void setTestKey(String testKey) {
        this.testKey = testKey;
    }

    public String getStart() {
        return start;
    }

    public void setStart(String start) {
        this.start = start;
    }

    public String getFinish() {
        return finish;
    }

    public void setFinish(String finish) {
        this.finish = finish;
    }

    public String getComment() {
        return comment;
    }

    public void setComment(String comment) {
        this.comment = comment;
    }

    public String getExecutedBy() {
        return executedBy;
    }

    public void setExecutedBy(String executedBy) {
        this.executedBy = executedBy;
    }

    public String getAssignee() {
        return assignee;
    }

    public void setAssignee(String assignee) {
        this.assignee = assignee;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public List<XrayCloudStep> getSteps() {
        return steps;
    }

    public void setSteps(List<XrayCloudStep> steps) {
        this.steps = steps;
    }

    public void addStep(XrayCloudStep step) {
        this.steps.add(step);
    }

    public String[] getDefects() {
        return defects;
    }

    public void setDefects(String[] defects) {
        this.defects = defects;
    }

    public List<XrayCloudEvidence> getEvidences() {
        return evidences;
    }

    public void setEvidences(List<XrayCloudEvidence> evidences) {
        this.evidences = evidences;
    }

    public void addEvidence(XrayCloudEvidence evidence) {
        this.evidences.add(evidence);
    }

    public String[] getCustomFields() {
        return customFields;
    }

    public void setCustomFields(String[] customFields) {
        this.customFields = customFields;
    }

}
