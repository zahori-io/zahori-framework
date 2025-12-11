package io.zahori.framework.utils.selenium4;

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

import java.util.HashMap;
import java.util.Map;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.chromium.ChromiumDriver;
import org.openqa.selenium.chromium.ChromiumNetworkConditions;

/**
 * Utilidades para Chrome DevTools Protocol (CDP) - Selenium 4.
 *
 * CDP permite acceder a funcionalidades avanzadas del navegador Chrome/Edge:
 * - Emulacion de red (3G, offline, latencia)
 * - Geolocalizacion mock
 * - Metricas de rendimiento
 * - Intercepcion de red
 *
 * Ejemplo de uso:
 * <pre>
 * // Emular red 3G lenta
 * ChromeDevToolsUtils.emulateSlowNetwork(driver, NetworkCondition.SLOW_3G);
 *
 * // Mock geolocalizacion (Madrid)
 * ChromeDevToolsUtils.mockGeolocation(driver, 40.4168, -3.7038, 1);
 *
 * // Obtener metricas de rendimiento
 * Map<String, Number> metrics = ChromeDevToolsUtils.getPerformanceMetrics(driver);
 * </pre>
 *
 * NOTA: Solo funciona con ChromeDriver y EdgeDriver (navegadores Chromium).
 */
public final class ChromeDevToolsUtils {

    private static final Logger LOG = LogManager.getLogger(ChromeDevToolsUtils.class);

    private ChromeDevToolsUtils() {
        // Utility class
    }

    /**
     * Condiciones de red predefinidas para emulacion.
     */
    public enum NetworkCondition {
        /** Sin conexion */
        OFFLINE(true, 0, 0, 0),
        /** 3G lento (~400 Kbps) */
        SLOW_3G(false, 2000, 400_000, 400_000),
        /** 3G regular (~750 Kbps) */
        REGULAR_3G(false, 100, 750_000, 750_000),
        /** 4G (~4 Mbps) */
        FAST_4G(false, 20, 4_000_000, 4_000_000),
        /** WiFi (~30 Mbps) */
        WIFI(false, 2, 30_000_000, 30_000_000);

        final boolean offline;
        final int latency;
        final int downloadThroughput;
        final int uploadThroughput;

        NetworkCondition(boolean offline, int latency, int downloadThroughput, int uploadThroughput) {
            this.offline = offline;
            this.latency = latency;
            this.downloadThroughput = downloadThroughput;
            this.uploadThroughput = uploadThroughput;
        }
    }

    /**
     * Emula condiciones de red especificas (latencia, ancho de banda).
     * Usa ChromiumNetworkConditions API (Selenium 4 alto nivel, sin version CDP).
     *
     * @param driver WebDriver (debe ser ChromeDriver o EdgeDriver)
     * @param condition condicion de red a emular
     */
    public static void emulateNetworkCondition(WebDriver driver, NetworkCondition condition) {
        if (!(driver instanceof ChromiumDriver chromiumDriver)) {
            LOG.warn("Network emulation solo disponible para Chrome/Edge");
            return;
        }

        try {
            ChromiumNetworkConditions networkConditions = new ChromiumNetworkConditions();
            networkConditions.setOffline(condition.offline);
            networkConditions.setLatency(java.time.Duration.ofMillis(condition.latency));
            networkConditions.setDownloadThroughput(condition.downloadThroughput);
            networkConditions.setUploadThroughput(condition.uploadThroughput);

            chromiumDriver.setNetworkConditions(networkConditions);
            LOG.info("Network condition emulada: {}", condition);
        } catch (Exception e) {
            LOG.error("Error emulando network condition: {}", e.getMessage());
        }
    }

    /**
     * Desactiva la emulacion de red y restaura la conexion normal.
     *
     * @param driver WebDriver (debe ser ChromeDriver o EdgeDriver)
     */
    public static void disableNetworkEmulation(WebDriver driver) {
        if (!(driver instanceof ChromiumDriver chromiumDriver)) {
            return;
        }

        try {
            chromiumDriver.deleteNetworkConditions();
            LOG.info("Network emulation desactivada");
        } catch (Exception e) {
            LOG.error("Error desactivando network emulation: {}", e.getMessage());
        }
    }

