package io.zahori.framework.utils;

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
import java.util.ArrayList;
import java.util.List;

import org.apache.commons.io.FileUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.zahori.framework.files.csv.PutObjectToCSV;

public class TestCSVRegistry {

    private static final Logger LOG = LoggerFactory.getLogger(TestCSVRegistry.class);

    private File testResultsFile;

    private PutObjectToCSV registry;

    public TestCSVRegistry(String... filePath) {
        try {
            this.testResultsFile = FileUtils.getFile(filePath);
            this.registry = new PutObjectToCSV(this.testResultsFile);
            File folder = this.testResultsFile;
            if (!folder.exists()) {
                FileUtils.forceMkdir(FileUtils.getFile(getFilePath(filePath)));
                File file = FileUtils.getFile(filePath);
                file.createNewFile();
                this.registry.writeRowInCSV(csvHead());
            }
        } catch (IOException e) {
            LOG.error("Error on TestCSVRegistry constructor: " + e.getMessage());
        }

    }

    public void writeResults(String date, String environment, String duration, String testcase, String result,
            String errorCode, String browser, String bookingCode, String sessionID) {
        List<String> results = new ArrayList<>();

        results.add(date);
        results.add(environment);
        results.add(duration);
        results.add(browser);
        results.add(testcase);
        results.add(result);
        results.add(bookingCode);
        results.add(errorCode);
        results.add(sessionID);

        this.registry.writeRowInCSV(results);
    }

    private List<String> csvHead() {
        List<String> result = new ArrayList<>();
        result.add("EXECUTION_DATE");
        result.add("ENVIRONMENT");
        result.add("EXECUTION_TIME");
        result.add("BROWSER");
        result.add("TESTCASE");
        result.add("RESULT");
        result.add("BOOKING_CODE");
        result.add("ERROR_DESCRIPTION");
        result.add("BROWSER_SESSION");

        return result;
    }

    private String[] getFilePath(String[] filePath) {
        String[] result = new String[filePath.length - 1];
        System.arraycopy(filePath, 0, result, 0, filePath.length - 1);

        return result;
    }
}
