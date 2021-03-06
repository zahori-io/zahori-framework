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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The type Pause.
 */
public class Pause {

    private Pause() {
    }

    private static final Logger LOG = LoggerFactory.getLogger(Pause.class);

    /**
     * The constant SHORTSLEEPTIME.
     */
    public static final int SHORTSLEEPTIME = 100;

    /**
     * The constant LONGSLEEPTIME.
     */
    public static final int LONGSLEEPTIME = 500;

    /**
     * The constant TOOLONGSLEEPTIME.
     */
    public static final int TOOLONGSLEEPTIME = 5000;

    /**
     * Pause.
     */
    public static void pause() {
        try {
            Thread.sleep(LONGSLEEPTIME);
        } catch (final InterruptedException e) {
            LOG.debug("Error en metodo pausa: " + e.getMessage());
        }
    }

    /**
     * Short pause.
     */
    public static void shortPause() {
        try {
            Thread.sleep(SHORTSLEEPTIME);
        } catch (final InterruptedException e) {
            LOG.debug("Error en metodo pausaRapida: " + e.getMessage());
        }
    }

    /**
     * Long pause.
     */
    public static void longPause() {
        try {
            Thread.sleep(TOOLONGSLEEPTIME);
        } catch (final InterruptedException e) {
            LOG.debug("Error en metodo pausaLarga: " + e.getMessage());
        }
    }

    /**
     * Pause.
     *
     * @param seconds the seconds
     */
    public static void pause(int seconds) {
        try {
            Thread.sleep(seconds * 1000);
        } catch (final InterruptedException e) {
            LOG.debug("Error en metodo pausa: " + e.getMessage());
        }
    }

}
