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

import java.util.LinkedHashMap;
import java.util.Map;

public class Messages {

    private String[] languages;
    private Map<String, MessageReader> messageReaders = new LinkedHashMap<>();

    public Messages(String[] languages) {
        this.languages = languages;
        for (String language : languages) {
            messageReaders.put(language, new MessageReader("messages_" + language));
        }
    }

    public String getMessage(String language, String messageKey, String... messageArgs) {
        return messageReaders.get(language).get(messageKey, messageArgs);
    }

    public String getMessageInFirstLanguage(String messageKey, String... messageArgs) {
        // Get the first messages reader specified in zahori.properties
        MessageReader messagesReader = messageReaders.entrySet().iterator().next().getValue();
        return messagesReader.get(messageKey, messageArgs);
    }

    public String[] getLanguages() {
        return languages;
    }

}
