package io.zahori.framework.driver;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.doThrow;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import io.zahori.framework.driver.browserfactory.Browsers;
import org.junit.jupiter.api.Test;
import org.openqa.selenium.Proxy;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.openqa.selenium.NoSuchSessionException;
import org.openqa.selenium.NoSuchElementException;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebDriverException;
import org.openqa.selenium.remote.UnreachableBrowserException;

/** Una sesion que el grid mata al configurarla se sustituye por otra; cualquier otro fallo se propaga. */
class RemoteDriverStartSessionTest {

    private final RemoteDriver driver = new RemoteDriver();
    private final List<WebDriver> created = new ArrayList<>();

    private WebDriver fresh() {
        WebDriver session = mock(WebDriver.class);
        created.add(session);
        return session;
    }

    @Test
    void aSessionConfiguredAtOnceIsReturnedUntouched() {
        WebDriver result = driver.startSession(this::fresh, session -> { });

        assertThat(result).isSameAs(created.get(0));
        assertThat(created).hasSize(1);
    }

    @Test
    void aSessionKilledByTheGridWhileConfiguringIsReplacedByANewOne() {
        AtomicInteger configured = new AtomicInteger();

        WebDriver result = driver.startSession(this::fresh, session -> {
            if (configured.incrementAndGet() == 1) {
                throw new UnreachableBrowserException("Error communicating with the remote browser. It may have died.");
            }
        });

        assertThat(created).hasSize(2);
        assertThat(result).isSameAs(created.get(1));
        verify(created.get(0)).quit();
        verify(created.get(1), never()).quit();
    }

    @Test
    void afterTheLastAttemptTheFailureIsPropagated() {
        assertThatThrownBy(() -> driver.startSession(this::fresh, session -> {
            throw new NoSuchSessionException("Session not started or terminated");
        })).isInstanceOf(NoSuchSessionException.class);

        assertThat(created).hasSize(RemoteDriver.SETUP_ATTEMPTS);
        created.forEach(session -> verify(session).quit());
    }

    @Test
    void aFailureThatIsNotALostSessionIsNotRetried() {
        assertThatThrownBy(() -> driver.startSession(this::fresh, session -> {
            throw new NoSuchElementException("not a session problem");
        })).isInstanceOf(NoSuchElementException.class);

        assertThat(created).hasSize(1);
        verify(created.get(0)).quit();
    }

    @Test
    void aDeadSessionThatCannotBeClosedDoesNotHideTheRetry() {
        AtomicInteger configured = new AtomicInteger();
        List<WebDriver> sessions = new ArrayList<>();

        WebDriver result = driver.startSession(() -> {
            WebDriver session = mock(WebDriver.class);
            doThrow(new WebDriverException("already gone")).when(session).quit();
            sessions.add(session);
            return session;
        }, session -> {
            if (configured.incrementAndGet() == 1) {
                throw new UnreachableBrowserException("died");
            }
        });

        assertThat(result).isSameAs(sessions.get(1));
    }

    @Test
    void aSessionKilledOnTheLastAttemptAfterRecoveringStillSucceeds() {
        AtomicInteger configured = new AtomicInteger();

        WebDriver result = driver.startSession(this::fresh, session -> {
            if (configured.incrementAndGet() < RemoteDriver.SETUP_ATTEMPTS) {
                throw new UnreachableBrowserException("died");
            }
        });

        assertThat(created).hasSize(RemoteDriver.SETUP_ATTEMPTS);
        assertThat(result).isSameAs(created.get(RemoteDriver.SETUP_ATTEMPTS - 1));
    }

    @Test
    void getDriverCreatesAndConfiguresThroughTheRetry() {
        List<WebDriver> configuredSessions = new ArrayList<>();
        AtomicInteger configured = new AtomicInteger();
        RemoteDriver wired = new RemoteDriver() {
            @Override
            protected WebDriver createWebDriver(Browsers browsers, Proxy proxy) {
                return fresh();
            }

            @Override
            protected void configureWebDriver(WebDriver webDriver, Browsers browsers) {
                configuredSessions.add(webDriver);
                if (configured.incrementAndGet() == 1) {
                    throw new UnreachableBrowserException("died");
                }
            }
        };

        WebDriver result = wired.getDriver(null, null);

        assertThat(configuredSessions).containsExactly(created.get(0), created.get(1));
        assertThat(result).isSameAs(created.get(1));
    }

    @ParameterizedTest
    @ValueSource(strings = {"Session not started or terminated", "SO_TIMEOUT", "Error communicating with the remote browser. It may have died."})
    void theGridMessagesThatMeanALostSessionAreRecognised(String message) {
        assertThat(RemoteDriver.isDeadSession(new WebDriverException(message))).isTrue();
    }

    @Test
    void theMessageOfTheRootCauseCounts() {
        WebDriverException wrapped = new WebDriverException("wrapper", new IllegalArgumentException("Session not started or terminated"));

        assertThat(RemoteDriver.isDeadSession(wrapped)).isTrue();
    }

    @Test
    void anUnrelatedErrorIsNotALostSession() {
        assertThat(RemoteDriver.isDeadSession(new WebDriverException("element not interactable"))).isFalse();
        assertThat(RemoteDriver.isDeadSession(new WebDriverException((String) null))).isFalse();
    }
}
