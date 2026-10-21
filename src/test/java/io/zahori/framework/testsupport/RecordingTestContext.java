package io.zahori.framework.testsupport;

import io.zahori.framework.core.TestContext;
import io.zahori.model.process.Browser;
import io.zahori.model.process.Case;
import io.zahori.model.process.CaseExecution;
import io.zahori.model.process.Configuration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.openqa.selenium.WebDriver;

public class RecordingTestContext extends TestContext {
    private final List<String> passedSteps = new ArrayList<>();
    private final List<String> failedSteps = new ArrayList<>();
    private final List<String> partialSteps = new ArrayList<>();
    private final List<String> infoMessages = new ArrayList<>();
    private final List<String> warnMessages = new ArrayList<>();
    private final List<String> executionNotes = new ArrayList<>();
    private final Map<String, String[]> stepArgs = new LinkedHashMap<>();
    private final Map<String, String> projectProperties = new LinkedHashMap<>();
    private boolean failedStepsThrow = true;

    private RecordingTestContext(CaseExecution caseExecution) {
        super(caseExecution, null);
        this.timeoutFindElement = 1;
    }

    public static RecordingTestContext forCase(String caseName) {
        return new RecordingTestContext(caseExecution(caseName));
    }

    public RecordingTestContext withTimeout(int seconds) {
        this.timeoutFindElement = seconds;
        return this;
    }

    /**
     * @param webDriver the driver double the page objects will use
     * @return this context, for chaining
     */
    public RecordingTestContext withDriver(WebDriver webDriver) {
        this.driver = webDriver;
        return this;
    }

    public RecordingTestContext continuingAfterFailedSteps() {
        this.failedStepsThrow = false;
        return this;
    }

    public RecordingTestContext withProjectProperty(String property, String value) {
        this.projectProperties.put(property, value);
        return this;
    }

    public List<String> passedSteps() {
        return Collections.unmodifiableList(this.passedSteps);
    }

    public List<String> failedSteps() {
        return Collections.unmodifiableList(this.failedSteps);
    }

    public List<String> partialSteps() {
        return Collections.unmodifiableList(this.partialSteps);
    }

    public List<String> infoMessages() {
        return Collections.unmodifiableList(this.infoMessages);
    }

    public List<String> warnMessages() {
        return Collections.unmodifiableList(this.warnMessages);
    }

    public List<String> executionNotes() {
        return Collections.unmodifiableList(this.executionNotes);
    }

    public String firstFailedStep() {
        return this.failedSteps.isEmpty() ? null : this.failedSteps.get(0);
    }

    public String[] argsOf(String stepKey) {
        return this.stepArgs.getOrDefault(stepKey, new String[0]);
    }

    public boolean passed(String stepKey) {
        return this.passedSteps.contains(stepKey);
    }

    public boolean failed(String stepKey) {
        return this.failedSteps.contains(stepKey);
    }

    public void logInfo(String text, String... textArgs) {
        this.infoMessages.add(text);
        this.stepArgs.put(text, textArgs);
    }

    public void logWarn(String text, String... textArgs) {
        this.warnMessages.add(text);
        this.stepArgs.put(text, textArgs);
    }

    public void logDebug(String text, String... textArgs) {
        this.infoMessages.add(text);
        this.stepArgs.put(text, textArgs);
    }

    public void logStepPassed(String description, String... descriptionArgs) {
        this.recordPassed(description, descriptionArgs);
    }

    public void logStepPassedWithScreenshot(String description, String... descriptionArgs) {
        this.recordPassed(description, descriptionArgs);
    }

    public void logStepFailed(String description, String... descriptionArgs) {
        this.recordFailed(description, descriptionArgs);
    }

    public void logStepFailedWithScreenshot(String description, String... descriptionArgs) {
        this.recordFailed(description, descriptionArgs);
    }

    public void logPartialStep(String description, String... descriptionArgs) {
        this.recordPartial(description, descriptionArgs);
    }

    public void logPartialStepSuccess(String description, String... descriptionArgs) {
        this.recordPartial(description, descriptionArgs);
    }

    public void logPartialStepFailed(String description, String... descriptionArgs) {
        this.recordPartial(description, descriptionArgs);
    }

    public void logPartialStepWithScreenshot(String description, String... descriptionArgs) {
        this.recordPartial(description, descriptionArgs);
    }

    public void logPartialStepSuccessWithScreenshot(String description, String... descriptionArgs) {
        this.recordPartial(description, descriptionArgs);
    }

    public void logPartialStepFailedWithScreenshot(String description, String... descriptionArgs) {
        this.recordPartial(description, descriptionArgs);
    }

    public String getMessage(String messageKey, String... messageArgs) {
        return messageArgs.length == 0 ? messageKey : messageKey + Arrays.toString((Object[])messageArgs);
    }

    public String getProjectProperty(String property) {
        return this.projectProperties.get(property);
    }

    public String getProjectProperty(String property, String... propertyArgs) {
        return this.projectProperties.get(property);
    }

    public void setExecutionNotes(String notes) {
        this.executionNotes.add(notes);
    }

    public void hideKeyboard() {
    }

    private void recordPassed(String description, String... descriptionArgs) {
        this.passedSteps.add(description);
        this.stepArgs.put(description, descriptionArgs);
    }

    private void recordPartial(String description, String... descriptionArgs) {
        this.partialSteps.add(description);
        this.stepArgs.put(description, descriptionArgs);
    }

    private void recordFailed(String description, String... descriptionArgs) {
        this.failedSteps.add(description);
        this.stepArgs.put(description, descriptionArgs);
        if (this.failedStepsThrow) {
            throw new RecordingTestContext.StepFailedException(description);
        }
    }

    private static CaseExecution caseExecution(String caseName) {
        Case cas = new Case();
        cas.setName(caseName);
        Browser browser = new Browser();
        browser.setBrowserName("chrome");
        browser.setDefaultVersion("latest");
        Configuration configuration = new Configuration();
        configuration.setName("windows");
        configuration.setEnvironmentName("staging");
        configuration.setTimeout(1L);
        CaseExecution caseExecution = new CaseExecution();
        caseExecution.setCaseExecutionId(1L);
        caseExecution.setCas(cas);
        caseExecution.setBrowser(browser);
        caseExecution.setConfiguration(configuration);
        caseExecution.setScreenResolution("1920x1080");
        return caseExecution;
    }

    public static class StepFailedException extends RuntimeException {
        private static final long serialVersionUID = 1L;

        public StepFailedException(String stepKey) {
            super(stepKey);
        }
    }
}
