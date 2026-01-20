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

import io.zahori.framework.files.properties.ZahoriProperties;
import io.zahori.framework.security.ZahoriCipher;
import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.openqa.selenium.UsernameAndPassword;

import java.net.URI;
import java.util.function.Predicate;

/**
 * Configuración centralizada para proxy HTTP (autenticado y no autenticado).
 *
 * <p>Lee la configuración desde zahori.properties del proyecto consumidor:</p>
 * <pre>
 * # Proxy HTTP/HTTPS (opcional)
 * zahori.test.execution.proxy.ip=proxy-bcn.inti.com
 * zahori.test.execution.proxy.port=8080
 *
 * # Autenticación del proxy (opcional, usar ZahoriCipher para cifrar password)
 * zahori.test.execution.proxy.user=jturegano
 * zahori.test.execution.proxy.password=ENCRYPTED_PASSWORD_HERE
 * </pre>
 *
 * <p>Soporta tres escenarios:</p>
 * <ul>
 *   <li><b>Sin proxy</b>: No se configura proxy.ip</li>
 *   <li><b>Proxy sin autenticación</b>: Solo proxy.ip y proxy.port</li>
 *   <li><b>Proxy con autenticación (407)</b>: proxy.ip, port, user y password cifrado</li>
 * </ul>
 *
 * <p>Para cifrar la contraseña del proxy, usar ZahoriCipher_GUI o:</p>
 * <pre>
 * ZahoriCipher cipher = new ZahoriCipher();
 * String encrypted = cipher.encode("miPassword");
 * </pre>
 *
 * @since 2.0.0
 * @see org.openqa.selenium.HasAuthentication
 * @see io.zahori.framework.security.ZahoriCipher
 */
public final class ProxyAuthConfig {

    private static final Logger LOG = LogManager.getLogger();

    /** Singleton lazy-loaded para evitar múltiples lecturas de properties */
    private static volatile ProxyAuthConfig instance;

    private final String proxyHost;
    private final int proxyPort;
    private final String proxyUser;
    private final String proxyPassword;

    private ProxyAuthConfig() {
        ZahoriProperties props = new ZahoriProperties();

        // Leer configuración de proxy desde zahori.properties
        this.proxyHost = props.getProxyIP();
        this.proxyPort = props.getProxyPort();
        this.proxyUser = props.getProxyUser();

        // Password cifrado - decodificar con ZahoriCipher
        String encodedPassword = props.getProxyEncodedPassword();
        this.proxyPassword = decodePassword(encodedPassword);

        logConfiguration();
    }

    /**
     * Obtiene la instancia singleton de configuración.
     * Thread-safe mediante double-checked locking.
     *
     * @return instancia de ProxyAuthConfig
     */
    private static ProxyAuthConfig getInstance() {
        ProxyAuthConfig localRef = instance;
        if (localRef == null) {
            synchronized (ProxyAuthConfig.class) {
                localRef = instance;
                if (localRef == null) {
                    instance = localRef = new ProxyAuthConfig();
                }
            }
        }
        return localRef;
    }

    /**
     * Decodifica la contraseña cifrada usando ZahoriCipher.
     *
     * @param encodedPassword contraseña cifrada en Base64
     * @return contraseña decodificada o null si no hay password
     */
    private String decodePassword(String encodedPassword) {
        if (StringUtils.isBlank(encodedPassword)) {
            return null;
        }
        try {
            ZahoriCipher cipher = new ZahoriCipher();
            return cipher.decode(encodedPassword);
        } catch (Exception e) {
            LOG.error("Error decodificando password del proxy: {}", e.getMessage());
            return null;
        }
    }

    /**
     * Log de la configuración (sin exponer credenciales).
     */
    private void logConfiguration() {
        if (isProxyEnabledInternal()) {
            LOG.info("Proxy configurado: {}:{}", proxyHost, proxyPort);
            if (isAuthenticationRequiredInternal()) {
                LOG.info("Autenticación de proxy habilitada para usuario: {}", proxyUser);
            } else {
                LOG.debug("Proxy sin autenticación");
            }
        } else {
            LOG.debug("Proxy no configurado");
        }
    }

    // ==================== MÉTODOS INTERNOS (instancia) ====================

    private boolean isProxyEnabledInternal() {
        return StringUtils.isNotBlank(proxyHost) && proxyPort > 0;
    }

    private boolean isAuthenticationRequiredInternal() {
        return isProxyEnabledInternal()
            && StringUtils.isNotBlank(proxyUser)
            && StringUtils.isNotBlank(proxyPassword);
    }

    private String getProxyAddressInternal() {
        return isProxyEnabledInternal() ? proxyHost + ":" + proxyPort : null;
    }

    // ==================== MÉTODOS ESTÁTICOS PÚBLICOS ====================

    /**
     * Indica si el uso de proxy está habilitado.
     *
     * @return true si se debe usar proxy (autenticado o no)
     */
    public static boolean isProxyEnabled() {
        return getInstance().isProxyEnabledInternal();
    }

    /**
     * Indica si el proxy requiere autenticación (HTTP 407).
     *
     * Solo devuelve true si:
     * - El proxy está habilitado (host y puerto configurados)
     * - Hay usuario configurado
     * - Hay password configurado (y decodificado correctamente)
     *
     * @return true si se requiere autenticación de proxy
     */
    public static boolean isAuthenticationRequired() {
        return getInstance().isAuthenticationRequiredInternal();
    }

    /**
     * Obtiene el host del proxy.
     *
     * @return hostname del proxy o null si no está configurado
     */
    public static String getHost() {
        ProxyAuthConfig config = getInstance();
        return config.isProxyEnabledInternal() ? config.proxyHost : null;
    }

    /**
     * Obtiene el puerto del proxy.
     *
     * @return puerto del proxy o -1 si no está configurado
     */
    public static int getPort() {
        return getInstance().proxyPort;
    }

    /**
     * Obtiene la dirección completa del proxy (host:port).
     *
     * @return dirección del proxy en formato host:port, o null si no está habilitado
     */
    public static String getProxyAddress() {
        return getInstance().getProxyAddressInternal();
    }

    /**
     * Obtiene las credenciales para autenticación HTTP 407.
     *
     * Usado por HasAuthentication.register() para responder a desafíos
     * de autenticación del proxy.
     *
     * @return credenciales de usuario y contraseña, o null si no hay autenticación
     */
    public static UsernameAndPassword getCredentials() {
        ProxyAuthConfig config = getInstance();
        if (!config.isAuthenticationRequiredInternal()) {
            return null;
        }
        return UsernameAndPassword.of(config.proxyUser, config.proxyPassword);
    }

    /**
     * Obtiene el predicado que filtra URIs para autenticación.
     *
     * Por defecto, aplica autenticación a TODAS las URIs que pasen
     * por el proxy. El proxy interceptará las peticiones y Selenium
     * responderá al desafío 407 automáticamente.
     *
     * @return predicado para filtrar URIs, o null si no hay autenticación
     */
    public static Predicate<URI> getUriPredicate() {
        return isAuthenticationRequired() ? uri -> true : null;
    }

    /**
     * Reinicia la configuración (útil para tests).
     * Fuerza la recarga de zahori.properties en la próxima llamada.
     */
    static void reset() {
        synchronized (ProxyAuthConfig.class) {
            instance = null;
        }
    }
}
