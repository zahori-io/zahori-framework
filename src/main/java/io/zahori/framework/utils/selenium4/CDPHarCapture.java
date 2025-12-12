package io.zahori.framework.utils.selenium4;

/*-
 * #%L
 * zahori-framework
 * $Id:$
 * $HeadURL:$
 * %%
 * Copyright (C) 2021 - 2024 PANEL SISTEMAS INFORMATICOS,S.L
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

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.chromium.ChromiumDriver;
import org.openqa.selenium.devtools.DevTools;
import org.openqa.selenium.devtools.HasDevTools;

/**
 * HAR (HTTP Archive) capture and network control using Chrome DevTools Protocol.
 * Replaces BrowserMob Proxy for Selenium 4+ compatibility.
 *
 * <p>Implements ZAH-156: Fix HAR evidence generation failure.
 *
 * <p>Features:
 * <ul>
 *   <li>No proxy required - captures traffic directly via CDP</li>
 *   <li>Full HTTPS support without certificate issues</li>
 *   <li>HAR 1.2 format output</li>
 *   <li>Filtering by URL, method, content-type</li>
 *   <li>Custom HTTP headers injection</li>
 *   <li>URL blacklisting</li>
 *   <li>Works with Chrome and Edge browsers</li>
 * </ul>
 *
 * <h2>Usage:</h2>
 * <pre>
 * CDPHarCapture harCapture = new CDPHarCapture(driver);
 *
 * // Optional: Add custom headers
 * harCapture.setExtraHeaders(Map.of("X-Custom", "value"));
 *
 * // Optional: Block URLs
 * harCapture.setBlockedUrls(List.of("*analytics*", "*tracking*"));
 *
 * // Start capture
 * harCapture.startCapture("Test Case Name");
 *
 * // ... execute test actions ...
 *
 * harCapture.stopCapture();
 * harCapture.writeToFile(new File("evidence.har"));
 * </pre>
 *
 * @see ChromeDevToolsUtils
 * @see BiDiNetworkUtils
 */
public class CDPHarCapture {

    private static final Logger LOG = LogManager.getLogger(CDPHarCapture.class);

    private static final DateTimeFormatter ISO_FORMAT = DateTimeFormatter
            .ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSSXXX")
            .withZone(ZoneId.systemDefault());

    private final WebDriver driver;
    private DevTools devTools;
    private boolean capturing = false;
    private String pageName;
    private Instant startTime;

    // Captured network data
    private final Map<String, RequestData> requests = new ConcurrentHashMap<>();
    private final List<HarEntry> entries = Collections.synchronizedList(new ArrayList<>());

    // Network configuration
    private Map<String, String> extraHeaders = new HashMap<>();
    private List<String> blockedUrls = new ArrayList<>();

    /**
     * Creates a new HAR capture instance.
     *
     * @param driver WebDriver (must be Chrome or Edge)
     */
    public CDPHarCapture(WebDriver driver) {
        this.driver = driver;
        if (!supportsDevTools(driver)) {
            LOG.warn("Driver does not support DevTools. HAR capture will not work.");
        }
    }

    // ==================== Network Configuration ====================

    /**
     * Sets extra HTTP headers to be added to all requests.
     * Replaces BrowserMob Proxy header injection functionality.
     *
     * @param headers Map of header name to value
     */
    public void setExtraHeaders(Map<String, String> headers) {
        this.extraHeaders = headers != null ? new HashMap<>(headers) : new HashMap<>();
    }

    /**
     * Sets URL patterns to block.
     * Replaces BrowserMob Proxy blacklist functionality.
     *
     * @param urls List of URL patterns (supports wildcards *)
     */
    public void setBlockedUrls(List<String> urls) {
        this.blockedUrls = urls != null ? new ArrayList<>(urls) : new ArrayList<>();
    }

    /**
     * Adds extra HTTP headers to be added to all requests.
     *
     * @param name header name
     * @param value header value
     */
    public void addExtraHeader(String name, String value) {
        this.extraHeaders.put(name, value);
    }

