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

import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.reporters.Files;

import io.zahori.framework.driver.browserfactory.Browsers;
import io.zahori.framework.exception.ZahoriException;
import io.zahori.model.process.CaseExecution;

/**
 * The type Base process.
 */
public abstract class BaseProcess {

    private static final Logger LOG = LoggerFactory.getLogger(BaseProcess.class);
    /**
     * The constant BASE_URL.
     */
    public static final String BASE_URL = "/";
    /**
     * The constant RUN_URL.
     */
    public static final String RUN_URL = "run";
    /**
     * The constant ZAHORI_SERVER_SERVICE_NAME.
     */
    public static final String ZAHORI_SERVER_SERVICE_NAME = "server";
    /**
     * The constant ZAHORI_SERVER_HEALTHCHECK_URL.
     */
    public static final String ZAHORI_SERVER_HEALTHCHECK_URL = "/healthcheck";
    /**
     * The constant ZAHORI_SERVER_PROCESS_REGISTRATION_URL.
     */
    public static final String ZAHORI_SERVER_PROCESS_REGISTRATION_URL = "/process";
    /**
     * The constant SECONDS_WAIT_FOR_SERVER.
     */
    public static final int SECONDS_WAIT_FOR_SERVER = 5;
    /**
     * The constant MAX_RETRIES_WAIT_FOR_SERVER.
     */
    public static final int MAX_RETRIES_WAIT_FOR_SERVER = 7;

    /**
     * The Retries.
     */
    protected int retries;

    /**
     * Healthcheck string.
     *
     * @param processName the process name
     * @return the string
     */
    protected String healthcheck(String processName) {
        return processName + " is up!";
    }

    /**
     * Run process string.
     *
     * @param caseExecution the case execution
     * @param remote        the remote
     * @param remoteUrl     the remote url
     * @return the string
     */
    protected String runProcess(CaseExecution caseExecution, String remote, String remoteUrl) {
        String id = "[" + caseExecution.getCas().getName() + "-" + caseExecution.getBrowser().getBrowserName() + "]";
        LOG.info("======> Start " + id);

        TestContext testContext = null;
        try {
            testContext = setup(caseExecution, remote, remoteUrl);
            process(testContext, caseExecution);

            return getResultsJson(testContext);
        } catch (Exception e) {
            if (testContext != null) {
                testContext.logError(e.getMessage());
            }
            return getResultsJson(testContext);
        } finally {
            teardown(testContext, caseExecution);
        }
    }

    private TestContext setup(CaseExecution caseExecution, String remote, String remoteUrl) {
        String id = "[" + caseExecution.getCas().getName() + "-" + caseExecution.getBrowser().getBrowserName() + "] ";
        LOG.info("==== Setup " + id);

        TestContext testContext = null;
        try {
            testContext = new TestContext();
            testContext.testCaseName = caseExecution.getCas().getName();
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
            LOG.error("==== Setup error " + id + ": " + e.getMessage());
            throw new ZahoriException(caseExecution.getCas().getName(), "Error on process setup: " + e.getMessage());
        }
    }

    private void process(TestContext testContext, CaseExecution caseExecution) {
        String id = "[" + caseExecution.getCas().getName() + "-" + caseExecution.getBrowser().getBrowserName() + "]";
        LOG.info("==== Process " + id);
        try {
            run(testContext, caseExecution);
        } catch (final Exception e) {
            LOG.error("==== Process error " + id + ": " + e.getMessage());
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
            testContext.writeSteps2Json();
            testContext.stopChronometer();
            testContext.logInfo("Test Finished: " + testContext.testCaseName);
            testContext.logInfo("Test duration: " + testContext.getTestDuration() + " seconds");
            testContext.setExecutionNotes("Test duration: " + testContext.getTestDuration() + " seconds");
            testContext.reportTestResult();
        }
    }

    /**
     * Run.
     *
     * @param testContext   the test context
     * @param caseExecution the case execution
     */
    protected abstract void run(TestContext testContext, CaseExecution caseExecution);

    private void teardown(TestContext testContext, CaseExecution caseExecution) {
        String id = "[" + caseExecution.getCas().getName() + "-" + caseExecution.getBrowser().getBrowserName() + "]";
        LOG.info("==== Teardown " + id);
        try {
            if (testContext != null) {
                testContext.stopVideo();
                if (testContext.getBrowser() != null) {
                    testContext.getBrowser().close();
                }
            }

            // Upload evidence files
            // TODO donde? en el metodo process?
            // ServiceInstance serviceInstance = loadBalancer.choose("server");
            // LOG.info(id + " Server Uri to upload results: " + serviceInstance.getUri());
        } catch (Exception e) {
            LOG.error("==== Teardown error " + id + ": " + e.getMessage());
            throw new ZahoriException(caseExecution.getCas().getName(), "Error on process teardown: " + e.getMessage());
        }
    }

    private String getResultsJson(TestContext testContext) {
        try {
            File stepsFile = new File(testContext.getEvidencesFolder() + File.separator + "testSteps.json");
            return Files.readFile(stepsFile);
        } catch (IOException e) {
            return "Error reading file testSteps.json";
        }
    }

    /**
     * Pause.
     *
     * @param seconds the seconds
     */
    protected void pause(int seconds) {
        try {
            Thread.sleep(seconds * 1000);
        } catch (InterruptedException e) {
            LOG.debug("Error while pausing: " + e.getMessage());
        }
    }

}