    /**
     * Emula una geolocalizacion especifica (mock GPS).
     * Usa executeCdpCommand para evitar dependencias de version CDP.
     *
     * @param driver WebDriver (debe ser ChromeDriver o EdgeDriver)
     * @param latitude latitud
     * @param longitude longitud
     * @param accuracy precision en metros
     */
    public static void mockGeolocation(WebDriver driver, double latitude, double longitude, int accuracy) {
        if (!(driver instanceof ChromiumDriver chromiumDriver)) {
            LOG.warn("Geolocation mock solo disponible para Chrome/Edge");
            return;
        }

        try {
            Map<String, Object> coordinates = new HashMap<>();
            coordinates.put("latitude", latitude);
            coordinates.put("longitude", longitude);
            coordinates.put("accuracy", accuracy);
            chromiumDriver.executeCdpCommand("Emulation.setGeolocationOverride", coordinates);
            LOG.info("Geolocation mockeada: lat={}, lon={}, accuracy={}", latitude, longitude, accuracy);
        } catch (Exception e) {
            LOG.error("Error mockeando geolocation: {}", e.getMessage());
        }
    }

    /**
     * Geolocalizaciones predefinidas comunes.
     */
    public static void mockGeolocationMadrid(WebDriver driver) {
        mockGeolocation(driver, 40.4168, -3.7038, 1);
    }

    public static void mockGeolocationBarcelona(WebDriver driver) {
        mockGeolocation(driver, 41.3851, 2.1734, 1);
    }

    public static void mockGeolocationLondon(WebDriver driver) {
        mockGeolocation(driver, 51.5074, -0.1278, 1);
    }

    public static void mockGeolocationNewYork(WebDriver driver) {
        mockGeolocation(driver, 40.7128, -74.0060, 1);
    }

    /**
     * Limpia la geolocalizacion mockeada y restaura el comportamiento real.
     *
     * @param driver WebDriver
     */
    public static void clearGeolocationOverride(WebDriver driver) {
        if (!(driver instanceof ChromiumDriver chromiumDriver)) {
            return;
        }

        try {
            chromiumDriver.executeCdpCommand("Emulation.clearGeolocationOverride", new HashMap<>());
            LOG.info("Geolocation override limpiado");
        } catch (Exception e) {
            LOG.error("Error limpiando geolocation: {}", e.getMessage());
        }
    }

    /**
     * Obtiene metricas de rendimiento de la pagina actual.
     * Usa executeCdpCommand para evitar dependencias de version CDP.
     *
     * @param driver WebDriver (debe ser ChromeDriver o EdgeDriver)
     * @return Map con metricas (JSHeapUsedSize, Documents, Frames, etc.)
     */
    @SuppressWarnings("unchecked")
    public static Map<String, Number> getPerformanceMetrics(WebDriver driver) {
        if (!(driver instanceof ChromiumDriver chromiumDriver)) {
            LOG.warn("Performance metrics solo disponible para Chrome/Edge");
            return new HashMap<>();
        }

        try {
            chromiumDriver.executeCdpCommand("Performance.enable", new HashMap<>());
            Map<String, Object> response = chromiumDriver.executeCdpCommand("Performance.getMetrics", new HashMap<>());

            Map<String, Number> result = new HashMap<>();
            if (response.containsKey("metrics")) {
                var metricsList = (java.util.List<Map<String, Object>>) response.get("metrics");
                for (Map<String, Object> metric : metricsList) {
                    String name = (String) metric.get("name");
                    Number value = (Number) metric.get("value");
                    result.put(name, value);
                }
            }

            LOG.debug("Performance metrics obtenidas: {} metricas", result.size());
            return result;
        } catch (Exception e) {
            LOG.error("Error obteniendo performance metrics: {}", e.getMessage());
            return new HashMap<>();
        }
    }

