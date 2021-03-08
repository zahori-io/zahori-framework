package io.zahori.framework.test.testng;

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

import org.testng.IRetryAnalyzer;
import org.testng.ITestResult;
import org.testng.log4testng.Logger;

public class RetryTest implements IRetryAnalyzer {

    private static final Logger LOG = Logger.getLogger(RetryTest.class);
    private int retryCount = 0;
    private int maxRetryCount = Integer.parseInt(System.getProperty("max.reintentos"));

    @Override
    public boolean retry(ITestResult result) {
        if (retryCount < maxRetryCount) {
            LOG.info("Relanzando test " + result.getName() + " con estado " + getResultStatusName(result.getStatus())
                    + ". Numero de reintentos: " + (retryCount + 1));
            retryCount++;
            return true;
        }
        return false;
    }

    public String getResultStatusName(int status) {
        String resultName = null;
        if (status == 1) {
            resultName = "SUCCESS";
        }
        if (status == 2) {
            resultName = "FAILURE";
        }
        if (status == 3) {
            resultName = "SKIP";
        }
        return resultName;
    }
}
