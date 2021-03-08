package io.zahori.framework.core;

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
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.io.UnsupportedEncodingException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLConnection;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import io.zahori.framework.driver.browserfactory.Browsers;
import io.zahori.framework.exception.ZahoriException;
import io.zahori.model.process.CaseExecution;
import io.zahori.model.process.ProcessRegistration;
import io.zahori.model.process.Step;
import io.zahori.model.process.Test;

public abstract class BaseProcess {

    private static final Logger LOG = LoggerFactory.getLogger(BaseProcess.class);
    public static final String BASE_URL = "/";
    public static final String RUN_URL = "run";
    public static final String ZAHORI_SERVER_SERVICE_NAME = "server";
    public static final String ZAHORI_SERVER_HEALTHCHECK_URL = "/healthcheck";
    public static final String ZAHORI_SERVER_PROCESS_REGISTRATION_URL = "/process";
    public static final int SECONDS_WAIT_FOR_SERVER = 5;
    public static final int MAX_RETRIES_WAIT_FOR_SERVER = 7;

    protected int retries;

    protected String healthcheck(String processName) {
        return processName + " is up!";
    }

    protected CaseExecution runProcess(CaseExecution caseExecution, ProcessRegistration processRegistration, String serverUrl, String remote,
            String remoteUrl) {
        String id = "[" + caseExecution.getCas().getName() + "-" + caseExecution.getBrowser().getBrowserName() + "]";
        LOG.info("======> Start {}", id);

        TestContext testContext = null;
        try {
            testContext = setup(caseExecution, processRegistration, remote, remoteUrl);
            process(testContext, caseExecution);

            return updateCaseExecution(testContext, caseExecution, serverUrl, processRegistration);
        } catch (Exception e) {
            return updateCaseExecution(testContext, caseExecution, serverUrl, processRegistration, e);
        } finally {
            teardown(testContext, caseExecution);
        }
    }

    private TestContext setup(CaseExecution caseExecution, ProcessRegistration processRegistration, String remote, String remoteUrl) {
        String id = "[" + caseExecution.getCas().getName() + "-" + caseExecution.getBrowser().getBrowserName() + "] ";
        LOG.info("==== Setup {}", id);

        TestContext testContext = null;
        try {
            testContext = new TestContext();
            testContext.processRegistration = processRegistration;
            testContext.testCaseName = caseExecution.getCas().getName();
            testContext.caseExecutionId = String.valueOf(caseExecution.getCaseExecutionId());
            testContext.platform = "LINUX"; // TODO
            testContext.bits = "32"; // TODO
            testContext.browserName = caseExecution.getBrowser().getBrowserName().toUpperCase();
            testContext.version = caseExecution.getBrowser().getDefaultVersion();
            testContext.remote = remote;
            testContext.remoteUrl = remoteUrl;
            testContext.constructor();
            testContext.startChronometer();
            // testContext.moveMouseToUpperLeftCorner();
            testContext.startVideo();

            retries = 0; // TODO

            return testContext;
        } catch (Exception e) {
            e.printStackTrace();
            LOG.error("==== Setup error {}: {}", id, e.getMessage());
            throw new ZahoriException(caseExecution.getCas().getName(), "Error on process setup: " + e.getMessage());
        }
    }

    private void process(TestContext testContext, CaseExecution caseExecution) {
        String id = "[" + caseExecution.getCas().getName() + "-" + caseExecution.getBrowser().getBrowserName() + "]";
        LOG.info("==== Process {}", id);
        try {
            run(testContext, caseExecution);
        } catch (final Exception e) {
            LOG.error("==== Process error {}: {}", id, e.getMessage());
            if (retries < testContext.getMaxRetries()) {
                retries++;
                testContext.logStepPassed(id + "Processing error: " + e.getMessage() + "\nTest retries are enabled. Relaunching test (" + retries + " retry of "
                        + testContext.getMaxRetries() + " max retries).");
                if (!StringUtils.equalsIgnoreCase(String.valueOf(Browsers.NULLBROWSER), testContext.browserName)) {
                    testContext.getBrowser().closeWithoutProcessKill();
                }
                process(testContext, caseExecution);
            } else {
                testContext.failTest(e);
            }
        } finally {
            testContext.stopChronometer();
            testContext.writeSteps2Json();
            testContext.logInfo("Test Finished: " + testContext.testCaseName);
            testContext.logInfo("Test duration: " + testContext.getTestDuration() + " seconds");
            testContext.setExecutionNotes("Test duration: " + testContext.getTestDuration() + " seconds");
            testContext.reportTestResult();
        }
    }

