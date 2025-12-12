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

/**
 * Exception thrown when a method execution fails.
 */
public class MethodException extends BaseZahoriException {

    private static final long serialVersionUID = 7632306832221561772L;

    public MethodException() {
        super();
    }

    public MethodException(String message) {
        super(message);
    }

    public MethodException(Throwable cause) {
        super(cause);
    }

    public MethodException(String message, Throwable cause) {
        super(message, cause);
    }
}
