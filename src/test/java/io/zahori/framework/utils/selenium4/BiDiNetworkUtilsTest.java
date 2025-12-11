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
import org.openqa.selenium.safari.SafariDriver;

import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for BiDiNetworkUtils.
 * Tests the underlying utilities for network interception (ZAH-146).
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("BiDiNetworkUtils Tests")
class BiDiNetworkUtilsTest {

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

    @Mock
    private SafariDriver mockSafariDriver;

    @BeforeEach
    void setUp() {
        BiDiNetworkUtils.clearCaptured();
    }

    @Nested
    @DisplayName("BiDi Support Detection Tests")
    class BiDiSupportTests {

        @Test
        @DisplayName("Should support BiDi for ChromeDriver")
        void shouldSupportBiDiForChromeDriver() {
            assertTrue(BiDiNetworkUtils.supportsBiDi(mockChromeDriver));
        }

        @Test
        @DisplayName("Should support BiDi for FirefoxDriver")
        void shouldSupportBiDiForFirefoxDriver() {
            assertTrue(BiDiNetworkUtils.supportsBiDi(mockFirefoxDriver));
        }

        @Test
        @DisplayName("Should support BiDi for EdgeDriver")
        void shouldSupportBiDiForEdgeDriver() {
            assertTrue(BiDiNetworkUtils.supportsBiDi(mockEdgeDriver));
        }

        @Test
        @DisplayName("Should not support BiDi for RemoteWebDriver")
        void shouldNotSupportBiDiForRemoteWebDriver() {
            assertFalse(BiDiNetworkUtils.supportsBiDi(mockRemoteDriver));
        }

        @Test
        @DisplayName("Should not support BiDi for SafariDriver")
        void shouldNotSupportBiDiForSafariDriver() {
            assertFalse(BiDiNetworkUtils.supportsBiDi(mockSafariDriver));
        }

        @Test
        @DisplayName("Should not support BiDi for generic WebDriver")
        void shouldNotSupportBiDiForGenericWebDriver() {
            assertFalse(BiDiNetworkUtils.supportsBiDi(mockDriver));
        }
    }

    @Nested
    @DisplayName("CapturedRequest Record Tests")
    class CapturedRequestRecordTests {

        @Test
        @DisplayName("Should create CapturedRequest with all fields")
        void shouldCreateCapturedRequestWithAllFields() {
            long timestamp = System.currentTimeMillis();
            CapturedRequest request = new CapturedRequest("https://api.example.com/users", "GET", timestamp);

            assertEquals("https://api.example.com/users", request.url());
            assertEquals("GET", request.method());
            assertEquals(timestamp, request.timestamp());
        }

        @Test
        @DisplayName("Should implement equals correctly")
        void shouldImplementEqualsCorrectly() {
            long timestamp = 1234567890L;
            CapturedRequest request1 = new CapturedRequest("https://api.example.com/users", "GET", timestamp);
            CapturedRequest request2 = new CapturedRequest("https://api.example.com/users", "GET", timestamp);
            CapturedRequest request3 = new CapturedRequest("https://api.example.com/posts", "GET", timestamp);

            assertEquals(request1, request2);
            assertNotEquals(request1, request3);
        }

        @Test
        @DisplayName("Should implement hashCode correctly")
        void shouldImplementHashCodeCorrectly() {
            long timestamp = 1234567890L;
            CapturedRequest request1 = new CapturedRequest("https://api.example.com/users", "GET", timestamp);
            CapturedRequest request2 = new CapturedRequest("https://api.example.com/users", "GET", timestamp);

            assertEquals(request1.hashCode(), request2.hashCode());
        }

        @Test
        @DisplayName("Should implement toString correctly")
        void shouldImplementToStringCorrectly() {
            CapturedRequest request = new CapturedRequest("https://api.example.com/users", "POST", 1234567890L);

            String str = request.toString();
            assertTrue(str.contains("https://api.example.com/users"));
            assertTrue(str.contains("POST"));
            assertTrue(str.contains("1234567890"));
        }
    }

    @Nested
    @DisplayName("CapturedResponse Record Tests")
    class CapturedResponseRecordTests {

        @Test
        @DisplayName("Should create CapturedResponse with all fields")
        void shouldCreateCapturedResponseWithAllFields() {
            long timestamp = System.currentTimeMillis();
            CapturedResponse response = new CapturedResponse("https://api.example.com/users", 200, "OK", timestamp);

            assertEquals("https://api.example.com/users", response.url());
            assertEquals(200, response.statusCode());
            assertEquals("OK", response.statusText());
            assertEquals(timestamp, response.timestamp());
        }

        @Test
        @DisplayName("Should implement equals correctly")
        void shouldImplementEqualsCorrectly() {
            long timestamp = 1234567890L;
            CapturedResponse response1 = new CapturedResponse("https://api.example.com/users", 200, "OK", timestamp);
            CapturedResponse response2 = new CapturedResponse("https://api.example.com/users", 200, "OK", timestamp);
            CapturedResponse response3 = new CapturedResponse("https://api.example.com/users", 404, "Not Found", timestamp);

            assertEquals(response1, response2);
            assertNotEquals(response1, response3);
        }

