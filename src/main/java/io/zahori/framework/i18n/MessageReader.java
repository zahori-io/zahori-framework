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

import io.zahori.framework.files.properties.SystemPropertiesUtils;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.helpers.MessageFormatter;

import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Properties;

public class MessageReader {

    private final Properties prop;

    public MessageReader(String fileName) {

        prop = new Properties();
        InputStreamReader input = null;
        String messagesFile = fileName + ".properties";
        try {

            input = new InputStreamReader(SystemPropertiesUtils.class.getClassLoader().getResourceAsStream(messagesFile), StandardCharsets.UTF_8);

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
            return MessageFormatter.arrayFormat(message, arguments).getMessage();
        } catch (IllegalArgumentException e) {
            // if message with invalid arguments like: {}
            return message;
        }
    }
}
