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

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

/**
 * The type System properties utils.
 */
public class SystemPropertiesUtils {

    /**
     * The Properties file.
     */
    static final String PROPERTIES_FILE = "zahori.properties";

    /**
     * Load system properties.
     */
    public static void loadSystemProperties() {
        final Properties properties = new Properties(System.getProperties());
        final InputStream is = SystemPropertiesUtils.class.getClassLoader().getResourceAsStream(PROPERTIES_FILE);
        if (is == null) {
            throw new RuntimeException("Can't find file " + PROPERTIES_FILE
                    + " in classpath. Please create it using one of the templates");
        }
        try {
            properties.load(is);
        } catch (final IOException e) {
            throw new RuntimeException(e);
        }

        System.setProperties(properties);
    }

    private SystemPropertiesUtils() {
        super();
    }

}
