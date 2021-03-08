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

import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.Assert;
import org.testng.ITestContext;
import org.testng.ITestResult;
import org.testng.Reporter;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import io.zahori.framework.driver.browserfactory.Browsers;

public abstract class BaseTest {

    private static final Logger LOG = LoggerFactory.getLogger(BaseTest.class);
    protected TestContext testContext;
    protected int retries;

    @BeforeMethod(alwaysRun = true)
    public void setup(ITestContext testNGContext) {
        try {
            // Inicializamos el test context: objeto que contiene todos los
            // datos relacionados con la prueba
            testContext = new TestContext(testNGContext);
            testContext.logInfo("##### Start test: " + testContext.testCaseName);
            testContext.startChronometer();
            testContext.moveMouseToUpperLeftCorner();
            testContext.startVideo();
            retries = 0;

        } catch (Exception e) {
            if (testContext != null) {
                testContext.logInfo("Error on test setup: " + e.getMessage());
            }
            LOG.error(e.getMessage());
            Assert.fail(e.getMessage());
        }
    }

    @Test
    public void testMain() {
        ITestResult result = Reporter.getCurrentTestResult();
        result.setAttribute("test", testContext.testCaseName);
        try {
            test();
        } catch (final Exception e) {
            if (retries < testContext.getMaxRetries()) {
                retries++;
                testContext.logStepPassed("Processing error: " + e.getMessage() + "\nTest retries are enabled. Relaunching test (" + retries + " retry of "
                        + testContext.getMaxRetries() + " max retries).");
                if (!StringUtils.equalsIgnoreCase(String.valueOf(Browsers.NULLBROWSER), testContext.browserName)) {
                    testContext.getBrowser().closeWithoutProcessKill();
                }
                testMain();
            } else {
                testContext.failTest(e);
            }
        }
    }

    public abstract void test();

    @AfterMethod(alwaysRun = true)
    public void afterTest() {
        try {
            testContext.stopChronometer();
            testContext.logInfo("##### Finish test: " + testContext.testCaseName);
            testContext.logInfo("Test duration: " + testContext.getTestDuration() + " seconds");
            testContext.writeSteps2Json();
            testContext.stopVideo();
            if (testContext.getBrowser() != null) {
                testContext.getBrowser().close();
            }

            testContext.setExecutionNotes("Test duration: " + testContext.getTestDuration() + " seconds");
            testContext.reportTestResult();
        } catch (Exception e) {
            testContext.logInfo(e.getMessage());
            Assert.fail(e.getMessage());
        }
    }
}
