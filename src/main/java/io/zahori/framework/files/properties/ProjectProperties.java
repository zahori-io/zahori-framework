package io.zahori.framework.files.properties;

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

import io.zahori.framework.i18n.MessageReader;
import org.apache.commons.lang3.StringUtils;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

public class ProjectProperties {

    private final Properties prop;
    private MessageReader messageReader;

    public ProjectProperties() {

        prop = new Properties();
        InputStream input = null;
        try {

            input = SystemPropertiesUtils.class.getClassLoader().getResourceAsStream("project.properties");

            // load properties file
            prop.load(input);

            messageReader = new MessageReader("project");

        } catch (Exception e) {
            throw new RuntimeException("ERROR loading project properties file: " + e.getMessage());
        } finally {
            if (input != null) {
                try {
                    input.close();
                } catch (IOException e) {
                    throw new RuntimeException("ERROR loading project properties file: " + e.getMessage());
                }
            }
        }
    }

    public String getProperty(String propertyName) {
        return StringUtils.trim(prop.getProperty(propertyName));
    }

    public String getProperty(String propertyName, String... propertyArgs) {
        return messageReader.get(propertyName, propertyArgs);
    }
}
