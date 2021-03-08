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

import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.apache.commons.lang.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Utils {

    private static final String MONTH_NAME_FORMAT = "MMMM";

    private static final Logger LOG = LoggerFactory.getLogger(Utils.class);

    private static final String SI = "si";

    private static final String ENGLISH = "English";

    private static final String JANUARY = "January";

    private static final String FEBRUARY = "February";

    private static final String MARCH = "March";

    private static final String APRIL = "April";

    private static final String MAY = "May";

    private static final String JUNE = "June";

    private static final String JULY = "July";

    private static final String AUGUST = "August";

    private static final String SEPTEMBER = "September";

    private static final String OCTOBER = "October";

    private static final String NOVEMBER = "November";

    private static final String DECEMBER = "December";

    private static final String ERROR = "error";

    private static final char CHAR_SLASH = '/';

    public static final char CHAR_DASH = '-';

    public static final int INDEX_DAYS = 0;

    public static final int INDEX_MONTHS = 1;

    public static final int INDEX_YEARS = 2;
    public static final String DD_MM_YYYY = "dd/MM/yyyy";

    public String addActualDateText(int days, int months, int years) {
        String result;

        Calendar actualDate = Calendar.getInstance();
        SimpleDateFormat sdf = new SimpleDateFormat(DD_MM_YYYY, Locale.getDefault());

        actualDate.add(Calendar.DATE, days);
        actualDate.add(Calendar.MONTH, months);
        actualDate.add(Calendar.YEAR, years);

        result = sdf.format(actualDate.getTime());
        return result;
    }

    public Date addActualDate(int days, int months, int years) {
        Calendar actualDate = Calendar.getInstance();

        actualDate.add(Calendar.DATE, days);
        actualDate.add(Calendar.MONTH, months);
        actualDate.add(Calendar.YEAR, years);

        return actualDate.getTime();
    }

    public int[] splitAddFactors(String text) {
        int[] arrayResult = new int[3];

        arrayResult[INDEX_DAYS] = Integer.parseInt(text.substring(0, text.indexOf('d')));
        text = text.substring(text.indexOf('d') + 1, text.length());
        arrayResult[INDEX_MONTHS] = Integer.parseInt(text.substring(0, text.indexOf('m')));
        text = text.substring(text.indexOf('m') + 1, text.length());
        arrayResult[INDEX_YEARS] = Integer.parseInt(text.substring(0, text.indexOf('y')));

        return arrayResult;
    }

    public String addDate(String paramDate, int days, int months, int years) {
        String result;

        Calendar actualDate = Calendar.getInstance();
        String[] date = paramDate.split(String.valueOf(CHAR_SLASH));
        actualDate.set(Integer.parseInt(date[2]), Integer.parseInt(date[1]) - 1, Integer.parseInt(date[0]));
        SimpleDateFormat sdf = new SimpleDateFormat(DD_MM_YYYY, Locale.getDefault());

        actualDate.add(Calendar.DAY_OF_YEAR, days);
        actualDate.add(Calendar.MONTH, months);
        actualDate.add(Calendar.YEAR, years);

        result = sdf.format(actualDate.getTime());
        return result;
    }

    public int calculateAge(String birthDate, String baseDate) {

        try {
            Date birth = new SimpleDateFormat(DD_MM_YYYY, Locale.getDefault()).parse(birthDate);
            Calendar birthCal = new GregorianCalendar();
            birthCal.setTime(birth);

            Date flightDate = new SimpleDateFormat(DD_MM_YYYY, Locale.getDefault()).parse(baseDate);
            Calendar actualDate = new GregorianCalendar();
            actualDate.setTime(flightDate);

            // Difference calc.
            int years = actualDate.get(Calendar.YEAR) - birthCal.get(Calendar.YEAR);
            int months = actualDate.get(Calendar.MONTH) - birthCal.get(Calendar.MONTH);
            int days = actualDate.get(Calendar.DAY_OF_MONTH) - birthCal.get(Calendar.DAY_OF_MONTH);

            // We should check if birth date is later than present day to take
            // off 1, because the present year there isn't birthday yet.
            if ((months < 0) || ((months == 0) && (days < 0))) {

                years--;
            }

            return years;

        } catch (ParseException e) {
            return -1;
        }
    }

    public boolean translateValue(String value) {
        return SI.equalsIgnoreCase(value);
    }

    public String getMonthText(int month, String language) {
        String monthText;
        if (language.equals(ENGLISH)) {
            switch (month) {
            case 1:
                monthText = JANUARY;
                break;
            case 2:
                monthText = FEBRUARY;
                break;
            case 3:
                monthText = MARCH;
                break;
            case 4:
                monthText = APRIL;
                break;
            case 5:
                monthText = MAY;
                break;
            case 6:
                monthText = JUNE;
                break;
            case 7:
                monthText = JULY;
                break;
            case 8:
                monthText = AUGUST;
                break;
            case 9:
                monthText = SEPTEMBER;
                break;
            case 10:
                monthText = OCTOBER;
                break;
            case 11:
                monthText = NOVEMBER;
                break;
            case 12:
                monthText = DECEMBER;
                break;
            default:
                monthText = ERROR;
                break;
            }

        } else {
            Calendar cal = Calendar.getInstance();
            cal.set(Calendar.YEAR, month - 1, 1);
            monthText = ZahoriStringUtils.capitalizeWithoutWhitespace(new SimpleDateFormat(MONTH_NAME_FORMAT).format(cal.getTime()));
        }

        return monthText;
    }

    public int getMonthNumber(String month) {
        try {
            Date date = new SimpleDateFormat(MONTH_NAME_FORMAT).parse(month.substring(0, MONTH_NAME_FORMAT.length()));
            Calendar cal = Calendar.getInstance();
            cal.setTime(date);
            return cal.get(Calendar.MONTH);
        } catch (ParseException e) {
            return -1;
        }

    }

    public String getDay(String paramDate) {
        return paramDate.substring(0, paramDate.indexOf(CHAR_SLASH));
    }

    public String getMonth(String paramDate) {
        String date = paramDate;
        date = date.substring(date.indexOf(CHAR_SLASH) + 1, date.length());
        return date.substring(0, date.indexOf(CHAR_SLASH));
    }

    public String getYear(String paramDate) {
        String date = paramDate;
        date = date.substring(date.indexOf(CHAR_SLASH) + 1, date.length());
        return date.substring(date.indexOf(CHAR_SLASH) + 1, date.length());
    }

    public String formatDate(String paramDate) {
        String date = paramDate;
        if ((date.indexOf(CHAR_SLASH) == -1) && (date.indexOf(CHAR_DASH) != -1)) {
            date = date.replace(CHAR_DASH, CHAR_SLASH);
        }

        return date;
    }

    public String getSmallerMonth(String month1, String month2) {
        try {
            String monthText1 = month1.substring(0, month1.indexOf(" "));
            String monthText2 = month2.substring(0, month2.indexOf(" "));

            int yearMonth1 = Integer.parseInt(month1.substring(month1.indexOf(" ") + 1, month1.length()));
            int yearMonth2 = Integer.parseInt(month2.substring(month2.indexOf(" ") + 1, month2.length()));

            if (yearMonth1 < yearMonth2) {
                return month1;
            } else if (yearMonth2 < yearMonth1) {
                return month2;
            } else {
                if (getMonthNumber(monthText1) < getMonthNumber(monthText2)) {
                    return month1;
                } else if (getMonthNumber(monthText2) < getMonthNumber(monthText1)) {
                    return month2;
                } else {
                    return "error";
                }
            }

        } catch (NumberFormatException e) {
            LOG.error("Error on getSmallerMonth method: " + e.getMessage());
            return "error";
        }

    }

    public String getDateId() {
        DateFormat sdf = new SimpleDateFormat("yyyyMMddHHmmss");
        Date day = Calendar.getInstance().getTime();
        return sdf.format(day);
    }

    public static String getListString(List<Map<String, String>> list) {
        int i = 0;
        StringBuilder text = new StringBuilder();

        if (list.isEmpty()) {
            text.append("The result has 0 records.");
        } else {
            while (i < list.size()) {
                text.append("\n");
                text.append("REG. ").append(String.valueOf(i + 1)).append("\n");
                text.append("----------------------------------------------------------------------------" + "\n");
                List<String> keys = new ArrayList<>(list.get(i).keySet());
                for (String currentKey : keys) {
                    if (list.get(i).get(currentKey) == null) {
                        text.append(currentKey).append(": null");
                    } else {
                        text.append(currentKey).append(": ").append(list.get(i).get(currentKey)).append("\n");
                    }
                }
                text.append("\n");
                i++;
            }
        }

        return text.toString();
    }

    public static String compareSortListsOfMaps(List<Map<String, String>> list1, List<Map<String, String>> list2,
            String locale) {
        boolean equals = list1.size() == list2.size();
        StringBuilder comparison = new StringBuilder();
        if (equals) {
            if (list1.isEmpty() && list2.isEmpty()) {
                return "";
            } else {
                List<String> keyMap = new ArrayList<>(list1.get(0).keySet());
                int i = 0;
                while (equals && (i < list1.size())) {
                    int j = 0;
                    while (equals && (j < keyMap.size())) {
                        if (((list1.get(i).get(keyMap.get(j)) == null) == (list2.get(i).get(keyMap.get(j)) == null))
                                && !(StringUtils.equalsIgnoreCase(list1.get(i).get(keyMap.get(j)).trim(),
                                        list2.get(i).get(keyMap.get(j)).trim()))
                                && !(compareNames(keyMap.get(j).trim(), list1.get(i).get(keyMap.get(j).trim()),
                                        list2.get(i).get(keyMap.get(j)).trim()))
                                && !(DateConstructor.compare(list1.get(i).get(keyMap.get(j)).trim(),
                                        list2.get(i).get(keyMap.get(j)).trim(), locale))) {
                            if (list1.get(i).get(keyMap.get(j)) == null) {
                                comparison.append("Key: ").append(keyMap.get(j))
                                        .append(" on List1 is null at the current element (")
                                        .append(String.valueOf(i + 1)).append(").");
                            } else if (list2.get(i).get(keyMap.get(j)) == null) {
                                comparison.append("Key: ").append(keyMap.get(j))
                                        .append(" on List2 is null at the current element (")
                                        .append(String.valueOf(i + 1)).append(").");
                            } else {
                                comparison.append("Key: ").append(keyMap.get(j));
                                comparison.append(" / current element: ").append(String.valueOf(i + 1));
                                comparison.append(" / List 1: ").append(list1.get(i).get(keyMap.get(j)));
                                comparison.append(" / List 2: ").append(list2.get(i).get(keyMap.get(j)));
                                comparison.append("\n");
                            }
                        }

                        j++;
                    }
                    equals = comparison.length() == 0;
                    i++;

                }
            }

        } else {
            comparison.append("Lists size do NOT match. List1 size: ").append(list1.size()).append("  /  List1 size: ")
                    .append(list2.size());
        }

        return comparison.toString();
    }

    public static boolean compareNames(String key, String value1, String value2) {
        boolean comparables = false;
        int minPartsOnName = 4;

        // Check if key is NAME.
        if (StringUtils.equalsIgnoreCase("name", key)) {
            String[] value1Parts = value1.split(" ");
            String[] value2Parts = value2.split(" ");
            // Check if the name values have at least 4 parts.
            if ((value1Parts.length > (minPartsOnName - 1)) && (value2Parts.length > (minPartsOnName - 1))
                    && (value1Parts.length == value2Parts.length)) {
                int checks = 0;
                for (int i = 0; i < value1Parts.length; i++) {
                    if (StringUtils.equalsIgnoreCase(value1Parts[i], value2Parts[i])) {
                        checks++;
                    }
                }
                comparables = (checks >= (minPartsOnName - 1));
            }
        }

        return comparables;
    }

    public static String lowerCase(String data) {
        if (data == null) {
            return "";
        }

        return data.toLowerCase();
    }
}
