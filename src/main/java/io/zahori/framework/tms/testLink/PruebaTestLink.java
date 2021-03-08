package io.zahori.framework.tms.testLink;

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

public class PruebaTestLink {

    private PruebaTestLink() {
    }

    public static void main(String... strings) {

        String urlTestLink = "http://10.60.3.117/testlink/lib/api/xmlrpc/v1/xmlrpc.php";
        String apiKey = "09d3cd7f59b755e430aaf5b914982460";
        String projectName = "CGAE - SIGA - AUTOMATIZACIÓN";
        String planName = "Informe de Justificación - AUTOMATIZACIÓN";

        TestLink testLink = new TestLink(urlTestLink, apiKey, projectName, planName);

        // Update test result
        int executionId = testLink.updateTestResult("AUT-1", 'P');

        // Upload attachment
        testLink.uploadExecutionAttachment(Integer.valueOf(executionId),
                "D:\\Designas_para_Justificar_Combinados_Caso_2222_check_marcado_01101000_20160510-143200.docx",
                "Titulo del adjunto");
    }
}
