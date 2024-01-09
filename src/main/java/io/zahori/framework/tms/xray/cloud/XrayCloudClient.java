package io.zahori.framework.tms.xray.cloud;

/*-
 * #%L
 * zahori-framework
 * $Id:$
 * $HeadURL:$
 * %%
 * Copyright (C) 2021 - 2024 PANEL SISTEMAS INFORMATICOS,S.L
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

import com.fasterxml.jackson.databind.ObjectMapper;
import io.zahori.framework.core.TestContext;
import io.zahori.framework.tms.xray.cloud.model.XrayCloudError;
import io.zahori.framework.tms.xray.cloud.model.XrayCloudEvidence;
import io.zahori.framework.tms.xray.cloud.model.XrayCloudReport;
import io.zahori.framework.tms.xray.cloud.model.XrayCloudReportInfo;
import io.zahori.framework.tms.xray.cloud.model.XrayCloudTest;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import org.apache.commons.codec.binary.Base64;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.hc.client5.http.config.RequestConfig;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.core5.util.Timeout;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.http.*;
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory;
import org.springframework.web.client.RestTemplate;

public class XrayCloudClient {

    private static final Logger LOG = LogManager.getLogger(XrayCloudClient.class);

    public static final String XRAY_API_DATEFORMAT = "yyyy-MM-dd'T'HH:mm:ssZ";
    private static final String XRAY_CLOUD_URL = "https://xray.cloud.getxray.app/api/v2";
    private static final String LOGIN_URL = XRAY_CLOUD_URL + "/authenticate";
    private static final String IMPORT_EXECUTION_URL = XRAY_CLOUD_URL + "/import/execution";
    private static final int TIMEOUT = 60;

    private String clientId;
    private String clientSecret;
    private String authToken = "";

    public XrayCloudClient(String clientId, String clientSecret) {
        this.clientId = clientId;
        this.clientSecret = clientSecret;
    }

    public void login(String clientId, String clientSecret) {
        RestTemplate restTemplate = createRestTemplateWithTimeout();
        String authenticationPayload = "{ \"client_id\": \"" + clientId + "\", \"client_secret\": \"" + clientSecret + "\" }";

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<String> requestEntity = new HttpEntity<>(authenticationPayload, headers);

        try {
            ResponseEntity<String> response = restTemplate.postForEntity(LOGIN_URL, requestEntity, String.class);
            String responseBody = response.getBody();

            if (response.getStatusCode() == HttpStatus.OK) {
                authToken = "Bearer " + responseBody.replace("\"", "");
                LOG.info("TMS: Login to Xray Cloud: OK");
            } else {
                ObjectMapper mapper = new ObjectMapper();
                XrayCloudError error = mapper.readValue(responseBody, XrayCloudError.class);
                throw new IOException(error.getError());
            }
        } catch (IOException e) {
            throw new RuntimeException("TMS: Login to Xray Cloud: KO -> " + e.getMessage());
        }
    }

    public void updateTestExecution(String testExecutionId, String testCaseId, boolean passed, String startDateText, List<String> evidences, String comment) {
        XrayCloudReport xrayReport = buildXrayReport(testExecutionId, null, null, testCaseId, passed, startDateText, evidences, comment);
        uploadTestResult(xrayReport);
    }

    public void createTestExecution(String testExecutionSummary, String testCaseId, boolean passed, String startDateText, List<String> evidences, String comment) {
        XrayCloudReport xrayReport = buildXrayReport(null, testExecutionSummary, null, testCaseId, passed, startDateText, evidences, comment);
        uploadTestResult(xrayReport);
    }

    public void createTestExecution(String testExecutionSummary, String testPlanId, String testCaseId, boolean passed, String startDateText, List<String> evidences, String comment) {
        XrayCloudReport xrayReport = buildXrayReport(null, testExecutionSummary, testPlanId, testCaseId, passed, startDateText, evidences, comment);
        uploadTestResult(xrayReport);
    }

    private XrayCloudReport buildXrayReport(String testExecutionId, String testExecutionSummary, String testPlanId, String testCaseId, boolean passed, String startDateText, List<String> evidences, String comment) {
        // Upload test result
        XrayCloudReport testReport = new XrayCloudReport();
        testReport.setTestExecutionKey(testExecutionId);

        // Info
        XrayCloudReportInfo reportInfo = new XrayCloudReportInfo();
        // reportInfo.setProject("XXX");
        reportInfo.setTestPlanKey(testPlanId);
        reportInfo.setSummary(testExecutionSummary);

        ZonedDateTime startDateZoned = LocalDateTime.parse(startDateText, DateTimeFormatter.ofPattern(TestContext.DATE_FORMAT)).atZone(ZoneId.systemDefault());
        String startDate = startDateZoned.format(DateTimeFormatter.ofPattern(XRAY_API_DATEFORMAT));
        String endDate = ZonedDateTime.now().format(DateTimeFormatter.ofPattern(XRAY_API_DATEFORMAT));

        reportInfo.setStartDate(startDate);
        reportInfo.setFinishDate(endDate);
        testReport.setInfo(reportInfo);

        // Test
        XrayCloudTest test = new XrayCloudTest();
        test.setTestKey(testCaseId);
        test.setStatus(passed ? "PASSED" : "FAILED");
        test.setStart(startDate);
        test.setFinish(endDate);
        test.setComment(comment);

        // Test evidences
        if (evidences != null) {
            for (String evidencePath : evidences) {
                try {
                    XrayCloudEvidence testEvidence = new XrayCloudEvidence();
                    testEvidence.setData(encodeToBase64(evidencePath));
                    testEvidence.setFilename(String.valueOf(Paths.get(evidencePath).getFileName()));
                    //testEvidence.setContentType("image/png");
                    test.addEvidence(testEvidence);
                } catch (IOException e) {
                    LOG.warn("TMS: Error encoding (base64) evidence file: {}", e.getMessage());
                }
            }
        }

        // TODO add other attachments
        testReport.addTest(test);

        return testReport;
    }

    public static String encodeToBase64(String fileName) throws IOException {
        File file = new File(fileName);
        byte[] encoded = Base64.encodeBase64(FileUtils.readFileToByteArray(file));
        return new String(encoded, StandardCharsets.US_ASCII);
    }

    public void uploadTestResult(XrayCloudReport testReport) {
        if (StringUtils.isBlank(authToken)) {
            login(clientId, clientSecret);
        }

        RestTemplate restTemplate = createRestTemplateWithTimeout();
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("Authorization", authToken);
        HttpEntity<XrayCloudReport> requestEntity = new HttpEntity<>(testReport, headers);

        try {
            ResponseEntity<String> response = restTemplate.postForEntity(IMPORT_EXECUTION_URL, requestEntity, String.class);
            if (response.getStatusCode() == HttpStatus.OK) {
                LOG.info("TMS: Test report uploaded to Xray Cloud! {}", getTestCaseIds(testReport));
            } else {
                XrayCloudError error = new ObjectMapper().readValue(response.getBody(), XrayCloudError.class);
                throw new IOException(error.getError());
            }
        } catch (IOException e) {
            throw new RuntimeException("TMS: Error uploading test report to Xray Cloud " + getTestCaseIds(testReport) + " -> " + e.getMessage());
        }
    }

    private String getTestCaseIds(XrayCloudReport testReport) {
        if (testReport.getTests().isEmpty()) {
            return "";
        }

        StringBuilder testCaseIds = new StringBuilder();
        testCaseIds.append("(Test Case IDs:");
        for (XrayCloudTest test : testReport.getTests()) {
            testCaseIds.append(" ").append(test.getTestKey());
        }
        testCaseIds.append(")");

        return testCaseIds.toString();
    }

    private RestTemplate createRestTemplateWithTimeout() {
        CloseableHttpClient httpClient = HttpClients.custom()
                .setDefaultRequestConfig(RequestConfig.custom()
                        .setConnectionRequestTimeout(Timeout.ofSeconds(TIMEOUT))
                        .setResponseTimeout(Timeout.ofSeconds(TIMEOUT))
                        .build())
                .build();

        return new RestTemplate(new HttpComponentsClientHttpRequestFactory(httpClient));
    }
}
