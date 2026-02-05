package io.zahori.framework.driver.browserfactory;

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
import io.zahori.framework.files.properties.ZahoriProperties;
import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import net.lightbody.bmp.BrowserMobProxyServer;
import net.lightbody.bmp.core.har.Har;
import net.lightbody.bmp.core.har.HarEntry;
import net.lightbody.bmp.core.har.HarLog;
import net.lightbody.bmp.core.har.HarPage;
import net.lightbody.bmp.proxy.CaptureType;
import org.apache.commons.lang3.StringUtils;

public class BrowserMobProxy {

    private ZahoriProperties zahoriProperties;
    private BrowserMobProxyServer proxy;

    public BrowserMobProxy(ZahoriProperties zahoriProperties) {
        this.zahoriProperties = zahoriProperties;

        proxy = new BrowserMobProxyServer();
        proxy.setTrustAllServers(true);
    }

    public void start() {
        if (isStarted()) {
            return;
        }

        proxy.start(); // random port

        // Black list
        if (zahoriProperties.isBlackListEnabled()) {
            addBlackListUrls(zahoriProperties.getBlackList());
        }

        // Set HAR capture types
        if (zahoriProperties.isHarEnabled()) {
            setHarCaptureTypes();
        }

        // Add headers (después de iniciar el proxy para evitar problemas con SSL/TLS)
        if (zahoriProperties.isAddHeadersEnabled()) {
            addHeaders(zahoriProperties.getHeadersToBeAdded());
        }
    }

    public void stop() {
        if (!isStarted()) {
            return;
        }

        try {
            proxy.stop();
        } catch (Exception e) {
            // TODO
        }
    }

    public boolean isStarted() {
        return proxy != null && proxy.isStarted();
    }

    public void startHarCapture(String tag) {
        if (!isStarted() || !zahoriProperties.isHarEnabled()) {
            return;
        }

        proxy.newHar(tag);
    }

    public void stopHarCapture(File file) throws IOException {
        if (!isStarted() || !zahoriProperties.isHarEnabled()) {
            return;
        }

        String urls = zahoriProperties.getHarFilterByUrls();
        String requestMethods = zahoriProperties.getHarFilterByRequestMethods();
        String responseContentTypes = zahoriProperties.getHarFilterByResponseContentTypes();

        Har harFiltered = filterHar(urls, requestMethods, responseContentTypes);

        if (harFiltered != null) {
            harFiltered.writeTo(file);
        }
    }

    public BrowserMobProxyServer getProxy() {
        return proxy;
    }

    public int getPort() {
        if (!isStarted()) {
            return 0;
        }
        return proxy.getPort();
    }

    public Har getHarLog() {
        if (!isStarted()) {
            return null;
        }

        return proxy.getHar();
    }

    public void overwriteHeaders(Map<String, String> headers) {
        if (!isStarted()) {
            return;
        }

        if (!headers.isEmpty()) {
            proxy.addRequestFilter((request, contents, messageInfo) -> {
                for (Map.Entry<String, String> header : headers.entrySet()) {
                    request.headers().set(header.getKey(), header.getValue());
                }
                return null; // importante: devolver null para continuar la cadena de filtros
            });
        }
    }

    public void overwriteHeader(String headerName, String headerValue) {
        if (!isStarted()) {
            return;
        }

        proxy.addRequestFilter((request, contents, messageInfo) -> {
            request.headers().set(headerName, headerValue);
            return null; // importante: devolver null para continuar la cadena de filtros
        });
    }

    public void addHeaders(Map<String, String> headers) {
        if (!isStarted()) {
            return;
        }

        proxy.addHeaders(headers);
    }

    public void addHeader(String headerName, String headerValue) {
        if (!isStarted()) {
            return;
        }

        proxy.addHeader(headerName, headerValue);
    }

    public void addBlackListUrl(String url) {
        if (!isStarted()) {
            return;
        }

        if (StringUtils.isNotBlank(url)) {
            proxy.blacklistRequests(".*" + url + ".*", 200);
        }
    }

    public void addBlackListUrls(Map<String, String> blackList) {
        if (!isStarted()) {
            return;
        }

        if (!blackList.isEmpty()) {
            for (String url : blackList.values()) {
                addBlackListUrl(url);
            }
        }
    }