    /**
     * Emula un dispositivo movil (viewport, user-agent, touch).
     * Usa executeCdpCommand para evitar dependencias de version CDP.
     *
     * @param driver WebDriver
     * @param width ancho del viewport
     * @param height alto del viewport
     * @param deviceScaleFactor factor de escala (1.0, 2.0, 3.0)
     * @param mobile true para emular dispositivo movil
     */
    public static void emulateDevice(WebDriver driver, int width, int height, double deviceScaleFactor, boolean mobile) {
        if (!(driver instanceof ChromiumDriver chromiumDriver)) {
            LOG.warn("Device emulation solo disponible para Chrome/Edge");
            return;
        }

        try {
            Map<String, Object> deviceMetrics = new HashMap<>();
            deviceMetrics.put("width", width);
            deviceMetrics.put("height", height);
            deviceMetrics.put("deviceScaleFactor", deviceScaleFactor);
            deviceMetrics.put("mobile", mobile);

            chromiumDriver.executeCdpCommand("Emulation.setDeviceMetricsOverride", deviceMetrics);
            LOG.info("Device emulado: {}x{}, scale={}, mobile={}", width, height, deviceScaleFactor, mobile);
        } catch (Exception e) {
            LOG.error("Error emulando device: {}", e.getMessage());
        }
    }

    /**
     * Emulaciones de dispositivos predefinidas.
     */
    public static void emulateIPhone12(WebDriver driver) {
        emulateDevice(driver, 390, 844, 3.0, true);
    }

    public static void emulateIPad(WebDriver driver) {
        emulateDevice(driver, 768, 1024, 2.0, true);
    }

    public static void emulatePixel5(WebDriver driver) {
        emulateDevice(driver, 393, 851, 2.75, true);
    }

    /**
     * Limpia la emulacion de dispositivo.
     * Usa executeCdpCommand para evitar dependencias de version CDP.
     *
     * @param driver WebDriver
     */
    public static void clearDeviceEmulation(WebDriver driver) {
        if (!(driver instanceof ChromiumDriver chromiumDriver)) {
            return;
        }

        try {
            chromiumDriver.executeCdpCommand("Emulation.clearDeviceMetricsOverride", new HashMap<>());
            LOG.info("Device emulation limpiada");
        } catch (Exception e) {
            LOG.error("Error limpiando device emulation: {}", e.getMessage());
        }
    }

    /**
     * Verifica si el driver soporta DevTools (CDP).
     *
     * @param driver WebDriver a verificar
     * @return true si soporta CDP
     */
    public static boolean supportsDevTools(WebDriver driver) {
        return driver instanceof ChromiumDriver;
    }

    // ==================== CPU Throttling ====================

    /**
     * Perfiles de CPU predefinidos para throttling.
     */
    public enum CPUThrottling {
        /** Sin throttling */
        NO_THROTTLE(1),
        /** CPU lento (4x mas lento) - Movil gama media */
        SLOW_4X(4),
        /** CPU muy lento (6x mas lento) - Movil gama baja */
        VERY_SLOW_6X(6),
        /** CPU extremadamente lento (20x) - Dispositivos antiguos */
        EXTREME_20X(20);

        final int rate;

        CPUThrottling(int rate) {
            this.rate = rate;
        }
    }

    /**
     * Aplica throttling de CPU para emular dispositivos lentos.
     * Util para probar rendimiento en dispositivos moviles de gama baja.
     *
     * @param driver WebDriver
     * @param throttling perfil de throttling a aplicar
     */
    public static void setCPUThrottling(WebDriver driver, CPUThrottling throttling) {
        if (!(driver instanceof ChromiumDriver chromiumDriver)) {
            LOG.warn("CPU throttling solo disponible para Chrome/Edge");
            return;
        }

        try {
            Map<String, Object> params = new HashMap<>();
            params.put("rate", throttling.rate);
            chromiumDriver.executeCdpCommand("Emulation.setCPUThrottlingRate", params);
            LOG.info("CPU throttling aplicado: {}x slowdown", throttling.rate);
        } catch (Exception e) {
            LOG.error("Error aplicando CPU throttling: {}", e.getMessage());
        }
    }

    /**
     * Desactiva el throttling de CPU.
     *
     * @param driver WebDriver
     */
    public static void clearCPUThrottling(WebDriver driver) {
        setCPUThrottling(driver, CPUThrottling.NO_THROTTLE);
    }

    // ==================== Timezone Override ====================

