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

import io.zahori.framework.security.ZahoriCipher;
import net.lightbody.bmp.BrowserMobProxyServer;
import net.lightbody.bmp.client.ClientUtil;
import net.lightbody.bmp.core.har.Har;
import net.lightbody.bmp.core.har.HarEntry;
import net.lightbody.bmp.core.har.HarLog;
import net.lightbody.bmp.core.har.HarPage;
import net.lightbody.bmp.proxy.CaptureType;
import net.lightbody.bmp.proxy.auth.AuthType;
import org.apache.commons.lang3.StringUtils;
import org.openqa.selenium.Proxy;

import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class BrowserMobProxy {

    private BrowserMobProxyServer server;

    public BrowserMobProxy() {
        server = new BrowserMobProxyServer();
    }

    public void enableRequestBinaryContent() {
        server.enableHarCaptureTypes(CaptureType.REQUEST_BINARY_CONTENT);
    }

    public void disableRequestBinaryContent() {
        server.disableHarCaptureTypes(CaptureType.REQUEST_BINARY_CONTENT);
    }

    public void enableRequestCookies() {
        server.enableHarCaptureTypes(CaptureType.REQUEST_COOKIES);
    }

    public void disableRequestCookies() {
        server.disableHarCaptureTypes(CaptureType.REQUEST_COOKIES);
    }

    public void enableRequestHeaders() {
        server.enableHarCaptureTypes(CaptureType.REQUEST_HEADERS);
    }

    public void disableRequestHeaders() {
        server.disableHarCaptureTypes(CaptureType.REQUEST_HEADERS);
    }

    public void enableRequestContent() {
        server.enableHarCaptureTypes(CaptureType.REQUEST_CONTENT);
    }

    public void disableRequestContent() {
        server.disableHarCaptureTypes(CaptureType.REQUEST_CONTENT);
    }

    public void enableResponseBinaryContent() {
        server.enableHarCaptureTypes(CaptureType.RESPONSE_BINARY_CONTENT);
    }

    public void disableResponseBinaryContent() {
        server.disableHarCaptureTypes(CaptureType.RESPONSE_BINARY_CONTENT);
    }

    public void enableResponseCookies() {
        server.enableHarCaptureTypes(CaptureType.RESPONSE_COOKIES);
    }

    public void disableResponseCookies() {
        server.disableHarCaptureTypes(CaptureType.RESPONSE_COOKIES);
    }

    public void enableResponseHeaders() {
        server.enableHarCaptureTypes(CaptureType.RESPONSE_HEADERS);
    }

    public void disableResponseHeaders() {
        server.disableHarCaptureTypes(CaptureType.RESPONSE_HEADERS);
    }

    public void enableResponseContent() {
        server.enableHarCaptureTypes(CaptureType.RESPONSE_CONTENT);
    }

    public void disableResponseContent() {
        server.disableHarCaptureTypes(CaptureType.RESPONSE_CONTENT);
    }

    public void configureProxy(String ipAddress, int port, String userProxy, String passProxy) {
        String password = new ZahoriCipher().decode(passProxy);
        InetSocketAddress proxy = new InetSocketAddress(ipAddress, port);
        server.setChainedProxy(proxy);
        if (!StringUtils.isEmpty(userProxy) && !StringUtils.isEmpty(password)) {
            server.autoAuthorization("", userProxy, password, AuthType.BASIC);
            server.chainedProxyAuthorization(userProxy, password, AuthType.BASIC);
        }
    }

    public Proxy getConfiguredProxy() {
        return getConfiguredProxy(null);
    }
    
    public Proxy getConfiguredProxy(Integer port) {
    	server.setTrustAllServers(true);
    	if (port != null) {
    		server.start(port);
    	} else {
    		server.start();
    	}

        server.newHar();
        return ClientUtil.createSeleniumProxy(server);
    }
    
    public int getProxyPort() {
    	return server.getPort();
    }
    
    public int getRandomPort() {
    	BrowserMobProxyServer madServer = new BrowserMobProxyServer();
    	madServer.start();
    	int port = madServer.getPort();
    	madServer.stop();
    	return port;
    }

    public void addHeaders(Map<String, String> headers) {
        server.addHeaders(headers);
    }

    public void addHeader(String headerName, String headerValue) {
        server.addHeader(headerName, headerValue);
    }
    
    public void addBlackLists(Map<String,String> blackLists) {
    	for (String currentKey: blackLists.keySet()) {
    		server.blacklistRequests(blackLists.get(currentKey), 200);
    	}
    }

    public Har getUnfilteredHarLog() {
        return getHarObject(server.getHar(), null);
    }

    public Har getFilteredHarLogByRequestUrl(String urlPattern) {
        Har harObject = server.getHar();
        List<HarEntry> entries = harObject.getLog().getEntries();
        HarLog newHarLog = prepareHarLog(harObject);

        for (HarEntry currentEntry : entries) {
            if (currentEntry.getRequest().getUrl().matches(urlPattern)) {
                newHarLog.addEntry(currentEntry);
            }
        }

        return getHarObject(harObject, newHarLog);
    }

    public Har getFilteredHarLogByRequestMethod(List<String> allowedMethods) {
        Har harObject = server.getHar();
        List<HarEntry> entries = harObject.getLog().getEntries();
        HarLog newHarLog = prepareHarLog(harObject);

        List<String> upperCaseMethods = normalizeAllowedMethods(allowedMethods);

        for (HarEntry currentEntry : entries) {
            if (upperCaseMethods.contains(currentEntry.getRequest().getMethod())) {
                newHarLog.addEntry(currentEntry);
            }
        }

        return getHarObject(harObject, newHarLog);
    }

    public Har getFilteredHarLogByUrlPatternAndRequestMethod(String urlPattern, List<String> allowedMethods) {
        Har harObject = server.getHar();
        List<HarEntry> entries = harObject.getLog().getEntries();
        HarLog newHarLog = prepareHarLog(harObject);

        List<String> upperCaseMethods = normalizeAllowedMethods(allowedMethods);

        for (HarEntry currentEntry : entries) {
            if (currentEntry.getRequest().getUrl().matches(urlPattern) && upperCaseMethods.contains(currentEntry.getRequest().getMethod())) {
                newHarLog.addEntry(currentEntry);
            }
        }

        return getHarObject(harObject, newHarLog);
    }

    private List<String> normalizeAllowedMethods(List<String> allowedMethods) {
        List<String> upperCaseMethods = new ArrayList<>();
        for (String currentMethod : allowedMethods) {
            upperCaseMethods.add(StringUtils.upperCase(currentMethod));
        }

        return upperCaseMethods;
    }

    private HarLog prepareHarLog(Har harObject) {
        HarLog newHarLog = new HarLog();
        newHarLog.setCreator(harObject.getLog().getCreator());
        newHarLog.setBrowser(harObject.getLog().getBrowser());
        newHarLog.setComment(harObject.getLog().getComment());
        for (HarPage currentPage : harObject.getLog().getPages()) {
            newHarLog.addPage(currentPage);
        }

        return newHarLog;
    }

    private Har getHarObject(Har harObject, HarLog harLog) {
        if (!server.isStopped()) {
            server.stop();
        }

        if (harLog != null) {
            harObject.setLog(harLog);
        }

        return harObject;
    }

}
