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

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import org.apache.commons.io.FilenameUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.poi.hssf.usermodel.HSSFWorkbook;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.FormulaEvaluator;
import org.apache.poi.ss.usermodel.IndexedColors;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.util.CellReference;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

/**
 * Utilidad para lectura y escritura de archivos Excel (.xls y .xlsx).
 *
 * <p>Implementa AutoCloseable para garantizar liberación de recursos.
 * Uso recomendado con try-with-resources:</p>
 *
 * <pre>{@code
 * try (Excel excel = new Excel("file.xlsx")) {
 *     String value = excel.getCellValue("Sheet1", "A1");
 * }
 * }</pre>
 *
 * @since POI 5.3.0 / JDK 17
 */
public class Excel implements AutoCloseable {

    private static final Logger LOG = LogManager.getLogger(Excel.class);
    private static final int MINIMUM_COLUMN_COUNT = 100;

    private final String filePath;
    private final Workbook workbook;
    private final DataFormatter dataFormatter;
    private final FormulaEvaluator formulaEvaluator;

    /**
     * Abre un archivo Excel existente.
     *
     * @param filePath Ruta al archivo .xls o .xlsx
     * @throws RuntimeException si el archivo no existe o formato no soportado
     */
    public Excel(String filePath) {
        this.filePath = filePath;
        this.dataFormatter = new DataFormatter();

        String extension = FilenameUtils.getExtension(filePath);

        try (FileInputStream fis = new FileInputStream(new File(filePath))) {
            this.workbook = createWorkbook(fis, extension);
            this.formulaEvaluator = workbook.getCreationHelper().createFormulaEvaluator();
        } catch (FileNotFoundException e) {
            throw new RuntimeException("Error reading Excel: file \"" + filePath + "\" does not exist", e);
        } catch (IOException e) {
            throw new RuntimeException("Error reading Excel file \"" + filePath + "\": " + e.getMessage(), e);
        }

        if (workbook == null) {
            throw new RuntimeException("Error reading Excel file " + filePath + ": unsupported file format");
        }
    }

    private Workbook createWorkbook(FileInputStream fis, String extension) throws IOException {
        return switch (extension.toLowerCase()) {
            case "xls" -> new HSSFWorkbook(fis);
            case "xlsx" -> new XSSFWorkbook(fis);
            default -> null;
        };
    }

    /**
     * Cierra el workbook y libera recursos.
     * Llamado automáticamente con try-with-resources.
     */
    @Override
    public void close() {
        if (workbook != null) {
            try {
                workbook.close();
                LOG.debug("Workbook cerrado: {}", filePath);
            } catch (IOException e) {
                LOG.warn("Error cerrando workbook {}: {}", filePath, e.getMessage());
            }
        }
    }

    public String getCellValue(String sheetName, String cellNumber) {
        Sheet sheet = getSheet(sheetName);

        CellReference cellReference = new CellReference(cellNumber);
        Row row = sheet.getRow(cellReference.getRow());
        if (row == null) {
            return "";
        }

        Cell cell = row.getCell(cellReference.getCol());
        return getCellValue(cell);
    }

    public void setCellValue(String sheetName, String cellNumber, String cellValue) {
        Sheet sheet = getSheet(sheetName);

        CellReference cellReference = new CellReference(cellNumber);

        Row row = sheet.getRow(cellReference.getRow());
        if (row == null) {
            row = sheet.createRow(cellReference.getRow());
        }

        Cell cell = row.getCell(cellReference.getCol());
        if (cell == null) {
            cell = row.createCell(cellReference.getCol());
        }

        cell.setCellValue(cellValue);
        save();
    }

    private void save() {
        try (FileOutputStream fileOut = new FileOutputStream(filePath)) {
            workbook.write(fileOut);
        } catch (FileNotFoundException e) {
            throw new RuntimeException("Error writing Excel file \"" + filePath + "\" -> file does not exist", e);
        } catch (IOException e) {
            throw new RuntimeException("Error writing Excel file \"" + filePath + "\" -> " + e.getMessage(), e);
        }
    }

    public List<String> getRowValues(String sheetName, int rowNumber) {
        if (rowNumber <= 0) {
            throw new RuntimeException("Error reading row " + rowNumber + " from Excel sheet \"" + sheetName
                    + "\": row number must be greater than 0");
        }

        List<String> rowValues = new ArrayList<>();
        Sheet sheet = getSheet(sheetName);

        Row row = sheet.getRow(rowNumber - 1); // -1 because sheet.getRow is 0-based
        if (row == null) {
            return rowValues;
        }

        int lastColumn = Math.max(row.getLastCellNum(), MINIMUM_COLUMN_COUNT);
        for (int columnNumber = 0; columnNumber < lastColumn; columnNumber++) {
            Cell cell = row.getCell(columnNumber, Row.MissingCellPolicy.RETURN_BLANK_AS_NULL);
            rowValues.add(getCellValue(cell));
        }

        return rowValues;
    }