    /**
     * Establece una zona horaria especifica para el navegador.
     *
     * @param driver WebDriver
     * @param timezoneId ID de zona horaria IANA (ej: "Europe/Madrid", "America/New_York")
     */
    public static void setTimezoneOverride(WebDriver driver, String timezoneId) {
        if (!(driver instanceof ChromiumDriver chromiumDriver)) {
            LOG.warn("Timezone override solo disponible para Chrome/Edge");
            return;
        }

        try {
            Map<String, Object> params = new HashMap<>();
            params.put("timezoneId", timezoneId);
            chromiumDriver.executeCdpCommand("Emulation.setTimezoneOverride", params);
            LOG.info("Timezone override: {}", timezoneId);
        } catch (Exception e) {
            LOG.error("Error configurando timezone: {}", e.getMessage());
        }
    }

    /**
     * Timezones predefinidas comunes.
     */
    public static void setTimezoneMadrid(WebDriver driver) {
        setTimezoneOverride(driver, "Europe/Madrid");
    }

    public static void setTimezoneLondon(WebDriver driver) {
        setTimezoneOverride(driver, "Europe/London");
    }

    public static void setTimezoneNewYork(WebDriver driver) {
        setTimezoneOverride(driver, "America/New_York");
    }

    public static void setTimezoneTokyo(WebDriver driver) {
        setTimezoneOverride(driver, "Asia/Tokyo");
    }

    // ==================== Locale Override ====================

    /**
     * Establece el locale del navegador (idioma y region).
     *
     * @param driver WebDriver
     * @param locale locale a establecer (ej: "es-ES", "en-US", "fr-FR")
     */
    public static void setLocaleOverride(WebDriver driver, String locale) {
        if (!(driver instanceof ChromiumDriver chromiumDriver)) {
            LOG.warn("Locale override solo disponible para Chrome/Edge");
            return;
        }

        try {
            Map<String, Object> params = new HashMap<>();
            params.put("locale", locale);
            chromiumDriver.executeCdpCommand("Emulation.setLocaleOverride", params);
            LOG.info("Locale override: {}", locale);
        } catch (Exception e) {
            LOG.error("Error configurando locale: {}", e.getMessage());
        }
    }

    // ==================== User Agent Override ====================

    /**
     * Establece un User-Agent personalizado.
     *
     * @param driver WebDriver
     * @param userAgent string de User-Agent
     */
    public static void setUserAgent(WebDriver driver, String userAgent) {
        if (!(driver instanceof ChromiumDriver chromiumDriver)) {
            LOG.warn("User-Agent override solo disponible para Chrome/Edge");
            return;
        }

        try {
            Map<String, Object> params = new HashMap<>();
            params.put("userAgent", userAgent);
            chromiumDriver.executeCdpCommand("Emulation.setUserAgentOverride", params);
            LOG.info("User-Agent override aplicado");
        } catch (Exception e) {
            LOG.error("Error configurando User-Agent: {}", e.getMessage());
        }
    }

    /**
     * User-Agents predefinidos comunes.
     */
    public static void setUserAgentChromeMobile(WebDriver driver) {
        setUserAgent(driver, "Mozilla/5.0 (Linux; Android 12; Pixel 5) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36");
    }

    public static void setUserAgentSafariIPhone(WebDriver driver) {
        setUserAgent(driver, "Mozilla/5.0 (iPhone; CPU iPhone OS 17_0 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/17.0 Mobile/15E148 Safari/604.1");
    }

    public static void setUserAgentFirefoxDesktop(WebDriver driver) {
        setUserAgent(driver, "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:121.0) Gecko/20100101 Firefox/121.0");
    }

    // ==================== Touch Emulation ====================

    /**
     * Habilita/deshabilita la emulacion de eventos touch.
     *
     * @param driver WebDriver
     * @param enabled true para habilitar touch, false para deshabilitar
     */
    public static void setTouchEmulation(WebDriver driver, boolean enabled) {
        if (!(driver instanceof ChromiumDriver chromiumDriver)) {
            LOG.warn("Touch emulation solo disponible para Chrome/Edge");
            return;
        }

        try {
            Map<String, Object> params = new HashMap<>();
            params.put("enabled", enabled);
            params.put("maxTouchPoints", enabled ? 5 : 0);
            chromiumDriver.executeCdpCommand("Emulation.setTouchEmulationEnabled", params);
            LOG.info("Touch emulation: {}", enabled ? "habilitado" : "deshabilitado");
        } catch (Exception e) {
            LOG.error("Error configurando touch emulation: {}", e.getMessage());
        }
    }

