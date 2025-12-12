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

import java.io.FileReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

public class CSVFile {

    private CSVFile() {
    }

    public static CSVRecord getRow(String fileName, final String columnKey, final String columnValue) {
        // Create the CSVFormat object with the header mapping
        CSVFormat csvFileFormat = CSVFormat.Builder.create().setDelimiter(';').build();

        try (FileReader fileReader = new FileReader(fileName, StandardCharsets.UTF_8);
             CSVParser csvFileParser = new CSVParser(fileReader, csvFileFormat)) {

            return (CSVRecord) CollectionUtils.find(csvFileParser.getRecords(),
                    number -> columnValue.equals(((CSVRecord) number).get(columnKey)));

        } catch (IOException e) {
            throw new RuntimeException("Error reading CSV file: " + e.getMessage(), e);
        }
    }

}
