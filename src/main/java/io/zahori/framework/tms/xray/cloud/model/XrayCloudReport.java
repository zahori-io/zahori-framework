package io.zahori.framework.tms.xray.cloud.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.ArrayList;
import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class XrayCloudReport {

    private String testExecutionKey;
    private XrayCloudReportInfo info;
    private List<XrayCloudTest> tests = new ArrayList<>();

    public XrayCloudReport() {
    }

    public String getTestExecutionKey() {
        return testExecutionKey;
    }

    public void setTestExecutionKey(String testExecutionKey) {
        this.testExecutionKey = testExecutionKey;
    }

    public XrayCloudReportInfo getInfo() {
        return info;
    }

    public void setInfo(XrayCloudReportInfo info) {
        this.info = info;
    }

    public List<XrayCloudTest> getTests() {
        return tests;
    }

    public void setTests(List<XrayCloudTest> tests) {
        this.tests = tests;
    }

    public void addTest(XrayCloudTest test) {
        this.tests.add(test);
    }

}
