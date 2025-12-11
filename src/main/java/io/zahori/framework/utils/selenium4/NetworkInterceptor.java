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

import io.zahori.framework.utils.selenium4.BiDiNetworkUtils.CapturedRequest;
import io.zahori.framework.utils.selenium4.BiDiNetworkUtils.CapturedResponse;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.openqa.selenium.WebDriver;

/**
 * API de alto nivel para interceptacion de peticiones HTTP en tests Selenium.
 * Implementa ZAH-146: Interceptar peticion servicios realizada por el front.
 *
 * <p>Esta clase proporciona una interfaz fluida y facil de usar para:
 * <ul>
 *   <li>Esperar peticiones HTTP especificas</li>
 *   <li>Capturar y analizar trafico de red</li>
 *   <li>Verificar llamadas a APIs</li>
 *   <li>Autenticacion HTTP automatica</li>
 * </ul>
 *
 * <h2>Ejemplo de uso basico:</h2>
 * <pre>
 * // Crear interceptor
 * NetworkInterceptor interceptor = new NetworkInterceptor(driver);
 *
 * // Iniciar captura
 * interceptor.startCapturing();
 *
 * // Ejecutar accion que dispara la peticion
 * loginButton.click();
 *
 * // Esperar y verificar la peticion
 * Optional&lt;CapturedResponse&gt; response = interceptor.waitForServiceResponse(
 *     "/api/auth/login",
 *     Duration.ofSeconds(10)
 * );
 *
 * assertTrue(response.isPresent(), "Login request should be captured");
 * assertEquals(200, response.get().statusCode());
 *
 * // Detener captura
 * interceptor.stopCapturing();
 * </pre>
 *
 * <h2>Ejemplo con patron async:</h2>
 * <pre>
 * NetworkInterceptor interceptor = new NetworkInterceptor(driver);
 *
 * // Preparar espera ANTES de la accion
 * CompletableFuture&lt;CapturedRequest&gt; future = interceptor.expectRequest("/api/data");
 *
 * // Ejecutar accion
 * refreshButton.click();
 *
 * // Obtener resultado
 * CapturedRequest request = future.get(10, TimeUnit.SECONDS);
 * assertNotNull(request);
 * </pre>
 *
 * @see BiDiNetworkUtils
 * @see ChromeDevToolsUtils
 */
public class NetworkInterceptor {

    private static final Logger LOG = LogManager.getLogger(NetworkInterceptor.class);

    private static final Duration DEFAULT_TIMEOUT = Duration.ofSeconds(30);

    private final WebDriver driver;
    private boolean capturing = false;

    /**
     * Crea un nuevo interceptor de red para el driver especificado.
     *
     * @param driver WebDriver (Chrome, Edge o Firefox con BiDi habilitado)
     */
    public NetworkInterceptor(WebDriver driver) {
        this.driver = driver;
        if (!BiDiNetworkUtils.supportsBiDi(driver)) {
            LOG.warn("El driver no soporta WebDriver BiDi. La interceptacion de red estara limitada.");
        }
    }

    // ==================== Capture Control ====================

    /**
     * Inicia la captura de trafico HTTP.
     * Debe llamarse antes de ejecutar las acciones que generan el trafico.
     *
     * @return this para encadenamiento fluido
     */
    public NetworkInterceptor startCapturing() {
        BiDiNetworkUtils.startCapturingRequests(driver);
        capturing = true;
        LOG.info("Captura de trafico HTTP iniciada");
        return this;
    }

    /**
     * Detiene la captura de trafico HTTP.
     *
     * @return this para encadenamiento fluido
     */
    public NetworkInterceptor stopCapturing() {
        BiDiNetworkUtils.stopCapturing();
        capturing = false;
        LOG.info("Captura de trafico HTTP detenida");
        return this;
    }

    /**
     * Limpia el trafico capturado previamente.
     *
     * @return this para encadenamiento fluido
     */
    public NetworkInterceptor clearCaptured() {
        BiDiNetworkUtils.clearCaptured();
        return this;
    }

    /**
     * Verifica si la captura esta activa.
     *
     * @return true si esta capturando
     */
    public boolean isCapturing() {
        return capturing;
    }

    // ==================== Wait Methods (ZAH-146 Core) ====================

    /**
     * Espera a que se realice una peticion HTTP a la URL especificada.
     * Este es el metodo principal para ZAH-146.
     *
     * @param urlPattern patron de URL (substring match)
     * @return Optional con el request capturado
     */
    public Optional<CapturedRequest> waitForRequest(String urlPattern) {
        return waitForRequest(urlPattern, DEFAULT_TIMEOUT);
    }

