package io.zahori.framework.files.transforms;

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


import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;

public class JsonXmlUtils {

    private static final Logger LOG = LogManager.getLogger(JsonXmlUtils.class);

    public JsonXmlUtils() {
        super();
    }

    public OutputStream getOutputPathJson(String jsonPath) {
        OutputStream output = null;
        try {
            output = new FileOutputStream(jsonPath);
        } catch (FileNotFoundException e1) {
            LOG.error(e1.getMessage());
        }
        return output;
    }

    public InputStream getInputPathXml(String xmlPath) {
        InputStream input;
        input = JsonXmlUtils.class.getResourceAsStream(xmlPath);
        return input;
    }
}
