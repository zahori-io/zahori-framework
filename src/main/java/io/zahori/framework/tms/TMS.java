package io.zahori.framework.tms;

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

import java.io.File;
import java.io.IOException;
import java.net.InetAddress;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.commons.lang.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.net.InetAddresses;

import io.zahori.framework.evidences.Evidences;
import io.zahori.framework.files.properties.ZahoriProperties;
import io.zahori.framework.i18n.Messages;
import io.zahori.framework.security.MyTrustManager;
import io.zahori.framework.tms.testLink.TestLink;
import io.zahori.framework.tms.xray.XrayRestClient;
import io.zahori.model.Run;
import io.zahori.model.Step;
import io.zahori.tms.alm.restclient.ALMRestClient;
import io.zahori.tms.alm.restclient.ALMRestClient.EntityType;

public class TMS {

    private static final Logger LOG = LoggerFactory.getLogger(TMS.class);

    // TMS
    private String tms;
    private TestLink testLink;
    private ALMRestClient alm;
    private XrayRestClient xray;
    private String executionNotes;
    private String date;

    // Zahorí options
    private ZahoriProperties zahoriProperties;

    // Evidences
    private Evidences evidences;

    // i18n Messages
    private Messages messages;

    public TMS(ZahoriProperties zahoriProperties, Evidences evidences, Messages messages) {
        tms = zahoriProperties.getTMS();
        this.zahoriProperties = zahoriProperties;
        this.evidences = evidences;
        this.messages = messages;
        executionNotes = null;
        date = null;
    }

    public TMS(ZahoriProperties zahoriProperties, Evidences evidences, Messages messages, String startDate) {
        tms = zahoriProperties.getTMS();
        this.zahoriProperties = zahoriProperties;
        this.evidences = evidences;
        this.messages = messages;
        executionNotes = null;
        date = startDate;
    }

