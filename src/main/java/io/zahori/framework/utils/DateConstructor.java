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

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;

import org.apache.commons.lang.StringUtils;

import io.zahori.framework.exception.ZahoriException;

public class DateConstructor {

    public static void main(String[] args) {
        //System.out.println(formatDateFromExcel("1/20/16"));
        //System.out.println(formatDateFromExcel("10/1/16"));
        //System.out.println(nowWithDefaultFormat());
    }

    public static final String DD_MM_YYYY = "dd/MM/yyyy";
    public static final String YYYY_MM_DD_SLASH = "yyyy/MM/dd";
    public static final String DD_MMM_YYYY = "dd-MMM-yyyy";

    public static final String YYYY_MM_DD_DASH = "yyyy-MM-dd";
    
    private static final String[] FORMAT_FECHAS = { DD_MM_YYYY, YYYY_MM_DD_SLASH, "dd-MM-yyyy", YYYY_MM_DD_DASH, "MM/dd/yy",
            DD_MMM_YYYY };
    private static final boolean[] FORMAT_Locale = { false, false, false, false, false, true };
    private static final Locale[] LOCALES = { new Locale("en", "UK"), new Locale("es", "ES") };

    public static Date formatDate(String fecha, String locale) {
        Date fechaFormated = null;
        if (!fecha.isEmpty()) {
            for (int i = 0; (i < DateConstructor.FORMAT_FECHAS.length) && (i < DateConstructor.FORMAT_Locale.length)
                    && (fechaFormated == null); i++) {
                try {
                    SimpleDateFormat formatoFecha = new SimpleDateFormat(DateConstructor.FORMAT_FECHAS[i],
                            DateConstructor.LOCALES[0]);
                    formatoFecha.setLenient(false);
                    fechaFormated = formatoFecha.parse(fecha);
                } catch (ParseException e) {
                }
            }
        }
        return fechaFormated;
    }

    public static String formatDate4DB(Date fecha) {
        SimpleDateFormat sdfFormatoFecha = new SimpleDateFormat("MM/dd/YY");
        return sdfFormatoFecha.format(fecha);
    }

    public static String formatDate4DB(Date fecha, String formatoFecha) {
        SimpleDateFormat sdfFormatoFecha = new SimpleDateFormat(formatoFecha);
        return sdfFormatoFecha.format(fecha);
    }

    public static String formatDate4DB(String fecha) {
        return DateConstructor.getDateWithFormatter(DateConstructor.lengthDateFormat(fecha), DD_MM_YYYY, YYYY_MM_DD_DASH);
    }

    public static String formatDate4DB(String fecha, String formatoOrigen) {
        if (fecha.length() == 10) {
            return DateConstructor.formatDate4DB(fecha);
        }

        return DateConstructor.getDateWithFormatter(DateConstructor.lengthDateFormat(fecha), formatoOrigen,
                YYYY_MM_DD_DASH);
    }

    public static String formatDate4DB(String fecha, String formatoOrigen, String formatoDestino) {
        return DateConstructor.getDateWithFormatter(fecha, formatoOrigen, formatoDestino);
    }

    public static String getCurrentDate4DB() {
        return DateConstructor.getCurrentDateWithFormat(YYYY_MM_DD_DASH);
    }

    public static boolean compare(String fecha1, String fecha2, String locale) {
        Date fechaFormated1 = DateConstructor.formatDate(fecha1, locale);
        Date fechaFormated2 = DateConstructor.formatDate(fecha2, locale);
        boolean control = false;
        if ((fechaFormated1 != null) && (fechaFormated2 != null) && fechaFormated1.equals(fechaFormated2)) {
            control = true;
        }

        return control;
    }

    public static String getDayNow() {
        LocalDate today = LocalDate.now();
        return String.valueOf(today.getDayOfMonth());
    }

    public static String getMonthNow() {
        LocalDate today = LocalDate.now();
        return String.valueOf(today.getMonth().getValue());
    }

    public static String getYearNow() {
        LocalDate today = LocalDate.now();
        return String.valueOf(today.getYear());
    }

    public static String getDay(Date date) {
        LocalDate today = date.toInstant().atZone(ZoneId.systemDefault()).toLocalDate();
        return String.valueOf(today.getDayOfMonth());
    }

