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

import org.openqa.selenium.By;

public class Locator {

    public enum LocatorTypes {
        className,
        css,
        id,
        linkText,
        partialLinkText,
        name,
        tagName,
        xpath
    }

    private LocatorTypes locatorType;

    private String locatorText;

    private By by;

    public Locator(LocatorTypes locatorType, String locatorText) {
        this.locatorType = locatorType;
        this.locatorText = locatorText;
        this.by = processLocator(locatorType, locatorText);
    }

    public static Locator id(String locatorText) {
        return new Locator(LocatorTypes.id, locatorText);
    }

    public static Locator css(String locatorText) {
        return new Locator(LocatorTypes.css, locatorText);
    }

    public static Locator name(String locatorText) {
        return new Locator(LocatorTypes.name, locatorText);
    }

    public static Locator linkText(String locatorText) {
        return new Locator(LocatorTypes.linkText, locatorText);
    }

    public static Locator className(String locatorText) {
        return new Locator(LocatorTypes.className, locatorText);
    }

    public static Locator partialLinkText(String locatorText) {
        return new Locator(LocatorTypes.partialLinkText, locatorText);
    }

    public static Locator tagName(String locatorText) {
        return new Locator(LocatorTypes.tagName, locatorText);
    }

    public static Locator xpath(String locatorText) {
        return new Locator(LocatorTypes.xpath, locatorText);
    }

    public LocatorTypes getLocatorType() {
        return locatorType;
    }

    public void setLocatorType(LocatorTypes locatorType) {
        this.locatorType = locatorType;
    }

    public String getLocatorText() {
        return locatorText;
    }

    public void setLocatorText(String locatorText) {
        this.locatorText = locatorText;
    }

    public By getBy() {
        return by;
    }

    public void setBy() {
        this.by = processLocator(this.locatorType, this.locatorText);
    }

    /*****************
     * AUXILIAR METHODS
     */

    private By processLocator(LocatorTypes locatorType, String locatorText) {
        switch (locatorType) {
        case className:
            return By.className(locatorText);
        case css:
            return By.cssSelector(locatorText);
        case id:
            return By.id(locatorText);
        case linkText:
            return By.linkText(locatorText);
        case partialLinkText:
            return By.partialLinkText(locatorText);
        case name:
            return By.name(locatorText);
        case tagName:
            return By.tagName(locatorText);
        case xpath:
            return By.xpath(locatorText);
        default:
            return null;
        }

    }

}
