package io.zahori.framework.driver;

import io.zahori.framework.driver.browserfactory.Browsers;
import io.zahori.framework.files.properties.ZahoriProperties;
import org.apache.commons.lang3.StringUtils;
import org.openqa.selenium.MutableCapabilities;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.remote.AbstractDriverOptions;
import org.openqa.selenium.remote.LocalFileDetector;
import org.openqa.selenium.remote.RemoteWebDriver;

import java.time.Duration;

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
    public AbstractDriverOptions<?> getOptions(Browsers browsers) {
        AbstractDriverOptions<?> options = OptionsFactory.valueOf(browsers.name()).getOptions();
        options.setAcceptInsecureCerts(true);
        options.setImplicitWaitTimeout(Duration.ofSeconds(100));

        MutableCapabilities capabilities = new MutableCapabilities();
        capabilities.setCapability("name", browsers.getCaseExecutionId());
        capabilities.setCapability("testName", browsers.getTestName());
        capabilities.setCapability("screenResolution", browsers.getScreenResolution());
        setZahoriPropertiesExtraCaps(capabilities);

        options.merge(capabilities);

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
}
