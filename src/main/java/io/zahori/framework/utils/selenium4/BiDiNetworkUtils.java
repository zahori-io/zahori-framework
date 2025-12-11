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

import java.net.URI;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.function.Predicate;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.openqa.selenium.HasAuthentication;
import org.openqa.selenium.UsernameAndPassword;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.bidi.module.Network;
import org.openqa.selenium.bidi.network.BeforeRequestSent;
import org.openqa.selenium.bidi.network.ResponseDetails;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.edge.EdgeDriver;
import org.openqa.selenium.firefox.FirefoxDriver;
import org.openqa.selenium.remote.http.HttpRequest;

/**
 * Utilidades para interceptacion de red via WebDriver BiDi (Selenium 4).
 *
 * BiDi permite:
 * - Interceptar requests antes de enviarlos
 * - Capturar responses en tiempo real
 * - Autenticacion HTTP automatica
 * - Filtrar por URL patterns
 *
 * Navegadores soportados: Chrome, Edge, Firefox (con BiDi habilitado)
 *
 * Ejemplo de uso:
 * <pre>
 * // Capturar todas las peticiones
 * BiDiNetworkUtils.startCapturingRequests(driver);
 * // ... navegar ...
 * List<String> urls = BiDiNetworkUtils.getCapturedUrls(driver);
 *
 * // Autenticacion HTTP automatica
 * BiDiNetworkUtils.registerBasicAuth(driver, "admin", "secret");
 *
 * // Listener personalizado
 * BiDiNetworkUtils.onRequest(driver, req -> System.out.println("Request: " + req));
 * </pre>
 */
public final class BiDiNetworkUtils {

    private static final Logger LOG = LogManager.getLogger(BiDiNetworkUtils.class);

    // Almacen de requests capturados
    private static final List<CapturedRequest> capturedRequests = Collections.synchronizedList(new ArrayList<>());
    private static final List<CapturedResponse> capturedResponses = Collections.synchronizedList(new ArrayList<>());
    private static Network activeNetwork;

    private BiDiNetworkUtils() {
        // Utility class
    }

    /**
     * Request HTTP capturado.
     */
    public record CapturedRequest(
            String url,
            String method,
            long timestamp
    ) {}

    /**
     * Response HTTP capturado.
     */
    public record CapturedResponse(
            String url,
            int statusCode,
            String statusText,
            long timestamp
    ) {}

    // ==================== Request Capture ====================

    /**
     * Inicia la captura de requests HTTP.
     *
     * @param driver WebDriver con BiDi habilitado
     */
    public static void startCapturingRequests(WebDriver driver) {
        capturedRequests.clear();
        capturedResponses.clear();

        if (!supportsBiDi(driver)) {
            LOG.warn("Driver no soporta BiDi, captura de red no disponible");
            return;
        }

        try {
            activeNetwork = new Network(driver);

            // Capturar requests
            activeNetwork.onBeforeRequestSent(request -> {
                CapturedRequest captured = new CapturedRequest(
                        request.getRequest().getUrl(),
                        request.getRequest().getMethod(),
                        System.currentTimeMillis()
                );
                capturedRequests.add(captured);
                LOG.debug("Request capturado: {} {}", captured.method(), captured.url());
            });

            // Capturar responses
            activeNetwork.onResponseCompleted(response -> {
                CapturedResponse captured = new CapturedResponse(
                        response.getRequest().getUrl(),
                        response.getResponseData().getStatus(),
                        response.getResponseData().getStatusText(),
                        System.currentTimeMillis()
                );
                capturedResponses.add(captured);
                LOG.debug("Response capturado: {} {} {}", captured.statusCode(), captured.statusText(), captured.url());
            });

            LOG.info("Captura de red BiDi iniciada");
        } catch (Exception e) {
            LOG.error("Error iniciando captura de red BiDi: {}", e.getMessage());
        }
    }

    /**
     * Detiene la captura de requests.
     */
    public static void stopCapturing() {
        if (activeNetwork != null) {
            try {
                activeNetwork.close();
                activeNetwork = null;
                LOG.info("Captura de red BiDi detenida");
            } catch (Exception e) {
                LOG.debug("Error cerrando Network: {}", e.getMessage());
            }
        }
    }

