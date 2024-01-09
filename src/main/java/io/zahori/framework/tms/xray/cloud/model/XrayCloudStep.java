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
public class XrayCloudStep {

    private String status;      // The status for the test step (PASSED, FAILED, EXECUTING, TODO, custom statuses ...)
    private String comment;     // The comment for the step result
    private String actualResult;// The actual result field for the step result
    private List<XrayCloudEvidence> evidences = new ArrayList<>();    // An array of evidence items of the test run (link)

    public XrayCloudStep() {
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getComment() {
        return comment;
    }

    public void setComment(String comment) {
        this.comment = comment;
    }

    public String getActualResult() {
        return actualResult;
    }

    public void setActualResult(String actualResult) {
        this.actualResult = actualResult;
    }

    public List<XrayCloudEvidence> getEvidences() {
        return evidences;
    }

    public void setEvidences(List<XrayCloudEvidence> evidences) {
        this.evidences = evidences;
    }

    public void addEvidence(XrayCloudEvidence evidence) {
        this.evidences.add(evidence);
    }
}
