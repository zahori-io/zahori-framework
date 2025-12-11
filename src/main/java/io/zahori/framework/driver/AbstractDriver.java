package io.zahori.framework.driver;

/*-
 * #%L
 * zahori-framework
 * $Id:$
 * $HeadURL:$
 * %%
 * Copyright (C) 2021 - 2024 PANEL SISTEMAS INFORMATICOS,S.L
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
import io.zahori.framework.driver.browserfactory.Browsers;
import io.zahori.framework.files.properties.ZahoriProperties;
import java.util.Map;
import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.openqa.selenium.PageLoadStrategy;
import org.openqa.selenium.Proxy;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.openqa.selenium.edge.EdgeOptions;
import org.openqa.selenium.firefox.FirefoxOptions;
import org.openqa.selenium.remote.AbstractDriverOptions;
import org.openqa.selenium.remote.DesiredCapabilities;
import org.openqa.selenium.remote.LocalFileDetector;
import org.openqa.selenium.remote.RemoteWebDriver;

/**
 * Clase abstracta que define la estructura y comportamientos comunes de un
 * driver. Esta clase debe ser extendida por clases especificas de drivers como
 * RemoteDriver o LocalDriver.
 */
public abstract class AbstractDriver implements Driver {

    private static final Logger LOG = LogManager.getLogger();

    /** Propiedad para configurar PageLoadStrategy desde zahori.properties */
    private static final String PROP_PAGE_LOAD_STRATEGY = "zahori.browser.pageLoadStrategy";

    /**
     * Metodo abstracto para crear una instancia de WebDriver. Debe ser
     * implementado por clases derivadas con logica especifica para cada tipo de
     * driver.
     *
     * @param browsers La configuracion del navegador.
     * @return Una instancia de WebDriver.
     */
    protected abstract WebDriver createWebDriver(Browsers browsers, Proxy proxy);

    /**
     * Crea y configura un WebDriver basado en la configuracion de navegadores
     * proporcionada.
     *
     * @param browsers La configuracion del navegador.
     * @return Un WebDriver configurado.
     */
    @Override
    public WebDriver getDriver(Browsers browsers, Proxy proxy) {
        WebDriver webDriver = createWebDriver(browsers, proxy);
        configureWebDriver(webDriver, browsers);
        return webDriver;
    }

    /**
     * Configura las opciones para el WebDriver. Este metodo crea y configura
     * opciones comunes para todos los tipos de drivers.
     *
     * @param browsers La configuracion del navegador.
     * @param proxy
     * @return Opciones configuradas para el WebDriver.
     */
    @Override
    public AbstractDriverOptions<?> getOptions(Browsers browsers, Proxy proxy) {
        AbstractDriverOptions<?> options = OptionsFactory.valueOf(browsers.getName()).getOptions();
        options.setAcceptInsecureCerts(true);
        if (proxy != null) {
            options.setProxy(proxy);
        }
        // PageLoadStrategy configurable desde zahori.properties
        // Valores: normal (default Selenium), eager, none
        options.setPageLoadStrategy(getPageLoadStrategy());
        setZahoriPropertiesBrowserOptions(options);

        // Set capabilities defined in zahori.properties starting with key: zahori.test.capabilities.add.
        DesiredCapabilities capabilities = CapabilitiesBuilder.getCapabilities(browsers);
        capabilities.asMap().forEach((key, value) -> options.setCapability(key, value));

        LOG.info("{} browser options and capabilities: {}", browsers.getName(), options);
        return options;
    }

    /**
     * Obtiene el PageLoadStrategy configurado en zahori.properties.
     * Valores permitidos: normal, eager, none
     * Por defecto: normal (comportamiento estandar de Selenium)
     *
     * @return PageLoadStrategy configurado
     */
    private PageLoadStrategy getPageLoadStrategy() {
        String strategy = new ZahoriProperties().getProperty(PROP_PAGE_LOAD_STRATEGY);
        if (StringUtils.isBlank(strategy)) {
            return PageLoadStrategy.NORMAL; // Default seguro
        }
        try {
            return PageLoadStrategy.fromString(strategy.toLowerCase());
        } catch (IllegalArgumentException e) {
            LOG.warn("PageLoadStrategy invalido '{}', usando NORMAL. Valores validos: normal, eager, none", strategy);
            return PageLoadStrategy.NORMAL;
        }
    }

    /**
     * Configura opciones adicionales del navegador basadas en las propiedades
     * de Zahori.
     *
     * @param options Objeto AbstractDriverOptions para agregar opciones del
     * navegador.
     */
    private void setZahoriPropertiesBrowserOptions(AbstractDriverOptions<?> options) {

        if (options instanceof ChromeOptions chromeOptions) {
            Map<String, String> extraPreferences = new ZahoriProperties().getBrowserPreferencesToBeAdded("chrome");
            for (String extraPrefKey : extraPreferences.keySet()) {
                String extraPrefValue = extraPreferences.get(extraPrefKey);
                String argument = StringUtils.isEmpty(extraPrefValue) ? extraPrefKey : extraPrefKey + "=" + extraPrefValue;
                chromeOptions.addArguments(argument);
            }
        }

        if (options instanceof EdgeOptions edgeOptions) {
            Map<String, String> extraPreferences = new ZahoriProperties().getBrowserPreferencesToBeAdded("edge");
            for (String extraPrefKey : extraPreferences.keySet()) {
                String extraPrefValue = extraPreferences.get(extraPrefKey);
                String argument = StringUtils.isEmpty(extraPrefValue) ? extraPrefKey : extraPrefKey + "=" + extraPrefValue;
                edgeOptions.addArguments(argument);
            }
        }

        if (options instanceof FirefoxOptions firefoxOptions) {
            Map<String, String> extraPreferences = new ZahoriProperties().getBrowserPreferencesToBeAdded("firefox");
            for (String extraPrefKey : extraPreferences.keySet()) {
                String extraPrefValue = extraPreferences.get(extraPrefKey);
                if (isBoolean(extraPreferences.get(extraPrefKey))) {
                    firefoxOptions.addPreference(extraPrefKey, Boolean.valueOf(extraPrefValue));
                } else if (isNumeric(extraPrefValue)) {
                    firefoxOptions.addPreference(extraPrefKey, Integer.valueOf(extraPrefValue));
                } else {
                    firefoxOptions.addPreference(extraPrefKey, extraPrefValue);
                }
            }
        }
    }

    /**
     * Metodo para configurar el WebDriver despues de su creacion. Puede ser
     * sobrescrito por clases derivadas para añadir configuraciones especificas.
     *
     * @param webDriver El WebDriver a configurar.
     * @param browsers La configuracion del navegador.
     */
    protected void configureWebDriver(WebDriver webDriver, Browsers browsers) {
        ((RemoteWebDriver) webDriver).setFileDetector(new LocalFileDetector());
    }

    /**
     * Determina si una cadena de texto representa un valor booleano.
     *
     * @param input La cadena de texto a evaluar.
     * @return Verdadero si la cadena es "true" o "false" (insensible a
     * mayusculas/minusculas), de lo contrario falso.
     */
    private boolean isBoolean(String input) {
        return StringUtils.equalsIgnoreCase("true", input) || StringUtils.equalsIgnoreCase("false", input);
    }

    /**
     * Determina si una cadena representa un valor numerico entero.
     *
     * @param input La cadena a evaluar
     * @return true si es un numero entero valido
     */
    private boolean isNumeric(String input) {
        return StringUtils.isNumeric(input);
    }
}
