package io.zahori.framework.exception;

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

public class ZahoriException extends RuntimeException {

    private static final long serialVersionUID = 1L;
    private final String message;
    private final String messageKey;
    private final String[] messageArgs;

    public ZahoriException(String testCaseName, String messageKey, String... messageArgs) {
        super();
        this.message = testCaseName + ": " + messageKey;
        this.messageKey = messageKey;
        this.messageArgs = messageArgs.clone();
    }

    @Override
    public String getMessage() {
        return message;
    }

    public String getMessageKey() {
        return messageKey;
    }

    public String[] getMessageArgs() {
        return messageArgs.clone();
    }

    @Override
    public String toString() {
        return "ZahoríException: " + message;
    }

}
