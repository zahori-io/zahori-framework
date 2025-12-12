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
 * Exception thrown when a WebDriver operation fails.
 */
public class WebdriverException extends BaseZahoriException {

    private static final long serialVersionUID = -7424643123448431126L;

    public WebdriverException() {
        super();
    }

    public WebdriverException(String message) {
        super(message);
    }

    public WebdriverException(Throwable cause) {
        super(cause);
    }

    public WebdriverException(String message, Throwable cause) {
        super(message, cause);
    }
}
