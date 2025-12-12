package io.zahori.framework.utils;

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

import java.time.Duration;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Utilidades para pausas y esperas.
 *
 * <strong>IMPORTANTE:</strong> En tests de Selenium, SIEMPRE es preferible usar
 * WebDriverWait con ExpectedConditions en lugar de pausas estaticas (Thread.sleep).
 *
 * Las pausas estaticas hacen los tests:
 * - Mas lentos (siempre esperan el tiempo completo)
 * - Mas fragiles (pueden fallar si el tiempo no es suficiente)
 * - No deterministas (dependen de la velocidad del sistema)
 *
 * Ejemplo CORRECTO (usar esto):
 * <pre>
 * WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(10));
 * wait.until(ExpectedConditions.elementToBeClickable(locator));
 * </pre>
 *
 * Ejemplo INCORRECTO (evitar):
 * <pre>
 * Pause.pause(5); // Siempre espera 5 segundos, innecesariamente lento
 * </pre>
 *
 * @see org.openqa.selenium.support.ui.WebDriverWait
 * @see org.openqa.selenium.support.ui.ExpectedConditions
 */
public class Pause {

    private Pause() {
    }

    private static final Logger LOG = LogManager.getLogger(Pause.class);

    public static final int SHORTSLEEPTIME = 100;

    public static final int LONGSLEEPTIME = 500;

    public static final int TOOLONGSLEEPTIME = 5000;

    /**
     * Pausa de 500ms.
     *
     * @deprecated Usar WebDriverWait con ExpectedConditions en su lugar.
     */
    @Deprecated(since = "0.2.26", forRemoval = false)
    public static void pause() {
        sleep(Duration.ofMillis(LONGSLEEPTIME));
    }

    /**
     * Pausa corta de 100ms.
     *
     * @deprecated Usar WebDriverWait con ExpectedConditions en su lugar.
     */
    @Deprecated(since = "0.2.26", forRemoval = false)
    public static void shortPause() {
        sleep(Duration.ofMillis(SHORTSLEEPTIME));
    }

    /**
     * Pausa de N segundos.
     *
     * @param seconds segundos a esperar
     * @deprecated Usar WebDriverWait con ExpectedConditions en su lugar.
     */
    @Deprecated(since = "0.2.26", forRemoval = false)
    public static void pause(int seconds) {
        sleep(Duration.ofSeconds(seconds));
    }

    /**
     * Pausa de N milisegundos.
     *
     * @param milliseconds milisegundos a esperar
     * @deprecated Usar WebDriverWait con ExpectedConditions en su lugar.
     */
    @Deprecated(since = "0.2.26", forRemoval = false)
    public static void pauseMillis(int milliseconds) {
        sleep(Duration.ofMillis(milliseconds));
    }

    // ==================== Metodos modernos (Duration API) ====================

    /**
     * Pausa usando Duration (API moderna Java 8+).
     *
     * @param duration duracion de la pausa
     */
    public static void sleep(Duration duration) {
        try {
            Thread.sleep(duration.toMillis());
        } catch (final InterruptedException e) {
            Thread.currentThread().interrupt();
            LOG.debug("Pausa interrumpida: {}", e.getMessage());
        }
    }

    /**
     * Espera con condicion de salida temprana.
     * Espera hasta que la condicion sea true o se agote el timeout.
     *
     * @param condition condicion a evaluar
     * @param timeout timeout maximo
     * @param pollingInterval intervalo de evaluacion
     * @return true si la condicion se cumplio, false si se agoto el timeout
     */
    public static boolean waitUntil(java.util.function.BooleanSupplier condition,
                                    Duration timeout,
                                    Duration pollingInterval) {
        long endTime = System.currentTimeMillis() + timeout.toMillis();
        while (System.currentTimeMillis() < endTime) {
            if (condition.getAsBoolean()) {
                return true;
            }
            sleep(pollingInterval);
        }
        return false;
    }

    /**
     * Espera con condicion de salida temprana (polling cada 100ms).
     *
     * @param condition condicion a evaluar
     * @param timeout timeout maximo
     * @return true si la condicion se cumplio, false si se agoto el timeout
     */
    public static boolean waitUntil(java.util.function.BooleanSupplier condition, Duration timeout) {
        return waitUntil(condition, timeout, Duration.ofMillis(100));
    }

}
