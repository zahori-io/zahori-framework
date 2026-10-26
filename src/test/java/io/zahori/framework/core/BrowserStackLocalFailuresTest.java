package io.zahori.framework.core;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

class BrowserStackLocalFailuresTest {

    // Tal cual lo devolvio BrowserStack Automate en la ejecucion 12140 (OS X - PRO)
    private static final String AUTOMATE = "Could not start a new session. Handshake response does not match any supported protocol. "
            + "Response payload: {\"value\":{\"message\":\"[browserstack.local] is set to true but local testing through BrowserStack is not connected.\"},"
            + "\"sessionId\":\"\",\"status\":13}";
    // Tal cual lo devolvio App Automate en la ejecucion 12109 (Android)
    private static final String APP_AUTOMATE = "[BROWSERSTACK_LOCAL_CONNECTION_FAILED] Issues with the local testing configurations have been observed. "
            + "Refer to this troubleshooting guide to identify and resolve your BrowserStack Local related issue";

    @Test
    void recognisesAutomateReportingTheTunnelAsNotConnected() {
        assertThat(BrowserStackLocalFailures.reasonOf(new RuntimeException(AUTOMATE)))
                .isEqualTo(BrowserStackLocalFailures.NOT_CONNECTED_REASON);
    }

    @Test
    void recognisesAppAutomateReportingTheLocalConnectionAsFailed() {
        assertThat(BrowserStackLocalFailures.reasonOf(new RuntimeException(APP_AUTOMATE)))
                .isEqualTo(BrowserStackLocalFailures.CONNECTION_FAILED_REASON);
    }

    @Test
    void looksForTheFailureDownTheWholeCauseChain() {
        Exception wrapped = new IllegalStateException("Error creating the driver",
                new RuntimeException("wrapper", new IllegalStateException(APP_AUTOMATE)));

        assertThat(BrowserStackLocalFailures.reasonOf(wrapped)).isEqualTo(BrowserStackLocalFailures.CONNECTION_FAILED_REASON);
    }

    @Test
    void aCauseWithoutMessageDoesNotHideTheOneBelowIt() {
        Exception wrapped = new RuntimeException((String) null, new RuntimeException(AUTOMATE));

        assertThat(BrowserStackLocalFailures.reasonOf(wrapped)).isEqualTo(BrowserStackLocalFailures.NOT_CONNECTED_REASON);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {
        "[BROWSERSTACK_QUEUE_SIZE_EXCEEDED] All parallel tests are currently in use",
        "BrowserStack rejected the session: browserstack.local is not set",
        "Invalid platformName, deviceName or browserName capabilities. localIdentifier=12140-104536",
        "local testing through BrowserStack is connected",
        "Timeout of 30 seconds creating WebDriver object (remote server: https://hub.browserstack.com/wd/hub)"})
    void doesNotMistakeAnotherFailureForALocalOne(String message) {
        assertThat(BrowserStackLocalFailures.reasonOf(new RuntimeException(message))).isNull();
    }

    @Test
    void withoutFailureThereIsNoReason() {
        assertThat(BrowserStackLocalFailures.reasonOf(null)).isNull();
    }
}