    /**
     * Adds a URL pattern to block.
     *
     * @param urlPattern URL pattern (supports wildcards *)
     */
    public void addBlockedUrl(String urlPattern) {
        this.blockedUrls.add(urlPattern);
    }

    // ==================== Capture Control ====================

    /**
     * Starts capturing network traffic.
     *
     * @param pageName name for the HAR page entry
     */
    public void startCapture(String pageName) {
        if (!supportsDevTools(driver)) {
            LOG.error("Cannot start HAR capture - driver does not support DevTools");
            return;
        }

        this.pageName = pageName;
        this.startTime = Instant.now();
        this.requests.clear();
        this.entries.clear();

        try {
            devTools = ((HasDevTools) driver).getDevTools();
            devTools.createSession();

            // Enable network domain via CDP command (version-agnostic)
            ChromiumDriver chromiumDriver = (ChromiumDriver) driver;
            enableNetworkCapture(chromiumDriver);

            // Enable performance logging for network event capture
            setupNetworkListeners(chromiumDriver);

            capturing = true;
            LOG.info("HAR capture started for: {}", pageName);

        } catch (Exception e) {
            LOG.error("Error starting HAR capture: {}", e.getMessage(), e);
            // Fallback: try simplified capture
            startSimplifiedCapture(pageName);
        }
    }

    /**
     * Simplified capture using only ChromiumDriver CDP commands.
     */
    private void startSimplifiedCapture(String pageName) {
        if (!(driver instanceof ChromiumDriver)) {
            LOG.error("Cannot start simplified capture - driver is not ChromiumDriver");
            return;
        }

        this.pageName = pageName;
        this.startTime = Instant.now();
        this.requests.clear();
        this.entries.clear();

        try {
            ChromiumDriver chromiumDriver = (ChromiumDriver) driver;
            enableNetworkCapture(chromiumDriver);

            capturing = true;
            LOG.info("Simplified HAR capture started for: {}", pageName);

        } catch (Exception e) {
            LOG.error("Error starting simplified HAR capture: {}", e.getMessage(), e);
        }
    }

    /**
     * Enables network capture with configured headers and URL blocking.
     */
    private void enableNetworkCapture(ChromiumDriver chromiumDriver) {
        // Enable network domain
        chromiumDriver.executeCdpCommand("Network.enable", new HashMap<>());

        // Apply extra headers if configured
        if (!extraHeaders.isEmpty()) {
            Map<String, Object> params = new HashMap<>();
            params.put("headers", extraHeaders);
            chromiumDriver.executeCdpCommand("Network.setExtraHTTPHeaders", params);
            LOG.info("Extra HTTP headers configured: {}", extraHeaders.keySet());
        }

        // Apply URL blocking if configured
        if (!blockedUrls.isEmpty()) {
            Map<String, Object> params = new HashMap<>();
            params.put("urls", blockedUrls);
            chromiumDriver.executeCdpCommand("Network.setBlockedURLs", params);
            LOG.info("URL blocking configured: {} patterns", blockedUrls.size());
        }
    }

    private void setupNetworkListeners(ChromiumDriver chromiumDriver) {
        // Note: ChromiumDriver doesn't expose raw CDP event listeners easily
        // We'll capture via Performance logs when stopCapture is called
        try {
            // Enable performance logging for network events
            Map<String, Object> params = new HashMap<>();
            params.put("enabled", true);
            chromiumDriver.executeCdpCommand("Performance.enable", params);
        } catch (Exception e) {
            LOG.debug("Performance.enable not available: {}", e.getMessage());
        }
    }

    /**
     * Stops capturing network traffic.
     */
    public void stopCapture() {
        if (!capturing) {
            return;
        }

        try {
            if (driver instanceof ChromiumDriver) {
                ChromiumDriver chromiumDriver = (ChromiumDriver) driver;
                // Capture final network state via Performance logs
                captureFromPerformanceLogs(chromiumDriver);

                chromiumDriver.executeCdpCommand("Network.disable", new HashMap<>());
            }

            if (devTools != null) {
                devTools.close();
            }

            capturing = false;
            LOG.info("HAR capture stopped. Captured {} entries", entries.size());

        } catch (Exception e) {
            LOG.error("Error stopping HAR capture: {}", e.getMessage());
        }
    }

