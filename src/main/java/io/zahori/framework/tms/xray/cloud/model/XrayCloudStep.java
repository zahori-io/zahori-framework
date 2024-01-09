package io.zahori.framework.tms.xray.cloud.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.ArrayList;
import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class XrayCloudStep {

    private String status;      // The status for the test step (PASSED, FAILED, EXECUTING, TODO, custom statuses ...)
    private String comment;     // The comment for the step result
    private String actualResult;// The actual result field for the step result
    private List<XrayCloudEvidence> evidences = new ArrayList<>();    // An array of evidence items of the test run (link)

    public XrayCloudStep() {
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getComment() {
        return comment;
    }

    public void setComment(String comment) {
        this.comment = comment;
    }

    public String getActualResult() {
        return actualResult;
    }

    public void setActualResult(String actualResult) {
        this.actualResult = actualResult;
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
}