    /**
     * Espera a que se realice una peticion HTTP a la URL especificada.
     *
     * @param urlPattern patron de URL (substring match)
     * @param timeout tiempo maximo de espera
     * @return Optional con el request capturado
     */
    public Optional<CapturedRequest> waitForRequest(String urlPattern, Duration timeout) {
        return BiDiNetworkUtils.waitForRequest(driver, urlPattern, timeout);
    }

    /**
     * Espera a que se reciba una respuesta HTTP de la URL especificada.
     *
     * @param urlPattern patron de URL
     * @return Optional con la respuesta capturada
     */
    public Optional<CapturedResponse> waitForResponse(String urlPattern) {
        return waitForResponse(urlPattern, DEFAULT_TIMEOUT);
    }

    /**
     * Espera a que se reciba una respuesta HTTP de la URL especificada.
     *
     * @param urlPattern patron de URL
     * @param timeout tiempo maximo de espera
     * @return Optional con la respuesta capturada
     */
    public Optional<CapturedResponse> waitForResponse(String urlPattern, Duration timeout) {
        return BiDiNetworkUtils.waitForResponse(driver, urlPattern, timeout);
    }

    /**
     * Espera a que se complete una llamada a un servicio/API.
     * Alias semantico de waitForResponse para claridad en tests.
     *
     * @param serviceUrl URL del servicio (ej: "/api/users")
     * @return Optional con la respuesta del servicio
     */
    public Optional<CapturedResponse> waitForServiceResponse(String serviceUrl) {
        return waitForServiceResponse(serviceUrl, DEFAULT_TIMEOUT);
    }

    /**
     * Espera a que se complete una llamada a un servicio/API.
     *
     * @param serviceUrl URL del servicio
     * @param timeout tiempo maximo de espera
     * @return Optional con la respuesta del servicio
     */
    public Optional<CapturedResponse> waitForServiceResponse(String serviceUrl, Duration timeout) {
        LOG.info("Esperando respuesta de servicio: {}", serviceUrl);
        return BiDiNetworkUtils.waitForServiceCall(driver, serviceUrl, timeout);
    }

    /**
     * Espera a que se reciba una respuesta exitosa (2xx).
     *
     * @param urlPattern patron de URL
     * @param timeout tiempo maximo
     * @return Optional con la respuesta si es exitosa
     */
    public Optional<CapturedResponse> waitForSuccessResponse(String urlPattern, Duration timeout) {
        return BiDiNetworkUtils.waitForSuccessResponse(driver, urlPattern, timeout);
    }

    // ==================== Async Wait (Pattern: setup before action) ====================

    /**
     * Prepara la espera de una peticion de forma asincrona.
     * Usar cuando necesitas configurar la espera ANTES de la accion que dispara la peticion.
     *
     * <p>Ejemplo:
     * <pre>
     * CompletableFuture&lt;CapturedRequest&gt; future = interceptor.expectRequest("/api/data");
     * button.click(); // Esta accion dispara la peticion
     * CapturedRequest request = future.get(10, TimeUnit.SECONDS);
     * </pre>
     *
     * @param urlPattern patron de URL
     * @return Future que se completara cuando se capture la peticion
     */
    public CompletableFuture<CapturedRequest> expectRequest(String urlPattern) {
        return expectRequest(urlPattern, DEFAULT_TIMEOUT);
    }

    /**
     * Prepara la espera de una peticion de forma asincrona.
     *
     * @param urlPattern patron de URL
     * @param timeout tiempo maximo
     * @return Future con la peticion capturada
     */
    public CompletableFuture<CapturedRequest> expectRequest(String urlPattern, Duration timeout) {
        return BiDiNetworkUtils.waitForRequestAsync(driver, urlPattern, timeout);
    }

    /**
     * Espera multiples peticiones que coincidan con el patron.
     *
     * @param urlPattern patron de URL
     * @param count numero de peticiones a esperar
     * @param timeout tiempo maximo
     * @return Lista de peticiones capturadas
     */
    public List<CapturedRequest> waitForRequests(String urlPattern, int count, Duration timeout) {
        return BiDiNetworkUtils.waitForRequests(driver, urlPattern, count, timeout);
    }

    // ==================== Query Methods ====================

    /**
     * Obtiene todas las peticiones capturadas.
     *
     * @return Lista de peticiones
     */
    public List<CapturedRequest> getCapturedRequests() {
        return BiDiNetworkUtils.getCapturedRequests();
    }

