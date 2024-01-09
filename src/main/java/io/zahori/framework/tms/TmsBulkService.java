package io.zahori.framework.tms;

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
import io.zahori.model.process.CaseExecution;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class TmsBulkService {

    private static Map<Long, List<CaseExecution>> executionCases = new ConcurrentHashMap<>();

    private TmsBulkService() {
    }

    public static void addCaseExecution(CaseExecution caseExecution) {
        if (!executionCases.containsKey(caseExecution.getExecutionId())) {
            List<CaseExecution> cases = new ArrayList<>();
            cases.add(caseExecution);

            executionCases.put(caseExecution.getExecutionId(), cases);
        } else {
            executionCases.get(caseExecution.getExecutionId()).add(caseExecution);
        }
    }

    public static boolean isExecutionCompleted(Long executionId) {
        if (executionCases.containsKey(executionId)) {
            List<CaseExecution> caseExecutions = getCaseExecutions(executionId);

            if (caseExecutions.isEmpty()) {
                return false;
            }

            return caseExecutions.get(0).getExecutionTotalCases() == caseExecutions.size();
        }
        return false;
    }

    public static List<CaseExecution> getCaseExecutions(Long executionId) {
        if (executionCases.containsKey(executionId)) {
            return executionCases.get(executionId);
        }
        return new ArrayList<>();
    }

    public static void removeExecution(Long executionId) {
        if (executionCases.containsKey(executionId)) {
            executionCases.remove(executionId);
        }
    }

}
