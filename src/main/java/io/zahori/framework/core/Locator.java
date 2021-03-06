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

/**
 * The type Locator.
 */
public class Locator {

    /**
     * The enum Locator types.
     */
    public enum LocatorTypes {
        /**
         * Class name locator types.
         */
        className,
        /**
         * Css locator types.
         */
        css,
        /**
         * Id locator types.
         */
        id,
        /**
         * Link text locator types.
         */
        linkText,
        /**
         * Partial link text locator types.
         */
        partialLinkText,
        /**
         * Name locator types.
         */
        name,
        /**
         * Tag name locator types.
         */
        tagName,
        /**
         * Xpath locator types.
         */
        xpath
    }

    private LocatorTypes locatorType;

    private String locatorText;

    private By by;

    /**
     * Instantiates a new Locator.
     *
     * @param locatorType the locator type
     * @param locatorText the locator text
     */
    public Locator(LocatorTypes locatorType, String locatorText) {
        this.locatorType = locatorType;
        this.locatorText = locatorText;
        this.by = processLocator(locatorType, locatorText);
    }

    /**
     * Id locator.
     *
     * @param locatorText the locator text
     * @return the locator
     */
    public static Locator id(String locatorText) {
        return new Locator(LocatorTypes.id, locatorText);
    }

    /**
     * Css locator.
     *
     * @param locatorText the locator text
     * @return the locator
     */
    public static Locator css(String locatorText) {
        return new Locator(LocatorTypes.css, locatorText);
    }

    /**
     * Name locator.
     *
     * @param locatorText the locator text
     * @return the locator
     */
    public static Locator name(String locatorText) {
        return new Locator(LocatorTypes.name, locatorText);
    }

    /**
     * Link text locator.
     *
     * @param locatorText the locator text
     * @return the locator
     */
    public static Locator linkText(String locatorText) {
        return new Locator(LocatorTypes.linkText, locatorText);
    }

    /**
     * Class name locator.
     *
     * @param locatorText the locator text
     * @return the locator
     */
    public static Locator className(String locatorText) {
        return new Locator(LocatorTypes.className, locatorText);
    }

    /**
     * Partial link text locator.
     *
     * @param locatorText the locator text
     * @return the locator
     */
    public static Locator partialLinkText(String locatorText) {
        return new Locator(LocatorTypes.partialLinkText, locatorText);
    }

    /**
     * Tag name locator.
     *
     * @param locatorText the locator text
     * @return the locator
     */
    public static Locator tagName(String locatorText) {
        return new Locator(LocatorTypes.tagName, locatorText);
    }

    /**
     * Xpath locator.
     *
     * @param locatorText the locator text
     * @return the locator
     */
    public static Locator xpath(String locatorText) {
        return new Locator(LocatorTypes.xpath, locatorText);
    }

    /**
     * Gets locator type.
     *
     * @return the locator type
     */
    public LocatorTypes getLocatorType() {
        return locatorType;
    }

    /**
     * Sets locator type.
     *
     * @param locatorType the locator type
     */
    public void setLocatorType(LocatorTypes locatorType) {
        this.locatorType = locatorType;
    }

    /**
     * Gets locator text.
     *
     * @return the locator text
     */
    public String getLocatorText() {
        return locatorText;
    }

    /**
     * Sets locator text.
     *
     * @param locatorText the locator text
     */
    public void setLocatorText(String locatorText) {
        this.locatorText = locatorText;
    }

    /**
     * Gets by.
     *
     * @return the by
     */
    public By getBy() {
        return by;
    }

    /**
     * Sets by.
     */
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