    /**
     * Obtiene todas las respuestas capturadas.
     *
     * @return Lista de respuestas
     */
    public List<CapturedResponse> getCapturedResponses() {
        return BiDiNetworkUtils.getCapturedResponses();
    }

    /**
     * Obtiene las URLs de todas las peticiones capturadas.
     *
     * @return Lista de URLs
     */
    public List<String> getCapturedUrls() {
        return BiDiNetworkUtils.getCapturedUrls();
    }

    /**
     * Obtiene peticiones que coincidan con el patron.
     *
     * @param urlPattern patron de URL
     * @return Lista de peticiones filtradas
     */
    public List<CapturedRequest> getRequestsByPattern(String urlPattern) {
        return BiDiNetworkUtils.getRequestsByUrlPattern(urlPattern);
    }

    /**
     * Obtiene respuestas con errores (4xx, 5xx).
     *
     * @return Lista de respuestas con error
     */
    public List<CapturedResponse> getErrorResponses() {
        return BiDiNetworkUtils.getErrorResponses();
    }

    // ==================== Verification Methods ====================

    /**
     * Verifica si se ha realizado una peticion a la URL especificada.
     *
     * @param urlPattern patron de URL
     * @return true si existe al menos una peticion
     */
    public boolean hasRequestTo(String urlPattern) {
        return BiDiNetworkUtils.hasRequestTo(urlPattern);
    }

    /**
     * Cuenta las peticiones realizadas a la URL especificada.
     *
     * @param urlPattern patron de URL
     * @return numero de peticiones
     */
    public long countRequestsTo(String urlPattern) {
        return BiDiNetworkUtils.countRequestsTo(urlPattern);
    }

    /**
     * Verifica si hubo errores HTTP (4xx, 5xx).
     *
     * @return true si hay respuestas de error
     */
    public boolean hasHttpErrors() {
        return BiDiNetworkUtils.hasHttpErrors();
    }

    /**
     * Obtiene la ultima peticion realizada a la URL.
     *
     * @param urlPattern patron de URL
     * @return Optional con la ultima peticion
     */
    public Optional<CapturedRequest> getLastRequestTo(String urlPattern) {
        return BiDiNetworkUtils.getLastRequestTo(urlPattern);
    }

    /**
     * Obtiene la ultima respuesta recibida de la URL.
     *
     * @param urlPattern patron de URL
     * @return Optional con la ultima respuesta
     */
    public Optional<CapturedResponse> getLastResponseFrom(String urlPattern) {
        return BiDiNetworkUtils.getLastResponseFrom(urlPattern);
    }

    // ==================== Listeners ====================

    /**
     * Registra un listener para cada peticion HTTP.
     *
     * @param consumer callback que recibe cada peticion
     * @return this para encadenamiento
     */
    public NetworkInterceptor onRequest(Consumer<CapturedRequest> consumer) {
        BiDiNetworkUtils.onRequest(driver, consumer);
        return this;
    }

    /**
     * Registra un listener para cada respuesta HTTP.
     *
     * @param consumer callback que recibe cada respuesta
     * @return this para encadenamiento
     */
    public NetworkInterceptor onResponse(Consumer<CapturedResponse> consumer) {
        BiDiNetworkUtils.onResponse(driver, consumer);
        return this;
    }

    // ==================== Authentication ====================

    /**
     * Registra autenticacion HTTP basica automatica.
     *
     * @param username usuario
     * @param password contrasena
     * @return this para encadenamiento
     */
    public NetworkInterceptor withBasicAuth(String username, String password) {
        BiDiNetworkUtils.registerBasicAuth(driver, username, password);
        return this;
    }

    /**
     * Registra autenticacion HTTP para un dominio especifico.
     *
     * @param domain dominio
     * @param username usuario
     * @param password contrasena
     * @return this para encadenamiento
     */
    public NetworkInterceptor withBasicAuthForDomain(String domain, String username, String password) {
        BiDiNetworkUtils.registerBasicAuthForDomain(driver, domain, username, password);
        return this;
    }

    // ==================== Utility ====================

    /**
     * Imprime un resumen del trafico capturado.
     */
    public void printSummary() {
        BiDiNetworkUtils.printTrafficSummary();
    }

    /**
     * Verifica si el driver soporta interceptacion BiDi.
     *
     * @return true si soporta BiDi
     */
    public boolean supportsBiDi() {
        return BiDiNetworkUtils.supportsBiDi(driver);
    }
}