    // ==================== Console & Exceptions ====================

    /**
     * Limpia la consola del navegador.
     *
     * @param driver WebDriver
     */
    public static void clearBrowserConsole(WebDriver driver) {
        if (!(driver instanceof ChromiumDriver chromiumDriver)) {
            return;
        }

        try {
            chromiumDriver.executeCdpCommand("Console.clearMessages", new HashMap<>());
            LOG.info("Consola del navegador limpiada");
        } catch (Exception e) {
            LOG.debug("Error limpiando consola: {}", e.getMessage());
        }
    }

    // ==================== Cache Control ====================

    /**
     * Deshabilita el cache del navegador.
     * Util para asegurar que siempre se obtienen recursos frescos.
     *
     * @param driver WebDriver
     * @param disabled true para deshabilitar cache
     */
    public static void setCacheDisabled(WebDriver driver, boolean disabled) {
        if (!(driver instanceof ChromiumDriver chromiumDriver)) {
            LOG.warn("Cache control solo disponible para Chrome/Edge");
            return;
        }

        try {
            Map<String, Object> params = new HashMap<>();
            params.put("cacheDisabled", disabled);
            chromiumDriver.executeCdpCommand("Network.setCacheDisabled", params);
            LOG.info("Cache del navegador: {}", disabled ? "deshabilitado" : "habilitado");
        } catch (Exception e) {
            LOG.error("Error configurando cache: {}", e.getMessage());
        }
    }

    /**
     * Limpia la cache del navegador.
     *
     * @param driver WebDriver
     */
    public static void clearBrowserCache(WebDriver driver) {
        if (!(driver instanceof ChromiumDriver chromiumDriver)) {
            return;
        }

        try {
            chromiumDriver.executeCdpCommand("Network.clearBrowserCache", new HashMap<>());
            LOG.info("Cache del navegador limpiada");
        } catch (Exception e) {
            LOG.error("Error limpiando cache: {}", e.getMessage());
        }
    }

    /**
     * Limpia las cookies del navegador via CDP.
     *
     * @param driver WebDriver
     */
    public static void clearBrowserCookies(WebDriver driver) {
        if (!(driver instanceof ChromiumDriver chromiumDriver)) {
            return;
        }

        try {
            chromiumDriver.executeCdpCommand("Network.clearBrowserCookies", new HashMap<>());
            LOG.info("Cookies del navegador limpiadas");
        } catch (Exception e) {
            LOG.error("Error limpiando cookies: {}", e.getMessage());
        }
    }

    // ==================== Page Screenshot via CDP ====================

    /**
     * Captura screenshot de la pagina completa incluyendo contenido fuera del viewport.
     * Usa CDP directamente para capturar toda la pagina.
     *
     * @param driver WebDriver
     * @return bytes del screenshot en PNG, o null si falla
     */
    @SuppressWarnings("unchecked")
    public static byte[] captureFullPageScreenshot(WebDriver driver) {
        if (!(driver instanceof ChromiumDriver chromiumDriver)) {
            LOG.warn("Full page screenshot via CDP solo disponible para Chrome/Edge");
            return null;
        }

        try {
            Map<String, Object> params = new HashMap<>();
            params.put("captureBeyondViewport", true);
            params.put("format", "png");

            Map<String, Object> result = chromiumDriver.executeCdpCommand("Page.captureScreenshot", params);
            String base64Data = (String) result.get("data");

            if (base64Data != null) {
                byte[] screenshot = java.util.Base64.getDecoder().decode(base64Data);
                LOG.info("Full page screenshot capturado: {} bytes", screenshot.length);
                return screenshot;
            }
        } catch (Exception e) {
            LOG.error("Error capturando full page screenshot: {}", e.getMessage());
        }

        return null;
    }

    // ==================== Emulation Reset ====================

    /**
     * Resetea todas las emulaciones a valores por defecto.
     *
     * @param driver WebDriver
     */
    public static void resetAllEmulations(WebDriver driver) {
        clearDeviceEmulation(driver);
        clearGeolocationOverride(driver);
        clearCPUThrottling(driver);
        disableNetworkEmulation(driver);
        setCacheDisabled(driver, false);
        LOG.info("Todas las emulaciones reseteadas");
    }
}
