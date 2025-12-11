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
import org.openqa.selenium.WebElement;
import org.openqa.selenium.support.locators.RelativeLocator;

/**
 * Clase para crear localizadores de elementos web.
 *
 * Selenium 4 introduce Relative Locators (Friendly Locators) que permiten
 * localizar elementos en relacion a otros elementos de la pagina.
 *
 * Ejemplo de uso tradicional:
 * <pre>
 * Locator.id("username")
 * Locator.css("[data-testid='submit']")
 * Locator.xpath("//button[@type='submit']")
 * </pre>
 *
 * Ejemplo de Relative Locators (Selenium 4):
 * <pre>
 * // Encontrar input encima de un boton
 * Locator.above(By.id("submitBtn"))
 *
 * // Encontrar label a la izquierda de un input
 * Locator.toLeftOf(By.id("emailInput"))
 *
 * // Combinar con tag especifico
 * Locator.tagName("input").above(By.id("submitBtn"))
 * </pre>
 */
public class Locator {

    public enum LocatorTypes {
        className,
        css,
        id,
        linkText,
        partialLinkText,
        name,
        tagName,
        xpath,
        relative
    }

    private LocatorTypes locatorType;

    private String locatorText;

    private By by;

    public Locator(LocatorTypes locatorType, String locatorText) {
        this.locatorType = locatorType;
        this.locatorText = locatorText;
        this.by = processLocator(locatorType, locatorText);
    }

    /**
     * Constructor para Relative Locators (Selenium 4).
     *
     * @param by By object (puede ser RelativeLocator.RelativeBy)
     */
    public Locator(By by) {
        this.locatorType = LocatorTypes.relative;
        this.locatorText = by.toString();
        this.by = by;
    }

    // ==================== Factory Methods Tradicionales ====================

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

    // ==================== Relative Locators (Selenium 4) ====================

    /**
     * Crea un localizador relativo para encontrar elementos ENCIMA de otro elemento.
     *
     * @param referenceElement By del elemento de referencia
     * @return Locator relativo
     */
    public static Locator above(By referenceElement) {
        return new Locator(RelativeLocator.with(By.tagName("*")).above(referenceElement));
    }

    /**
     * Crea un localizador relativo para encontrar elementos DEBAJO de otro elemento.
     *
     * @param referenceElement By del elemento de referencia
     * @return Locator relativo
     */
    public static Locator below(By referenceElement) {
        return new Locator(RelativeLocator.with(By.tagName("*")).below(referenceElement));
    }

    /**
     * Crea un localizador relativo para encontrar elementos A LA IZQUIERDA de otro elemento.
     *
     * @param referenceElement By del elemento de referencia
     * @return Locator relativo
     */
    public static Locator toLeftOf(By referenceElement) {
        return new Locator(RelativeLocator.with(By.tagName("*")).toLeftOf(referenceElement));
    }

    /**
     * Crea un localizador relativo para encontrar elementos A LA DERECHA de otro elemento.
     *
     * @param referenceElement By del elemento de referencia
     * @return Locator relativo
     */
    public static Locator toRightOf(By referenceElement) {
        return new Locator(RelativeLocator.with(By.tagName("*")).toRightOf(referenceElement));
    }

    /**
     * Crea un localizador relativo para encontrar elementos CERCA de otro elemento.
     * Por defecto busca dentro de 50 pixeles.
     *
     * @param referenceElement By del elemento de referencia
     * @return Locator relativo
     */
    public static Locator near(By referenceElement) {
        return new Locator(RelativeLocator.with(By.tagName("*")).near(referenceElement));
    }

    /**
     * Crea un localizador relativo para encontrar elementos CERCA de otro elemento
     * dentro de una distancia especifica.
     *
     * @param referenceElement By del elemento de referencia
     * @param atMostDistanceInPixels distancia maxima en pixeles
     * @return Locator relativo
     */
    public static Locator near(By referenceElement, int atMostDistanceInPixels) {
        return new Locator(RelativeLocator.with(By.tagName("*")).near(referenceElement, atMostDistanceInPixels));
    }

    // ==================== Relative Locators con WebElement ====================

    /**
     * Crea un localizador relativo para encontrar elementos ENCIMA de otro WebElement.
     *
     * @param referenceElement WebElement de referencia
     * @return Locator relativo
     */
    public static Locator above(WebElement referenceElement) {
        return new Locator(RelativeLocator.with(By.tagName("*")).above(referenceElement));
    }

    /**
     * Crea un localizador relativo para encontrar elementos DEBAJO de otro WebElement.
     *
     * @param referenceElement WebElement de referencia
     * @return Locator relativo
     */
    public static Locator below(WebElement referenceElement) {
        return new Locator(RelativeLocator.with(By.tagName("*")).below(referenceElement));
    }

