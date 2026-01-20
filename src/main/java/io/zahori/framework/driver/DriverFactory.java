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
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.openqa.selenium.Proxy;
import org.openqa.selenium.WebDriver;

/**
 * Factory class to create WebDriver instances.
 *
 * Soporta tres escenarios de proxy (configurados en zahori.properties):
 * <ul>
 *   <li><b>Sin proxy</b>: No se configura zahori.test.execution.proxy.ip</li>
 *   <li><b>Proxy sin autenticación</b>: Solo proxy.ip y proxy.port</li>
 *   <li><b>Proxy con autenticación (407)</b>: proxy.ip, port, user y password cifrado</li>
 * </ul>
 *
 * La autenticación 407 se maneja mediante HasAuthentication en los drivers
 * individuales (RemoteDriver/LocalDriver).
 *
 * @since 2.0.0 - Añadido soporte para proxy autenticado
 */
public class DriverFactory implements AbstractFactory<WebDriver> {

    private static final Logger LOG = LogManager.getLogger();

    /**
     * Creates a WebDriver instance based on the specified browser settings.
     *
     * Si el proxy proporcionado es null y hay configuración de proxy en
     * zahori.properties, se crea un proxy con host/puerto configurados.
     *
     * @param browsers The browser settings used to configure the WebDriver.
     * @param proxy The proxy configuration (can be null).
     * @return A WebDriver instance.
     */
    @Override
    public WebDriver create(Browsers browsers, Proxy proxy) {
        Proxy effectiveProxy = ensureProxyConfiguration(proxy);
        return isRemoteExecution(browsers)
            ? createRemoteDriver(browsers, effectiveProxy)
            : createLocalDriver(browsers, effectiveProxy);
    }

    /**
     * Asegura que el proxy tenga la configuración correcta de host/puerto.
     *
     * Escenarios:
     * 1. Sin proxy configurado en zahori.properties -> devuelve proxy original (puede ser null)
     * 2. Proxy configurado y proxy=null -> crea nuevo Proxy con configuración
     * 3. Proxy configurado y proxy existente sin httpProxy -> actualiza con configuración
     * 4. Proxy existente con httpProxy -> usa el existente sin modificar
     *
     * La autenticación (usuario/password) se maneja por separado mediante
     * HasAuthentication.register() en los drivers.
     *
     * @param proxy proxy original (puede ser null)
     * @return proxy configurado o el original
     */
    private Proxy ensureProxyConfiguration(Proxy proxy) {
        // Si no hay proxy configurado en zahori.properties, usar el proporcionado (o null)
        if (!ProxyAuthConfig.isProxyEnabled()) {
            LOG.debug("Proxy no configurado en zahori.properties");
            return proxy;
        }

        String proxyAddress = ProxyAuthConfig.getProxyAddress();

        // Si no hay proxy proporcionado, crear uno nuevo con configuración de properties
        if (proxy == null) {
            proxy = new Proxy();
            proxy.setHttpProxy(proxyAddress);
            proxy.setSslProxy(proxyAddress);
            LOG.info("Proxy configurado desde zahori.properties: {}", proxyAddress);
            return proxy;
        }

        // Si el proxy existe pero no tiene httpProxy, configurarlo
        if (proxy.getHttpProxy() == null) {
            proxy.setHttpProxy(proxyAddress);
            proxy.setSslProxy(proxyAddress);
            LOG.info("Proxy existente actualizado con: {}", proxyAddress);
        } else {
            LOG.debug("Usando proxy preconfigurado: {}", proxy.getHttpProxy());
        }

        return proxy;
    }

    /**
     * Determines if the execution should be remote based on the browser
     * settings.
     *
     * @param browsers The browser settings.
     * @return true if execution is remote, false otherwise.
     */
    private boolean isRemoteExecution(Browsers browsers) {
        return ExecutionType.REMOTE.getName().equalsIgnoreCase(browsers.getRemote());
    }

    /**
     * Creates a remote WebDriver based on the browser settings.
     *
     * @param browsers The browser settings.
     * @param proxy The proxy configuration.
     * @return A remote WebDriver instance.
     */
    private WebDriver createRemoteDriver(Browsers browsers, Proxy proxy) {
        LOG.debug("Creando driver remoto para: {}", browsers.getName());
        return new RemoteDriver().getDriver(browsers, proxy);
    }

    /**
     * Creates a local WebDriver based on the browser settings.
     *
     * @param browsers The browser settings.
     * @param proxy The proxy configuration.
     * @return A local WebDriver instance.
     */
    private WebDriver createLocalDriver(Browsers browsers, Proxy proxy) {
        LOG.debug("Creando driver local para: {}", browsers.getName());
        return new LocalDriver().getDriver(browsers, proxy);
    }
}