    public static String getMonth(Date date) {
        LocalDate today = date.toInstant().atZone(ZoneId.systemDefault()).toLocalDate();
        return String.valueOf(today.getMonth().getValue());
    }

    public static String getYear(Date date) {
        LocalDate today = date.toInstant().atZone(ZoneId.systemDefault()).toLocalDate();
        return String.valueOf(today.getYear());
    }

    public static String getHour() {
        LocalTime justoAhora = LocalTime.now();
        return String.valueOf(justoAhora.getHour());
    }

    public static String getMinute() {
        LocalTime justoAhora = LocalTime.now();
        return String.valueOf(justoAhora.getMinute());
    }

    public static String getDateDBEntoSpa(String input) {
        return DateConstructor.getDateWithFormatter(input, DD_MMM_YYYY, "EN", YYYY_MM_DD_DASH, "ES");
    }

    public static String getDateDBEntoSpaWitOutFormat(String input, String outFormat) {
        return DateConstructor.getDateWithFormatter(input, DD_MMM_YYYY, "EN", outFormat, "ES");
    }

    public static String getDateDBEntoSpaWithInOutFormat(String input, String inFormat, String outFormat) {
        return DateConstructor.getDateWithFormatter(input, inFormat, "EN", outFormat, "ES");
    }

    public static String formatDateFromExcel(String fecha) {
        return DateConstructor.getDateWithFormatter(
                StringUtils.join(DateConstructor.parseDateLpad(fecha, "/", "0"), '/'), "MM/dd/yy", DD_MM_YYYY);
    }

    private static String getDateWithFormatter(String inputDate, String patternOrigin, String patternOut) {
        String resultado;
        try {
            resultado = LocalDate.parse(inputDate, DateTimeFormatter.ofPattern(patternOrigin))
                    .format(DateTimeFormatter.ofPattern(patternOut));
        } catch (DateTimeParseException exc) {
            throw new ZahoriException(inputDate, exc.getMessage());
        }
        return resultado;
    }

    public static String getDateWithFormatter(String inputDate, String patternOrigin, String localeOrigin,
            String patternOut, String localeOut) {
        String resultado;
        try {
            resultado = LocalDate
                    .parse(inputDate.trim(), DateTimeFormatter.ofPattern(patternOrigin, new Locale(localeOrigin)))
                    .format(DateTimeFormatter.ofPattern(patternOut, new Locale(localeOut)));
        } catch (DateTimeParseException exc) {
            throw new ZahoriException(inputDate, exc.getMessage());
        }
        return resultado;
    }

    public static String getCurrentDateWithFormat(String patternOut) {
        return DateTimeFormatter.ofPattern(patternOut).format(LocalDate.now());
    }

    private static String[] parseDateLpad(String fecha, String separator, String cadFilled) {
        String[] dateLista = fecha.split(separator);

        for (int i = 0; i < dateLista.length; i++) {
            dateLista[i] = StringUtils.leftPad(dateLista[i], 2, cadFilled);
        }
        return dateLista;
    }

    public static Date sumarRestarDiasFecha(int dias) {
        Calendar calendar = Calendar.getInstance();
        calendar.setTime(Date.from(LocalDate.now().atStartOfDay(ZoneId.systemDefault()).toInstant()));
        calendar.add(Calendar.DAY_OF_YEAR, dias);
        return calendar.getTime();
    }

    public static String lengthDateFormat(String date) {
        String[] arrayDate = date.split("/");
        StringBuilder formattedDate = new StringBuilder();
        for (int i = 0; i < arrayDate.length; i++) {
            if (arrayDate[i].length() == 1) {
                formattedDate.append("0");
            }
            formattedDate.append(arrayDate[i]);
            if (i < (arrayDate.length - 1)) {
                formattedDate.append("/");
            }
        }

        return formattedDate.toString();
    }

    public static String nowWithDefaultFormat() {
        return nowWithDefaultFormat(DD_MM_YYYY, "0");
    }
    public static String nowWithDefaultFormatAddNatural(String numDays) {
        return nowWithDefaultFormat(DD_MM_YYYY, numDays);
    }

    public static String nowWithDefaultFormat(String pattern, String numDays){
        return LocalDate.now().plusDays(Long.valueOf(numDays)).format(DateTimeFormatter.ofPattern(pattern));
    }

}