    /**
     * Crea un localizador relativo para encontrar elementos A LA IZQUIERDA de otro WebElement.
     *
     * @param referenceElement WebElement de referencia
     * @return Locator relativo
     */
    public static Locator toLeftOf(WebElement referenceElement) {
        return new Locator(RelativeLocator.with(By.tagName("*")).toLeftOf(referenceElement));
    }

    /**
     * Crea un localizador relativo para encontrar elementos A LA DERECHA de otro WebElement.
     *
     * @param referenceElement WebElement de referencia
     * @return Locator relativo
     */
    public static Locator toRightOf(WebElement referenceElement) {
        return new Locator(RelativeLocator.with(By.tagName("*")).toRightOf(referenceElement));
    }

    /**
     * Crea un localizador relativo para encontrar elementos CERCA de otro WebElement.
     *
     * @param referenceElement WebElement de referencia
     * @return Locator relativo
     */
    public static Locator near(WebElement referenceElement) {
        return new Locator(RelativeLocator.with(By.tagName("*")).near(referenceElement));
    }

    // ==================== Relative Locators con tag especifico ====================

    /**
     * Crea un localizador relativo para encontrar elementos de un tag especifico
     * ENCIMA de otro elemento.
     *
     * Ejemplo: Locator.withTag("input").above(By.id("submitBtn"))
     *
     * @param tagName tag HTML a buscar (input, button, label, etc.)
     * @return RelativeLocatorBuilder para encadenar metodos
     */
    public static RelativeLocatorBuilder withTag(String tagName) {
        return new RelativeLocatorBuilder(By.tagName(tagName));
    }

    /**
     * Crea un localizador relativo para encontrar elementos que coincidan con un By
     * en relacion a otro elemento.
     *
     * Ejemplo: Locator.with(By.cssSelector(".error")).below(By.id("emailInput"))
     *
     * @param by localizador base
     * @return RelativeLocatorBuilder para encadenar metodos
     */
    public static RelativeLocatorBuilder with(By by) {
        return new RelativeLocatorBuilder(by);
    }

    /**
     * Builder para crear Relative Locators con sintaxis fluida.
     */
    public static class RelativeLocatorBuilder {
        private final By baseLocator;

        RelativeLocatorBuilder(By baseLocator) {
            this.baseLocator = baseLocator;
        }

        public Locator above(By referenceElement) {
            return new Locator(RelativeLocator.with(baseLocator).above(referenceElement));
        }

        public Locator below(By referenceElement) {
            return new Locator(RelativeLocator.with(baseLocator).below(referenceElement));
        }

        public Locator toLeftOf(By referenceElement) {
            return new Locator(RelativeLocator.with(baseLocator).toLeftOf(referenceElement));
        }

        public Locator toRightOf(By referenceElement) {
            return new Locator(RelativeLocator.with(baseLocator).toRightOf(referenceElement));
        }

        public Locator near(By referenceElement) {
            return new Locator(RelativeLocator.with(baseLocator).near(referenceElement));
        }

        public Locator near(By referenceElement, int atMostDistanceInPixels) {
            return new Locator(RelativeLocator.with(baseLocator).near(referenceElement, atMostDistanceInPixels));
        }

        public Locator above(WebElement referenceElement) {
            return new Locator(RelativeLocator.with(baseLocator).above(referenceElement));
        }

        public Locator below(WebElement referenceElement) {
            return new Locator(RelativeLocator.with(baseLocator).below(referenceElement));
        }

        public Locator toLeftOf(WebElement referenceElement) {
            return new Locator(RelativeLocator.with(baseLocator).toLeftOf(referenceElement));
        }

        public Locator toRightOf(WebElement referenceElement) {
            return new Locator(RelativeLocator.with(baseLocator).toRightOf(referenceElement));
        }

        public Locator near(WebElement referenceElement) {
            return new Locator(RelativeLocator.with(baseLocator).near(referenceElement));
        }
    }

    // ==================== Getters / Setters ====================

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

    // ==================== Metodos auxiliares ====================

    private By processLocator(LocatorTypes locatorType, String locatorText) {
        return switch (locatorType) {
            case className -> By.className(locatorText);
            case css -> By.cssSelector(locatorText);
            case id -> By.id(locatorText);
            case linkText -> By.linkText(locatorText);
            case partialLinkText -> By.partialLinkText(locatorText);
            case name -> By.name(locatorText);
            case tagName -> By.tagName(locatorText);
            case xpath -> By.xpath(locatorText);
            case relative -> this.by; // Ya asignado en constructor
        };
    }

}
