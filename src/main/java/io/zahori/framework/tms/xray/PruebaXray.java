package io.zahori.framework.tms.xray;

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

import io.zahori.framework.security.ZahoriCipher;

/**
 * The type Prueba xray.
 */
public class PruebaXray {

	private PruebaXray() {
	}

    /**
     * The entry point of application.
     *
     * @param strings the input arguments
     */
    public static void main(String... strings) {

		String urlTestLink = "https://jira.globalia.com/jira";
		String user = "aeapanel";
		String pass = "a3apanel19";
		String tcId = "DRL-29226";
		String teId = "DRL-29233";

		XrayRestClient xray = new XrayRestClient(urlTestLink, user, new ZahoriCipher().encode(pass));

		// Evidences
		List<String> evidencesPath = new ArrayList<>();

		// Update test result
		int result = xray.updateTestResult(teId, tcId, "FAIL", "Testing integration", "20190821-143000", evidencesPath);
		System.out.println(result);
	}
}
