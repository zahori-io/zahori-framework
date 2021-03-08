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

import java.util.List;
import java.util.Map;

import org.testng.annotations.Test;

public class DataBaseTest {

    @Test(enabled = true)
    public void testQuery() {
        final String driver = "com.ibm.db2.jcc.DB2Driver";
        final String url = "jdbc:db2://cbuq.cbuk.pre.corp:5002/DYQG";
        final String user = "DESMTM";
        final String password = "mirpa2";

        DataBase db = new DataBase(driver, url, user, password);

        // String query =
        // "SELECT * FROM ORG0Y.PCUCLI_CLI_ULT where H0028_IDEMPR = ? AND
        // H0028_USERID = ? ORDER BY H0028_TIMESMOD DESC";
        String query = "SELECT (SELECT COALESCE(MAX(RTRIM(E0478_ORGAN_NM)), (SELECT RTRIM(TTR_NOMTIPO) || ' ' || RTRIM(E0476_FIRST_NM) || ' ' || RTRIM(E0476_MIDDL_NM) || ' ' || RTRIM(E0476_LAST_NM) "
                + " FROM ORG01.PERS_FISICA_UK, ORG01.TIP_TRATAMIENTO"
                + " WHERE E0476_COD_PERS = GESTVIG.E1467_CODPERS AND E0476_TIP_TRA = TTR_IDTIPTRAT))"
                + " FROM ORG01.PERS_JCA_UK WHERE E0478_COD_PERS = GESTVIG.E1467_CODPERS) \"Name\","
                + " GESTVIG.E1467_ORDPRIO3," + " RTRIM(OGRUPO.G9703_DESCABR) \"Product Family\","
                + " RTRIM(EVENTO.B803_DESCABR) \"Opportunity\"," + " RTRIM(LINEGOCIO.B822_DESLINAB) \"Type\","
                + " CASE(RTRIM(GESTVIG.E1467_INDPRIOR)) WHEN 'H' THEN 'High' WHEN 'M' THEN 'Medium' WHEN 'L' THEN 'Low' ELSE '' END \"Priority\","
                + " RTRIM(GESTVIG.H7568_DESCRI50) \"Sales Stage\"," + " GESTVIG.E1467_FECCALEN \"Contact Date\","
                + " GESTVIG.E1467_FECHLIMI \"Expiry Date\","
                + " GESTVIG.E1467_CODPERS,GESTVIG.E1467_TIPOPERS, GESTVIG.E1467_CODABREV FROM (SELECT E1467_IDEMPR, E1467_IDCENTR, E1467_IDUSURE1, E1467_CANALDIS,E1467_INDBP, E1467_AGR_CAM, E1467_CODCLASE, E1467_IDTIPEVE,E1467_IDSUBEVE, E1467_CODMETOD, E1467_CODLINEG, E1467_CODPERS, E1467_TIPOPERS,E1467_INDPRIOR, E1467_FECCALEN, E1467_FECHLIMI, E1467_IDSTAGE,E1467_ORDPRIO3, E1467_FECBOLSA, E1467_IDDIRECT, E1467_OCCUREN,E1467_ORDSECUE, E1467_CODABREV, H7568_DESCRI50, E1467_ACTCOD FROM ORG01.B8_EJE_GEST_VIG GESTVIG LEFT OUTER JOIN ORG01.B8_SITUACION ON (E1467_IDEMPR = H7568_IDEMPR AND E1467_IDSTAGE = H7568_IDSTAGE)) GESTVIG, ORG01.COP_OPOR_GRUPO OGRUPO, ORG01.B8_EVENTO EVENTO, ORG01.B8_LIN_NEGOCIO LINEGOCIO WHERE GESTVIG.E1467_AGR_CAM = OGRUPO.G9703_AGR_CAM  AND GESTVIG.E1467_CODCLASE = EVENTO.B803_CODCLASE  AND GESTVIG.E1467_IDTIPEVE = EVENTO.B803_IDTIPEVE  AND GESTVIG.E1467_IDSUBEVE = EVENTO.B803_IDSUBEVE  AND GESTVIG.E1467_IDEMPR= LINEGOCIO.B822_IDEMPR  AND GESTVIG.E1467_CODMETOD = LINEGOCIO.B822_CODMETOD  AND GESTVIG.E1467_CODLINEG = LINEGOCIO.B822_CODLINEG"
                + " AND GESTVIG.E1467_IDEMPR = '1600'" + " AND GESTVIG.E1467_IDCENTR = '0421'"
                + " AND GESTVIG.E1467_IDUSURE1 = '002231'" + " AND GESTVIG.E1467_CANALDIS = 'RED'"
                + " AND GESTVIG.E1467_INDBP = ''" + " AND GESTVIG.E1467_FECCALEN BETWEEN '2016-10-01' AND '2016-12-31'"
                + " AND GESTVIG.E1467_CODPERS = '70135161'" + " AND GESTVIG.E1467_TIPOPERS = 'J'"
                + " AND GESTVIG.E1467_CODABREV = ''"
                + " ORDER BY E1467_IDEMPR,E1467_TIPOPERS,E1467_CODPERS,E1467_ORDPRIO3,\"Contact Date\""
                + " FETCH FIRST 10 ROWS ONLY";
        List<Map<String, String>> results = db.executeAndNotify(query);

        int size = results.size();
        for (int i = 0; i < size; i++) {
            Map<String, String> row = results.get(i);
            System.out.println("Row: " + row);

            // do not process more than 10 rows
            if (i == 9) {
                break;
            }
        }
    }
}