    /**
     * Captures network entries from browser performance logs.
     * This is the most reliable method for Selenium 4.
     */
    @SuppressWarnings("unchecked")
    private void captureFromPerformanceLogs(ChromiumDriver chromiumDriver) {
        try {
            // Get network entries via CDP (response reserved for future metric processing)
            chromiumDriver.executeCdpCommand("Performance.getMetrics", new HashMap<>());

            // Also try to get entries from Log domain
            try {
                chromiumDriver.executeCdpCommand("Log.enable", new HashMap<>());
            } catch (Exception ignored) {
                // Log domain might not be available
            }

            // Parse performance entries from browser
            org.openqa.selenium.logging.LogEntries logEntries = chromiumDriver.manage().logs().get("performance");
            for (org.openqa.selenium.logging.LogEntry logEntry : logEntries) {
                try {
                    String message = logEntry.getMessage();
                    if (message.contains("Network.responseReceived") || message.contains("Network.requestWillBeSent")) {
                        parseNetworkLogEntry(message);
                    }
                } catch (Exception e) {
                    LOG.debug("Error parsing log entry: {}", e.getMessage());
                }
            }

        } catch (Exception e) {
            LOG.debug("Error capturing from performance logs: {}", e.getMessage());
        }
    }

    @SuppressWarnings("unchecked")
    private void parseNetworkLogEntry(String message) {
        try {
            Gson gson = new Gson();
            Map<String, Object> logMessage = gson.fromJson(message, Map.class);
            Map<String, Object> messageData = (Map<String, Object>) logMessage.get("message");

            if (messageData == null) return;

            String method = (String) messageData.get("method");
            Map<String, Object> params = (Map<String, Object>) messageData.get("params");

            if (params == null) return;

            if ("Network.requestWillBeSent".equals(method)) {
                handleRequestWillBeSent(params);
            } else if ("Network.responseReceived".equals(method)) {
                handleResponseReceived(params);
            } else if ("Network.loadingFinished".equals(method)) {
                handleLoadingFinished(params);
            }

        } catch (Exception e) {
            LOG.debug("Error parsing network log: {}", e.getMessage());
        }
    }

    @SuppressWarnings("unchecked")
    private void handleRequestWillBeSent(Map<String, Object> params) {
        try {
            String requestId = (String) params.get("requestId");
            Map<String, Object> request = (Map<String, Object>) params.get("request");

            if (requestId == null || request == null) return;

            RequestData data = new RequestData();
            data.requestId = requestId;
            data.url = (String) request.get("url");
            data.method = (String) request.get("method");
            data.headers = (Map<String, Object>) request.get("headers");
            data.postData = (String) request.get("postData");
            data.startTime = Instant.now();

            Object timestamp = params.get("timestamp");
            if (timestamp instanceof Number) {
                data.timestamp = ((Number) timestamp).doubleValue();
            }

            requests.put(requestId, data);
            LOG.debug("Request captured: {} {}", data.method, data.url);

        } catch (Exception e) {
            LOG.debug("Error handling request: {}", e.getMessage());
        }
    }

