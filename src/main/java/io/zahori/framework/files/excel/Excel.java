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

import org.apache.commons.io.FilenameUtils;
import org.apache.poi.hssf.usermodel.*;
import org.apache.poi.hssf.util.HSSFColor;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.ss.util.CellReference;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import java.io.*;
import java.util.*;

public class Excel {

    private static final int MINIMUM_COLUMN_COUNT = 100;
    private String filePath;
    private FileInputStream file;
    private Workbook workbook;

    public Excel(String filePath) {
        try {

            this.filePath = filePath;
            file = new FileInputStream(new File(filePath));

            String extension = FilenameUtils.getExtension(filePath);
            if ("xls".equalsIgnoreCase(extension)) {
                workbook = new HSSFWorkbook(file);
            }

            if ("xlsx".equalsIgnoreCase(extension)) {
                workbook = new XSSFWorkbook(file);
            }

            if (workbook == null) {
                throw new RuntimeException("Error reading excel file " + filePath + ": unsupported file format");
            }

        } catch (FileNotFoundException e) {
            throw new RuntimeException("Error reading XLS: file \"" + filePath + "\" does not exist");
        } catch (IOException e) {
            throw new RuntimeException("Error reading XLS file \"" + filePath + "\": " + e.getMessage());
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

        // Write cell value
        cell.setCellValue(cellValue);

        save();
    }

    private void save() {
        // Save changes in excel file
        try {
            FileOutputStream fileOut;
            fileOut = new FileOutputStream(filePath);
            workbook.write(fileOut);
            fileOut.close();
        } catch (FileNotFoundException e) {
            throw new RuntimeException("Error writing in excel file \"" + filePath + "\" -> file does not exist");
        } catch (IOException e) {
            throw new RuntimeException("Error writing in excel file \"" + filePath + "\" -> " + e.getMessage());
        }
    }

    public List<String> getRowValues(String sheetName, int rowNumber) {

        if (rowNumber <= 0) {
            throw new RuntimeException("Error reading row " + rowNumber + " from excel sheet \"" + sheetName
                    + "\": row number must be greater than 0");
        }

        List<String> rowValues = new ArrayList<>();

        Sheet sheet = getSheet(sheetName);

        Row row = sheet.getRow(rowNumber - 1); // -1 because sheet.getRow is 0
                                               // based

        if (row == null) {
            return rowValues;
        }

        int lastColumn = Math.max(row.getLastCellNum(), MINIMUM_COLUMN_COUNT);
        for (int columnNumber = 0; columnNumber < lastColumn; columnNumber++) {
            Cell cell = row.getCell(columnNumber, Row.MissingCellPolicy.RETURN_BLANK_AS_NULL);
            rowValues.add(getCellValue(cell));
        }

        // row.CREATE_NULL_AS_BLANK;
        // Iterator<Cell> cellIterator = row.cellIterator();
        // int cells = row.getPhysicalNumberOfCells();
        // while (cellIterator.hasNext()) {
        // Cell cell = cellIterator.next();
        // rowValues.add(getCellValue(cell));
        // }

        return rowValues;
    }

    public void print(String sheetName) {

        Sheet sheet = getSheet(sheetName);

        // Iterate through each rows from first sheet
        Iterator<Row> rowIterator = sheet.iterator();

        while (rowIterator.hasNext()) {
            Row row = rowIterator.next();

            if (row == null) {
                continue;
            }

            int lastColumn = Math.max(row.getLastCellNum(), MINIMUM_COLUMN_COUNT);
            for (int columnNumber = 0; columnNumber < lastColumn; columnNumber++) {
                Cell cell = row.getCell(columnNumber, Row.MissingCellPolicy.RETURN_BLANK_AS_NULL);
                System.out.print(getCellValue(cell) + "\t\t");
            }
            System.out.println("");
        }
    }

    public Map<String, String> getRowValuesWithHeaders(String sheetName, int rowWithHeaders, int rowWithValues) {

        Map<String, String> row = new HashMap<>();

        List<String> headers = getRowValues(sheetName, rowWithHeaders);
        List<String> values = getRowValues(sheetName, rowWithValues);

        if ((headers == null) || headers.isEmpty() || (values == null) || values.isEmpty()) {
            return row;
        }

        if (headers.size() != values.size()) {
            throw new RuntimeException("Error reading row with headers from excel file: number of headers ("
                    + headers.size() + ") is different than number of values (" + values.size() + ")");
        }

        for (int i = 0; i < headers.size(); i++) {
            row.put(headers.get(i), values.get(i));
        }

        return row;
    }

    public Map<String, Object> getRowComplexValuesWithHeaders(String sheetName, int rowWithHeaders,
            int rowWithValues) {

        Map<String, Object> row = new HashMap<>();

        List<String> headers = getRowValues(sheetName, rowWithHeaders);
        List<String> values = getRowValues(sheetName, rowWithValues);

        if ((headers == null) || headers.isEmpty() || (values == null) || values.isEmpty()) {
            return row;
        }

        if (headers.size() != values.size()) {
            throw new RuntimeException("Error reading row with headers from excel file: number of headers ("
                    + headers.size() + ") is different than number of values (" + values.size() + ")");
        }

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

    // private boolean isLastValue (List<String> values, int position){
    // boolean foundValue = false;
    // int i = position + 1;
    // while (i < values.size() && !foundValue){
    // foundValue = !values.get(i).isEmpty();
    // i++;
    // }
    //
    // return !foundValue;
    // }

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

        DataFormatter formatter = new DataFormatter();
        FormulaEvaluator evaluator = workbook.getCreationHelper().createFormulaEvaluator();

        if ( CellType.FORMULA.equals(cell.getCellTypeEnum())) {
            if (CellType.ERROR.equals(cell.getCachedFormulaResultTypeEnum())) {
                return "#VALUE!";
            } else {
                String value = formatter.formatCellValue(cell, evaluator);
                return value;
            }
        } else {
            try {
                String value = formatter.formatCellValue(cell);
                return value;
            } catch (Exception e) {
                cell.setCellType(CellType.STRING);
                return cell.getRichStringCellValue().toString();
            }
        }
    }

    private Sheet getSheet(String sheetName) {
        Sheet sheet = workbook.getSheet(sheetName);

        if (sheet == null) {
            throw new RuntimeException("Error reading xls file: sheet \"" + sheetName + "\" does not exist");
        }

        return sheet;
    }

    public void setColorCell(String sheetName, String cellNumber, IndexedColors color) {
        Sheet sheet = getSheet(sheetName);
        CellReference cellReference = new CellReference(cellNumber);

        Row row = sheet.getRow(cellReference.getRow());
        if (row != null) {
            Cell cell = row.getCell(cellReference.getCol());
            if (cell != null) {
                HSSFCellStyle style = (HSSFCellStyle) workbook.createCellStyle();
                style.setFillForegroundColor(color.getIndex());
                style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
                cell.setCellStyle(style);
                save();
            }
        }
    }

    public void setColorCellOld(String sheet, String color, int fila, int columna) {
        // my_workbook = new HSSFWorkbook();
        HSSFSheet mySheet = (HSSFSheet) workbook.getSheet(sheet);

        // Creamos el estilo de celda del color ROJO
        HSSFCellStyle backgroundRed = (HSSFCellStyle) workbook.createCellStyle();
        backgroundRed.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        backgroundRed.setFillForegroundColor(HSSFColor.HSSFColorPredefined.RED.getIndex());

        // Creamos el estilo de celda del color NARANJA
        HSSFCellStyle backgroundOrange = (HSSFCellStyle) workbook.createCellStyle();
        backgroundOrange.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        backgroundOrange.setFillForegroundColor(HSSFColor.HSSFColorPredefined.RED.getIndex());

        // Creamos el estilo de celda del color VERDE
        HSSFCellStyle backgroundGreen = (HSSFCellStyle) workbook.createCellStyle();
        backgroundGreen.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        backgroundGreen.setFillForegroundColor(HSSFColor.HSSFColorPredefined.RED.getIndex());

        HSSFRow row = mySheet.getRow(fila - 1); // -1 because sheet.getRow is 0
                                                // based
        HSSFCell cell = row.getCell(columna);

        switch (color) {
        case "RED":
            cell.setCellStyle(backgroundRed);
            break;
        case "ORANGE":
            cell.setCellStyle(backgroundOrange);
            break;
        case "GREEN":
            cell.setCellStyle(backgroundGreen);
            break;
        }

        save();

    }
}
