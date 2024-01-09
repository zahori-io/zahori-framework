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

import java.util.ArrayList;
import java.util.List;

public class XrayCloudMainTest {

    private static final String CLIENT_ID = "<CLIENT_ID>";
    private static final String CLIENT_SECRET = "<CLIENT_SECRET>";
    private static final String TEST_PLAN_ID = "XXX-1";
    private static final String TEST_EXECUTION_ID = "XXX-2";
    private static final String TEST_CASE_ID = "XXX-3";

    public static void main(String... args) {

        XrayCloudClient xrayClient = new XrayCloudClient(CLIENT_ID, CLIENT_SECRET);

        // Test evidences
        List<String> evidences = new ArrayList<>();
        evidences.add("/path/to/evidencie/file");

        // Update existing Test Execution
        xrayClient.updateTestExecution(TEST_EXECUTION_ID, TEST_CASE_ID, true, "20231230-120010", evidences, "Comments...");

        // Create new Test Execution (associated to existing test plan)
        xrayClient.createTestExecution("Test execution summary", TEST_PLAN_ID, TEST_CASE_ID, true, "20231230-120010", evidences, "Comments...");

        // Create new Test Execution (not associated to a test plan)
        xrayClient.createTestExecution("New TE-Search Wikipedia", TEST_CASE_ID, true, "20231230-120010", evidences, "Comments...");
    }
}