    public void updateTestResult(String testCaseName, boolean passed, List<List<Step>> testSteps, int testDuration, String tmsTestSetId, String tmsTestCaseId,
            String tmsTestExecId, String tmsTestPlanId, String browserName, String platformName) {

        // TMS is disabled
        if (!zahoriProperties.isTMSEnabled()) {
            return;
        }

        // TMS not supported
        if (!("TestLink").equalsIgnoreCase(tms) && !("ALM").equalsIgnoreCase(tms) && !("XRAY").equalsIgnoreCase(tms)) {
            throw new RuntimeException("TMS " + tms + " not supported");
        }

        logInfo("Uploading test results to " + tms);

        // Create TestLink instance
        if (("TestLink").equalsIgnoreCase(tms)) {
            MyTrustManager.disableSSL();
            pingTMS(zahoriProperties.getTestLinkUrl());
            logInfo("Connecting to " + tms + ": " + zahoriProperties.getTestLinkUrl());

            testLink = getTestLinkInstance(browserName, platformName);
        }

        // Create XRAY instance
        if (("XRAY").equalsIgnoreCase(tms)) {
            String jiraUrl = zahoriProperties.getXrayUrl();
            checkParameter(tms, "jiraUrl", jiraUrl);
            String jiraUser = zahoriProperties.getXrayUser();
            checkParameter(tms, "jiraUser", jiraUser);
            String jiraPassword = zahoriProperties.getXrayPassword();
            checkParameter(tms, "jiraPassword", jiraPassword);
            xray = new XrayRestClient(jiraUrl, jiraUser, jiraPassword);
        }

        // Create ALM instance
        if (("ALM").equalsIgnoreCase(tms)) {
            // pingTMS(zahoriProperties.getALMUrl());
            alm = new ALMRestClient(zahoriProperties.getALMUrl(), zahoriProperties.getALMDomain(), zahoriProperties.getALMProject());
            logInfo("Connecting to " + tms + ": " + zahoriProperties.getALMUrl());
            alm.loginWithBasicAuth(zahoriProperties.getALMUsername(), zahoriProperties.getALMPassword());
        }

        // 1. CREATE NEW TEST RUN

        // 1.a) TestLink
        int executionId = updateResultTestLink(tmsTestCaseId, passed);

        // 1.b) ALM
        Run runALM = updateResultALM(tmsTestSetId, tmsTestCaseId, testCaseName, passed, testDuration);

        // 1.c) XRAY
        List<String> evidencesFiles = getEvidencesFilesToUpload(passed);
        if (updateResultXray(tmsTestPlanId, tmsTestExecId, tmsTestCaseId, passed, evidencesFiles) < 0) {
            logInfo("Cannot update test result on JIRA XRAY");
        }

        // 2. UPLOAD EVIDENCES TO TEST RUN

        // 2.a) When test is PASSED
        if (passed) {
            // Doc
            if (zahoriProperties.isDocGenerationEnabled() && zahoriProperties.uploadEvidenceDocWhenPassed()) {
                for (String docFileName : evidences.getDocFileNames()) {
                    uploadAttachmentTestLink(Integer.valueOf(executionId), evidences.getEvidencesPath() + docFileName, testCaseName);
                    uploadAttachmentALM(runALM, evidences.getEvidencesPath() + docFileName);
                }
            }
            // Video
            if (zahoriProperties.isVideoGenerationEnabledWhenPassed() && zahoriProperties.uploadEvidenceVideoWhenPassed()) {
                uploadAttachmentTestLink(Integer.valueOf(executionId), evidences.getEvidencesPath() + evidences.getVideoFileName(), testCaseName);
                uploadAttachmentALM(runALM, evidences.getEvidencesPath() + evidences.getVideoFileName());
            }
            // Log file (is the last attachment to be uploaded in order to
            // register logs with TMS)
            if (zahoriProperties.isLogFileGenerationEnabled() && zahoriProperties.uploadEvidenceLogFileWhenPassed()) {
                for (String logFileName : evidences.getLogFileNames()) {
                    uploadAttachmentTestLink(Integer.valueOf(executionId), evidences.getEvidencesPath() + logFileName, testCaseName);
                    uploadAttachmentALM(runALM, evidences.getEvidencesPath() + logFileName);
                }
            }
            // HarLog
            if (zahoriProperties.isHarLogFileEnabled() && zahoriProperties.uploadEvidenceHarLogFileWhenPassed()) {
                uploadAttachmentTestLink(Integer.valueOf(executionId), evidences.getEvidencesPath() + evidences.getHarLogFileName(), testCaseName);
                uploadAttachmentALM(runALM, evidences.getEvidencesPath() + evidences.getHarLogFileName());
            }
        }

        // 2.b) When test is FAILED
        if (!passed) {
            // Doc
            if (zahoriProperties.isDocGenerationEnabled() && zahoriProperties.uploadEvidenceDocWhenFailed()) {
                for (String docFileName : evidences.getDocFileNames()) {
                    uploadAttachmentTestLink(Integer.valueOf(executionId), evidences.getEvidencesPath() + docFileName, testCaseName);
                    uploadAttachmentALM(runALM, evidences.getEvidencesPath() + docFileName);
                }
            }
            // Video
            if (zahoriProperties.isVideoGenerationEnabledWhenFailed() && zahoriProperties.uploadEvidenceVideoWhenFailed()) {
                uploadAttachmentTestLink(Integer.valueOf(executionId), evidences.getEvidencesPath() + evidences.getVideoFileName(), testCaseName);
                uploadAttachmentALM(runALM, evidences.getEvidencesPath() + evidences.getVideoFileName());
            }
            // Log file (is the last attachment to be uploaded in order to
            // register logs with TMS)
            if (zahoriProperties.isLogFileGenerationEnabled() && zahoriProperties.uploadEvidenceLogFileWhenFailed()) {
                for (String logFileName : evidences.getLogFileNames()) {
                    uploadAttachmentTestLink(Integer.valueOf(executionId), evidences.getEvidencesPath() + logFileName, testCaseName);
                    uploadAttachmentALM(runALM, evidences.getEvidencesPath() + logFileName);
                }
            }
            // HarLog
            if (zahoriProperties.isHarLogFileEnabled() && zahoriProperties.uploadEvidenceHarLogFileWhenFailed()) {
                uploadAttachmentTestLink(Integer.valueOf(executionId), evidences.getEvidencesPath() + evidences.getHarLogFileName(), testCaseName);
                uploadAttachmentALM(runALM, evidences.getEvidencesPath() + evidences.getHarLogFileName());
            }
        }

        // 3. CREATE TEST RUN STEPS
        if (!testSteps.isEmpty()) {

            logInfo("Creating run steps");
            for (List<Step> steps : testSteps) {

                // ALM
                createRunStepALM(runALM, steps);

                // TODO TestLink
            }
        }

    }

