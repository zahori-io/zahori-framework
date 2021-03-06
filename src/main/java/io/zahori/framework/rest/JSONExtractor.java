package io.zahori.framework.rest;

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

import org.apache.commons.lang3.StringUtils;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;

/**
 * The type Json extractor.
 */
public class JSONExtractor {

    /**
     * The Auth token.
     */
    protected String authToken;
    /**
     * The Session id.
     */
    protected String sessionId;
    /**
     * The Default request properties.
     */
    protected Map<String, String> defaultRequestProperties;
    /**
     * The Default request params.
     */
    protected Map<String, String> defaultRequestParams;

    private JSONExtractor() {
    }

    /**
     * Gets value from json.
     *
     * @param json the json
     * @param path the path
     * @return the value from json
     */
    public static String getValueFromJson(JSONObject json, String path) {
        String[] pathElements = path.split("\\.");
        JSONObject jsonTemp = getSomethingFromJson(json, path);
        if (pathElements.length == 0 || jsonTemp == null) {
            return null;
        } else {
            String result = String.valueOf(jsonTemp.get(pathElements[pathElements.length - 1]));
            return (result == null || StringUtils.equalsIgnoreCase("null", result)) ? null : result;
        }
    }

    /**
     * Gets value or empty from json.
     *
     * @param json the json
     * @param path the path
     * @return the value or empty from json
     */
    public static String getValueOrEmptyFromJson(JSONObject json, String path) {
        String[] pathElements = path.split("\\.");
        JSONObject jsonTemp = getSomethingFromJson(json, path);
        if (pathElements.length == 0 || jsonTemp == null) {
            return null;
        } else {
            String result = String.valueOf(jsonTemp.get(pathElements[pathElements.length - 1]));
            return (result == null || StringUtils.equalsIgnoreCase("null", result)) ? "" : result;
        }
    }

    /**
     * Gets object from json.
     *
     * @param json the json
     * @param path the path
     * @return the object from json
     */
    public static JSONObject getObjectFromJson(JSONObject json, String path) {
        String[] pathElements = path.split("\\.");
        JSONObject jsonTemp = getSomethingFromJson(json, path);
        return pathElements.length == 0 || jsonTemp == null ? null : (JSONObject) jsonTemp.get(pathElements[pathElements.length - 1]);
    }

    /**
     * Gets array from json.
     *
     * @param json the json
     * @param path the path
     * @return the array from json
     */
    public static JSONArray getArrayFromJson(JSONObject json, String path) {
        String[] pathElements = path.split("\\.");
        JSONObject jsonTemp = getSomethingFromJson(json, path);
        return pathElements.length == 0 || jsonTemp == null ? null : (JSONArray) jsonTemp.get(pathElements[pathElements.length - 1]);
    }

    /**
     * Gets boolean from json.
     *
     * @param json the json
     * @param path the path
     * @return the boolean from json
     */
    public static boolean getBooleanFromJson(JSONObject json, String path) {
        String[] pathElements = path.split("\\.");
        JSONObject jsonTemp = getSomethingFromJson(json, path);
        return pathElements.length == 0 || jsonTemp == null ? null : (boolean) jsonTemp.get(pathElements[pathElements.length - 1]);
    }

    private static JSONObject getSomethingFromJson(JSONObject json, String path) {
        String[] pathElements = path.split("\\.");
        if (pathElements.length == 0) {
            return null;
        } else {
            try {
                JSONObject jsonTemp = json;
                for (int i = 0; i < pathElements.length - 1; i++) {
                    jsonTemp = (JSONObject) jsonTemp.get(pathElements[i]);
                }

                return jsonTemp;
            } catch (NullPointerException e) {
                return null;
            }
        }
    }

}
