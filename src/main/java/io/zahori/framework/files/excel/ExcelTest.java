package io.zahori.framework.files.excel;

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

import java.util.Map;

/**
 * The type Excel test.
 */
public class ExcelTest {

    /**
     * The constant LINE_SEPARATOR.
     */
    public static final String LINE_SEPARATOR = "--------------";

    /**
     * The entry point of application.
     *
     * @param args the input arguments
     */
    public static void main(String... args) {

        final String file = "D:\\test.xls";
        final String sheet2 = "Hoja1";

        Excel excel = new Excel(file);

        // print file
        System.out.println(LINE_SEPARATOR);
        excel.print(sheet2);

        // get cell values
        System.out.println(LINE_SEPARATOR);
        String g16 = excel.getCellValue(sheet2, "X16");
        System.out.println("Celda G16: " + g16);

        // get row values
        System.out.println(LINE_SEPARATOR);
        System.out.println("Row 1: " + excel.getRowValues(sheet2, 1));

        // get row values
        System.out.println(LINE_SEPARATOR);
        System.out.println("Row 2: " + excel.getRowValues(sheet2, 2));

        // row with headers
        System.out.println(LINE_SEPARATOR);
        Map<String, String> valuesWithHeaders = excel.getRowValuesWithHeaders(sheet2, 1, 2);
        System.out.println("Row with headers: " + valuesWithHeaders);
        System.out.println("Campo9: " + valuesWithHeaders.get("Campo9"));
    }
}
