package io.zahori.framework.tms.testLink;

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
import java.net.MalformedURLException;
import java.net.URL;

import javax.activation.FileTypeMap;

import org.apache.commons.codec.binary.Base64;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.FilenameUtils;
import org.apache.commons.lang3.StringUtils;

import br.eti.kinoshita.testlinkjavaapi.TestLinkAPI;
import br.eti.kinoshita.testlinkjavaapi.constants.ExecutionStatus;
import br.eti.kinoshita.testlinkjavaapi.model.Build;
import br.eti.kinoshita.testlinkjavaapi.model.ReportTCResultResponse;
import br.eti.kinoshita.testlinkjavaapi.model.TestCase;
import br.eti.kinoshita.testlinkjavaapi.model.TestPlan;
import br.eti.kinoshita.testlinkjavaapi.util.TestLinkAPIException;

public class TestLink {

    private TestLinkAPI api;
    private String projectName;
    private String planName;
    private String platformName;
    private String buildName;

    public TestLink(String url, String apiKey, String projectName, String planName) {
        super();
        this.api = new TestLinkAPI(getTestlinkURL(url), apiKey);
        this.projectName = projectName;
        this.planName = planName;
        this.platformName = null;
        this.buildName = null;
    }

    public TestLink(String url, String apiKey, String projectName, String planName, String platformName) {
        super();
        this.api = new TestLinkAPI(getTestlinkURL(url), apiKey);
        this.projectName = projectName;
        this.planName = planName;
        this.platformName = platformName;
        this.buildName = null;
    }

    public TestLink(String url, String apiKey, String projectName, String planName, String buildName, String platformName) {
        super();
        this.api = new TestLinkAPI(getTestlinkURL(url), apiKey);
        this.projectName = projectName;
        this.planName = planName;
        this.buildName = buildName;
        this.platformName = platformName;
    }

    public int updateTestResult(String testCaseExternalId, char status) {
        return updateTestResult(testCaseExternalId, status, null);
    }

    public int updateTestResult(String testCaseExternalId, char status, String executionNotes) {
        TestPlan plan = getTestPlanByProjectNamePlanName(planName, projectName);
        TestCase testCase = getTescaseID(testCaseExternalId);
        Build build = StringUtils.isEmpty(buildName) ? getBuildByTestPlanID(plan) : getBuildByName(plan, buildName);

        ReportTCResultResponse response = api.setTestCaseExecutionResult(testCase.getId(), null, plan.getId(), ExecutionStatus.getExecutionStatus(status),
                build.getId(), build.getName(), executionNotes, null, null, null, platformName, null, null);

        if ((response == null) || (response.getExecutionId().intValue() <= 0)) {
            throw new RuntimeException("Error updating test result on Test Link");
        }

        return response.getExecutionId().intValue();
    }

    public void uploadExecutionAttachment(Integer executionId, String filePath, String title) {
        try {
            this.api.uploadExecutionAttachment(executionId, title, "", FilenameUtils.getName(filePath),
                    FileTypeMap.getDefaultFileTypeMap().getContentType(filePath), getFileToStringBase64(filePath));
        } catch (TestLinkAPIException e) {
            throw new RuntimeException("Error uploading file: " + filePath + " to test execution " + executionId + " in Test Link -> " + e.getMessage());
        }
    }

    public void uploadTestCaseAttachment(String testCaseExternalId, String filePath, String title) {

        TestCase testCase = getTescaseID(testCaseExternalId);
        try {
            this.api.uploadTestCaseAttachment(testCase.getId(), title, "", FilenameUtils.getName(filePath),
                    FileTypeMap.getDefaultFileTypeMap().getContentType(filePath), getFileToStringBase64(filePath));
        } catch (TestLinkAPIException e) {
            throw new RuntimeException("Error uploading file: " + filePath + " to test case " + testCase.getName() + " in Test Link -> " + e.getMessage());
        }
    }

    private URL getTestlinkURL(String url) {

        URL testlinkURL = null;
        try {
            testlinkURL = new URL(url);
        } catch (MalformedURLException mue) {
            mue.printStackTrace(System.err);
        }
        return testlinkURL;
    }

    private TestPlan getTestPlanByProjectNamePlanName(String planName, String testProjectName) throws TestLinkAPIException {
        TestPlan testPlan;

        try {
            testPlan = api.getTestPlanByName(planName, testProjectName);
        } catch (TestLinkAPIException e) {
            System.out.println("No existe el plan con nombre: " + planName + " en el proyecto con nombre: " + testProjectName
                    + " creando Plan de Pruebas via api . . . . . . . . . . ");
            testPlan = api.createTestPlan(planName, testProjectName, planName, Boolean.TRUE, Boolean.TRUE);
        }
        return testPlan;
    }

    private Build getBuildByTestPlanID(TestPlan currentPlan) throws TestLinkAPIException {
        return api.getLatestBuildForTestPlan(currentPlan.getId());
    }

    private Build getBuildByName(TestPlan testPlan, String buildName) throws TestLinkAPIException {
        Build[] planBuilds = api.getBuildsForTestPlan(testPlan.getId());
        boolean found = false;
        int i = 0;
        while (!found && i < planBuilds.length) {
            found = StringUtils.equalsIgnoreCase(buildName, planBuilds[i].getName());
            i++;
        }

        if (found) {
            return planBuilds[i - 1];
        } else {
            throw new TestLinkAPIException("No existe ninguna build en el plan de pruebas con nombre: " + buildName);
        }
    }

    private TestCase getTescaseID(String fullTestCaseExternalId) throws TestLinkAPIException {

        return api.getTestCaseByExternalId(fullTestCaseExternalId, null);
    }

    private static String getFileToStringBase64(String pathname) {
        String fileContent = "";

        try {
            fileContent = new String(Base64.encodeBase64Chunked(FileUtils.readFileToByteArray(new File(pathname))));
        } catch (IOException e) {
            System.out.println("Error al convertir el contenido del fichero a String" + e.getMessage());
        }
        return fileContent;
    }
}
