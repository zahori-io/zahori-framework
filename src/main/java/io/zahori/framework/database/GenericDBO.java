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

import static java.nio.charset.StandardCharsets.ISO_8859_1;
import static java.nio.charset.StandardCharsets.UTF_8;

import java.util.List;
import java.util.Map;

import io.zahori.framework.core.TestContext;
import io.zahori.framework.utils.Utils;

/**
 * The type Generic dbo.
 */
public class GenericDBO {

    private DataBase db;
    /**
     * The Test context.
     */
    protected TestContext testContext;
    private String dbSchema;
    private String labelSchema;

    /**
     * Instantiates a new Generic dbo.
     *
     * @param driver      the driver
     * @param url         the url
     * @param user        the user
     * @param password    the password
     * @param testContext the test context
     */
    public GenericDBO(String driver, String url, String user, String password, TestContext testContext) {
        this.db = new DataBase(driver, url, user, password);
        this.testContext = testContext;
        this.dbSchema = null;
        this.labelSchema = null;
    }

    /**
     * Instantiates a new Generic dbo.
     *
     * @param driver      the driver
     * @param url         the url
     * @param user        the user
     * @param password    the password
     * @param dbSchema    the db schema
     * @param labelSchema the label schema
     * @param testContext the test context
     */
    public GenericDBO(String driver, String url, String user, String password, String dbSchema, String labelSchema,
            TestContext testContext) {
        this.db = new DataBase(driver, url, user, password);
        this.testContext = testContext;
        this.dbSchema = dbSchema;
        this.labelSchema = labelSchema;
    }

    /**
     * Execute query list.
     *
     * @param messageKey the message key
     * @param query      the query
     * @param args       the args
     * @return the list
     */
    protected List<Map<String, String>> executeQuery(String messageKey, String query, Object... args) {
        String txtQuery = query;
        System.out.println(txtQuery);
        for (Object arg : args) {
            txtQuery = txtQuery.replaceFirst("\\?", String.valueOf(arg));
        }

        final List<Map<String, String>> respuesta;
        if (this.dbSchema != null) {
            txtQuery = txtQuery.replaceAll(labelSchema, dbSchema);
            System.out.println(txtQuery);
            respuesta = db.executeAndNotify(query.replaceAll(labelSchema, dbSchema), args);
        } else {
            System.out.println(txtQuery);
            respuesta = db.executeAndNotify(query, args);
        }
        String partialStepText = testContext.getMessage(messageKey) + "\n";
        partialStepText += "Exec. query:\n" + txtQuery + "\n";
        partialStepText += Utils.getListString(respuesta);
        byte ptext[] = partialStepText.getBytes(ISO_8859_1);
        partialStepText = new String(ptext, UTF_8);
        // partialStepText =
        // Charset.forName("UTF-8").encode(partialStepText).toString();
        testContext.logPartialStep(partialStepText);

        return respuesta;
    }
}