    public void setExecutionNotes(String notes) {
        executionNotes = StringUtils.isEmpty(executionNotes) ? notes : executionNotes + "\n" + notes;
    }

    // TestLink - update test run result
    private int updateResultTestLink(String testCaseExternalId, boolean passed) {
        if (testLink != null) {
            if (StringUtils.isBlank(testCaseExternalId)) {
                throw new RuntimeException("Error updating test in TestLink -> testCaseExternalId not provided");
            }
            logInfo("Updating test result: " + (passed ? "PASSED" : "FAILED"));
            return testLink.updateTestResult(testCaseExternalId, (passed ? 'P' : 'F'), executionNotes);
        }
        return 0;
    }

    // TestLink - upload attachment to run
    private void uploadAttachmentTestLink(Integer executionId, String filePath, String title) {
        if ((testLink != null) && (executionId.intValue() > 0)) {
            logInfo("Uploading attachment: " + filePath);
            testLink.uploadExecutionAttachment(executionId, filePath, title);
        }
    }

    // ALM - update test result
    private Run updateResultALM(String testSetId, String testId, String testCaseName, boolean passed, int testDuration) {
        String status = passed ? "Passed" : "Failed";
        Run run = new Run(null, testCaseName, status, testDuration);
        if (alm != null) {

            if (StringUtils.isBlank(testSetId)) {
                throw new RuntimeException("Error updating test in ALM -> testSet not provided");
            }

            if (StringUtils.isBlank(testId)) {
                throw new RuntimeException("Error updating test in ALM -> testId not provided");
            }

            if (StringUtils.isBlank(testCaseName)) {
                throw new RuntimeException("Error updating test in ALM -> testCaseName not provided");
            }

            logInfo("Creating new run with status: " + (passed ? "Passed" : "Failed"));
            run = alm.createRun(run, testSetId, testId);
        }
        return run;
    }

    // ALM - upload attachment to run
    private void uploadAttachmentALM(Run run, String filePath) {
        if ((alm != null) && (run != null)) {
            logInfo("Uploading attachment: " + filePath);
            alm.createAttachment(EntityType.RUNS, run.getId(), new File(filePath));
        }
    }

    private void createRunStepALM(Run run, List<Step> steps) {
        if ((alm != null) && (run != null) && (steps != null)) {

            StringBuilder stepDescription = new StringBuilder();
            List<File> attachments = new ArrayList<>();
            for (Step partialStep : steps) {
                // description
                stepDescription.append(messages.getMessageInFirstLanguage(partialStep.getDescription(), partialStep.getDescriptionArgs()));
                stepDescription.append("\n\n");

                // attachment
                if ((partialStep.getAttachments() != null) && !partialStep.getAttachments().isEmpty()) {
                    attachments.add(partialStep.getAttachments().get(0));
                }
            }

            Step lastPartialStep = steps.get(steps.size() - 1);
            alm.createRunStep(run.getId(), "Step " + lastPartialStep.getName(), lastPartialStep.getStatus(), stepDescription.toString(), "", "",
                    lastPartialStep.getExecutionTime(), attachments);
        }
    }

