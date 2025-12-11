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

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Consumer;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.bidi.module.LogInspector;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.edge.EdgeDriver;
import org.openqa.selenium.firefox.FirefoxDriver;
import org.openqa.selenium.logging.LogEntries;
import org.openqa.selenium.logging.LogEntry;
import org.openqa.selenium.logging.LogType;

/**
 * Captura de logs del navegador usando WebDriver BiDi (Selenium 4).
 *
 * BiDi permite capturar logs de consola JavaScript en tiempo real via WebSocket.
 * Para navegadores que no soportan BiDi, se usa el metodo legacy via DevTools.
 *
 * Ejemplo de uso:
 * <pre>
 * // Capturar todos los logs de consola
 * BrowserLogCapture.startCapturing(driver);
 * // ... ejecutar acciones ...
 * List<String> logs = BrowserLogCapture.getLogs(driver);
 * BrowserLogCapture.stopCapturing(driver);
 *
 * // Listener en tiempo real
 * BrowserLogCapture.onConsoleLog(driver, log -> System.out.println("JS: " + log));
 * </pre>
 *
 * Navegadores soportados:
 * - Chrome/Edge: BiDi + DevTools
 * - Firefox: BiDi nativo
 * - Safari: No soportado
 */
public final class BrowserLogCapture {

    private static final Logger LOG = LogManager.getLogger(BrowserLogCapture.class);

    // Almacen de logs capturados por driver
    private static final List<String> capturedLogs = Collections.synchronizedList(new ArrayList<>());
    private static LogInspector activeLogInspector;

    private BrowserLogCapture() {
        // Utility class
    }

    /**
     * Niveles de log del navegador.
     */
    public enum LogLevel {
        ALL,
        DEBUG,
        INFO,
        WARNING,
        ERROR
    }

    /**
     * Inicia la captura de logs de consola del navegador.
     * Los logs se almacenan internamente y pueden recuperarse con getLogs().
     *
     * @param driver WebDriver
     */
    public static void startCapturing(WebDriver driver) {
        capturedLogs.clear();

        if (supportsBiDi(driver)) {
            try {
                activeLogInspector = new LogInspector(driver);
                activeLogInspector.onConsoleEntry(entry -> {
                    String logMessage = String.format("[%s] %s",
                            entry.getLevel().toString().toUpperCase(),
                            entry.getText());
                    capturedLogs.add(logMessage);
                    LOG.debug("Browser console: {}", logMessage);
                });
                LOG.info("BiDi log capture iniciado");
            } catch (Exception e) {
                LOG.warn("BiDi no disponible, usando metodo legacy: {}", e.getMessage());
            }
        } else {
            LOG.info("Driver no soporta BiDi, los logs se obtendran via DevTools/LogType");
        }
    }

    /**
     * Detiene la captura de logs.
     *
     * @param driver WebDriver
     */
    public static void stopCapturing(WebDriver driver) {
        if (activeLogInspector != null) {
            try {
                activeLogInspector.close();
                activeLogInspector = null;
                LOG.info("BiDi log capture detenido");
            } catch (Exception e) {
                LOG.debug("Error cerrando LogInspector: {}", e.getMessage());
            }
        }
    }

    /**
     * Obtiene los logs capturados durante la sesion.
     *
     * @param driver WebDriver
     * @return Lista de mensajes de log
     */
    public static List<String> getLogs(WebDriver driver) {
        // Si hay logs capturados via BiDi, devolverlos
        if (!capturedLogs.isEmpty()) {
            return new ArrayList<>(capturedLogs);
        }

        // Fallback: intentar obtener logs via DevTools/LogType (Chrome/Edge)
        return getLogsViaDevTools(driver);
    }

    /**
     * Obtiene logs de un nivel especifico.
     *
     * @param driver WebDriver
     * @param level nivel minimo de log
     * @return Lista filtrada de logs
     */
    public static List<String> getLogs(WebDriver driver, LogLevel level) {
        List<String> allLogs = getLogs(driver);
        if (level == LogLevel.ALL) {
            return allLogs;
        }

        return allLogs.stream()
                .filter(log -> matchesLevel(log, level))
                .toList();
    }

