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

import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.apache.commons.io.FilenameUtils;
import org.apache.commons.io.input.BOMInputStream;
import org.apache.commons.lang3.Validate;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.util.ArrayList;
import java.util.List;

public class GetCSVtoObject {

    private static final String ERROR_GET_INFO_FROM_CSV = "Error en metodo getInfoFromCSV: ";

    private static final String ENCODING = "ISO-8859-1";

    private String ruta;

    private static final Logger LOG = LogManager.getLogger(GetCSVtoObject.class);

    private static final char SEPARADOR = ';';

    public GetCSVtoObject(String rutaFichero) {
        this.ruta = rutaFichero;
    }

    public CSVRecord getRowFromCSV(final String nombreClave, final String claveFila) throws IOException {
        Reader in = null;
        CSVParser parser = null;

        try {
            InputStream resourceAsStream = GetCSVtoObject.class.getClassLoader()
                    .getResourceAsStream(FilenameUtils.normalize(ruta));

            in = new InputStreamReader(new BOMInputStream(resourceAsStream), ENCODING);

            parser = new CSVParser(in, CSVFormat.Builder.create().setDelimiter(SEPARADOR).build());

            CSVRecord fila = (CSVRecord) CollectionUtils.find(parser.getRecords(),
                    number -> claveFila.equals(((CSVRecord) number).get(nombreClave)));

            return fila;

        } catch (IOException | IllegalArgumentException e) {
            LOG.error(ERROR_GET_INFO_FROM_CSV + e.getMessage());
            return null;
        } finally {
            if (parser != null) {
                parser.close();
            }
            if (in != null) {
                in.close();
            }
        }
    }

    public List<CSVRecord> getRowsFromCSV(final String nombreClave, final String claveFila) throws IOException {
        Reader in = null;
        CSVParser parser = null;
        List<CSVRecord> listaFilas;

        try {
            InputStream resourceAsStream = GetCSVtoObject.class.getClassLoader()
                    .getResourceAsStream(FilenameUtils.normalize(ruta));

            in = new InputStreamReader(new BOMInputStream(resourceAsStream), ENCODING);

            parser = new CSVParser(in, CSVFormat.Builder.create().setDelimiter(SEPARADOR).build());


            listaFilas = new ArrayList<>(parser.getRecords());
            CollectionUtils.filter(listaFilas, number -> claveFila.equals(((CSVRecord) number).get(nombreClave)));

            Validate.notEmpty(listaFilas, "No se ha encontrado la fila seleccionada.");

        } catch (IOException | IllegalArgumentException e) {
            LOG.error(ERROR_GET_INFO_FROM_CSV + e.getMessage());
            return new ArrayList<>();
            /* return (List<CSVRecord>) CollectionUtils.EMPTY_COLLECTION; */
        } finally {
            if (parser != null) {
                parser.close();
            }
            if (in != null) {
                in.close();
            }
        }
        return listaFilas;
    }

    public String getRuta() {
        return ruta;
    }

    public void setRuta(String ruta) {
        this.ruta = ruta;
    }
}