    @SuppressWarnings("unchecked")
    private void handleResponseReceived(Map<String, Object> params) {
        try {
            String requestId = (String) params.get("requestId");
            Map<String, Object> response = (Map<String, Object>) params.get("response");

            if (requestId == null || response == null) return;

            RequestData data = requests.get(requestId);
            if (data == null) return;

            Object status = response.get("status");
            if (status instanceof Number) {
                data.status = ((Number) status).intValue();
            }
            data.statusText = (String) response.get("statusText");
            data.responseHeaders = (Map<String, Object>) response.get("headers");
            data.mimeType = (String) response.get("mimeType");

            Object timestamp = params.get("timestamp");
            if (timestamp instanceof Number) {
                data.responseTimestamp = ((Number) timestamp).doubleValue();
            }

            // Extract content size from headers if available
            if (data.responseHeaders != null) {
                Object contentLength = data.responseHeaders.get("content-length");
                if (contentLength != null) {
                    try {
                        data.contentSize = Long.parseLong(contentLength.toString());
                    } catch (NumberFormatException ignored) {
                    }
                }
            }

            LOG.debug("Response captured: {} {} {}", data.status, data.statusText, data.url);

        } catch (Exception e) {
            LOG.debug("Error handling response: {}", e.getMessage());
        }
    }

    @SuppressWarnings("unchecked")
    private void handleLoadingFinished(Map<String, Object> params) {
        try {
            String requestId = (String) params.get("requestId");
            if (requestId == null) return;

            RequestData data = requests.get(requestId);
            if (data == null) return;

            data.endTime = Instant.now();

            Object encodedDataLength = params.get("encodedDataLength");
            if (encodedDataLength instanceof Number) {
                data.encodedDataLength = ((Number) encodedDataLength).longValue();
            }

            // Calculate timing
            if (data.responseTimestamp > 0 && data.timestamp > 0) {
                data.waitTime = (long) ((data.responseTimestamp - data.timestamp) * 1000);
            }
            if (data.startTime != null && data.endTime != null) {
                data.totalTime = data.endTime.toEpochMilli() - data.startTime.toEpochMilli();
            }

            // Create HAR entry
            HarEntry entry = createHarEntry(data);
            entries.add(entry);

            LOG.debug("Entry completed: {} ({}ms)", data.url, data.totalTime);

        } catch (Exception e) {
            LOG.debug("Error handling loading finished: {}", e.getMessage());
        }
    }

    // ==================== HAR Output ====================

    /**
     * Writes captured traffic to HAR file.
     *
     * @param file output file
     * @throws IOException if write fails
     */
    public void writeToFile(File file) throws IOException {
        writeToFile(file, null, null, null);
    }

    /**
     * Writes captured traffic to HAR file with filters.
     *
     * @param file output file
     * @param urlFilter comma-separated URL patterns to include (null = all)
     * @param methodFilter comma-separated HTTP methods to include (null = all)
     * @param contentTypeFilter comma-separated content types to include (null = all)
     * @throws IOException if write fails
     */
    public void writeToFile(File file, String urlFilter, String methodFilter, String contentTypeFilter) throws IOException {
        Map<String, Object> har = buildHar(urlFilter, methodFilter, contentTypeFilter);

        if (har == null) {
            LOG.warn("No HAR data to write");
            return;
        }

        // Ensure parent directory exists
        File parentDir = file.getParentFile();
        if (parentDir != null && !parentDir.exists()) {
            parentDir.mkdirs();
        }

        Gson gson = new GsonBuilder().setPrettyPrinting().create();
        try (OutputStreamWriter writer = new OutputStreamWriter(
                new FileOutputStream(file), StandardCharsets.UTF_8)) {
            gson.toJson(har, writer);
            LOG.info("HAR file written: {} ({} bytes)", file.getAbsolutePath(), file.length());
        }
    }

    /**
     * Returns the HAR data as a Map (for programmatic access).
     *
     * @return HAR structure as Map, or null if no data
     */
    public Map<String, Object> getHar() {
        return buildHar(null, null, null);
    }

    /**
     * Returns the number of captured entries.
     *
     * @return entry count
     */
    public int getEntryCount() {
        return entries.size();
    }

    /**
     * Checks if capture is currently active.
     *
     * @return true if capturing
     */
    public boolean isCapturing() {
        return capturing;
    }

    // ==================== HAR Building ====================

