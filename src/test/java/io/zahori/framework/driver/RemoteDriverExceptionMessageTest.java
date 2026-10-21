package io.zahori.framework.driver;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class RemoteDriverExceptionMessageTest {

    private static final String GRID = "http://grid.test:4444/wd/hub";
    private static final String BROWSERSTACK = "https://hub.browserstack.com/wd/hub";

    private static RuntimeException failingWith(String causeMessage) {
        return new RuntimeException("top", new RuntimeException(causeMessage));
    }

    static Stream<Arguments> causes() {
        return Stream.of(
                Arguments.of(GRID, "java.net.UnknownHostException: grid.test", "Unknown host: grid.test"),
                // Un texto con Timeout no es una espera: solo lo es la excepcion de espera
                Arguments.of(GRID, "Timeout of 30 s", "top Causes: . Timeout of 30 s"),
                Arguments.of(GRID, "Conexión rehusada", "Connection refused from remote server " + GRID
                        + ". Check that the remote server is up and there is connectivity with it."),
                Arguments.of(GRID, "connection refused: connect", "Connection refused from remote server " + GRID
                        + ". Check that the remote server is up and there is connectivity with it."),
                Arguments.of(BROWSERSTACK, "Authorization required", "Authorization required: userName and accessKey capabilities are not present"),
                Arguments.of(BROWSERSTACK, "Platform and Browser not valid", "Invalid platformName, deviceName or browserName capabilities."),
                Arguments.of(BROWSERSTACK, "Could not find device: Pixel 9", "Could not find device: Pixel 9"),
                Arguments.of(BROWSERSTACK, "[browserstack.local] is set to true but local testing is not connected",
                        "[browserstack.local] is set to true but local testing is not connected"),
                Arguments.of(BROWSERSTACK, "[BROWSERSTACK_INVALID_APP_URL] bs://x",
                        "Application not present in BrowserStack. Review that the application is uploaded in BrowserStack and the correct url (bs://...) is set in Zahori."),
                Arguments.of(BROWSERSTACK, "[BROWSERSTACK_QUEUE_SIZE_EXCEEDED] queue full", "[BROWSERSTACK_QUEUE_SIZE_EXCEEDED] queue full"),
                Arguments.of(BROWSERSTACK, "Invalid value for browserstack.idleTimeout",
                        "BrowserStack rejected the session: Invalid value for browserstack.idleTimeout (remote server: " + BROWSERSTACK + ")"),
                Arguments.of(BROWSERSTACK, "something else", "top Causes: . something else"),
                // Fuera de BrowserStack sus codigos no se interpretan
                Arguments.of(GRID, "Authorization required", "top Causes: . Authorization required"),
                Arguments.of(GRID, "[BROWSERSTACK_QUEUE_SIZE_EXCEEDED] queue full", "top Causes: . [BROWSERSTACK_QUEUE_SIZE_EXCEEDED] queue full"));
    }

    @ParameterizedTest
    @MethodSource("causes")
    void namesTheReasonTheGridGave(String remoteUrl, String causeMessage, String expected) {
        assertThat(RemoteDriver.getExceptionMessage(failingWith(causeMessage), remoteUrl)).isEqualTo(expected);
    }

    static Stream<Throwable> timeouts() {
        return Stream.of(new java.util.concurrent.TimeoutException("request timed out"),
                new org.openqa.selenium.TimeoutException("request timed out"),
                new java.net.SocketTimeoutException("request timed out"),
                new java.net.http.HttpTimeoutException("request timed out"));
    }

    @ParameterizedTest
    @MethodSource("timeouts")
    void aTimeoutIsRecognisedByItsType(Throwable timeout) {
        String message = RemoteDriver.getExceptionMessage(new RuntimeException("top", timeout), BROWSERSTACK);

        assertThat(message).startsWith("request timed out").contains("(remote server: " + BROWSERSTACK + ")");
        assertThat(RemoteDriver.isDeterministicRejection(message)).isFalse();
    }

    @Test
    void aRejectionThatNamesATimeoutCapabilityFailsAtOnce() {
        String message = RemoteDriver.getExceptionMessage(failingWith("Invalid value for browserstack.idleTimeout"), BROWSERSTACK);

        assertThat(RemoteDriver.isDeterministicRejection(message)).isTrue();
    }

    @Test
    void withoutCauseKeepsTheMessageWithoutTheHostAndBuildInfo() {
        Exception failure = new RuntimeException("Could not start a new session. Host info: h Build info: b");

        assertThat(RemoteDriver.getExceptionMessage(failure, GRID)).isEqualTo("Could not start a new session. ");
    }

    @Test
    void walksTheWholeChainOfCauses() {
        Exception failure = new RuntimeException("top", new IllegalStateException("middle", new IllegalArgumentException("bottom")));

        assertThat(RemoteDriver.getExceptionMessage(failure, GRID)).isEqualTo("top Causes: . middle. bottom");
    }

    @Test
    void findsAKnownReasonDeepInTheChain() {
        Exception failure = new RuntimeException("top",
                new IllegalStateException("middle", new RuntimeException("java.net.UnknownHostException: grid.test")));

        assertThat(RemoteDriver.getExceptionMessage(failure, GRID)).isEqualTo("Unknown host: grid.test");
    }
}
