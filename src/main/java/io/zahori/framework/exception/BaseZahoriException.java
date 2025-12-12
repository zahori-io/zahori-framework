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
 * Base exception class for all Zahori framework exceptions.
 * Provides common functionality for message handling.
 */
public abstract class BaseZahoriException extends Exception {

    private static final long serialVersionUID = 1L;
    private String message;

    protected BaseZahoriException() {
        super();
    }

    protected BaseZahoriException(String message) {
        super(message);
        this.message = message;
    }

    protected BaseZahoriException(Throwable cause) {
        super(cause);
        this.message = cause != null ? cause.getMessage() : null;
    }

    protected BaseZahoriException(String message, Throwable cause) {
        super(message, cause);
        this.message = message;
    }

    @Override
    public String toString() {
        return message;
    }

    @Override
    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }
}