    /**
     * Obtiene todos los requests capturados.
     *
     * @return Lista de requests capturados
     */
    public static List<CapturedRequest> getCapturedRequests() {
        return new ArrayList<>(capturedRequests);
    }

    /**
     * Obtiene todas las URLs de requests capturados.
     *
     * @return Lista de URLs
     */
    public static List<String> getCapturedUrls() {
        return capturedRequests.stream()
                .map(CapturedRequest::url)
                .toList();
    }

    /**
     * Obtiene requests filtrados por patron de URL.
     *
     * @param urlPattern patron a buscar (contains)
     * @return Lista filtrada de requests
     */
    public static List<CapturedRequest> getRequestsByUrlPattern(String urlPattern) {
        return capturedRequests.stream()
                .filter(r -> r.url().contains(urlPattern))
                .toList();
    }

    /**
     * Obtiene todos los responses capturados.
     *
     * @return Lista de responses capturados
     */
    public static List<CapturedResponse> getCapturedResponses() {
        return new ArrayList<>(capturedResponses);
    }

    /**
     * Obtiene responses con errores (4xx, 5xx).
     *
     * @return Lista de responses con error
     */
    public static List<CapturedResponse> getErrorResponses() {
        return capturedResponses.stream()
                .filter(r -> r.statusCode() >= 400)
                .toList();
    }

    /**
     * Verifica si hubo errores HTTP.
     *
     * @return true si hay responses 4xx o 5xx
     */
    public static boolean hasHttpErrors() {
        return !getErrorResponses().isEmpty();
    }

    /**
     * Limpia los requests/responses capturados.
     */
    public static void clearCaptured() {
        capturedRequests.clear();
        capturedResponses.clear();
    }

    // ==================== Request Listeners ====================

    /**
     * Registra un listener para cada request HTTP.
     *
     * @param driver WebDriver
     * @param consumer callback que recibe cada request
     */
    public static void onRequest(WebDriver driver, Consumer<CapturedRequest> consumer) {
        if (!supportsBiDi(driver)) {
            LOG.warn("BiDi no soportado - listener no registrado");
            return;
        }

        try {
            Network network = new Network(driver);
            network.onBeforeRequestSent(request -> {
                CapturedRequest captured = new CapturedRequest(
                        request.getRequest().getUrl(),
                        request.getRequest().getMethod(),
                        System.currentTimeMillis()
                );
                consumer.accept(captured);
            });
            LOG.info("Request listener registrado");
        } catch (Exception e) {
            LOG.error("Error registrando request listener: {}", e.getMessage());
        }
    }

    /**
     * Registra un listener para cada response HTTP.
     *
     * @param driver WebDriver
     * @param consumer callback que recibe cada response
     */
    public static void onResponse(WebDriver driver, Consumer<CapturedResponse> consumer) {
        if (!supportsBiDi(driver)) {
            LOG.warn("BiDi no soportado - listener no registrado");
            return;
        }

        try {
            Network network = new Network(driver);
            network.onResponseCompleted(response -> {
                CapturedResponse captured = new CapturedResponse(
                        response.getRequest().getUrl(),
                        response.getResponseData().getStatus(),
                        response.getResponseData().getStatusText(),
                        System.currentTimeMillis()
                );
                consumer.accept(captured);
            });
            LOG.info("Response listener registrado");
        } catch (Exception e) {
            LOG.error("Error registrando response listener: {}", e.getMessage());
        }
    }

    // ==================== HTTP Authentication (BiDi) ====================

    /**
     * Registra autenticacion HTTP basica automatica.
     * Cuando el navegador reciba un challenge 401, respondera automaticamente.
     *
     * @param driver WebDriver con soporte HasAuthentication
     * @param username usuario
     * @param password contrasena
     */
    public static void registerBasicAuth(WebDriver driver, String username, String password) {
        if (!(driver instanceof HasAuthentication)) {
            LOG.warn("Driver no soporta HasAuthentication");
            return;
        }

        try {
            HasAuthentication authDriver = (HasAuthentication) driver;
            authDriver.register(() -> new UsernameAndPassword(username, password));
            LOG.info("Autenticacion basica registrada para usuario: {}", username);
        } catch (Exception e) {
            LOG.error("Error registrando autenticacion: {}", e.getMessage());
        }
    }

