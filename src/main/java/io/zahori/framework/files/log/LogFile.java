package io.zahori.framework.files.log;

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

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;

/**
 * The type Log file.
 */
public class LogFile {

    /**
     * The constant IOEXCEPTION.
     */
    public static final String IOEXCEPTION = "IOException: ";
    private String fileName;

    /**
     * Instantiates a new Log file.
     *
     * @param fileName the file name
     */
    public LogFile(String fileName) {
        try {
            this.fileName = fileName;

            File file = new File(fileName);

            // if file doesnt exists, then create it
            if (!file.exists()) {
                file.createNewFile();
            }
        } catch (IOException ioe) {
            throw new RuntimeException(IOEXCEPTION + ioe);
        }
    }

    /**
     * Write.
     *
     * @param log the log
     */
    public void write(String log) {
        if (log == null) {
            return;
        }

        BufferedWriter out = null;
        try {
            FileWriter fstream = new FileWriter(fileName, true);
            out = new BufferedWriter(fstream);

            out.write(log);
            out.newLine();

        } catch (IOException ioe) {
            throw new RuntimeException(IOEXCEPTION + ioe);
        } finally {
            // close buffer writer
            try {
                if (out != null) {
                    out.close();
                }
            } catch (IOException ioe) {
                throw new RuntimeException(IOEXCEPTION + ioe);
            }
        }
    }
}