    public void setHarCaptureTypes() {
        if (!isStarted()) {
            return;
        }

        // Request capture options
        if (zahoriProperties.isHarRequestHeadersEnabled()) {
            proxy.enableHarCaptureTypes(CaptureType.REQUEST_HEADERS);
        } else {
            proxy.disableHarCaptureTypes(CaptureType.REQUEST_HEADERS);
        }

        if (zahoriProperties.isHarRequestCookiesEnabled()) {
            proxy.enableHarCaptureTypes(CaptureType.REQUEST_COOKIES);
        } else {
            proxy.disableHarCaptureTypes(CaptureType.REQUEST_COOKIES);
        }

        if (zahoriProperties.isHarRequestContentEnabled()) {
            proxy.enableHarCaptureTypes(CaptureType.REQUEST_CONTENT);
        } else {
            proxy.disableHarCaptureTypes(CaptureType.REQUEST_CONTENT);
        }

        if (zahoriProperties.isHarRequestBinaryContentEnabled()) {
            proxy.enableHarCaptureTypes(CaptureType.REQUEST_BINARY_CONTENT);
        } else {
            proxy.disableHarCaptureTypes(CaptureType.REQUEST_BINARY_CONTENT);
        }

        // Response capture options
        if (zahoriProperties.isHarResponseHeadersEnabled()) {
            proxy.enableHarCaptureTypes(CaptureType.RESPONSE_HEADERS);
        } else {
            proxy.disableHarCaptureTypes(CaptureType.RESPONSE_HEADERS);
        }

        if (zahoriProperties.isHarResponseCookiesEnabled()) {
            proxy.enableHarCaptureTypes(CaptureType.RESPONSE_COOKIES);
        } else {
            proxy.disableHarCaptureTypes(CaptureType.RESPONSE_COOKIES);
        }

        if (zahoriProperties.isHarResponseContentEnabled()) {
            proxy.enableHarCaptureTypes(CaptureType.RESPONSE_CONTENT);
        } else {
            proxy.disableHarCaptureTypes(CaptureType.RESPONSE_CONTENT);
        }

        if (zahoriProperties.isHarResponseBinaryContentEnabled()) {
            proxy.enableHarCaptureTypes(CaptureType.RESPONSE_BINARY_CONTENT);
        } else {
            proxy.disableHarCaptureTypes(CaptureType.RESPONSE_BINARY_CONTENT);
        }
    }

    public Har filterHar(String urlFilter, String methodFilter, String contentTypeFilter) {
        if (!isStarted()) {
            return null;
        }

        Set<String> allowedUrls = parseFilterValues(urlFilter);
        Set<String> allowedMethods = parseFilterValues(methodFilter);
        Set<String> allowedContentTypes = parseFilterValues(contentTypeFilter);

        boolean filterUrls = !allowedUrls.isEmpty();
        boolean filterMethods = !allowedMethods.isEmpty();
        boolean filterContentTypes = !allowedContentTypes.isEmpty();

        Har originalHar = proxy.getHar();
        if (originalHar == null || originalHar.getLog() == null) {
            return null;
        }

        Har newHar = new Har();
        newHar.setLog(prepareHarLog(originalHar));

        List<HarEntry> filteredEntries = originalHar.getLog().getEntries().stream()
                .filter(entry -> {
                    String requestUrl = entry.getRequest().getUrl().toLowerCase();
                    String requestMethod = entry.getRequest().getMethod().toLowerCase();
                    String contentType = "";
                    if (entry.getResponse().getContent() != null && entry.getResponse().getContent().getMimeType() != null) {
                        contentType = entry.getResponse().getContent().getMimeType().toLowerCase();
                    }

                    boolean urlMatch = !filterUrls || allowedUrls.stream().anyMatch(requestUrl::contains);
                    boolean methodMatch = !filterMethods || allowedMethods.contains(requestMethod);
                    boolean contentTypeMatch = !filterContentTypes || allowedContentTypes.stream().anyMatch(contentType::contains);

                    return urlMatch && methodMatch && contentTypeMatch;
                })
                .collect(Collectors.toList());

        newHar.getLog().getEntries().addAll(filteredEntries);
        return newHar;
    }

    private HarLog prepareHarLog(Har originalHar) {
        HarLog newHarLog = new HarLog();
        newHarLog.setCreator(originalHar.getLog().getCreator());
        newHarLog.setBrowser(originalHar.getLog().getBrowser());
        newHarLog.setComment(originalHar.getLog().getComment());
        for (HarPage currentPage : originalHar.getLog().getPages()) {
            newHarLog.addPage(currentPage);
        }

        return newHarLog;
    }

    private Set<String> parseFilterValues(String input) {
        if (input == null || input.trim().isEmpty()) {
            return Collections.emptySet(); // indica que no se debe filtrar este campo
        }
        return Arrays.stream(input.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .map(s -> s.toLowerCase())
                .collect(Collectors.toSet());
    }

}
