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
import org.openqa.selenium.MutableCapabilities;
import org.openqa.selenium.PageLoadStrategy;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.openqa.selenium.edge.EdgeOptions;
import org.openqa.selenium.firefox.FirefoxOptions;
import org.openqa.selenium.remote.AbstractDriverOptions;
import org.openqa.selenium.remote.LocalFileDetector;
import org.openqa.selenium.remote.RemoteWebDriver;

/**
 * Clase abstracta que define la estructura y comportamientos comunes de un driver.
 * Esta clase debe ser extendida por clases especificas de drivers como RemoteDriver o LocalDriver.
 */
public abstract class AbstractDriver implements Driver {

    /**
     * Metodo abstracto para crear una instancia de WebDriver.
     * Debe ser implementado por clases derivadas con logica especifica para cada tipo de driver.
     *
     * @param browsers La configuracion del navegador.
     * @return Una instancia de WebDriver.
     */
    protected abstract WebDriver createWebDriver(Browsers browsers);

    /**
     * Crea y configura un WebDriver basado en la configuracion de navegadores proporcionada.
     *
     * @param browsers La configuracion del navegador.
     * @return Un WebDriver configurado.
     */
    @Override
    public WebDriver getDriver(Browsers browsers) {
        WebDriver webDriver = createWebDriver(browsers);
        configureWebDriver(webDriver, browsers);
        return webDriver;
    }

    /**
     * Configura las opciones para el WebDriver.
     * Este metodo crea y configura opciones comunes para todos los tipos de drivers.
     *
     * @param browsers La configuracion del navegador.
     * @return Opciones configuradas para el WebDriver.
     */
    @Override
    public AbstractDriverOptions<?> getOptions(Browsers browsers) {
        AbstractDriverOptions<?> options = OptionsFactory.valueOf(browsers.getName()).getOptions();
        options.setAcceptInsecureCerts(true);
        /*
            PageLoadStrategy:
            - normal	Ready State=complete --> Used by default, waits for all resources to download
            - eager	Ready State=interactive	--> DOM access is ready, but other resources like images may still be loading
            - none	Ready State=Any	--> Does not block WebDriver at all
        */
        options.setPageLoadStrategy(PageLoadStrategy.NONE);
        setZahoriPropertiesBrowserOptions(options);

        MutableCapabilities capabilities = new MutableCapabilities();
        capabilities.setCapability("name", browsers.getCaseExecutionId());
        capabilities.setCapability("testName", browsers.getTestName());
        capabilities.setCapability("screenResolution", browsers.getScreenResolution());
        setZahoriPropertiesExtraCaps(capabilities);
        
        options.merge(capabilities);

        System.out.println(browsers.getName() + " browser options and capabilities: " + options.toString());
        return options;
    }

    /**
     * Configura capacidades adicionales del WebDriver basadas en las propiedades de Zahori.
     *
     * @param capabilities Objeto MutableCapabilities para agregar capacidades.
     */
    private void setZahoriPropertiesExtraCaps(MutableCapabilities capabilities) {
        var extraCapabilities = new ZahoriProperties().getExtraCapabilities();
        extraCapabilities.forEach((extraCap, value) -> {
            if (isBoolean(value)) {
                capabilities.setCapability(extraCap, Boolean.valueOf(value));
            } else {
                capabilities.setCapability(extraCap, value);
            }
        });
    }

    /**
     * Configura opciones adicionales del navegador basadas en las propiedades de Zahori.
     *
     * @param options Objeto AbstractDriverOptions para agregar opciones del navegador.
     */
    private void setZahoriPropertiesBrowserOptions (AbstractDriverOptions<?> options) {
        
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
                } else if (isDigit(extraPrefValue)) {
                    firefoxOptions.addPreference(extraPrefKey, Integer.valueOf(extraPrefValue));
                } else {
                    firefoxOptions.addPreference(extraPrefKey, extraPrefValue);
                }
            }
        }
    }
    
    /**
     * Metodo para configurar el WebDriver despues de su creacion.
     * Puede ser sobrescrito por clases derivadas para añadir configuraciones especificas.
     *
     * @param webDriver El WebDriver a configurar.
     * @param browsers  La configuracion del navegador.
     */
    protected void configureWebDriver(WebDriver webDriver, Browsers browsers) {
        ((RemoteWebDriver) webDriver).setFileDetector(new LocalFileDetector());
    }

    /**
     * Determina si una cadena de texto representa un valor booleano.
     *
     * @param input La cadena de texto a evaluar.
     * @return Verdadero si la cadena es "true" o "false" (insensible a mayusculas/minusculas), de lo contrario falso.
     */
    private boolean isBoolean(String input) {
        return StringUtils.equalsIgnoreCase("true", input) || StringUtils.equalsIgnoreCase("false", input);
    }
    
    private boolean isDigit(String input) {
        return StringUtils.equalsIgnoreCase("0", input) || StringUtils.equalsIgnoreCase("1", input) || StringUtils.equalsIgnoreCase("2", input)
                || StringUtils.equalsIgnoreCase("3", input) || StringUtils.equalsIgnoreCase("4", input) || StringUtils.equalsIgnoreCase("5", input)
                || StringUtils.equalsIgnoreCase("6", input) || StringUtils.equalsIgnoreCase("7", input) || StringUtils.equalsIgnoreCase("8", input)
                || StringUtils.equalsIgnoreCase("9", input);
    }
}
