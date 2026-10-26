package io.zahori.framework.driver;

import java.time.Duration;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

class RemoteDriverTimeoutTest {
    @Test
    void usesTheConfiguredValue() {
        Assertions.assertThat(RemoteDriver.positiveSeconds("120", RemoteDriver.DEFAULT_DRIVER_CREATION_TIMEOUT_SECONDS)).isEqualTo(120);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(
        strings = {"   ", "no-es-un-numero", "0", "-30"}
    )
    void fallsBackToTheDefaultWhenTheValueIsNotUsable(String configured) {
        Assertions.assertThat(RemoteDriver.positiveSeconds(configured, RemoteDriver.DEFAULT_DRIVER_CREATION_TIMEOUT_SECONDS)).isEqualTo(3600);
    }

    @Test
    void theDefaultLetsACaseWaitItsTurnInTheGridQueue() {
        // Con 300 s caian los casos en cola de Selenoid
        Assertions.assertThat(RemoteDriver.DEFAULT_DRIVER_CREATION_TIMEOUT_SECONDS).isEqualTo(3600);
    }

    @Test
    void aTimeoutKeepsTheRootCauseSoTheReasonIsNotLost() {
        Exception timeout = new IllegalStateException("Timeout of 30 seconds creating WebDriver object",
                new java.net.SocketTimeoutException("Read timed out"));

        Assertions.assertThat(RemoteDriver.timeoutMessage(timeout, "http://localhost:4444/wd/hub")).isEqualTo(
                "Timeout of 30 seconds creating WebDriver object (remote server: http://localhost:4444/wd/hub). Cause: java.net.SocketTimeoutException: Read timed out");
    }

    @Test
    void aGridRejectionHiddenBehindATimeoutIsStillRecognised() {
        Exception timeout = new IllegalStateException("Timeout of 30 seconds creating WebDriver object",
                new IllegalStateException("wrapper", new java.net.ConnectException("Connection refused")));

        String message = RemoteDriver.timeoutMessage(timeout, "http://localhost:4444/wd/hub");

        Assertions.assertThat(message).endsWith("Cause: java.net.ConnectException: Connection refused");
        Assertions.assertThat(RemoteDriver.isDeterministicRejection(message)).isTrue();
    }

    @Test
    void aTimeoutWithoutCauseIsReportedAsIs() {
        Assertions.assertThat(RemoteDriver.timeoutMessage(new IllegalStateException("Timeout of 30 seconds creating WebDriver object"), "http://grid"))
                .isEqualTo("Timeout of 30 seconds creating WebDriver object (remote server: http://grid)");
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   ", "no-es-un-numero", "0", "-30"})
    void byDefaultTheSessionRequestMayWaitTheWholeTimeInTheGridQueue(String configured) {
        // Con 1200 s caian en la cola de Selenoid los casos de la ejecucion 12145; antes se esperaba la hora entera
        Assertions.assertThat(RemoteDriver.sessionRequestTimeout(configured, Duration.ofSeconds(3600))).isEqualTo(Duration.ofSeconds(3600));
    }

    @Test
    void aConfiguredSessionRequestTimeoutIsRespected() {
        Assertions.assertThat(RemoteDriver.sessionRequestTimeout("1200", Duration.ofSeconds(3600))).isEqualTo(Duration.ofSeconds(1200));
    }

    @Test
    void theDefaultSessionRequestFollowsAConfiguredTotal() {
        Assertions.assertThat(RemoteDriver.sessionRequestTimeout(null, Duration.ofSeconds(900))).isEqualTo(Duration.ofSeconds(900));
    }

    @Test
    void theSessionRequestStillOutlastsTheBrowserStackQueue() {
        // BrowserStack descarta lo encolado a los 15 min: el cliente no puede rendirse antes
        Assertions.assertThat(RemoteDriver.sessionRequestTimeout(null,
                Duration.ofSeconds(RemoteDriver.DEFAULT_DRIVER_CREATION_TIMEOUT_SECONDS))).isGreaterThan(Duration.ofMinutes(15));
    }

    @Test
    void readsAnyPositiveSecondsWithItsOwnDefault() {
        Assertions.assertThat(RemoteDriver.positiveSeconds(" 90 ", 1200)).isEqualTo(90);
        Assertions.assertThat(RemoteDriver.positiveSeconds("0", 1200)).isEqualTo(1200);
        Assertions.assertThat(RemoteDriver.positiveSeconds(null, 7)).isEqualTo(7);
        Assertions.assertThat(RemoteDriver.positiveSeconds("x", 7)).isEqualTo(7);
    }

    @ParameterizedTest
    @CsvSource({"1, 5000", "2, 10000", "3, 20000", "4, 40000", "5, 60000", "6, 60000", "50, 60000"})
    void theBackoffDoublesUpToAMinute(int failures, long expectedMillis) {
        Assertions.assertThat(RemoteDriver.backoff(failures, Long.MAX_VALUE, 1.0)).isEqualTo(Duration.ofMillis(expectedMillis));
    }

    @Test
    void theJitterSpreadsTheThreadsThatFailedTogether() {
        Assertions.assertThat(RemoteDriver.backoff(1, Long.MAX_VALUE, 1.2)).isEqualTo(Duration.ofMillis(6000));
        Assertions.assertThat(RemoteDriver.backoff(5, Long.MAX_VALUE, 0.8)).isEqualTo(Duration.ofMillis(48000));
    }

    @Test
    void theBackoffNeverOutlastsTheRemainingTime() {
        Assertions.assertThat(RemoteDriver.backoff(3, 7000L, 1.0)).isEqualTo(Duration.ofMillis(7000));
    }

    @ParameterizedTest
    @ValueSource(
        strings = {"BrowserStack rejected the session: browserstack.local is not set", "Authorization required: userName and accessKey capabilities are not present", "Invalid platformName, deviceName or browserName capabilities.", "[BROWSERSTACK_INVALID_APP_URL]", "Unknown host: hub.browserstack.com", "Connection refused from remote server"}
    )
    void recognisesTheRejectionThatRetryingWillNotFix(String message) {
        Assertions.assertThat(RemoteDriver.isDeterministicRejection(message)).isTrue();
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(
        strings = {"Timeout of 300 seconds creating WebDriver object", "All parallel tests are currently in use, queued", "Read timed out",
            // Visto en la prueba del 20/10: con la causa raiz el codigo de cola llegaba y cortaba el caso
            "Timeout of 30 seconds creating WebDriver object (remote server: https://hub.browserstack.com/wd/hub). Cause: org.openqa.selenium.WebDriverException: [BROWSERSTACK_QUEUE_SIZE_EXCEEDED] All parallel tests are currently in use, and the number of sessions have exceeded the queue limit.",
            "[BROWSERSTACK_ALL_PARALLELS_IN_USE] wait for a free parallel", "BROWSERSTACK_QUEUE_SIZE_EXCEEDED",
            "[BROWSERSTACK_QUEUE_TIME_LIMIT_EXCEEDED] Session was dropped from the queue"}
    )
    void doesNotMistakeATransientFailureForARejection(String message) {
        Assertions.assertThat(RemoteDriver.isDeterministicRejection(message)).isFalse();
    }
}
