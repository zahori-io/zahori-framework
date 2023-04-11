package io.zahori.framework.tms.xray;

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

import io.zahori.framework.core.TestContext;
import io.zahori.framework.rest.NetClient;
import io.zahori.framework.rest.ServiceInfo;
import io.zahori.framework.security.ZahoriCipher;
import org.apache.commons.codec.binary.Base64;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class XrayRestClient {

    private static final Logger LOG = LogManager.getLogger(XrayRestClient.class);

    private static final String TAG_TESTEXEC_ID = "{{testExecId}}";
    private static final String TAG_TESTPLAN_ID = "{{testPlanId}}";
    private static final String XRAY_API_EXECURL_SUFIX = "/rest/raven/1.0/import/execution";
    private static final String XRAY_API_DATEFORMAT = "yyyy-MM-dd'T'HH:mm:ssZ";
    // private static final String JIRA_API_GET_CREATEMETA =
    // "/rest/api/latest/issue/createmeta";
    private static final String JIRA_API_JQL_SEARCH = "/rest/api/latest/search";
    private static final String JIRA_API_CREATE_ISSUE = "/rest/api/latest/issue/";
    private static final String XRAY_API_TESTEXEC_TEST = "/rest/raven/1.0/api/testexec/" + TAG_TESTEXEC_ID + "/test";
    private static final String XRAY_API_TESTPLAN_TESTEXEC = "/rest/raven/1.0/api/testplan/" + TAG_TESTPLAN_ID + "/testexecution";
    private static final String XRAY_API_TESTPLAN_TEST = "/rest/raven/1.0/api/testplan/" + TAG_TESTPLAN_ID + "/test";

    private String jiraURL;
    private String jiraUser;
    private String jiraPassword;

    private Map<String, String> params = new HashMap<>();
    private Map<String, String> headers = new HashMap<>();

    public XrayRestClient(String jiraURL, String jiraUser, String jiraPassword) {
        this.jiraURL = jiraURL;
        this.jiraUser = jiraUser;
        this.jiraPassword = new ZahoriCipher().decode(jiraPassword);
        params = null;
        headers = null;
    }

    public int updateTestResult(String testExecutionId, String testCaseId, String status, String executionNotes, String startDate, List<String> evidences) {
        initRestObjects();
        String body = buildBody4TestCreation(testExecutionId, testCaseId, status, startDate, executionNotes, jiraUser, evidences);
        ServiceInfo info = NetClient.executePOSTwJson(jiraURL + XRAY_API_EXECURL_SUFIX, params, headers, body);
        if (info.getResponseCode() == 400) {
            LOG.info("Se ha producido un error intentando actualizar el resultado en JIRA. Se reintenta sin adjuntar las evidencias.");
            String newExecutionNotes = executionNotes + "\n ERROR ADJUNTANDO EVIDENCIAS";
            body = buildBody4TestCreation(testExecutionId, testCaseId, status, startDate, newExecutionNotes, jiraUser, Collections.emptyList());
            info = NetClient.executePOSTwJson(jiraURL + XRAY_API_EXECURL_SUFIX, params, headers, body);
        }
        printApiInteraction("Actualizacion resultado ejecucion en JIRA", info);
        return info.getResponseCode() >= 200 && info.getResponseCode() <= 299 ? 0 : -1;
    }

    public boolean existsTEOnJira(String testExecutionId) {
        initRestObjects();
        String URL = jiraURL + XRAY_API_TESTEXEC_TEST.replace(TAG_TESTEXEC_ID, testExecutionId);
        ServiceInfo info = NetClient.executeGET(URL, params, headers);
        printApiInteraction("Comprobacion elemento Test Execution " + testExecutionId + " en JIRA", info);
        return info.getResponseCode() == 200;
    }

    public String createTestExecution(String projectKey, String summary, String description, String priorityId, String[] labelsArray, String[] componentsArray,
            String assigneeName) {
        initRestObjects();
        String body = buidlBody4TestExecutionCreation(projectKey, summary, description, priorityId, labelsArray, componentsArray, assigneeName);
        ServiceInfo info = NetClient.executePOSTwJson(jiraURL + JIRA_API_CREATE_ISSUE, params, headers, body);
        printApiInteraction("Creacion elemento Test Execution", info);
        if (info.getResponseCode() == 201) {
            JSONObject response;
            try {
                response = (JSONObject) new JSONParser().parse(info.getResponseBody());
                return String.valueOf(response.get("key"));
            } catch (ParseException e) {
                return null;
            }
        }
        return null;
    }

    public boolean isTCAssociated2TE(String testExecutionId, String testCaseId) {
        String URL = XRAY_API_TESTEXEC_TEST.replace(TAG_TESTEXEC_ID, testExecutionId);
        return isItemAssociated2Container(testExecutionId, testCaseId, URL);
    }

    public boolean isTEAssociated2TP(String testPlanId, String testExecutionId) {
        String URL = XRAY_API_TESTPLAN_TESTEXEC.replace(TAG_TESTPLAN_ID, testPlanId);
        return isItemAssociated2Container(testPlanId, testExecutionId, URL);
    }

    public boolean isTCAssociated2TP(String testPlanId, String testCaseId) {
        String URL = XRAY_API_TESTPLAN_TEST.replace(TAG_TESTPLAN_ID, testPlanId);
        return isItemAssociated2Container(testPlanId, testCaseId, URL);
    }

    public void associateTC2TE(String testExecutionId, String testCaseId) {
        String URL = XRAY_API_TESTEXEC_TEST.replace(TAG_TESTEXEC_ID, testExecutionId);
        associateItem2Container(testCaseId, testExecutionId, URL);
    }

    public void associateTE2TP(String testExecutionId, String testPlanId) {
        String URL = XRAY_API_TESTPLAN_TESTEXEC.replace(TAG_TESTPLAN_ID, testPlanId);
        associateItem2Container(testExecutionId, testPlanId, URL);
    }

    public void associateTC2TP(String testCaseId, String testPlanId) {
        String URL = XRAY_API_TESTPLAN_TEST.replace(TAG_TESTPLAN_ID, testPlanId);
        associateItem2Container(testCaseId, testPlanId, URL);
    }

    public String look4TE(String projectKey, String summary) {
        initRestObjects();
        String jql = "project=" + projectKey + " and issueType=\"Test Execution\" and summary ~ \"\\\"" + summary + "\\\"\"";
        params.put("jql", jql);
        ServiceInfo info = NetClient.executeGET(jiraURL + JIRA_API_JQL_SEARCH, params, headers);
        printApiInteraction("Busqueda Test Execution por campo Summary", info);
        String testExecutionKey = null;
        if (info.getResponseCode() == 200) {
            try {
                JSONObject response = (JSONObject) new JSONParser().parse(info.getResponseBody());
                JSONArray issues = (JSONArray) response.get("issues");
                boolean found = false;
                int i = 0;
                while (!found && issues != null && i < issues.size()) {
                    JSONObject currentIssue = (JSONObject) issues.get(i);
                    JSONObject currentIssueFields = (JSONObject) currentIssue.get("fields");
                    if (StringUtils.equalsIgnoreCase(String.valueOf(currentIssueFields.get("summary")), summary)) {
                        found = true;
                        testExecutionKey = String.valueOf(currentIssue.get("key"));
                    }
                    i++;
                }
            } catch (ParseException e) {
                return null;
            }
        }
        return testExecutionKey;
    }

    @SuppressWarnings("unchecked")
    private String buildBody4TestCreation(String testExecutionId, String testCaseId, String status, String startDateText, String executionNotes, String user,
            List<String> evidences) {
        JSONObject body = new JSONObject();
        JSONObject test = new JSONObject();
        test.put("testKey", testCaseId);
        ZonedDateTime startDate = LocalDateTime.parse(startDateText, DateTimeFormatter.ofPattern(TestContext.DATE_FORMAT)).atZone(ZoneId.of("Europe/Madrid"));
        test.put("start", startDate.format(DateTimeFormatter.ofPattern(XRAY_API_DATEFORMAT)));
        test.put("finish", ZonedDateTime.now().format(DateTimeFormatter.ofPattern(XRAY_API_DATEFORMAT)));
        test.put("comment", executionNotes);
        test.put("status", status);
        test.put("executedBy", user);

        JSONArray evidencesArray = new JSONArray();
        for (String evidencePath : evidences) {
            JSONObject currentEvidence = new JSONObject();
            try {
                currentEvidence.put("data", encodeFileToBase64Binary(evidencePath));
                currentEvidence.put("filename", String.valueOf(Paths.get(evidencePath).getFileName()));
                evidencesArray.add(currentEvidence);
            } catch (IOException e) {
                LOG.info("Evidence file '" + currentEvidence + "' has been skipped");
            }
        }

        if (evidencesArray.size() > 0) {
            test.put("evidences", evidencesArray);
        }

        JSONArray tests = new JSONArray();
        tests.add(test);
        body.put("testExecutionKey", testExecutionId);
        body.put("tests", tests);
        return body.toJSONString();
    }

    @SuppressWarnings("unchecked")
    private String buidlBody4TestExecutionCreation(String projectKey, String summary, String description, String priorityId, String[] labelsArray,
            String[] componentsArray, String assigneeName) {
        JSONObject body = new JSONObject();
        JSONObject fields = new JSONObject();

        JSONObject project = new JSONObject();
        project.put("key", projectKey);
        fields.put("project", project);

        fields.put("summary", summary);

        fields.put("description", description);

        JSONObject issueType = new JSONObject();
        issueType.put("name", "Test Execution");
        fields.put("issuetype", issueType);

        JSONObject priority = new JSONObject();
        priority.put("id", priorityId);
        fields.put("priority", priority);

        if (labelsArray != null && labelsArray.length > 0) {
            JSONArray labels = new JSONArray();
            for (String currentLabel : labelsArray) {
                labels.add(currentLabel);
            }
            fields.put("labels", labels);
        }

        if (componentsArray != null && componentsArray.length > 0) {
            JSONArray components = new JSONArray();
            for (String currentComponent : componentsArray) {
                JSONObject component = new JSONObject();
                component.put("name", currentComponent);
                components.add(component);
            }
            fields.put("components", components);
        }

        JSONObject assignee = new JSONObject();
        assignee.put("name", assigneeName);
        fields.put("assignee", assignee);

        body.put("fields", fields);
        return body.toJSONString();
    }

    @SuppressWarnings("unchecked")
    private String buildBody4AssociateItem(String itemId) {
        JSONObject body = new JSONObject();
        JSONArray tests = new JSONArray();
        tests.add(itemId);
        body.put("add", tests);
        return body.toJSONString();
    }

    private void initRestObjects() {
        params = new HashMap<>();
        headers = new HashMap<>();
        String auth = Base64.encodeBase64String((jiraUser + ":" + jiraPassword).getBytes());
        headers.put("Authorization", "Basic " + auth);
        headers.put("Content-Type", "application/json");
    }

    private boolean isItemAssociated2Container(String containerId, String itemId, String urlSufix) {
        initRestObjects();
        String URL = jiraURL + urlSufix;
        ServiceInfo info = NetClient.executeGET(URL, params, headers);
        printApiInteraction("Consulta elementos asociados [" + containerId + " - " + itemId, info);
        if (info.getResponseCode() == 200) {
            try {
                JSONArray response = (JSONArray) new JSONParser().parse(info.getResponseBody());
                boolean found = false;
                int i = 0;
                while (!found && i < response.size()) {
                    JSONObject currentItem = (JSONObject) response.get(i);
                    found = StringUtils.equalsIgnoreCase(String.valueOf(currentItem.get("key")), itemId);
                    i++;
                }
                return found;
            } catch (ParseException e) {
                return false;
            }
        }
        return false;
    }

    private void associateItem2Container(String itemId, String containerId, String urlSufix) {
        initRestObjects();
        String URL = jiraURL + urlSufix;
        String body = buildBody4AssociateItem(itemId);
        NetClient.executePOSTwJson(URL, params, headers, body);
    }

    private String encodeFileToBase64Binary(String fileName) throws IOException {
        File file = new File(fileName);
        byte[] encoded = Base64.encodeBase64(FileUtils.readFileToByteArray(file));
        return new String(encoded, StandardCharsets.US_ASCII);
    }

    private void printApiInteraction(String message, ServiceInfo info) {
        if (!responseOK(info.getResponseCode())) {
            LOG.info(message);
            LOG.info("\nURL: " + info.getUrl());
            LOG.info("METHOD: " + info.getMethod());
            LOG.info("INPUT DATA:\n     Parameters: " + info.getParameters() + "\n     Request headers: " + info.getHeaders());
            if (!StringUtils.isEmpty(info.getRequestBody())) {
                LOG.info("     Request Body: " + info.getRequestBody());
            }

            LOG.info("OUTPUT DATA:\n     Response code: " + info.getResponseCode() + "\n     Response message: " + info.getResponseMessage()
                    + "\n     Service Response: " + info.getResponseBody());
        }
    }

    private boolean responseOK(int responseCode) {
        return responseCode >= 200 && responseCode <= 299;
    }

}
