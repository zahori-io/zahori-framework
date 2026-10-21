package io.zahori.framework.core;

/*-
 * #%L
 * zahori-framework
 * $Id:$
 * $HeadURL:$
 * %%
 * Copyright (C) 2021 - 2023 PANEL SISTEMAS INFORMATICOS,S.L
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
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import static io.zahori.framework.core.PageElement.ERROR;
import io.zahori.framework.exception.ZahoriException;
import io.zahori.framework.exception.ZahoriPassedException;
import io.zahori.framework.tms.TmsBulkService;
import io.zahori.model.process.CaseExecution;
import io.zahori.model.process.ProcessRegistration;
import io.zahori.model.process.Step;
import io.zahori.model.process.Test;
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
import java.util.List;
import java.util.stream.Collectors;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.Properties;
import java.util.Random;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.FilenameUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public abstract class BaseProcess {

    private static final Logger LOG = LogManager.getLogger(BaseProcess.class);

    /** Lo que no llego al server no se borra en local: es la unica copia que queda. */
    private static final Set<String> evidencePathsWithFailedUpload = ConcurrentHashMap.newKeySet();

    public static final String BASE_URL = "/";
    public static final String RUN_URL = "run";
    public static final String ZAHORI_SERVER_SERVICE_NAME = "server";
    public static final String ZAHORI_SERVER_HEALTHCHECK_URL = "/healthcheck";
    public static final String ZAHORI_SERVER_PROCESS_REGISTRATION_URL = "/process";
    public static final int SECONDS_WAIT_FOR_SERVER = 5;
    public static final int MAX_RETRIES_WAIT_FOR_SERVER = 10;
    public static final String DEFAULT_BIT_DEPTH = "x24";
    public static final String DEFAULT_SCREEN_RESOLUTION = "1920x1080" + DEFAULT_BIT_DEPTH;

    protected String healthcheck(String processName) {
        return processName + " is up!";
    }

    private String getCaseExcutionDetails(CaseExecution caseExecution) {
        String id = caseExecution.getCas().getName() + "-" + caseExecution.getBrowser().getBrowserName() + "-" + caseExecution.getScreenResolution();
        return "(" + caseExecution.getCaseExecutionId() + "): " + id;
    }

    protected CaseExecution runProcess(CaseExecution caseExecution, ProcessRegistration processRegistration, String serverUrl, String remote,
            String remoteUrl) {
        LOG.info("======> Start {}", getCaseExcutionDetails(caseExecution));

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
        LOG.info("==== Setup {}", getCaseExcutionDetails(caseExecution));

        TestContext testContext = null;
        try {
            testContext = new TestContext(caseExecution, processRegistration);

            testContext.remote = remote;
            testContext.remoteUrl = remoteUrl;

            testContext.constructor();
            testContext.startRemoteTunnel();
            testContext.createDriver();

            testContext.startChronometer();
            // testContext.moveMouseToUpperLeftCorner();
            testContext.startVideo();

            return testContext;
        } catch (Exception e) {
            e.printStackTrace();
            throw new ZahoriException("", "Error initializing case: " + e.getMessage());
        }
    }

    private int randomNumber() {
        Random rnd = new Random();
        return 100 + rnd.nextInt(900);
    }

    private void process(TestContext testContext, CaseExecution caseExecution) {
        LOG.info("==== Process {}", getCaseExcutionDetails(caseExecution));

        try {
            run(testContext, caseExecution);
            setRetriesInExecutionNotes(testContext);
        } catch (ZahoriPassedException passedException) {
            LOG.info("==== Process passed {}: {}", getCaseExcutionDetails(caseExecution), passedException.getMessage());
        } catch (final Exception e) {
            manageException(testContext, caseExecution, e);
        } finally {
            testContext.stopChronometer();
            testContext.writeSteps2Json();
            testContext.logInfo("Test Finished: " + testContext.testCaseName);
            testContext.logInfo("Test duration: " + testContext.getTestDuration() + " seconds");
            testContext.reportTestResult();
        }
    }

    private void manageException(TestContext testContext, CaseExecution caseExecution, Exception e) {
        LOG.error("==== Process error {}: {}", getCaseExcutionDetails(caseExecution), e.getMessage());
        if (testContext.retries < testContext.getMaxRetries()) {
            testContext.retries++;
            testContext.failCause = StringUtils.EMPTY;
            // testContext.resetExecutionNotes();
            testContext.setExecutionNotes(" --> Retry " + testContext.retries + ": ");

            if (!(e instanceof ZahoriException)) {
                testContext.logPartialStepFailedWithScreenshot(e.getMessage());
            }

            testContext.logStepPassed("Case failed! but retries are enabled, rerunning case... (Retry " + testContext.retries + " of " + testContext.getMaxRetries() + ")");

            if (testContext.getBrowser() != null) {
                testContext.getBrowser().close();
            }

            testContext.createDriver();
            process(testContext, caseExecution);
        } else {
            setRetriesInExecutionNotes(testContext);
            testContext.failTest(e);
        }
    }

    private void setRetriesInExecutionNotes(TestContext testContext) {
        if (testContext.retries > 0) {
            String executionNotes = testContext.getExecutionNotes();
            testContext.resetExecutionNotes();
            testContext.setExecutionNotes(testContext.retries + " retries: " + executionNotes);
        }
    }

    protected abstract void run(TestContext testContext, CaseExecution caseExecution);

    private void teardown(TestContext testContext, CaseExecution caseExecution) {
        LOG.info("==== Teardown {}", getCaseExcutionDetails(caseExecution));

        try {
            if (testContext != null) {
                testContext.stopVideo();

                if (testContext.getBrowser() != null) {
                    testContext.logBrowserStackStatus(); // Log test result on BrowserStack
                    testContext.stopRemoteTunnel(); // Stop BrowserStack local tunnel
                    testContext.getBrowser().close();
                }
            }
        } catch (Exception e) {
            LOG.error("Error on process teardown: {}", e.getMessage());
        } finally {
            if (testContext != null) {
                // do asynchronously:
                new Thread(() -> {
                    TmsBulkService.addCaseExecution(caseExecution);
                    uploadResultsToTms(testContext);

                    if (TmsBulkService.isExecutionCompleted(caseExecution.getExecutionId())) {
                        List<CaseExecution> caseExecutions = TmsBulkService.getCaseExecutions(caseExecution.getExecutionId());
                        deleteEvidenceDirectory(caseExecutions);
                        TmsBulkService.removeExecution(caseExecution.getExecutionId());
                    }
                }).start();
            }
        }
    }

    private void uploadResultsToTms(TestContext testContext) {
        try {
            testContext.uploadResultsToTms();
        } catch (Exception ex) {
            LOG.error("Error uploading case results to {}: {}", testContext.getTmsName(), ex.getMessage());
            // ex.printStackTrace();
        }
    }

    private void deleteEvidenceDirectory(List<CaseExecution> caseExecutions) {
        if (caseExecutions.isEmpty()) {
            return;
        }

        for (CaseExecution caseExecution : caseExecutions) {
            if (evidencePathsWithFailedUpload.remove(caseExecution.getEvidencesPath())) {
                LOG.warn("Evidences kept in '{}': some file did not reach the server", caseExecution.getEvidencesPath());
                continue;
            }
            try {
                FileUtils.deleteDirectory(new File(caseExecution.getEvidencesPath()));
            } catch (Exception ex) {
                LOG.error("Error deleting evidence directory: {}", ex.getMessage());
            }
        }
    }

    protected void pause(int seconds) {
        try {
            Thread.sleep((seconds * 1000));
        } catch (InterruptedException e) {
            LOG.error("Error while pausing: {}", e.getMessage());
            Thread.currentThread().interrupt();
        }
    }

    private CaseExecution updateCaseExecution(TestContext testContext, CaseExecution caseExecution, String serverUrl, ProcessRegistration processRegistration,
            Exception e) {
        if (testContext != null) {
            testContext.logError(e.getMessage());
        }

        caseExecution.setNotes(getErrorMessage(e));
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
        caseExecution.setDateTimestamp(System.currentTimeMillis());
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
        testContext.stopBrowserMobProxy();
        caseExecution.setHar(getPathIfFileExists(testContext, evidencePath + testContext.evidences.getHarLogFileName()));

        // Convert step attachments absolut paths to relative paths
        List<Step> steps = test.getSteps();
        updateStepsAttachmentUrls(steps, evidencePath);
        caseExecution.setSteps(getStepsString(steps));

        String uploadUrl = serverUrl + "/evidence/?path=" + encodeUrl(evidencePath);
        boolean allUploaded = uploadEvidences(testContext, caseExecution, test, uploadUrl);
        allUploaded &= uploadAttachments(testContext.getAttachments(), uploadUrl);
        if (!allUploaded) {
            evidencePathsWithFailedUpload.add(caseExecution.getEvidencesPath());
        }
        setCaseExecutionAttachments(caseExecution, testContext.getAttachments(), evidencePath);

        return caseExecution;
    }

    private void setCaseExecutionAttachments(CaseExecution caseExecution, List<String> attachments, String evidencePath) {
        for (String attachment : attachments) {
            Path path = Paths.get(attachment);
            caseExecution.addAttachment(evidencePath + path.getFileName());
        }
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
        final String pathSeparator = "/";
        return processRegistration.getClientId() + pathSeparator + processRegistration.getTeamId() + pathSeparator + processRegistration.getName()
                + pathSeparator + testContext.testCaseName + pathSeparator + testContext.platform + pathSeparator
                + StringUtils.upperCase(testContext.browserName) + pathSeparator + testContext.resolution + pathSeparator + testContext.testId + pathSeparator;
    }

    /**
     * Pasos y subpasos: un adjunto en un subpaso se sube igual que uno de primer nivel, y
     * olvidarlo dejaria evidencia generada y sin subir.
     */
    void updateStepsAttachmentUrls(List<Step> steps, String artifactRelativePath) {
        for (Step step : steps) {
            updateStepAttachmentUrl(step, artifactRelativePath);
            for (Step substep : step.getSubSteps()) {
                updateStepAttachmentUrl(substep, artifactRelativePath);
            }
        }
    }

    // Package-private y no private: es la conversion que perdia adjuntos, y se verifica directamente.
    void updateStepAttachmentUrl(Step step, String artifactRelativePath) {
        if (step.getAttachments().isEmpty()) {
            return;
        }
        // Todos y no sólo el primero: un paso fallido adjunta captura y volcado del DOM, y
        // quedarse con get(0) dejaba el .html en disco, sin subir, hasta que se borraba el
        // directorio de evidencias.
        List<String> relativePaths = step.getAttachments().stream()
                .map(attachment -> artifactRelativePath + Paths.get(attachment).getFileName())
                .collect(Collectors.toList());
        step.getAttachments().clear();
        relativePaths.forEach(step::addAttachment);
    }

    private boolean uploadEvidences(TestContext testContext, CaseExecution caseExecution, Test test, String url) {
        String resultsDir = testContext.zahoriProperties.getResultsDir();
        boolean allUploaded = uploadEvidenceFile(resultsDir, caseExecution.getLog(), url);
        allUploaded &= uploadEvidenceFile(resultsDir, caseExecution.getDoc(), url);
        allUploaded &= uploadEvidenceFile(resultsDir, caseExecution.getVideo(), url);
        allUploaded &= uploadEvidenceFile(resultsDir, caseExecution.getHar(), url);
        allUploaded &= uploadStepsAttachments(testContext, test, url);
        return allUploaded;
    }

    private boolean uploadEvidenceFile(String resultsDir, String filePath, String url) {
        if (StringUtils.isBlank(filePath)) {
            return true;
        }

        Path evidenceFile = Paths.get(normalizePath(resultsDir + filePath));
        if (!Files.exists(evidenceFile)) {
            return true;
        }
        return uploadEvidence(url, evidenceFile.toString());
    }

    private boolean uploadAttachments(List<String> filePaths, String url) {
        boolean allUploaded = true;
        for (String filePath : filePaths) {
            allUploaded &= uploadEvidenceFile("", filePath, url);
        }
        return allUploaded;
    }

    private boolean uploadStepsAttachments(TestContext testContext, Test test, String url) {
        boolean allUploaded = true;
        for (Step step : test.getSteps()) {
            allUploaded &= uploadStepAttachments(testContext, step, url);
            // Substeps
            for (Step substep : step.getSubSteps()) {
                allUploaded &= uploadStepAttachments(testContext, substep, url);
            }
        }
        return allUploaded;
    }

    private boolean uploadStepAttachments(TestContext testContext, Step step, String url) {
        boolean allUploaded = true;
        for (String attachment : step.getAttachments()) {
            allUploaded &= uploadEvidenceFile(testContext.zahoriProperties.getResultsDir(), attachment, url);
        }
        return allUploaded;
    }

    private Test parseStringToTest(String result) {
        ObjectMapper mapper = new ObjectMapper();
        try {
            return mapper.readValue(result, Test.class);
        } catch (Exception e) {
            throw new RuntimeException("Error parsing content of file " + TestContext.JSON_REPORT + ": " + e.getMessage());
        }
    }

    private String getStepsString(List<Step> steps) {
        ObjectMapper mapper = new ObjectMapper();
        try {
            return mapper.writeValueAsString(steps);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Error parsing content of file " + TestContext.JSON_REPORT + ": " + e.getMessage());
        }
    }

    private boolean uploadEvidence(String url, String filePath) {
        String charset = "UTF-8";
        String boundary = Long.toHexString(System.currentTimeMillis()); // Just generate some unique random value.
        String crlf = "\r\n"; // Line separator required by multipart/form-data.
        File file = new File(filePath);

        URLConnection connection = null;
        try {
            connection = new URL(url).openConnection();
            connection.setDoOutput(true);
            connection.setRequestProperty("Content-Type", "multipart/form-data; boundary=" + boundary);
        } catch (Exception e) {
            LOG.error("Error uploading evidence file '{}': {}", filePath, e.getMessage());
            return false;
        }

        try (OutputStream output = connection.getOutputStream(); PrintWriter writer = new PrintWriter(new OutputStreamWriter(output, charset), true);) {
            // Send binary file.
            writer.append("--" + boundary).append(crlf);
            writer.append("Content-Disposition: form-data; name=\"file\"; filename=\"" + file + "\"").append(crlf);
            writer.append("Content-Type: " + URLConnection.guessContentTypeFromName(file.getName())).append(crlf);
            writer.append("Content-Transfer-Encoding: binary").append(crlf);
            writer.append(crlf).flush();
            Files.copy(file.toPath(), output);
            output.flush(); // Important before continuing with writer!
            writer.append(crlf).flush(); // crlf is important! It indicates end of boundary.

            // End of multipart/form-data.
            writer.append("--" + boundary + "--").append(crlf).flush();

            int responseCode = ((HttpURLConnection) connection).getResponseCode();
            LOG.info("Upload evidence file '{}' -> {}", file.getName(), Integer.valueOf(responseCode)); // Should be 200
            return responseCode >= HttpURLConnection.HTTP_OK && responseCode < HttpURLConnection.HTTP_MULT_CHOICE;
        } catch (Exception e) {
            LOG.error("Error uploading evidence file '{}': {}", filePath, e.getMessage());
            return false;
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

    private String normalizePath(String rawPath) {
        Path path = Paths.get(rawPath);
        Path normalizedPath = path.normalize();
        return FilenameUtils.separatorsToSystem(normalizedPath.toString());
    }

    public static Properties getProperties() {
        Properties properties = new Properties();
        String os = System.getProperty("os.name");
        LOG.info("OS: {}", os);
        if (StringUtils.containsIgnoreCase(os, "linux")) {
            properties.put("eureka.instance.preferIpAddress", "true");
        } else {
            properties.put("eureka.instance.preferIpAddress", "false");
        }
        return properties;
    }

    private String getErrorMessage(Exception exception) {
        return ERROR + removeSeleniumDriverInfo(removeSeleniumSystemInfo(removeSeleniumBuildInfo(removeSeleniumSessionInfo(exception.getMessage()))));
    }

    private String removeSeleniumDriverInfo(String error) {
        return StringUtils.substringBefore(error, "Driver info:");
    }

    private String removeSeleniumSystemInfo(String error) {
        return StringUtils.substringBefore(error, "System info:");
    }

    private String removeSeleniumSessionInfo(String error) {
        return StringUtils.substringBefore(error, "(Session info:");
    }

    private String removeSeleniumBuildInfo(String error) {
        return StringUtils.substringBefore(error, "Build info:");
    }
}