    /**
     * Registra autenticacion HTTP para un dominio especifico.
     *
     * @param driver WebDriver
     * @param domain dominio (ej: "example.com")
     * @param username usuario
     * @param password contrasena
     */
    public static void registerBasicAuthForDomain(WebDriver driver, String domain, String username, String password) {
        if (!(driver instanceof HasAuthentication)) {
            LOG.warn("Driver no soporta HasAuthentication");
            return;
        }

        try {
            HasAuthentication authDriver = (HasAuthentication) driver;
            Predicate<URI> domainFilter = uri -> uri.getHost().contains(domain);
            authDriver.register(domainFilter, () -> new UsernameAndPassword(username, password));
            LOG.info("Autenticacion basica registrada para dominio: {}", domain);
        } catch (Exception e) {
            LOG.error("Error registrando autenticacion para dominio: {}", e.getMessage());
        }
    }

    // ==================== Utility Methods ====================

    /**
     * Verifica si el driver soporta WebDriver BiDi.
     *
     * @param driver WebDriver
     * @return true si soporta BiDi
     */
    public static boolean supportsBiDi(WebDriver driver) {
        return driver instanceof FirefoxDriver
                || driver instanceof ChromeDriver
                || driver instanceof EdgeDriver;
    }

    /**
     * Imprime resumen de trafico capturado.
     */
    public static void printTrafficSummary() {
        LOG.info("=== Resumen de Trafico HTTP ===");
        LOG.info("Total requests: {}", capturedRequests.size());
        LOG.info("Total responses: {}", capturedResponses.size());

        long errors = capturedResponses.stream().filter(r -> r.statusCode() >= 400).count();
        LOG.info("Responses con error: {}", errors);

        if (!capturedRequests.isEmpty()) {
            LOG.info("URLs accedidas:");
            capturedRequests.stream()
                    .map(CapturedRequest::url)
                    .distinct()
                    .limit(10)
                    .forEach(url -> LOG.info("  - {}", url));
        }
        LOG.info("=== Fin Resumen ===");
    }

    // ==================== Wait for Request/Response (ZAH-146) ====================

    /**
     * Espera a que se realice una peticion HTTP que coincida con el patron de URL.
     * Este es el metodo principal solicitado en ZAH-146.
     *
     * <p>Ejemplo de uso:
     * <pre>
     * // Esperar a que se llame a la API de login
     * Optional&lt;CapturedRequest&gt; loginRequest = BiDiNetworkUtils.waitForRequest(
     *     driver,
     *     "/api/auth/login",
     *     Duration.ofSeconds(10)
     * );
     *
     * if (loginRequest.isPresent()) {
     *     System.out.println("Login request capturado: " + loginRequest.get().url());
     * }
     * </pre>
     *
     * @param driver WebDriver con BiDi habilitado
     * @param urlPattern patron de URL a buscar (substring match)
     * @param timeout tiempo maximo de espera
     * @return Optional con el request capturado, o empty si timeout
     */
    public static Optional<CapturedRequest> waitForRequest(WebDriver driver, String urlPattern, Duration timeout) {
        if (!supportsBiDi(driver)) {
            LOG.warn("BiDi no soportado - waitForRequest no disponible");
            return Optional.empty();
        }

        AtomicReference<CapturedRequest> result = new AtomicReference<>();
        CountDownLatch latch = new CountDownLatch(1);

        try {
            Network network = new Network(driver);
            network.onBeforeRequestSent(request -> {
                String url = request.getRequest().getUrl();
                if (url.contains(urlPattern)) {
                    CapturedRequest captured = new CapturedRequest(
                            url,
                            request.getRequest().getMethod(),
                            System.currentTimeMillis()
                    );
                    result.set(captured);
                    latch.countDown();
                    LOG.info("Request esperado capturado: {} {}", captured.method(), captured.url());
                }
            });

            LOG.debug("Esperando request que coincida con: {}", urlPattern);
            boolean found = latch.await(timeout.toMillis(), TimeUnit.MILLISECONDS);

            if (!found) {
                LOG.warn("Timeout esperando request: {} ({}ms)", urlPattern, timeout.toMillis());
            }

            network.close();
            return Optional.ofNullable(result.get());

        } catch (Exception e) {
            LOG.error("Error en waitForRequest: {}", e.getMessage());
            return Optional.empty();
        }
    }