        @Test
        @DisplayName("Should support different status codes")
        void shouldSupportDifferentStatusCodes() {
            CapturedResponse okResponse = new CapturedResponse("url", 200, "OK", 0);
            CapturedResponse createdResponse = new CapturedResponse("url", 201, "Created", 0);
            CapturedResponse notFoundResponse = new CapturedResponse("url", 404, "Not Found", 0);
            CapturedResponse serverErrorResponse = new CapturedResponse("url", 500, "Internal Server Error", 0);

            assertEquals(200, okResponse.statusCode());
            assertEquals(201, createdResponse.statusCode());
            assertEquals(404, notFoundResponse.statusCode());
            assertEquals(500, serverErrorResponse.statusCode());
        }
    }

    @Nested
    @DisplayName("Captured Data Management Tests")
    class CapturedDataManagementTests {

        @Test
        @DisplayName("Should return empty lists after clearCaptured")
        void shouldReturnEmptyListsAfterClearCaptured() {
            BiDiNetworkUtils.clearCaptured();

            List<CapturedRequest> requests = BiDiNetworkUtils.getCapturedRequests();
            List<CapturedResponse> responses = BiDiNetworkUtils.getCapturedResponses();
            List<String> urls = BiDiNetworkUtils.getCapturedUrls();

            assertTrue(requests.isEmpty());
            assertTrue(responses.isEmpty());
            assertTrue(urls.isEmpty());
        }

        @Test
        @DisplayName("Should return empty list for getRequestsByUrlPattern when no matches")
        void shouldReturnEmptyListForGetRequestsByUrlPatternWhenNoMatches() {
            List<CapturedRequest> filtered = BiDiNetworkUtils.getRequestsByUrlPattern("/api/users");
            assertTrue(filtered.isEmpty());
        }

        @Test
        @DisplayName("Should return empty list for getErrorResponses when nothing captured")
        void shouldReturnEmptyListForGetErrorResponsesWhenNothingCaptured() {
            List<CapturedResponse> errors = BiDiNetworkUtils.getErrorResponses();
            assertTrue(errors.isEmpty());
        }

        @Test
        @DisplayName("Should return false for hasHttpErrors when nothing captured")
        void shouldReturnFalseForHasHttpErrorsWhenNothingCaptured() {
            assertFalse(BiDiNetworkUtils.hasHttpErrors());
        }
    }

    @Nested
    @DisplayName("Query Helper Methods Tests")
    class QueryHelperMethodsTests {

        @Test
        @DisplayName("Should return false for hasRequestTo when nothing captured")
        void shouldReturnFalseForHasRequestToWhenNothingCaptured() {
            assertFalse(BiDiNetworkUtils.hasRequestTo("/api/users"));
        }

        @Test
        @DisplayName("Should return 0 for countRequestsTo when nothing captured")
        void shouldReturnZeroForCountRequestsToWhenNothingCaptured() {
            assertEquals(0, BiDiNetworkUtils.countRequestsTo("/api/users"));
        }

        @Test
        @DisplayName("Should return empty for getLastRequestTo when nothing captured")
        void shouldReturnEmptyForGetLastRequestToWhenNothingCaptured() {
            Optional<CapturedRequest> result = BiDiNetworkUtils.getLastRequestTo("/api/users");
            assertTrue(result.isEmpty());
        }

        @Test
        @DisplayName("Should return empty for getLastResponseFrom when nothing captured")
        void shouldReturnEmptyForGetLastResponseFromWhenNothingCaptured() {
            Optional<CapturedResponse> result = BiDiNetworkUtils.getLastResponseFrom("/api/users");
            assertTrue(result.isEmpty());
        }
    }

    @Nested
    @DisplayName("Wait Methods Tests (Non-BiDi Driver)")
    class WaitMethodsNonBiDiTests {

        @Test
        @DisplayName("Should return empty for waitForRequest with non-BiDi driver")
        void shouldReturnEmptyForWaitForRequestWithNonBiDiDriver() {
            Optional<CapturedRequest> result = BiDiNetworkUtils.waitForRequest(
                    mockDriver, "/api/users", Duration.ofMillis(100));
            assertTrue(result.isEmpty());
        }

        @Test
        @DisplayName("Should return empty for waitForResponse with non-BiDi driver")
        void shouldReturnEmptyForWaitForResponseWithNonBiDiDriver() {
            Optional<CapturedResponse> result = BiDiNetworkUtils.waitForResponse(
                    mockDriver, "/api/users", Duration.ofMillis(100));
            assertTrue(result.isEmpty());
        }

        @Test
        @DisplayName("Should return empty for waitForSuccessResponse with non-BiDi driver")
        void shouldReturnEmptyForWaitForSuccessResponseWithNonBiDiDriver() {
            Optional<CapturedResponse> result = BiDiNetworkUtils.waitForSuccessResponse(
                    mockDriver, "/api/users", Duration.ofMillis(100));
            assertTrue(result.isEmpty());
        }

