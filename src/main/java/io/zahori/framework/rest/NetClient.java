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

import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;

import java.io.DataOutputStream;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Map;

public class NetClient {

    private static final String CONTENT_TYPE = "Content-Type";
    private static final String CONTENT_LENGTH = "Content-Length";

    private NetClient() {
    }

    private static final String CONTENTTYPE_JSON = "application/json";
    private static final String CONTENTTYPE_FORMURLENCODED = "application/x-www-form-urlencoded";
    private static final String METHOD_GET = "GET";
    private static final String METHOD_PUT = "PUT";
    private static final String METHOD_POST = "POST";
    private static final String METHOD_DELETE = "DELETE";
    private static final String ENCODING = "UTF-8";
    private static final String ACCEPT = "Accept";

    public static ServiceInfo executeGET(String serviceURL, Map<String, String> params, Map<String, String> properties) {
        return executeGenericWithoutBody(METHOD_GET, serviceURL, params, properties);
    }

    public static ServiceInfo executeDELETE(String serviceURL, Map<String, String> params, Map<String, String> properties) {
        return executeGenericWithoutBody(METHOD_DELETE, serviceURL, params, properties);
    }

    public static ServiceInfo executeGenericWithoutBody(String method, String serviceURL, Map<String, String> params, Map<String, String> properties) {
        try {
            URL url = getFinalUrl(serviceURL, params);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod(method);
            conn.setRequestProperty(ACCEPT, CONTENTTYPE_JSON);
            for (Map.Entry<String, String> property : properties.entrySet()) {
                conn.addRequestProperty(property.getKey(), property.getValue());
            }

            String responseBody = null;
            int responseCode = conn.getResponseCode();
            if (isOK(responseCode)) {
                responseBody = IOUtils.toString(conn.getInputStream(), ENCODING);
            }
            String responseMessage = conn.getResponseMessage();

            conn.disconnect();

            return new ServiceInfo(method, String.valueOf(url), params, properties, null, responseCode, responseBody, responseMessage);
        } catch (IOException e) {
            throw new RuntimeException(e.getMessage());
        }
    }

    public static ServiceInfo executePOSTwJson(String serviceURL, Map<String, String> params, Map<String, String> requestProperties) {
        return executePOST(serviceURL, params, requestProperties, null, CONTENTTYPE_JSON);
    }

    public static ServiceInfo executePOSTwJson(String serviceURL, Map<String, String> params, Map<String, String> requestProperties, String body) {
        return executePOST(serviceURL, params, requestProperties, body, CONTENTTYPE_JSON);
    }

    public static ServiceInfo executePOSTwFormUrl(String serviceURL, Map<String, String> params, Map<String, String> requestProperties) {
        return executePOST(serviceURL, params, requestProperties, null, CONTENTTYPE_FORMURLENCODED);
    }

    public static ServiceInfo executePOSTwFormUrl(String serviceURL, Map<String, String> params, Map<String, String> requestProperties, String body) {
        return executePOST(serviceURL, params, requestProperties, body, CONTENTTYPE_FORMURLENCODED);
    }

    private static ServiceInfo executePOST(String serviceURL, Map<String, String> params, Map<String, String> requestProperties, String body,
            String contentType) {
        return executePOSTPUT(METHOD_POST, serviceURL, params, requestProperties, body, contentType);
    }

    public static ServiceInfo executePUT(String serviceURL, Map<String, String> params, Map<String, String> requestProperties) {
        return executePUT(serviceURL, params, requestProperties, null);
    }

    public static ServiceInfo executePUT(String serviceURL, Map<String, String> params, Map<String, String> requestProperties, String body) {
        return executePOSTPUT(METHOD_PUT, serviceURL, params, requestProperties, body, CONTENTTYPE_JSON);
    }

    public static boolean isOK(int responseCode) {
        return responseCode >= 200 && responseCode <= 299;
    }

    private static URL getFinalUrl(String serviceURL, Map<String, String> params) throws MalformedURLException, UnsupportedEncodingException {
        StringBuilder urlParameters = new StringBuilder();
        boolean firstParam = true;
        for (Map.Entry<String, String> parameter : params.entrySet()) {
            if (!firstParam) {
                urlParameters.append("&");
            }
            firstParam = false;
            urlParameters.append(parameter.getKey()).append("=").append(URLEncoder.encode(parameter.getValue(), "UTF-8"));
        }

        return StringUtils.isEmpty(urlParameters.toString()) ? new URL(serviceURL) : new URL(serviceURL + "?" + urlParameters.toString());
    }

    private static ServiceInfo executePOSTPUT(String method, String serviceURL, Map<String, String> params, Map<String, String> requestProperties, String body,
            String contentType) {
        try {
            URL url = getFinalUrl(serviceURL, params);
            String bodyText = StringUtils.isEmpty(body) ? StringUtils.EMPTY : body;
            byte[] postData = bodyText.getBytes(StandardCharsets.UTF_8);
            int postDataLength = postData.length;
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setDoOutput(true);
            conn.setInstanceFollowRedirects(false);
            conn.setRequestMethod(method);
            for (Map.Entry<String, String> property : requestProperties.entrySet()) {
                conn.setRequestProperty(property.getKey(), property.getValue());
            }
            conn.setRequestProperty(CONTENT_LENGTH, Integer.toString(postDataLength));
            conn.setRequestProperty(CONTENT_TYPE, contentType);
            conn.setUseCaches(false);
            try (DataOutputStream wr = new DataOutputStream(conn.getOutputStream())) {
                wr.write(postData);
                String responseBody = null;
                int responseCode = conn.getResponseCode();
                if (isOK(responseCode)) {
                    responseBody = IOUtils.toString(conn.getInputStream(), ENCODING);
                }
                String responseMessage = conn.getResponseMessage();
                conn.disconnect();

                return new ServiceInfo(method, String.valueOf(url), params, requestProperties, body, responseCode, responseBody, responseMessage);
            }
        } catch (Exception e) {
            throw new RuntimeException(e.getMessage());
        }
    }

}