    protected abstract void run(TestContext testContext, CaseExecution caseExecution);

    private void teardown(TestContext testContext, CaseExecution caseExecution) {
        String id = "[" + caseExecution.getCas().getName() + "-" + caseExecution.getBrowser().getBrowserName() + "]";
        LOG.info("==== Teardown {}", id);
        try {
            if (testContext != null) {
                testContext.stopVideo();
                if (testContext.getBrowser() != null) {
                    testContext.getBrowser().close();
                }
            }

        } catch (Exception e) {
            LOG.error("==== Teardown error {}: {}", id, e.getMessage());
            throw new ZahoriException(caseExecution.getCas().getName(), "Error on process teardown: " + e.getMessage());
        }
    }

    protected void pause(int seconds) {
        try {
            Thread.sleep(seconds * 1000);
        } catch (InterruptedException e) {
            LOG.error("Error while pausing: {}", e.getMessage());
        }
    }

    private CaseExecution updateCaseExecution(TestContext testContext, CaseExecution caseExecution, String serverUrl, ProcessRegistration processRegistration,
            Exception e) {
        if (testContext != null) {
            testContext.logError(e.getMessage());
        }

        caseExecution.setNotes(e.getMessage());
        caseExecution.setStatus("FAILED");
        return updateCaseExecution(testContext, caseExecution, serverUrl, processRegistration);
    }

    private CaseExecution updateCaseExecution(TestContext testContext, CaseExecution caseExecution, String serverUrl, ProcessRegistration processRegistration) {
        if (testContext == null) {
            return caseExecution;
        }

        Test test = parseStringToTest(getResultsJson(testContext));

        caseExecution.setStatus(test.getTestStatus());
        caseExecution.setDate(test.getExecutionDate());
        caseExecution.setNotes(test.getExecutionNotes());
        caseExecution.setDurationSeconds(test.getDurationSeconds());

        String evidencePath = getEvidencePath(testContext, processRegistration);

        // Set log file path
        if (!testContext.evidences.getLogFileNames().isEmpty()) {
            caseExecution.setLog(getPathIfFileExists(testContext, evidencePath + testContext.evidences.getLogFileNames().get(0)));
        }

        // Set doc file path
        if (!testContext.evidences.getDocFileNames().isEmpty()) {
            caseExecution.setDoc(getPathIfFileExists(testContext, evidencePath + testContext.evidences.getDocFileNames().get(0)));
        }

        // Set video file path
        caseExecution.setVideo(getPathIfFileExists(testContext, evidencePath + testContext.evidences.getVideoFileName()));

        // Set har file path
        caseExecution.setHar(getPathIfFileExists(testContext, evidencePath + testContext.evidences.getHarLogFileName()));

        // Convert attachments absolut paths to relative paths
        for (Step step : test.getSteps()) {
            updateAttachmentUrl(step, evidencePath);

            // Substeps
            for (Step substep : step.getSubSteps()) {
                updateAttachmentUrl(substep, evidencePath);
            }
        }
        caseExecution.setSteps(getStepsString(test));

        uploadEvidences(testContext, caseExecution, test, serverUrl + "/evidence/?path=" + encodeUrl(evidencePath));
        return caseExecution;
    }

    private String getPathIfFileExists(TestContext testContext, String filePath) {
        Path file = Paths.get(testContext.zahoriProperties.getResultsDir() + filePath);
        return Files.exists(file) ? filePath : "";
    }

    private String getResultsJson(TestContext testContext) {
        try {
            if (testContext != null) {
                Path filePath = Path.of(testContext.getEvidencesFolder() + TestContext.JSON_REPORT);
                return Files.readString(filePath);
            }
            return "Error reading file " + TestContext.JSON_REPORT + ": testContext is null";
        } catch (IOException e) {
            throw new RuntimeException("Error reading file " + TestContext.JSON_REPORT);
        }
    }

    private String getEvidencePath(TestContext testContext, ProcessRegistration processRegistration) {
        return processRegistration.getClientId() + File.separator + processRegistration.getTeamId() + File.separator + processRegistration.getName()
                + File.separator + testContext.testCaseName + File.separator + testContext.platform + File.separator
                + StringUtils.upperCase(testContext.browserName) + File.separator + testContext.testId + File.separator;
    }