    /**
     * Obtiene solo los errores JavaScript del navegador.
     *
     * @param driver WebDriver
     * @return Lista de errores
     */
    public static List<String> getErrors(WebDriver driver) {
        return getLogs(driver, LogLevel.ERROR);
    }

    /**
     * Verifica si hay errores JavaScript en la consola.
     *
     * @param driver WebDriver
     * @return true si hay errores
     */
    public static boolean hasJavaScriptErrors(WebDriver driver) {
        return !getErrors(driver).isEmpty();
    }

    /**
     * Registra un listener para logs de consola en tiempo real (BiDi).
     *
     * @param driver WebDriver
     * @param consumer callback que recibe cada mensaje de log
     */
    public static void onConsoleLog(WebDriver driver, Consumer<String> consumer) {
        if (!supportsBiDi(driver)) {
            LOG.warn("BiDi no soportado por este driver - listener no registrado");
            return;
        }

        try {
            LogInspector inspector = new LogInspector(driver);
            inspector.onConsoleEntry(entry -> {
                String message = String.format("[%s] %s",
                        entry.getLevel().toString().toUpperCase(),
                        entry.getText());
                consumer.accept(message);
            });
            LOG.info("Console log listener registrado");
        } catch (Exception e) {
            LOG.error("Error registrando console log listener: {}", e.getMessage());
        }
    }

    /**
     * Registra un listener solo para errores JavaScript (BiDi).
     *
     * @param driver WebDriver
     * @param consumer callback que recibe cada error
     */
    public static void onJavaScriptError(WebDriver driver, Consumer<String> consumer) {
        if (!supportsBiDi(driver)) {
            LOG.warn("BiDi no soportado por este driver - listener no registrado");
            return;
        }

        try {
            LogInspector inspector = new LogInspector(driver);
            inspector.onJavaScriptException(exception -> {
                String message = exception.getText();
                consumer.accept(message);
                LOG.warn("JavaScript error capturado: {}", message);
            });
            LOG.info("JavaScript error listener registrado");
        } catch (Exception e) {
            LOG.error("Error registrando JS error listener: {}", e.getMessage());
        }
    }

    /**
     * Limpia los logs capturados.
     */
    public static void clearLogs() {
        capturedLogs.clear();
    }

    /**
     * Imprime todos los logs capturados al Logger.
     *
     * @param driver WebDriver
     */
    public static void printLogs(WebDriver driver) {
        List<String> logs = getLogs(driver);
        if (logs.isEmpty()) {
            LOG.info("No hay logs de navegador capturados");
            return;
        }

        LOG.info("=== Browser Console Logs ({}) ===", logs.size());
        logs.forEach(log -> LOG.info("  {}", log));
        LOG.info("=== Fin Browser Logs ===");
    }

    // ==================== Private Methods ====================

    /**
     * Obtiene logs via DevTools/LogType (metodo legacy para Chrome).
     */
    private static List<String> getLogsViaDevTools(WebDriver driver) {
        List<String> logs = new ArrayList<>();

        try {
            LogEntries logEntries = driver.manage().logs().get(LogType.BROWSER);
            for (LogEntry entry : logEntries) {
                String logMessage = String.format("[%s] %s",
                        entry.getLevel().getName().toUpperCase(),
                        entry.getMessage());
                logs.add(logMessage);
            }
        } catch (Exception e) {
            LOG.debug("No se pudieron obtener logs via DevTools: {}", e.getMessage());
        }

        return logs;
    }

    /**
     * Verifica si el driver soporta WebDriver BiDi.
     */
    private static boolean supportsBiDi(WebDriver driver) {
        // Firefox y Chrome/Edge modernos soportan BiDi
        return driver instanceof FirefoxDriver
                || driver instanceof ChromeDriver
                || driver instanceof EdgeDriver;
    }

    /**
     * Verifica si un log coincide con el nivel especificado.
     */
    private static boolean matchesLevel(String log, LogLevel level) {
        String upperLog = log.toUpperCase();
        return switch (level) {
            case ERROR -> upperLog.contains("[ERROR]") || upperLog.contains("[SEVERE]");
            case WARNING -> upperLog.contains("[WARNING]") || upperLog.contains("[WARN]");
            case INFO -> upperLog.contains("[INFO]");
            case DEBUG -> upperLog.contains("[DEBUG]") || upperLog.contains("[FINE]");
            case ALL -> true;
        };
    }
}
