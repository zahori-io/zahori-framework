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

import io.zahori.framework.utils.selenium4.BiDiNetworkUtils;
import io.zahori.framework.utils.selenium4.BiDiNetworkUtils.CapturedRequest;
import io.zahori.framework.utils.selenium4.BiDiNetworkUtils.CapturedResponse;
import io.zahori.framework.utils.selenium4.NetworkInterceptor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.edge.EdgeDriver;
import org.openqa.selenium.firefox.FirefoxDriver;
import org.openqa.selenium.remote.RemoteWebDriver;

import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for NetworkInterceptor.
 * Tests the high-level API for network interception (ZAH-146).
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("NetworkInterceptor Tests")
class NetworkInterceptorTest {

    @Mock
    private WebDriver mockDriver;

    @Mock
    private ChromeDriver mockChromeDriver;

    @Mock
    private FirefoxDriver mockFirefoxDriver;

    @Mock
    private EdgeDriver mockEdgeDriver;

    @Mock
    private RemoteWebDriver mockRemoteDriver;

    private NetworkInterceptor interceptor;

    @BeforeEach
    void setUp() {
        BiDiNetworkUtils.clearCaptured();
    }

    @Nested
    @DisplayName("Constructor Tests")
    class ConstructorTests {

        @Test
        @DisplayName("Should create interceptor with any WebDriver")
        void shouldCreateInterceptorWithAnyDriver() {
            NetworkInterceptor interceptor = new NetworkInterceptor(mockDriver);
            assertNotNull(interceptor);
        }

        @Test
        @DisplayName("Should detect BiDi support for Chrome")
        void shouldDetectBiDiSupportForChrome() {
            NetworkInterceptor interceptor = new NetworkInterceptor(mockChromeDriver);
            assertTrue(interceptor.supportsBiDi());
        }

        @Test
        @DisplayName("Should detect BiDi support for Firefox")
        void shouldDetectBiDiSupportForFirefox() {
            NetworkInterceptor interceptor = new NetworkInterceptor(mockFirefoxDriver);
            assertTrue(interceptor.supportsBiDi());
        }

        @Test
        @DisplayName("Should detect BiDi support for Edge")
        void shouldDetectBiDiSupportForEdge() {
            NetworkInterceptor interceptor = new NetworkInterceptor(mockEdgeDriver);
            assertTrue(interceptor.supportsBiDi());
        }

        @Test
        @DisplayName("Should not support BiDi for RemoteWebDriver")
        void shouldNotSupportBiDiForRemoteDriver() {
            NetworkInterceptor interceptor = new NetworkInterceptor(mockRemoteDriver);
            assertFalse(interceptor.supportsBiDi());
        }

        @Test
        @DisplayName("Should not support BiDi for generic WebDriver")
        void shouldNotSupportBiDiForGenericDriver() {
            NetworkInterceptor interceptor = new NetworkInterceptor(mockDriver);
            assertFalse(interceptor.supportsBiDi());
        }
    }

    @Nested
    @DisplayName("Capture Control Tests")
    class CaptureControlTests {

        @Test
        @DisplayName("Should start capturing and update state")
        void shouldStartCapturingAndUpdateState() {
            interceptor = new NetworkInterceptor(mockDriver);

            assertFalse(interceptor.isCapturing());

            NetworkInterceptor result = interceptor.startCapturing();

            assertSame(interceptor, result);
            assertTrue(interceptor.isCapturing());
        }

        @Test
        @DisplayName("Should stop capturing and update state")
        void shouldStopCapturingAndUpdateState() {
            interceptor = new NetworkInterceptor(mockDriver);
            interceptor.startCapturing();

            assertTrue(interceptor.isCapturing());

            NetworkInterceptor result = interceptor.stopCapturing();

            assertSame(interceptor, result);
            assertFalse(interceptor.isCapturing());
        }

        @Test
        @DisplayName("Should support fluent API chaining")
        void shouldSupportFluentApiChaining() {
            interceptor = new NetworkInterceptor(mockDriver);

            NetworkInterceptor result = interceptor
                    .startCapturing()
                    .clearCaptured()
                    .stopCapturing();

            assertSame(interceptor, result);
            assertFalse(interceptor.isCapturing());
        }

        @Test
        @DisplayName("Should clear captured data")
        void shouldClearCapturedData() {
            interceptor = new NetworkInterceptor(mockDriver);

            NetworkInterceptor result = interceptor.clearCaptured();

            assertSame(interceptor, result);
            assertTrue(interceptor.getCapturedRequests().isEmpty());
            assertTrue(interceptor.getCapturedResponses().isEmpty());
        }
    }

    @Nested
    @DisplayName("Query Methods Tests")
    class QueryMethodsTests {

        @BeforeEach
        void setUp() {
            interceptor = new NetworkInterceptor(mockDriver);
        }