    private void pingTMS(String urlTMS) {
        try {
            URL url = new URL(urlTMS);
            InetAddress inet = InetAddresses.forString(url.getHost());

            boolean isReachable = inet.isReachable(10000);
            if (!isReachable) {
                throw new RuntimeException("Error stablishing connection to TMS: host (" + url.getHost() + ") not reachable");
            }
        } catch (IOException e) {
            throw new RuntimeException("Error stablishing connection to TMS " + urlTMS + ": " + e.getLocalizedMessage());

        }
    }

    private void logInfo(String text) {
        LOG.info(text);
        evidences.insertTextInLogFile(text);
    }

    private TestLink getTestLinkInstance(String browserName, String platformName) {
        if (zahoriProperties.isTestLinkUsingPlatform()) {
            String buildName = zahoriProperties.getTestLinkBuildName();
            String browser = StringUtils.capitalize(browserName.toLowerCase());
            if (StringUtils.equalsIgnoreCase("Android", platformName) || (StringUtils.equalsIgnoreCase("iOS", platformName))) {
                browser = "Mobile " + browser;
            }
            return StringUtils.isEmpty(buildName)
                    ? new TestLink(zahoriProperties.getTestLinkUrl(), zahoriProperties.getTestLinkApiKey(), zahoriProperties.getTestLinkProjectName(),
                            zahoriProperties.getTestLinkPlanName(), browser)
                    : new TestLink(zahoriProperties.getTestLinkUrl(), zahoriProperties.getTestLinkApiKey(), zahoriProperties.getTestLinkProjectName(),
                            zahoriProperties.getTestLinkPlanName(), buildName, browser);
        } else {
            return new TestLink(zahoriProperties.getTestLinkUrl(), zahoriProperties.getTestLinkApiKey(), zahoriProperties.getTestLinkProjectName(),
                    zahoriProperties.getTestLinkPlanName());
        }
    }

    // JIRA XRAY - update test run result
    private int updateResultXray(String testPlanId, String testExecutionId, String testCaseId, boolean passed, List<String> evidencesFiles) {
        if (xray != null) {
            checkParameter("XRAY", "testPlanId", testPlanId);
            checkParameter("XRAY", "testCaseId", testCaseId);
            String newTExec = testExecutionId;
            if (StringUtils.isEmpty(newTExec) || !xray.existsTEOnJira(newTExec)) {
                newTExec = processTestExecutionOnXray();
            }

            if (!StringUtils.isEmpty(newTExec)) {
                xray.associateTC2TE(newTExec, testCaseId);
                logInfo("Associated Test Case " + testCaseId + " with Test Execution " + newTExec);

                xray.associateTE2TP(newTExec, testPlanId);
                logInfo("Associated Test Execution " + newTExec + " with Test Plan " + testPlanId);

                xray.associateTC2TP(testCaseId, testPlanId);
                logInfo("Associated Test Case " + testCaseId + " with Test Plan " + testPlanId);

                logInfo("Updating test result: " + (passed ? "PASSED" : "FAILED"));
                return xray.updateTestResult(newTExec, testCaseId, (passed ? "PASS" : "FAIL"), executionNotes, date, evidencesFiles);
            } else {
                return -1;
            }
        }
        return 0;
    }

