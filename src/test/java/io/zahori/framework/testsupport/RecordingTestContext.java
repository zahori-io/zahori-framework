package io.zahori.framework.testsupport;

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
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public
 * License along with this program.  If not, see
 * <http://www.gnu.org/licenses/agpl-3.0.html>.
 * #L%
 */

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

/**
 * A {@link TestContext} that records steps in memory instead of writing evidences.
 *
 * <p>Unit-testing a page object needs a TestContext, and building one is not trivial: its
 * constructor reads the case name, the browser, the configuration and the screen resolution off a
 * {@link CaseExecution}, so every test ends up hand-rolling the same forty lines of scaffolding —
 * and then overriding the same log methods. This class does it once.
 *
 * <p><b>Failing a step throws, like in production.</b> {@code logStepFailedWithScreenshot} calls
 * {@code throwZahoriException} in the real TestContext, so a page object stops there. A double that
 * merely records would let the test run past a failure that in a real execution would have aborted
 * the case, which is how a test ends up asserting something that can never happen. Use
 * {@link #continuingAfterFailedSteps()} for the page objects that deliberately register several
 * failures in a row and carry on.
 *
 * <p><b>Platform predicates are not overridden on purpose.</b> {@code isIOSDriver()} and friends
 * are {@code driver instanceof IOSDriver} in the real TestContext, so the way to exercise the
 * Web/Android/iOS branching is to hand this context the right driver double —
 * {@link SeleniumDoubles#iosDriver()}, {@link SeleniumDoubles#androidDriver()} — and let the production
 * logic decide. Stubbing the predicate would test the stub, not the branching.
 *
 * <p>Typical use:
 *
 * <pre>{@code
 * RecordingTestContext testContext = RecordingTestContext.forCase("TCMP29")
 *         .withTimeout(1)
 *         .withDriver(SeleniumDoubles.driver());
 *
 * assertThrows(StepFailedException.class, () -> page.doSomething());
 * assertEquals("error.something", testContext.firstFailedStep());
 * }</pre>
 */
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

    /**
     * Creates a context for a case, with a one second timeout and no driver.
     *
     * @param caseName the case name, as it would come from the Zahori server
     * @return a new recording context
     */
    public static RecordingTestContext forCase(String caseName) {
        return new RecordingTestContext(caseExecution(caseName));
    }

    /**
     * @param seconds value for {@code timeoutFindElement}
     * @return this context, for chaining
     */
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

    /**
     * Makes a failed step record and continue instead of throwing.
     *
     * <p>Only for page objects that deliberately register several failures in one method. If you
     * are not testing one of those, leave the default: it is what production does.
     *
     * @return this context, for chaining
     */
    public RecordingTestContext continuingAfterFailedSteps() {
        this.failedStepsThrow = false;
        return this;
    }

    /**
     * @param property property name
     * @param value    value {@code getProjectProperty} will return
     * @return this context, for chaining
     */
    public RecordingTestContext withProjectProperty(String property, String value) {
        projectProperties.put(property, value);
        return this;
    }

    /**
     * @return keys of the steps logged as passed, in order
     */
    public List<String> passedSteps() {
        return Collections.unmodifiableList(passedSteps);
    }

    /**
     * @return keys or messages of the steps logged as failed, in order
     */
    public List<String> failedSteps() {
        return Collections.unmodifiableList(failedSteps);
    }

    /**
     * @return keys of the partial steps logged, in order
     */
    public List<String> partialSteps() {
        return Collections.unmodifiableList(partialSteps);
    }

    /**
     * @return texts logged as info, in order
     */
    public List<String> infoMessages() {
        return Collections.unmodifiableList(infoMessages);
    }

    /**
     * @return texts logged as warnings, in order
     */
    public List<String> warnMessages() {
        return Collections.unmodifiableList(warnMessages);
    }

    /**
     * @return notes added for the TMS, in order
     */
    public List<String> executionNotes() {
        return Collections.unmodifiableList(executionNotes);
    }

    /**
     * @return the first failed step, or {@code null} if none failed
     */
    public String firstFailedStep() {
        return failedSteps.isEmpty() ? null : failedSteps.get(0);
    }

    /**
     * Arguments a step, info or warning was logged with.
     *
     * <p>Kept per key and not "the last ones": a page object usually logs more steps after the one
     * under test, and the last-write-wins version silently returns the wrong arguments.
     *
     * <p>Warnings and info carry arguments too — a passenger number, a timeout, an amount — and they
     * are what the client reads in the evidence, so they are recorded like a step's.
     *
     * @param stepKey the step, info or warning key
     * @return its arguments, or an empty array if it was never logged
     */
    public String[] argsOf(String stepKey) {
        return stepArgs.getOrDefault(stepKey, new String[0]);
    }

    /**
     * @param stepKey the step key
     * @return whether that step was logged as passed
     */
    public boolean passed(String stepKey) {
        return passedSteps.contains(stepKey);
    }

    /**
     * @param stepKey the step key
     * @return whether that step was logged as failed
     */
    public boolean failed(String stepKey) {
        return failedSteps.contains(stepKey);
    }

    // ---------------------------------------------------------------- TestContext overrides

    @Override
    public void logInfo(String text, String... textArgs) {
        infoMessages.add(text);
        stepArgs.put(text, textArgs);
    }

    @Override
    public void logWarn(String text, String... textArgs) {
        warnMessages.add(text);
        stepArgs.put(text, textArgs);
    }

    @Override
    public void logDebug(String text, String... textArgs) {
        infoMessages.add(text);
        stepArgs.put(text, textArgs);
    }

    @Override
    public void logStepPassed(String description, String... descriptionArgs) {
        recordPassed(description, descriptionArgs);
    }

    @Override
    public void logStepPassedWithScreenshot(String description, String... descriptionArgs) {
        recordPassed(description, descriptionArgs);
    }

    @Override
    public void logStepFailed(String description, String... descriptionArgs) {
        recordFailed(description, descriptionArgs);
    }

    @Override
    public void logStepFailedWithScreenshot(String description, String... descriptionArgs) {
        recordFailed(description, descriptionArgs);
    }

    @Override
    public void logPartialStep(String description, String... descriptionArgs) {
        recordPartial(description, descriptionArgs);
    }

    @Override
    public void logPartialStepSuccess(String description, String... descriptionArgs) {
        recordPartial(description, descriptionArgs);
    }

    @Override
    public void logPartialStepFailed(String description, String... descriptionArgs) {
        recordPartial(description, descriptionArgs);
    }

    @Override
    public void logPartialStepWithScreenshot(String description, String... descriptionArgs) {
        recordPartial(description, descriptionArgs);
    }

    @Override
    public void logPartialStepSuccessWithScreenshot(String description, String... descriptionArgs) {
        recordPartial(description, descriptionArgs);
    }

    @Override
    public void logPartialStepFailedWithScreenshot(String description, String... descriptionArgs) {
        recordPartial(description, descriptionArgs);
    }

    /**
     * Returns the key, followed by the arguments when there are any.
     *
     * <p>Assertions stay independent of the message bundles being loaded, but the arguments remain
     * visible: dropping them hides every defect that lives in an interpolated value — an off-by-one
     * index, the wrong amount — because the message reads the same with or without it.
     */
    @Override
    public String getMessage(String messageKey, String... messageArgs) {
        return messageArgs.length == 0 ? messageKey : messageKey + Arrays.toString(messageArgs);
    }

    @Override
    public String getProjectProperty(String property) {
        return projectProperties.get(property);
    }

    @Override
    public String getProjectProperty(String property, String... propertyArgs) {
        return projectProperties.get(property);
    }

    /**
     * Recorded instead of sent to the TMS. They are the notes a human reads to know what to do with
     * a booking a case left behind — cancel it, watch it — so a test has to be able to assert them.
     */
    @Override
    public void setExecutionNotes(String notes) {
        executionNotes.add(notes);
    }

    /** No real keyboard in a unit test. */
    @Override
    public void hideKeyboard() {
        // intentionally empty
    }

    // ---------------------------------------------------------------- internals

    private void recordPassed(String description, String... descriptionArgs) {
        passedSteps.add(description);
        stepArgs.put(description, descriptionArgs);
    }

    private void recordPartial(String description, String... descriptionArgs) {
        partialSteps.add(description);
        stepArgs.put(description, descriptionArgs);
    }

    private void recordFailed(String description, String... descriptionArgs) {
        failedSteps.add(description);
        stepArgs.put(description, descriptionArgs);
        if (failedStepsThrow) {
            throw new StepFailedException(description);
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
        configuration.setTimeout(1);

        CaseExecution caseExecution = new CaseExecution();
        caseExecution.setCaseExecutionId(1L);
        caseExecution.setCas(cas);
        caseExecution.setBrowser(browser);
        caseExecution.setConfiguration(configuration);
        caseExecution.setScreenResolution("1920x1080");
        return caseExecution;
    }

    /**
     * Thrown when a step is logged as failed, mirroring what the real TestContext does.
     */
    public static class StepFailedException extends RuntimeException {

        private static final long serialVersionUID = 1L;

        /**
         * @param stepKey the key or message of the step that failed
         */
        public StepFailedException(String stepKey) {
            super(stepKey);
        }
    }
}