        @Test
        @DisplayName("Should return empty list when no requests captured")
        void shouldReturnEmptyListWhenNoRequestsCaptured() {
            List<CapturedRequest> requests = interceptor.getCapturedRequests();
            assertTrue(requests.isEmpty());
        }

        @Test
        @DisplayName("Should return empty list when no responses captured")
        void shouldReturnEmptyListWhenNoResponsesCaptured() {
            List<CapturedResponse> responses = interceptor.getCapturedResponses();
            assertTrue(responses.isEmpty());
        }

        @Test
        @DisplayName("Should return empty list for URLs when nothing captured")
        void shouldReturnEmptyListForUrlsWhenNothingCaptured() {
            List<String> urls = interceptor.getCapturedUrls();
            assertTrue(urls.isEmpty());
        }

        @Test
        @DisplayName("Should return empty list for pattern search when nothing matches")
        void shouldReturnEmptyListForPatternSearchWhenNothingMatches() {
            List<CapturedRequest> requests = interceptor.getRequestsByPattern("/api/users");
            assertTrue(requests.isEmpty());
        }

        @Test
        @DisplayName("Should return empty list for error responses when none captured")
        void shouldReturnEmptyListForErrorResponsesWhenNoneCaptured() {
            List<CapturedResponse> errors = interceptor.getErrorResponses();
            assertTrue(errors.isEmpty());
        }
    }

    @Nested
    @DisplayName("Verification Methods Tests")
    class VerificationMethodsTests {

        @BeforeEach
        void setUp() {
            interceptor = new NetworkInterceptor(mockDriver);
        }

        @Test
        @DisplayName("Should return false for hasRequestTo when nothing captured")
        void shouldReturnFalseForHasRequestToWhenNothingCaptured() {
            assertFalse(interceptor.hasRequestTo("/api/users"));
        }

        @Test
        @DisplayName("Should return 0 for countRequestsTo when nothing captured")
        void shouldReturnZeroForCountRequestsToWhenNothingCaptured() {
            assertEquals(0, interceptor.countRequestsTo("/api/users"));
        }

        @Test
        @DisplayName("Should return false for hasHttpErrors when nothing captured")
        void shouldReturnFalseForHasHttpErrorsWhenNothingCaptured() {
            assertFalse(interceptor.hasHttpErrors());
        }

        @Test
        @DisplayName("Should return empty Optional for getLastRequestTo when nothing captured")
        void shouldReturnEmptyOptionalForGetLastRequestToWhenNothingCaptured() {
            Optional<CapturedRequest> result = interceptor.getLastRequestTo("/api/users");
            assertTrue(result.isEmpty());
        }

        @Test
        @DisplayName("Should return empty Optional for getLastResponseFrom when nothing captured")
        void shouldReturnEmptyOptionalForGetLastResponseFromWhenNothingCaptured() {
            Optional<CapturedResponse> result = interceptor.getLastResponseFrom("/api/users");
            assertTrue(result.isEmpty());
        }
    }

    @Nested
    @DisplayName("Wait Methods Tests (Non-BiDi Driver)")
    class WaitMethodsNonBiDiTests {

        @BeforeEach
        void setUp() {
            interceptor = new NetworkInterceptor(mockRemoteDriver);
        }

        @Test
        @DisplayName("Should return empty Optional for waitForRequest with non-BiDi driver")
        void shouldReturnEmptyOptionalForWaitForRequestWithNonBiDiDriver() {
            Optional<CapturedRequest> result = interceptor.waitForRequest("/api/users");
            assertTrue(result.isEmpty());
        }

        @Test
        @DisplayName("Should return empty Optional for waitForRequest with timeout")
        void shouldReturnEmptyOptionalForWaitForRequestWithTimeout() {
            Optional<CapturedRequest> result = interceptor.waitForRequest("/api/users", Duration.ofMillis(100));
            assertTrue(result.isEmpty());
        }

        @Test
        @DisplayName("Should return empty Optional for waitForResponse with non-BiDi driver")
        void shouldReturnEmptyOptionalForWaitForResponseWithNonBiDiDriver() {
            Optional<CapturedResponse> result = interceptor.waitForResponse("/api/users");
            assertTrue(result.isEmpty());
        }

        @Test
        @DisplayName("Should return empty Optional for waitForServiceResponse with non-BiDi driver")
        void shouldReturnEmptyOptionalForWaitForServiceResponseWithNonBiDiDriver() {
            Optional<CapturedResponse> result = interceptor.waitForServiceResponse("/api/users");
            assertTrue(result.isEmpty());
        }

        @Test
        @DisplayName("Should return empty Optional for waitForSuccessResponse with non-BiDi driver")
        void shouldReturnEmptyOptionalForWaitForSuccessResponseWithNonBiDiDriver() {
            Optional<CapturedResponse> result = interceptor.waitForSuccessResponse("/api/users", Duration.ofMillis(100));
            assertTrue(result.isEmpty());
        }

