package io.zahori.framework.i18n;

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
import java.text.MessageFormat;
import java.util.Properties;

import org.apache.commons.lang3.StringUtils;

import io.zahori.framework.files.properties.SystemPropertiesUtils;

/**
 * The type Message reader.
 */
public class MessageReader {

    private final Properties prop;

    /**
     * Instantiates a new Message reader.
     *
     * @param fileName the file name
     */
    public MessageReader(String fileName) {

        prop = new Properties();
        InputStream input = null;
        String messagesFile = fileName + ".properties";
        try {

            input = SystemPropertiesUtils.class.getClassLoader().getResourceAsStream(messagesFile);

            // load properties file
            prop.load(input);

        } catch (Exception e) {
            throw new RuntimeException("ERROR loading file " + messagesFile + ": " + e.getMessage());
        } finally {
            if (input != null) {
                try {
                    input.close();
                } catch (IOException e) {
                    throw new RuntimeException("ERROR loading file " + messagesFile + ": " + e.getMessage());
                }
            }
        }
    }

    /**
     * Get string.
     *
     * @param messageKey the message key
     * @param arguments  the arguments
     * @return the string
     */
    public String get(String messageKey, String... arguments) {
        if (StringUtils.isBlank(messageKey)) {
            return messageKey;
        }

        String message = prop.getProperty(messageKey);

        // if messageKey is not found in properties file
        if (StringUtils.isBlank(message)) {
            return replaceMessageArguments(messageKey, arguments);
        }

        return replaceMessageArguments(message, arguments);
    }

    private String replaceMessageArguments(String message, String... arguments) {
        // replace message arguments with their values
        try {
            return MessageFormat.format(message, arguments);
        } catch (IllegalArgumentException e) {
            // if message with invalid arguments like: {}
            return message;
        }
    }
}
