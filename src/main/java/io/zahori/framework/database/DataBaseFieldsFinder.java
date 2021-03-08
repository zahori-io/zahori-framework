package io.zahori.framework.database;

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

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.testng.annotations.Test;

public class DataBaseFieldsFinder {

    public static final String TBNAME = "TBNAME";

    @Test(enabled = true)
    public void testQuery() {
        final String driver = "com.ibm.db2.jcc.DB2Driver";
        final String url = "jdbc:db2://cbuq.cbuk.pre.corp:5002/DYQG";
        final String user = "DESMTM";
        final String password = "mirpa3";

        DataBase db = new DataBase(driver, url, user, password);

        boolean stopIfFound = false;
        String text = "Laverne";
        String tableNamePrefix = "";
        String query = "SELECT TBNAME, NAME FROM SYSIBM.SYSCOLUMNS WHERE TBNAME LIKE '" + tableNamePrefix + "%'";

        List<Map<String, String>> results = db.executeAndNotify(query);
        List<String> tablas = new ArrayList<>();
        int i = -1;
        boolean controlExec = true;
        while ((i < results.size()) && controlExec) {
            i++;
            String tbName = results.get(i).get(TBNAME);
            if (tbName.startsWith(tableNamePrefix)
                    && !esDigito(tbName.substring(tbName.length() - 2, tbName.length()))) {
                String subQuery = "SELECT * FROM ORG01." + results.get(i).get(TBNAME) + " WHERE "
                        + results.get(i).get("NAME") + " LIKE '%" + text + "%'";
                // System.out.println(subQuery);
                List<Map<String, String>> partialResults = db.tryToExecute(subQuery);
                if (!partialResults.isEmpty()) {
                    String txtInsert = results.get(i).get(TBNAME) + " - " + results.get(i).get("NAME");
                    System.out.println(txtInsert);
                    tablas.add(txtInsert);
                }

            }
            controlExec = !(stopIfFound && !tablas.isEmpty());
        }

        System.out.println(tablas);
    }

    private boolean esDigito(String text) {
        try {
            return ((Integer.parseInt(text) >= 0) || (Integer.parseInt(text) <= 0));
        } catch (Exception e) {
            return false;
        }
    }
}