        @Test
        @DisplayName("Should return empty list for waitForRequests with non-BiDi driver")
        void shouldReturnEmptyListForWaitForRequestsWithNonBiDiDriver() {
            List<CapturedRequest> results = interceptor.waitForRequests("/api/users", 3, Duration.ofMillis(100));
            assertTrue(results.isEmpty());
        }
    }

    @Nested
    @DisplayName("Async Wait Tests")
    class AsyncWaitTests {

        @BeforeEach
        void setUp() {
            interceptor = new NetworkInterceptor(mockRemoteDriver);
        }

        @Test
        @DisplayName("Should return CompletableFuture for expectRequest")
        void shouldReturnCompletableFutureForExpectRequest() {
            CompletableFuture<CapturedRequest> future = interceptor.expectRequest("/api/users");
            assertNotNull(future);
        }

        @Test
        @DisplayName("Should complete with null for non-BiDi driver")
        void shouldCompleteWithNullForNonBiDiDriver() throws Exception {
            CompletableFuture<CapturedRequest> future = interceptor.expectRequest("/api/users", Duration.ofMillis(100));
            CapturedRequest result = future.get();
            assertNull(result);
        }
    }

    @Nested
    @DisplayName("Default Timeout Tests")
    class DefaultTimeoutTests {

        @BeforeEach
        void setUp() {
            interceptor = new NetworkInterceptor(mockRemoteDriver);
        }

        @Test
        @DisplayName("Should return quickly for waitForRequest with non-BiDi driver")
        void shouldReturnQuicklyForWaitForRequestWithNonBiDiDriver() {
            long startTime = System.currentTimeMillis();
            Optional<CapturedRequest> result = interceptor.waitForRequest("/api/users");
            long elapsed = System.currentTimeMillis() - startTime;

            assertTrue(result.isEmpty());
            assertTrue(elapsed < 1000, "Should return quickly for non-BiDi driver, took " + elapsed + "ms");
        }

        @Test
        @DisplayName("Should return quickly for waitForResponse with non-BiDi driver")
        void shouldReturnQuicklyForWaitForResponseWithNonBiDiDriver() {
            long startTime = System.currentTimeMillis();
            Optional<CapturedResponse> result = interceptor.waitForResponse("/api/users");
            long elapsed = System.currentTimeMillis() - startTime;

            assertTrue(result.isEmpty());
            assertTrue(elapsed < 1000, "Should return quickly for non-BiDi driver, took " + elapsed + "ms");
        }

        @Test
        @DisplayName("Should return quickly for waitForServiceResponse with non-BiDi driver")
        void shouldReturnQuicklyForWaitForServiceResponseWithNonBiDiDriver() {
            long startTime = System.currentTimeMillis();
            Optional<CapturedResponse> result = interceptor.waitForServiceResponse("/api/users");
            long elapsed = System.currentTimeMillis() - startTime;

            assertTrue(result.isEmpty());
            assertTrue(elapsed < 1000, "Should return quickly for non-BiDi driver, took " + elapsed + "ms");
        }
    }

    @Nested
    @DisplayName("Listener Registration Tests")
    class ListenerRegistrationTests {

        @BeforeEach
        void setUp() {
            interceptor = new NetworkInterceptor(mockDriver);
        }

        @Test
        @DisplayName("Should support onRequest listener registration with fluent API")
        void shouldSupportOnRequestListenerRegistrationWithFluentApi() {
            NetworkInterceptor result = interceptor.onRequest(request -> {});
            assertSame(interceptor, result);
        }

        @Test
        @DisplayName("Should support onResponse listener registration with fluent API")
        void shouldSupportOnResponseListenerRegistrationWithFluentApi() {
            NetworkInterceptor result = interceptor.onResponse(response -> {});
            assertSame(interceptor, result);
        }
    }

    @Nested
    @DisplayName("Authentication Tests")
    class AuthenticationTests {

        @BeforeEach
        void setUp() {
            interceptor = new NetworkInterceptor(mockDriver);
        }

        @Test
        @DisplayName("Should support withBasicAuth with fluent API")
        void shouldSupportWithBasicAuthWithFluentApi() {
            NetworkInterceptor result = interceptor.withBasicAuth("user", "pass");
            assertSame(interceptor, result);
        }

        @Test
        @DisplayName("Should support withBasicAuthForDomain with fluent API")
        void shouldSupportWithBasicAuthForDomainWithFluentApi() {
            NetworkInterceptor result = interceptor.withBasicAuthForDomain("example.com", "user", "pass");
            assertSame(interceptor, result);
        }
    }

    @Nested
    @DisplayName("Utility Tests")
    class UtilityTests {

        @BeforeEach
        void setUp() {
            interceptor = new NetworkInterceptor(mockDriver);
        }

        @Test
        @DisplayName("Should handle printSummary without errors")
        void shouldHandlePrintSummaryWithoutErrors() {
            assertDoesNotThrow(() -> interceptor.printSummary());
        }
    }
}