    /**
     * Espera a que se reciba una respuesta HTTP que coincida con el patron de URL.
     *
     * @param driver WebDriver con BiDi habilitado
     * @param urlPattern patron de URL a buscar
     * @param timeout tiempo maximo de espera
     * @return Optional con el response capturado, o empty si timeout
     */
    public static Optional<CapturedResponse> waitForResponse(WebDriver driver, String urlPattern, Duration timeout) {
        if (!supportsBiDi(driver)) {
            LOG.warn("BiDi no soportado - waitForResponse no disponible");
            return Optional.empty();
        }

        AtomicReference<CapturedResponse> result = new AtomicReference<>();
        CountDownLatch latch = new CountDownLatch(1);

        try {
            Network network = new Network(driver);
            network.onResponseCompleted(response -> {
                String url = response.getRequest().getUrl();
                if (url.contains(urlPattern)) {
                    CapturedResponse captured = new CapturedResponse(
                            url,
                            response.getResponseData().getStatus(),
                            response.getResponseData().getStatusText(),
                            System.currentTimeMillis()
                    );
                    result.set(captured);
                    latch.countDown();
                    LOG.info("Response esperado capturado: {} {} {}", captured.statusCode(), captured.statusText(), captured.url());
                }
            });

            LOG.debug("Esperando response que coincida con: {}", urlPattern);
            boolean found = latch.await(timeout.toMillis(), TimeUnit.MILLISECONDS);

            if (!found) {
                LOG.warn("Timeout esperando response: {} ({}ms)", urlPattern, timeout.toMillis());
            }

            network.close();
            return Optional.ofNullable(result.get());

        } catch (Exception e) {
            LOG.error("Error en waitForResponse: {}", e.getMessage());
            return Optional.empty();
        }
    }

    /**
     * Espera a que se reciba una respuesta HTTP exitosa (2xx) que coincida con el patron.
     *
     * @param driver WebDriver
     * @param urlPattern patron de URL
     * @param timeout tiempo maximo
     * @return Optional con el response si es exitoso, empty si timeout o error
     */
    public static Optional<CapturedResponse> waitForSuccessResponse(WebDriver driver, String urlPattern, Duration timeout) {
        if (!supportsBiDi(driver)) {
            LOG.warn("BiDi no soportado");
            return Optional.empty();
        }

        AtomicReference<CapturedResponse> result = new AtomicReference<>();
        CountDownLatch latch = new CountDownLatch(1);

        try {
            Network network = new Network(driver);
            network.onResponseCompleted(response -> {
                String url = response.getRequest().getUrl();
                int status = response.getResponseData().getStatus();
                if (url.contains(urlPattern) && status >= 200 && status < 300) {
                    CapturedResponse captured = new CapturedResponse(
                            url, status, response.getResponseData().getStatusText(), System.currentTimeMillis()
                    );
                    result.set(captured);
                    latch.countDown();
                    LOG.info("Response exitoso capturado: {} {}", status, url);
                }
            });

            boolean found = latch.await(timeout.toMillis(), TimeUnit.MILLISECONDS);
            network.close();

            if (!found) {
                LOG.warn("Timeout esperando response exitoso: {}", urlPattern);
            }

            return Optional.ofNullable(result.get());

        } catch (Exception e) {
            LOG.error("Error en waitForSuccessResponse: {}", e.getMessage());
            return Optional.empty();
        }
    }