    private Map<String, Object> buildHar(String urlFilter, String methodFilter, String contentTypeFilter) {
        // Also add any pending requests that have responses
        for (RequestData data : requests.values()) {
            if (data.status > 0 && entries.stream().noneMatch(e -> e.url.equals(data.url))) {
                if (data.endTime == null) {
                    data.endTime = Instant.now();
                    data.totalTime = data.endTime.toEpochMilli() - (data.startTime != null ? data.startTime.toEpochMilli() : data.endTime.toEpochMilli());
                }
                entries.add(createHarEntry(data));
            }
        }

        if (entries.isEmpty()) {
            return null;
        }

        Set<String> allowedUrls = parseFilterValues(urlFilter);
        Set<String> allowedMethods = parseFilterValues(methodFilter);
        Set<String> allowedContentTypes = parseFilterValues(contentTypeFilter);

        boolean filterUrls = !allowedUrls.isEmpty();
        boolean filterMethods = !allowedMethods.isEmpty();
        boolean filterContentTypes = !allowedContentTypes.isEmpty();

        // Filter entries
        List<Map<String, Object>> filteredEntries = entries.stream()
                .filter(entry -> {
                    String url = entry.url != null ? entry.url.toLowerCase() : "";
                    String method = entry.method != null ? entry.method.toLowerCase() : "";
                    String contentType = entry.mimeType != null ? entry.mimeType.toLowerCase() : "";

                    boolean urlMatch = !filterUrls || allowedUrls.stream().anyMatch(url::contains);
                    boolean methodMatch = !filterMethods || allowedMethods.contains(method);
                    boolean contentTypeMatch = !filterContentTypes || allowedContentTypes.stream().anyMatch(contentType::contains);

                    return urlMatch && methodMatch && contentTypeMatch;
                })
                .map(this::entryToMap)
                .collect(Collectors.toList());

        if (filteredEntries.isEmpty()) {
            return null;
        }

        // Build HAR structure
        Map<String, Object> har = new LinkedHashMap<>();
        Map<String, Object> log = new LinkedHashMap<>();

        log.put("version", "1.2");
        log.put("creator", buildCreator());
        log.put("pages", buildPages());
        log.put("entries", filteredEntries);

        har.put("log", log);
        return har;
    }

    private Map<String, Object> buildCreator() {
        Map<String, Object> creator = new LinkedHashMap<>();
        creator.put("name", "Zahori Framework CDP HAR Capture");
        creator.put("version", "1.0.0");
        creator.put("comment", "Selenium 4 CDP-based HAR capture (ZAH-156)");
        return creator;
    }

    private List<Map<String, Object>> buildPages() {
        List<Map<String, Object>> pages = new ArrayList<>();
        Map<String, Object> page = new LinkedHashMap<>();

        page.put("startedDateTime", ISO_FORMAT.format(startTime != null ? startTime : Instant.now()));
        page.put("id", "page_1");
        page.put("title", pageName != null ? pageName : "Zahori Test");

        Map<String, Object> pageTimings = new LinkedHashMap<>();
        pageTimings.put("onContentLoad", -1);
        pageTimings.put("onLoad", -1);
        page.put("pageTimings", pageTimings);

        pages.add(page);
        return pages;
    }

    private HarEntry createHarEntry(RequestData data) {
        HarEntry entry = new HarEntry();
        entry.url = data.url;
        entry.method = data.method;
        entry.status = data.status;
        entry.statusText = data.statusText;
        entry.mimeType = data.mimeType;
        entry.startedDateTime = data.startTime != null ? ISO_FORMAT.format(data.startTime) : ISO_FORMAT.format(Instant.now());
        entry.time = data.totalTime;
        entry.requestHeaders = data.headers;
        entry.responseHeaders = data.responseHeaders;
        entry.postData = data.postData;
        entry.contentSize = data.contentSize;
        entry.encodedDataLength = data.encodedDataLength;
        entry.waitTime = data.waitTime;
        return entry;
    }

