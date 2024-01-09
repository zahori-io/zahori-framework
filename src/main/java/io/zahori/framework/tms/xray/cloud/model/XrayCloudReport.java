package io.zahori.framework.tms.xray.cloud.model;

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

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.ArrayList;
import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class XrayCloudReport {

    private String testExecutionKey;
    private XrayCloudReportInfo info;
    private List<XrayCloudTest> tests = new ArrayList<>();

    public XrayCloudReport() {
    }

    public String getTestExecutionKey() {
        return testExecutionKey;
    }

    public void setTestExecutionKey(String testExecutionKey) {
        this.testExecutionKey = testExecutionKey;
    }

    public XrayCloudReportInfo getInfo() {
        return info;
    }

    public void setInfo(XrayCloudReportInfo info) {
        this.info = info;
    }

    public List<XrayCloudTest> getTests() {
        return tests;
    }

    public void setTests(List<XrayCloudTest> tests) {
        this.tests = tests;
    }

    public void addTest(XrayCloudTest test) {
        this.tests.add(test);
    }

}
