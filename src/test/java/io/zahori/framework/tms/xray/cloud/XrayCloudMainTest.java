package io.zahori.framework.tms.xray.cloud;

import java.util.ArrayList;
import java.util.List;

public class XrayCloudMainTest {

    private static final String CLIENT_ID = "<CLIENT_ID>";
    private static final String CLIENT_SECRET = "<CLIENT_SECRET>";
    private static final String TEST_PLAN_ID = "XXX-1";
    private static final String TEST_EXECUTION_ID = "XXX-2";
    private static final String TEST_CASE_ID = "XXX-3";

    public static void main(String... args) {

        XrayCloudClient xrayClient = new XrayCloudClient(CLIENT_ID, CLIENT_SECRET);

        // Test evidences
        List<String> evidences = new ArrayList<>();
        evidences.add("/path/to/evidencie/file");

        // Update existing Test Execution
        xrayClient.updateTestExecution(TEST_EXECUTION_ID, TEST_CASE_ID, true, "20231230-120010", evidences, "Comments...");

        // Create new Test Execution (associated to existing test plan)
        xrayClient.createTestExecution("Test execution summary", TEST_PLAN_ID, TEST_CASE_ID, true, "20231230-120010", evidences, "Comments...");

        // Create new Test Execution (not associated to a test plan)
        xrayClient.createTestExecution("New TE-Search Wikipedia", TEST_CASE_ID, true, "20231230-120010", evidences, "Comments...");
    }
}