    private Map<String, Object> entryToMap(HarEntry entry) {
        Map<String, Object> map = new LinkedHashMap<>();

        map.put("startedDateTime", entry.startedDateTime);
        map.put("time", entry.time);
        map.put("request", buildRequest(entry));
        map.put("response", buildResponse(entry));
        map.put("cache", new LinkedHashMap<>());
        map.put("timings", buildTimings(entry));
        map.put("pageref", "page_1");

        return map;
    }

    private Map<String, Object> buildRequest(HarEntry entry) {
        Map<String, Object> request = new LinkedHashMap<>();

        request.put("method", entry.method != null ? entry.method : "GET");
        request.put("url", entry.url != null ? entry.url : "");
        request.put("httpVersion", "HTTP/1.1");
        request.put("headers", headersToList(entry.requestHeaders));
        request.put("queryString", new ArrayList<>());
        request.put("cookies", new ArrayList<>());
        request.put("headersSize", -1);
        request.put("bodySize", entry.postData != null ? entry.postData.length() : 0);

        if (entry.postData != null && !entry.postData.isEmpty()) {
            Map<String, Object> postData = new LinkedHashMap<>();
            postData.put("mimeType", "application/x-www-form-urlencoded");
            postData.put("text", entry.postData);
            request.put("postData", postData);
        }

        return request;
    }

    private Map<String, Object> buildResponse(HarEntry entry) {
        Map<String, Object> response = new LinkedHashMap<>();

        response.put("status", entry.status);
        response.put("statusText", entry.statusText != null ? entry.statusText : "");
        response.put("httpVersion", "HTTP/1.1");
        response.put("headers", headersToList(entry.responseHeaders));
        response.put("cookies", new ArrayList<>());

        Map<String, Object> content = new LinkedHashMap<>();
        content.put("size", entry.contentSize);
        content.put("compression", 0);
        content.put("mimeType", entry.mimeType != null ? entry.mimeType : "");
        response.put("content", content);

        response.put("redirectURL", "");
        response.put("headersSize", -1);
        response.put("bodySize", entry.encodedDataLength);

        return response;
    }

    private Map<String, Object> buildTimings(HarEntry entry) {
        Map<String, Object> timings = new LinkedHashMap<>();

        timings.put("blocked", -1);
        timings.put("dns", -1);
        timings.put("connect", -1);
        timings.put("send", 0);
        timings.put("wait", entry.waitTime > 0 ? entry.waitTime : 0);
        timings.put("receive", entry.time - (entry.waitTime > 0 ? entry.waitTime : 0));
        timings.put("ssl", -1);

        return timings;
    }

    private List<Map<String, Object>> headersToList(Map<String, Object> headers) {
        List<Map<String, Object>> list = new ArrayList<>();
        if (headers != null) {
            for (Map.Entry<String, Object> header : headers.entrySet()) {
                Map<String, Object> item = new LinkedHashMap<>();
                item.put("name", header.getKey());
                item.put("value", header.getValue() != null ? header.getValue().toString() : "");
                list.add(item);
            }
        }
        return list;
    }

    private Set<String> parseFilterValues(String input) {
        if (input == null || input.trim().isEmpty()) {
            return Collections.emptySet();
        }
        return Arrays.stream(input.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .map(String::toLowerCase)
                .collect(Collectors.toSet());
    }

    private boolean supportsDevTools(WebDriver driver) {
        return driver instanceof HasDevTools;
    }

    // ==================== Inner Classes ====================

    private static class RequestData {
        String requestId;
        String url;
        String method;
        Map<String, Object> headers;
        String postData;
        Instant startTime;
        Instant endTime;
        double timestamp;
        double responseTimestamp;

        int status;
        String statusText;
        Map<String, Object> responseHeaders;
        String mimeType;
        long contentSize;
        long encodedDataLength;

        long waitTime;
        long totalTime;
    }

    private static class HarEntry {
        String url;
        String method;
        int status;
        String statusText;
        String mimeType;
        String startedDateTime;
        long time;
        Map<String, Object> requestHeaders;
        Map<String, Object> responseHeaders;
        String postData;
        long contentSize;
        long encodedDataLength;
        long waitTime;
    }
}
