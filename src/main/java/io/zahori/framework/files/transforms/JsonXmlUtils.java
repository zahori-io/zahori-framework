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

import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The type Json xml utils.
 */
public class JsonXmlUtils {

    private static final Logger LOG = LoggerFactory.getLogger(JsonXmlUtils.class);

    /**
     * Instantiates a new Json xml utils.
     */
    public JsonXmlUtils() {
        super();
    }

    /**
     * Gets output path json.
     *
     * @param jsonPath the json path
     * @return the output path json
     */
    public OutputStream getOutputPathJson(String jsonPath) {
        OutputStream output = null;
        try {
            output = new FileOutputStream(jsonPath);
        } catch (FileNotFoundException e1) {
            LOG.error(e1.getMessage());
        }
        return output;
    }

    /**
     * Gets input path xml.
     *
     * @param xmlPath the xml path
     * @return the input path xml
     */
    public InputStream getInputPathXml(String xmlPath) {
        InputStream input;
        input = JsonXmlUtils.class.getResourceAsStream(xmlPath);
        return input;
    }
}
