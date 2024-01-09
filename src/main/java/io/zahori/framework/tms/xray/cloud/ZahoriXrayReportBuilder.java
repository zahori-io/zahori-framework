package io.zahori.framework.tms.xray.cloud;

import io.zahori.framework.core.TestContext;
import io.zahori.framework.tms.xray.cloud.model.XrayCloudEvidence;
import io.zahori.framework.tms.xray.cloud.model.XrayCloudReport;
import io.zahori.framework.tms.xray.cloud.model.XrayCloudReportInfo;
import io.zahori.framework.tms.xray.cloud.model.XrayCloudTest;
import io.zahori.model.process.Attachment;
import io.zahori.model.process.CaseExecution;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import org.apache.commons.io.FilenameUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class ZahoriXrayReportBuilder {

    private static final Logger LOG = LogManager.getLogger(ZahoriXrayReportBuilder.class);

    private ZahoriXrayReportBuilder() {
    }

    public static XrayCloudReport build(CaseExecution caseExecution, String evidencesPath) {
        List<CaseExecution> caseExecutions = new ArrayList<>();
        caseExecutions.add(caseExecution);
        return build(caseExecutions, evidencesPath);
    }

    public static XrayCloudReport build(List<CaseExecution> caseExecutions, String evidencesPath) {
        if (caseExecutions == null || caseExecutions.isEmpty()) {
            throw new RuntimeException("Error building xray report: caseExecutions list is empty");
        }

        CaseExecution firstCaseExecution = caseExecutions.get(0);

        // Upload test result
        XrayCloudReport testReport = new XrayCloudReport();
        testReport.setTestExecutionKey(firstCaseExecution.getConfiguration().getTms().getTestExecutionId());

        // Info
        XrayCloudReportInfo reportInfo = new XrayCloudReportInfo();
        // reportInfo.setProject("XXX"); // not needed
        reportInfo.setTestPlanKey(firstCaseExecution.getConfiguration().getTms().getTestPlanId());
        reportInfo.setSummary(firstCaseExecution.getConfiguration().getTms().getTestExecutionSummary());

        ZonedDateTime startDateZonedGlobal = LocalDateTime.parse(firstCaseExecution.getDate(), DateTimeFormatter.ofPattern(TestContext.DATE_WEB_FORMAT)).atZone(ZoneId.systemDefault());
        String startDateGlobal = startDateZonedGlobal.format(DateTimeFormatter.ofPattern(XrayCloudClient.XRAY_API_DATEFORMAT));
        String endDateGlobal = ZonedDateTime.now().format(DateTimeFormatter.ofPattern(XrayCloudClient.XRAY_API_DATEFORMAT));

        reportInfo.setStartDate(startDateGlobal);
        reportInfo.setFinishDate(endDateGlobal);
        testReport.setInfo(reportInfo);

        // Add tests
        for (CaseExecution caseExecution : caseExecutions) {
            XrayCloudTest test = new XrayCloudTest();
            test.setTestKey(caseExecution.getConfiguration().getTms().getTestCaseId());
            test.setStatus("PASSED".equalsIgnoreCase(caseExecution.getStatus()) ? "PASSED" : "FAILED");

            ZonedDateTime startDateZoned = LocalDateTime.parse(caseExecution.getDate(), DateTimeFormatter.ofPattern(TestContext.DATE_WEB_FORMAT)).atZone(ZoneId.systemDefault());
            String startDate = startDateZoned.format(DateTimeFormatter.ofPattern(XrayCloudClient.XRAY_API_DATEFORMAT));
            String endDate = ZonedDateTime.now().format(DateTimeFormatter.ofPattern(XrayCloudClient.XRAY_API_DATEFORMAT));
            test.setStart(startDate);
            test.setFinish(endDate);

            test.setComment(caseExecution.getNotes());

            addEvidences(test, caseExecution, evidencesPath);

            testReport.addTest(test);
        }

        return testReport;
    }

    private static void addEvidences(XrayCloudTest test, CaseExecution caseExecution, String evidencesPath) {
        // Test evidences
        addEvidence(test, evidencesPath, caseExecution.getLog());
        addEvidence(test, evidencesPath, caseExecution.getDoc());
        addEvidence(test, evidencesPath, caseExecution.getVideo());
        addEvidence(test, evidencesPath, caseExecution.getHar());

        // Other attachments
        if (caseExecution.getAttachments() != null) {
            for (Attachment attachment : caseExecution.getAttachments()) {
                addEvidence(test, evidencesPath, attachment.getPath());
            }
        }
    }

    private static void addEvidence(XrayCloudTest test, String evidencesPath, String evidenceFile) {
        if (StringUtils.isBlank(evidenceFile)) {
            return;
        }

        String path = normalizePath(evidencesPath + evidenceFile);
        try {
            XrayCloudEvidence testEvidence = new XrayCloudEvidence();
            testEvidence.setData(XrayCloudClient.encodeToBase64(path));
            testEvidence.setFilename(String.valueOf(Paths.get(path).getFileName()));
            //testEvidence.setContentType("image/png");
            test.addEvidence(testEvidence);
        } catch (IOException e) {
            LOG.warn("Error encoding (base64) evidence file: " + e.getMessage());
        }
    }

    private static String normalizePath(String rawPath) {
        Path path = Paths.get(rawPath);
        Path normalizedPath = path.normalize();
        return FilenameUtils.separatorsToSystem(normalizedPath.toString());
    }

}
