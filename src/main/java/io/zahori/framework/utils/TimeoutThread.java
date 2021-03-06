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

import org.openqa.selenium.UnsupportedCommandException;
import org.openqa.selenium.WebDriver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The type Timeout thread.
 */
public class TimeoutThread extends Thread {

    private static final Logger LOG = LoggerFactory.getLogger(TimeoutThread.class);

    private boolean executionStopped = false;

    private boolean executionFinished = false;

    private WebDriver driver;

    private Chronometer chrono;

    private int timeout;

    /**
     * Instantiates a new Timeout thread.
     *
     * @param name       the name
     * @param d          the d
     * @param maxTimeout the max timeout
     */
    public TimeoutThread(String name, WebDriver d, int maxTimeout) {
        super(name);
        this.driver = d;
        this.chrono = new Chronometer();
        this.timeout = maxTimeout;
    }

    @Override
    public void run() {
        try {
            while (!executionFinished) {
                this.chrono.setExecutionFinished();
                String executionTime = this.chrono.getExecutionTime();
                if (executionTime.indexOf(" min") > 0) {
                    String textoMinutos = executionTime.substring(0, executionTime.indexOf(" min"));
                    int minutosEjecucion = Integer.parseInt(textoMinutos);
                    if ((minutosEjecucion >= this.timeout) && (!this.executionStopped)) {
                        this.executionStopped = true;
                        LOG.error("Browser has been closed from TimeoutThread.");
                        this.driver.quit();
                    }
                }
                Thread.sleep(60000L);
            }
            this.interrupt();

        } catch (InterruptedException e) {
            LOG.warn("Error on TimeoutThread: " + e.getMessage());
            this.interrupt();
        } catch (UnsupportedCommandException e) {
            LOG.debug("Error on TimeoutThread: " + e.getMessage());
            this.interrupt();
        }
    }

    /**
     * Sets execution finished.
     */
    public void setExecutionFinished() {
        this.executionFinished = true;
    }

    /**
     * Is ejecucion cortada boolean.
     *
     * @return the boolean
     */
    public boolean isEjecucionCortada() {
        return this.executionStopped;
    }
}
