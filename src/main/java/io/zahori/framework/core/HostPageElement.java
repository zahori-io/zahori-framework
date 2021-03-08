package io.zahori.framework.core;

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

public class HostPageElement extends PageElement {

    private int row;

    private int column;

    public HostPageElement(HostPage page, String name, Locator locator) {
        super(page, name, locator);
        this.setDriver(page.getDriver());
        try {
            this.row = Integer.parseInt(locator.getLocatorText().split("_")[2]);
            this.column = Integer.parseInt(locator.getLocatorText().split("_")[1]);
        } catch (NumberFormatException | ArrayIndexOutOfBoundsException e) {
            this.row = -1;
            this.column = -1;
        }
    }

    public int getPositionRow() {
        return this.row;
    }

    public int getPositionColumn() {
        return this.column;
    }

    public String write(String text, String source) {
        super.write(text);
        return ("Field " + this.name + " (pos: " + this.row + "/" + this.column + ") set with value: " + source + ": '"
                + text + "'.\n");
    }
}