    /**
     * Imprime el contenido de una hoja Excel al log.
     *
     * @param sheetName Nombre de la hoja
     */
    public void print(String sheetName) {
        Sheet sheet = getSheet(sheetName);
        Iterator<Row> rowIterator = sheet.iterator();

        while (rowIterator.hasNext()) {
            Row row = rowIterator.next();
            if (row == null) {
                continue;
            }

            StringBuilder rowContent = new StringBuilder();
            int lastColumn = Math.max(row.getLastCellNum(), MINIMUM_COLUMN_COUNT);
            for (int columnNumber = 0; columnNumber < lastColumn; columnNumber++) {
                Cell cell = row.getCell(columnNumber, Row.MissingCellPolicy.RETURN_BLANK_AS_NULL);
                rowContent.append(getCellValue(cell)).append("\t\t");
            }
            LOG.info("{}", rowContent);
        }
    }

    public Map<String, String> getRowValuesWithHeaders(String sheetName, int rowWithHeaders, int rowWithValues) {
        List<String> headers = getRowValues(sheetName, rowWithHeaders);
        List<String> values = getRowValues(sheetName, rowWithValues);

        if (!validateHeadersAndValues(headers, values)) {
            return new HashMap<>();
        }

        Map<String, String> row = new HashMap<>();
        for (int i = 0; i < headers.size(); i++) {
            row.put(headers.get(i), values.get(i));
        }
        return row;
    }

    public Map<String, Object> getRowComplexValuesWithHeaders(String sheetName, int rowWithHeaders,
            int rowWithValues) {
        List<String> headers = getRowValues(sheetName, rowWithHeaders);
        List<String> values = getRowValues(sheetName, rowWithValues);

        if (!validateHeadersAndValues(headers, values)) {
            return new HashMap<>();
        }

        Map<String, Object> row = new HashMap<>();
        for (int i = 0; i < headers.size(); i++) {
            int maxColumn = getLastColumnForHeader(headers, i);
            if ((maxColumn == i) && !headers.get(i).isEmpty()) {
                row.put(headers.get(i), values.get(i));
            } else if (maxColumn > i) {
                List<String> fieldValues = new ArrayList<>();
                for (int j = i; j <= maxColumn; j++) {
                    if (!values.get(j).isEmpty()) {
                        fieldValues.add(values.get(j));
                    }
                }
                row.put(headers.get(i), fieldValues);
                i = maxColumn;
            }
        }
        return row;
    }

    private boolean validateHeadersAndValues(List<String> headers, List<String> values) {
        if ((headers == null) || headers.isEmpty() || (values == null) || values.isEmpty()) {
            return false;
        }
        if (headers.size() != values.size()) {
            throw new RuntimeException("Error reading row with headers from Excel file: number of headers ("
                    + headers.size() + ") is different than number of values (" + values.size() + ")");
        }
        return true;
    }

    private int getLastColumnForHeader(List<String> values, int position) {
        int i = position + 1;
        boolean foundValue = !values.get(i).isEmpty();
        while ((i < values.size()) && !foundValue) {
            foundValue = !values.get(i).isEmpty();
            i++;
        }
        return i - 1;
    }

    private String getCellValue(Cell cell) {
        if (cell == null) {
            return "";
        }

        if (CellType.FORMULA.equals(cell.getCellType())) {
            if (CellType.ERROR.equals(cell.getCachedFormulaResultType())) {
                return "#VALUE!";
            }
            return dataFormatter.formatCellValue(cell, formulaEvaluator);
        }

        try {
            return dataFormatter.formatCellValue(cell);
        } catch (Exception e) {
            return cell.getRichStringCellValue().toString();
        }
    }

    private Sheet getSheet(String sheetName) {
        Sheet sheet = workbook.getSheet(sheetName);
        if (sheet == null) {
            throw new RuntimeException("Error reading Excel file: sheet \"" + sheetName + "\" does not exist");
        }
        return sheet;
    }

    /**
     * Establece el color de fondo de una celda.
     * Compatible con formatos .xls y .xlsx.
     *
     * @param sheetName  Nombre de la hoja
     * @param cellNumber Referencia de celda (ej: "A1")
     * @param color      Color de IndexedColors
     */
    public void setColorCell(String sheetName, String cellNumber, IndexedColors color) {
        Sheet sheet = getSheet(sheetName);
        CellReference cellReference = new CellReference(cellNumber);

        Row row = sheet.getRow(cellReference.getRow());
        if (row == null) {
            return;
        }

        Cell cell = row.getCell(cellReference.getCol());
        if (cell == null) {
            return;
        }

        // Usar CellStyle (interfaz) en lugar de HSSFCellStyle para compatibilidad
        CellStyle style = workbook.createCellStyle();
        style.setFillForegroundColor(color.getIndex());
        style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        cell.setCellStyle(style);
        save();
    }
}