    private String processTestExecutionOnXray() {
        String summary = getTESummary(zahoriProperties.getXrayTestExecSummary());
        String environment = System.getProperty("entorno");
        environment = StringUtils.isEmpty(environment) ? "UNDEFINED ENV" : environment;
        summary = StringUtils.replace(summary, "{environment}", environment);
        String testExecutionId = xray.look4TE(summary);
        if (StringUtils.isEmpty(testExecutionId)) {
            testExecutionId = xray.createTestExecution(zahoriProperties.getXrayProjectKey(), summary, zahoriProperties.getXrayTestExecDescription(),
                    zahoriProperties.getXrayTestExecPriorityId(), zahoriProperties.getXrayTestExecLabels(), zahoriProperties.getXrayTestExecComponents(),
                    zahoriProperties.getXrayTestExecAssignee());
            if (StringUtils.isEmpty(testExecutionId)) {
                logInfo("An error has been ocurred when trying to create new Test Execution on JIRA");
            } else {
                logInfo("Created new Test Execution on JIRA: " + testExecutionId);
            }
        } else {
            logInfo("Recovered Test Execution from JIRA: " + testExecutionId);
        }
        return testExecutionId;
    }

    public static String getTESummary(String base) {
        String result = base;
        List<String> propertiesToSearch = new ArrayList<>();
        Pattern pattern = Pattern.compile("\\{[\\w\\d]+\\}");
        Matcher matcher = pattern.matcher(base);
        while (matcher.find()) {
            String match = matcher.group();
            match = StringUtils.replace(match, "{", StringUtils.EMPTY);
            match = StringUtils.replace(match, "}", StringUtils.EMPTY);
            propertiesToSearch.add(match);
        }

        for (String currentProperty : propertiesToSearch) {
            String propValue = System.getProperty(currentProperty);
            propValue = StringUtils.isEmpty(propValue) ? "UNDEFINED" : propValue;
            result = StringUtils.replace(result, "{" + currentProperty + "}", propValue);
        }

        return result;
    }

    // JIRA XRAY - upload attachment to run
    private List<String> getEvidencesFilesToUpload(boolean passed) {
        List<String> evidencesFiles = new ArrayList<>();
        boolean uploadDoc = passed ? (zahoriProperties.isDocGenerationEnabled() && zahoriProperties.uploadEvidenceDocWhenPassed())
                : (zahoriProperties.isDocGenerationEnabled() && zahoriProperties.uploadEvidenceDocWhenFailed());
        boolean uploadVideo = passed ? (zahoriProperties.isVideoGenerationEnabledWhenPassed() && zahoriProperties.uploadEvidenceVideoWhenPassed())
                : (zahoriProperties.isVideoGenerationEnabledWhenFailed() && zahoriProperties.uploadEvidenceVideoWhenFailed());
        boolean uploadLog = passed ? (zahoriProperties.isLogFileGenerationEnabled() && zahoriProperties.uploadEvidenceLogFileWhenPassed())
                : (zahoriProperties.isLogFileGenerationEnabled() && zahoriProperties.uploadEvidenceLogFileWhenFailed());
        boolean uploadHarLog = passed ? (zahoriProperties.isHarLogFileEnabled() && zahoriProperties.uploadEvidenceHarLogFileWhenPassed())
                : (zahoriProperties.isHarLogFileEnabled() && zahoriProperties.uploadEvidenceHarLogFileWhenFailed());

        if (uploadDoc) {
            for (String docFileName : evidences.getDocFileNames())
                evidencesFiles.add(evidences.getEvidencesPath() + docFileName);
        }

        if (uploadVideo) {
            evidencesFiles.add(evidences.getEvidencesPath() + evidences.getVideoFileName());
        }

        if (uploadLog) {
            for (String logFileName : evidences.getLogFileNames())
                evidencesFiles.add(evidences.getEvidencesPath() + logFileName);
        }

        if (uploadHarLog) {
            evidencesFiles.add(evidences.getEvidencesPath() + evidences.getHarLogFileName());
        }

        return evidencesFiles;
    }

    private void checkParameter(String tms, String parameterName, String parameter) {
        if (StringUtils.isBlank(parameter)) {
            throw new RuntimeException("Error updating test in " + parameter + " -> " + parameterName + " not provided");
        }
    }

}
