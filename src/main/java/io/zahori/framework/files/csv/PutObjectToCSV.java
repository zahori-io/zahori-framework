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

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;


import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.List;

public class PutObjectToCSV {

    private static final String ERROR_WRITE_ROW_IN_CSV = "Error en metodo writeRowInCSV: ";

    private File ficheroEscritura;

    private static final Logger LOG = LogManager.getLogger(PutObjectToCSV.class);

    private static final char SEPARADOR = ';';

    public PutObjectToCSV(File rutaFichero) {
        this.ficheroEscritura = rutaFichero;
    }

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

    public File getRuta() {
        return ficheroEscritura;
    }

    public void setRuta(File ruta) {
        this.ficheroEscritura = ruta;
    }
}