    /**
     * Espera a que se realicen N peticiones que coincidan con el patron.
     *
     * @param driver WebDriver
     * @param urlPattern patron de URL
     * @param count numero de peticiones a esperar
     * @param timeout tiempo maximo
     * @return Lista de requests capturados (puede ser menor que count si timeout)
     */
    public static List<CapturedRequest> waitForRequests(WebDriver driver, String urlPattern, int count, Duration timeout) {
        if (!supportsBiDi(driver)) {
            LOG.warn("BiDi no soportado");
            return Collections.emptyList();
        }

        List<CapturedRequest> results = Collections.synchronizedList(new ArrayList<>());
        CountDownLatch latch = new CountDownLatch(count);

        try {
            Network network = new Network(driver);
            network.onBeforeRequestSent(request -> {
                String url = request.getRequest().getUrl();
                if (url.contains(urlPattern)) {
                    CapturedRequest captured = new CapturedRequest(
                            url, request.getRequest().getMethod(), System.currentTimeMillis()
                    );
                    results.add(captured);
                    latch.countDown();
                    LOG.debug("Request {}/{} capturado: {}", results.size(), count, url);
                }
            });

            LOG.debug("Esperando {} requests que coincidan con: {}", count, urlPattern);
            latch.await(timeout.toMillis(), TimeUnit.MILLISECONDS);
            network.close();

            LOG.info("Capturados {}/{} requests para patron: {}", results.size(), count, urlPattern);
            return new ArrayList<>(results);

        } catch (Exception e) {
            LOG.error("Error en waitForRequests: {}", e.getMessage());
            return new ArrayList<>(results);
        }
    }

    /**
     * Espera asincrona a que se realice una peticion.
     * Permite continuar ejecutando acciones mientras se espera.
     *
     * <p>Ejemplo:
     * <pre>
     * CompletableFuture&lt;CapturedRequest&gt; future = BiDiNetworkUtils.waitForRequestAsync(
     *     driver, "/api/data", Duration.ofSeconds(30)
     * );
     *
     * // Ejecutar acciones que disparan el request
     * button.click();
     *
     * // Esperar resultado
     * CapturedRequest request = future.get();
     * </pre>
     *
     * @param driver WebDriver
     * @param urlPattern patron de URL
     * @param timeout tiempo maximo
     * @return CompletableFuture con el request capturado
     */
    public static CompletableFuture<CapturedRequest> waitForRequestAsync(WebDriver driver, String urlPattern, Duration timeout) {
        return CompletableFuture.supplyAsync(() ->
                waitForRequest(driver, urlPattern, timeout).orElse(null)
        );
    }

    /**
     * Espera a que se complete una peticion XHR/fetch especifica.
     * Verifica tanto el request como el response.
     *
     * @param driver WebDriver
     * @param urlPattern patron de URL del servicio
     * @param timeout tiempo maximo
     * @return Optional con la respuesta si se completo correctamente
     */
    public static Optional<CapturedResponse> waitForServiceCall(WebDriver driver, String urlPattern, Duration timeout) {
        LOG.info("Esperando llamada a servicio: {}", urlPattern);
        return waitForResponse(driver, urlPattern, timeout);
    }

    /**
     * Verifica si se ha realizado una peticion a una URL especifica.
     * Usa los requests ya capturados (requiere startCapturingRequests previo).
     *
     * @param urlPattern patron de URL a buscar
     * @return true si se encontro al menos una peticion
     */
    public static boolean hasRequestTo(String urlPattern) {
        return capturedRequests.stream()
                .anyMatch(r -> r.url().contains(urlPattern));
    }

    /**
     * Cuenta el numero de peticiones realizadas a una URL.
     *
     * @param urlPattern patron de URL
     * @return numero de peticiones que coinciden
     */
    public static long countRequestsTo(String urlPattern) {
        return capturedRequests.stream()
                .filter(r -> r.url().contains(urlPattern))
                .count();
    }

    /**
     * Obtiene el ultimo request realizado a una URL especifica.
     *
     * @param urlPattern patron de URL
     * @return Optional con el ultimo request, o empty si no hay
     */
    public static Optional<CapturedRequest> getLastRequestTo(String urlPattern) {
        return capturedRequests.stream()
                .filter(r -> r.url().contains(urlPattern))
                .reduce((first, second) -> second); // Obtiene el ultimo
    }

    /**
     * Obtiene la ultima respuesta recibida de una URL especifica.
     *
     * @param urlPattern patron de URL
     * @return Optional con la ultima respuesta, o empty si no hay
     */
    public static Optional<CapturedResponse> getLastResponseFrom(String urlPattern) {
        return capturedResponses.stream()
                .filter(r -> r.url().contains(urlPattern))
                .reduce((first, second) -> second);
    }
}