    private void updateAttachmentUrl(Step step, String artifactRelativePath) {
        if (!step.getAttachments().isEmpty()) {
            String attachmentUrl = step.getAttachments().get(0);
            Path path = Paths.get(attachmentUrl);
            Path fileName = path.getFileName();
            step.getAttachments().clear();
            step.addAttachment(artifactRelativePath + fileName);
        }
    }

    private void uploadEvidences(TestContext testContext, CaseExecution caseExecution, Test test, String url) {
        // logs
        uploadEvidenceFile(testContext.zahoriProperties.getResultsDir(), caseExecution.getLog(), url);

        // docs
        uploadEvidenceFile(testContext.zahoriProperties.getResultsDir(), caseExecution.getDoc(), url);

        // video
        uploadEvidenceFile(testContext.zahoriProperties.getResultsDir(), caseExecution.getVideo(), url);

        // har
        uploadEvidenceFile(testContext.zahoriProperties.getResultsDir(), caseExecution.getHar(), url);

        // step screenshots
        uploadStepsAttachments(testContext, test, url);
    }

    private void uploadEvidenceFile(String resultsDir, String filePath, String url) {
        if (StringUtils.isBlank(filePath)) {
            return;
        }

        Path evidenceFile = Paths.get(resultsDir + filePath);
        if (Files.exists(evidenceFile)) {
            uploadEvidence(url, evidenceFile.toString());
        }
    }

    private void uploadStepsAttachments(TestContext testContext, Test test, String url) {
        for (Step step : test.getSteps()) {
            uploadStepAttachments(testContext, step, url);
            // Substeps
            for (Step substep : step.getSubSteps()) {
                uploadStepAttachments(testContext, substep, url);
            }
        }
    }

    private void uploadStepAttachments(TestContext testContext, Step step, String url) {
        if (!step.getAttachments().isEmpty()) {
            for (String attachment : step.getAttachments()) {
                uploadEvidenceFile(testContext.zahoriProperties.getResultsDir(), attachment, url);
            }
        }
    }

    private Test parseStringToTest(String result) {
        ObjectMapper mapper = new ObjectMapper();
        try {
            return mapper.readValue(result, Test.class);
        } catch (Exception e) {
            throw new RuntimeException("Error parsing content of file " + TestContext.JSON_REPORT + ": " + e.getMessage());
        }
    }

    private String getStepsString(Test test) {
        ObjectMapper mapper = new ObjectMapper();
        try {
            return mapper.writeValueAsString(test.getSteps());
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Error parsing content of file " + TestContext.JSON_REPORT + ": " + e.getMessage());
        }
    }

    private void uploadEvidence(String url, String filePath) {
        String charset = "UTF-8";
        String boundary = Long.toHexString(System.currentTimeMillis()); // Just generate some unique random value.
        String CRLF = "\r\n"; // Line separator required by multipart/form-data.
        File file = new File(filePath);

        URLConnection connection = null;
        try {
            connection = new URL(url).openConnection();
            connection.setDoOutput(true);
            connection.setRequestProperty("Content-Type", "multipart/form-data; boundary=" + boundary);
        } catch (Exception e) {
            LOG.error("Error uploading evidence file '{}': {}", filePath, e.getMessage());
        }

        try (OutputStream output = connection.getOutputStream(); PrintWriter writer = new PrintWriter(new OutputStreamWriter(output, charset), true);) {
            // Send binary file.
            writer.append("--" + boundary).append(CRLF);
            writer.append("Content-Disposition: form-data; name=\"file\"; filename=\"" + file + "\"").append(CRLF);
            writer.append("Content-Type: " + URLConnection.guessContentTypeFromName(file.getName())).append(CRLF);
            writer.append("Content-Transfer-Encoding: binary").append(CRLF);
            writer.append(CRLF).flush();
            Files.copy(file.toPath(), output);
            output.flush(); // Important before continuing with writer!
            writer.append(CRLF).flush(); // CRLF is important! It indicates end of boundary.

            // End of multipart/form-data.
            writer.append("--" + boundary + "--").append(CRLF).flush();

            int responseCode = ((HttpURLConnection) connection).getResponseCode();
            LOG.info("Upload evidence file '{}' -> {}", file.getName(), responseCode); // Should be 200
        } catch (Exception e) {
            LOG.error("Error uploading evidence file '{}': {}", filePath, e.getMessage());
        }
    }

    private String encodeUrl(String value) {
        try {
            return URLEncoder.encode(value, StandardCharsets.UTF_8.toString());
        } catch (UnsupportedEncodingException e) {
            LOG.error("Error url encoding value '{}': {}", value, e.getMessage());
            return value;
        }
    }

}
