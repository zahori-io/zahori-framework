package io.zahori.framework.utils.video;

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

import java.io.IOException;

/**
 * The interface Enterprise screen recorder.
 */
public interface EnterpriseScreenRecorder {

    /**
     * Start.
     *
     * @throws IOException the io exception
     */
    void start() throws IOException;

    /**
     * Stop.
     *
     * @throws IOException the io exception
     */
    void stop() throws IOException;

    /**
     * Save as.
     *
     * @param fileName the file name
     * @throws IOException the io exception
     */
    void saveAs(String fileName) throws IOException;

    /**
     * Save video fail.
     *
     * @param name the name
     * @throws IOException the io exception
     */
    void saveVideoFail(String name) throws IOException;

    /**
     * Delete video temp boolean.
     *
     * @return the boolean
     */
    boolean deleteVideoTemp();
}
