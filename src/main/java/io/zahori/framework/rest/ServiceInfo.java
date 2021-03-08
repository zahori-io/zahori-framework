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

public class ServiceInfo {

    private String method;
    private String url;
    private Map<String, String> parameters;
    private Map<String, String> headers;
    private String requestBody;
    private int responseCode;
    private String responseMessage;
    private String responseBody;

    public ServiceInfo(String method, String url, Map<String, String> parameters, Map<String, String> headers, String requestBody, int responseCode,
            String responseBody, String responseMessage) {
        this.method = method;
        this.url = url;
        this.parameters = parameters;
        this.headers = headers;
        this.responseCode = responseCode;
        this.responseBody = responseBody;
        this.responseMessage = responseMessage;
        this.requestBody = requestBody;
    }

    public String getMethod() {
        return method;
    }

    public String getUrl() {
        return url;
    }

    public Map<String, String> getParameters() {
        return parameters;
    }

    public Map<String, String> getHeaders() {
        return headers;
    }

    public int getResponseCode() {
        return responseCode;
    }

    public String getResponseBody() {
        return responseBody;
    }

    public String getResponseMessage() {
        return responseMessage;
    }

    public String getRequestBody() {
        return requestBody;
    }

}