        @Test
        @DisplayName("Should return empty list for waitForRequests with non-BiDi driver")
        void shouldReturnEmptyListForWaitForRequestsWithNonBiDiDriver() {
            List<CapturedRequest> results = BiDiNetworkUtils.waitForRequests(
                    mockDriver, "/api/users", 3, Duration.ofMillis(100));
            assertTrue(results.isEmpty());
        }

        @Test
        @DisplayName("Should return empty for waitForServiceCall with non-BiDi driver")
        void shouldReturnEmptyForWaitForServiceCallWithNonBiDiDriver() {
            Optional<CapturedResponse> result = BiDiNetworkUtils.waitForServiceCall(
                    mockDriver, "/api/users", Duration.ofMillis(100));
            assertTrue(result.isEmpty());
        }
    }

    @Nested
    @DisplayName("Async Wait Tests")
    class AsyncWaitTests {

        @Test
        @DisplayName("Should return CompletableFuture for waitForRequestAsync")
        void shouldReturnCompletableFutureForWaitForRequestAsync() {
            CompletableFuture<CapturedRequest> future = BiDiNetworkUtils.waitForRequestAsync(
                    mockDriver, "/api/users", Duration.ofMillis(100));

            assertNotNull(future);
        }

        @Test
        @DisplayName("Should complete with null for non-BiDi driver")
        void shouldCompleteWithNullForNonBiDiDriver() throws Exception {
            CompletableFuture<CapturedRequest> future = BiDiNetworkUtils.waitForRequestAsync(
                    mockDriver, "/api/users", Duration.ofMillis(100));

            CapturedRequest result = future.get();
            assertNull(result);
        }
    }

    @Nested
    @DisplayName("Stop Capturing Tests")
    class StopCapturingTests {

        @Test
        @DisplayName("Should handle stopCapturing gracefully when not capturing")
        void shouldHandleStopCapturingGracefullyWhenNotCapturing() {
            assertDoesNotThrow(() -> BiDiNetworkUtils.stopCapturing());
        }

        @Test
        @DisplayName("Should handle multiple stopCapturing calls gracefully")
        void shouldHandleMultipleStopCapturingCallsGracefully() {
            assertDoesNotThrow(() -> {
                BiDiNetworkUtils.stopCapturing();
                BiDiNetworkUtils.stopCapturing();
                BiDiNetworkUtils.stopCapturing();
            });
        }
    }

    @Nested
    @DisplayName("Start Capturing Tests (Non-BiDi)")
    class StartCapturingNonBiDiTests {

        @Test
        @DisplayName("Should handle startCapturing gracefully for non-BiDi driver")
        void shouldHandleStartCapturingGracefullyForNonBiDiDriver() {
            assertDoesNotThrow(() -> BiDiNetworkUtils.startCapturingRequests(mockDriver));
        }
    }

    @Nested
    @DisplayName("Listener Registration Tests (Non-BiDi)")
    class ListenerRegistrationNonBiDiTests {

        @Test
        @DisplayName("Should handle onRequest gracefully for non-BiDi driver")
        void shouldHandleOnRequestGracefullyForNonBiDiDriver() {
            assertDoesNotThrow(() ->
                    BiDiNetworkUtils.onRequest(mockDriver, request -> {}));
        }

        @Test
        @DisplayName("Should handle onResponse gracefully for non-BiDi driver")
        void shouldHandleOnResponseGracefullyForNonBiDiDriver() {
            assertDoesNotThrow(() ->
                    BiDiNetworkUtils.onResponse(mockDriver, response -> {}));
        }
    }

    @Nested
    @DisplayName("Print Summary Tests")
    class PrintSummaryTests {

        @Test
        @DisplayName("Should handle printTrafficSummary when nothing captured")
        void shouldHandlePrintTrafficSummaryWhenNothingCaptured() {
            assertDoesNotThrow(() -> BiDiNetworkUtils.printTrafficSummary());
        }
    }

    @Nested
    @DisplayName("Authentication Tests (Non-BiDi)")
    class AuthenticationNonBiDiTests {

        @Test
        @DisplayName("Should handle registerBasicAuth gracefully for non-BiDi driver")
        void shouldHandleRegisterBasicAuthGracefullyForNonBiDiDriver() {
            assertDoesNotThrow(() ->
                    BiDiNetworkUtils.registerBasicAuth(mockDriver, "user", "pass"));
        }

        @Test
        @DisplayName("Should handle registerBasicAuthForDomain gracefully for non-BiDi driver")
        void shouldHandleRegisterBasicAuthForDomainGracefullyForNonBiDiDriver() {
            assertDoesNotThrow(() ->
                    BiDiNetworkUtils.registerBasicAuthForDomain(mockDriver, "example.com", "user", "pass"));
        }
    }
}
