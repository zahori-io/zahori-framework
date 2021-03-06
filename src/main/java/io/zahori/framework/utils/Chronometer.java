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

import java.sql.Date;
import java.text.SimpleDateFormat;
import java.time.Duration;
import java.time.Instant;

/**
 * The type Chronometer.
 */
public class Chronometer {

    /**
     * The constant DATE_FORMAT.
     */
    public static String DATE_FORMAT = "dd/MM/yyyy HH:mm:ss";

    private Instant executionStarted;

    private Instant executionFinished;

    private String dateExecutionStarted;

    private String dateExecutionFinished;

    /**
     * Instantiates a new Chronometer.
     */
    public Chronometer() {
        executionStarted = Instant.now();
        dateExecutionStarted = new SimpleDateFormat(DATE_FORMAT).format(Date.from(executionStarted));
    }

    /**
     * Sets execution finished.
     */
    public void setExecutionFinished() {
        executionFinished = Instant.now();
        dateExecutionFinished = new SimpleDateFormat(DATE_FORMAT).format(Date.from(executionFinished));
    }

    /**
     * Gets execution time.
     *
     * @return the execution time
     */
    public String getExecutionTime() {
        String executionDuration;
        int seconds = (int) Duration.between(executionStarted, executionFinished).getSeconds();
        int minutes;
        if (seconds > 60) {
            minutes = (seconds - (seconds % 60)) / 60;
            seconds = seconds % 60;
            executionDuration = Integer.toString(minutes) + " min, " + Integer.toString(seconds) + " sec";
        } else {
            executionDuration = Integer.toString(seconds) + " sec";
        }

        return executionDuration;
    }

    /**
     * Gets elapsed seconds.
     *
     * @return the elapsed seconds
     */
    public int getElapsedSeconds() {
        setExecutionFinished();
        return (int) Duration.between(executionStarted, executionFinished).getSeconds();
    }

    /**
     * Gets elapsed seconds long.
     *
     * @return the elapsed seconds long
     */
    public long getElapsedSecondsLong() {
        setExecutionFinished();
        return Duration.between(executionStarted, executionFinished).getSeconds();
    }

    /**
     * Gets elapsed time.
     *
     * @return the elapsed time
     */
    public long getElapsedTime() {
        setExecutionFinished();
        return Duration.between(executionStarted, executionFinished).toMillis();
    }

    /**
     * Gets date execution started.
     *
     * @return the date execution started
     */
    public String getDateExecutionStarted() {
        return this.dateExecutionStarted;
    }

    /**
     * Gets date execution finished.
     *
     * @return the date execution finished
     */
    public String getDateExecutionFinished() {
        return this.dateExecutionFinished;
    }
}
