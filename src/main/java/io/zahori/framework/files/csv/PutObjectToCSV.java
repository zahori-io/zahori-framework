package io.zahori.framework.files.csv;

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

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The type Put object to csv.
 */
public class PutObjectToCSV {

    private static final String ERROR_WRITE_ROW_IN_CSV = "Error en metodo writeRowInCSV: ";

    private File ficheroEscritura;

    private static final Logger LOG = LoggerFactory.getLogger(PutObjectToCSV.class);

    private static final char SEPARADOR = ';';

    /**
     * Instantiates a new Put object to csv.
     *
     * @param rutaFichero the ruta fichero
     */
    public PutObjectToCSV(File rutaFichero) {
        this.ficheroEscritura = rutaFichero;
    }

    /**
     * Write row in csv.
     *
     * @param campos the campos
     */
    public void writeRowInCSV(List<String> campos) {
        try {
            FileWriter fichero = new FileWriter(this.ficheroEscritura, true);
            int i = 0;
            while (i < campos.size()) {
                fichero.append(campos.get(i));
                fichero.append(SEPARADOR);
                i++;
            }
            fichero.append("\n");
            fichero.flush();
            fichero.close();
        } catch (IOException e) {
            LOG.error(ERROR_WRITE_ROW_IN_CSV + e.getMessage());
        }
    }

    /**
     * Gets ruta.
     *
     * @return the ruta
     */
    public File getRuta() {
        return ficheroEscritura;
    }

    /**
     * Sets ruta.
     *
     * @param ruta the ruta
     */
    public void setRuta(File ruta) {
        this.ficheroEscritura = ruta;
    }
}
