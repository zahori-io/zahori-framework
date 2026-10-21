package io.zahori.framework.driver;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import io.appium.java_client.android.AndroidDriver;
import io.appium.java_client.ios.IOSDriver;
import io.zahori.framework.driver.browserfactory.Browsers;
import java.net.URL;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.mockito.InOrder;
import org.openqa.selenium.Capabilities;
import org.openqa.selenium.MutableCapabilities;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.remote.HttpCommandExecutor;
import org.openqa.selenium.remote.RemoteWebDriver;
import org.openqa.selenium.remote.http.ClientConfig;
import org.openqa.selenium.remote.http.HttpClient;

@Timeout(value = 60, unit = TimeUnit.SECONDS)
class RemoteDriverSessionTest {

    private static final Duration TOTAL = Duration.ofHours(1);
    private static final Duration SESSION_REQUEST = Duration.ofMinutes(20);
    private static final String QUEUE_FULL = "[BROWSERSTACK_QUEUE_SIZE_EXCEEDED] All parallel tests are currently in use";

    /** Clientes HTTP que solo registran su plazo y si se cerraron. */
    static final class RecordingClients implements HttpClient.Factory {
        final List<HttpClient> clients = new CopyOnWriteArrayList<>();
        final List<Duration> readTimeouts = new CopyOnWriteArrayList<>();

        @Override
        public HttpClient createClient(ClientConfig config) {
            HttpClient client = mock(HttpClient.class);
            clients.add(client);
            readTimeouts.add(config.readTimeout());
            return client;
        }
    }

    /** Grid con guion: cada intento tarda lo indicado en el reloj y falla o da sesion. */
    static final class ScriptedGrid implements RemoteDriver.SessionFactory {
        record Attempt(long tookMillis, RuntimeException failure) {
        }

        final Queue<Attempt> script = new ConcurrentLinkedQueue<>();
        final AtomicLong clock;
        final WebDriver session = mock(WebDriver.class);
        int attempts;

        ScriptedGrid(AtomicLong clock) {
            this.clock = clock;
        }

        ScriptedGrid failsAfter(long millis, String reason) {
            script.add(new Attempt(millis, new IllegalStateException(reason)));
            return this;
        }

        ScriptedGrid failsAtOnce(String reason) {
            return failsAfter(0, reason);
        }

        @Override
        public WebDriver create(URL grid, Capabilities capabilities, HttpClient.Factory clients) {
            attempts++;
            clients.createClient(ClientConfig.defaultConfig().baseUrl(grid));
            Attempt attempt = script.poll();
            if (attempt == null) {
                return session;
            }
            clock.addAndGet(attempt.tookMillis());
            throw attempt.failure();
        }
    }

    private final AtomicLong clock = new AtomicLong(3_600_000L);
    private final RemoteDriver.Sleeper sleeper = mock(RemoteDriver.Sleeper.class);
    private final RecordingClients http = new RecordingClients();
    private final ScriptedGrid grid = new ScriptedGrid(clock);
    private RemoteDriver driver;

    @BeforeEach
    void setUp() throws Exception {
        doAnswer(invocation -> clock.addAndGet(invocation.<Duration>getArgument(0).toMillis())).when(sleeper).sleep(any());
        driver = new RemoteDriver(clock::get, sleeper, () -> 1.0, http);
    }

    private WebDriver create(Duration total) throws Exception {
        return driver.createSession(grid, new URL("http://grid.test/wd/hub"), new MutableCapabilities(), total, SESSION_REQUEST,
                Exception::getMessage);
    }

    @Nested
    class TheFirstAttempt {

        @Test
        void returnsTheSessionWithoutPausing() throws Exception {
            assertThat(create(TOTAL)).isSameAs(grid.session);

            assertThat(grid.attempts).isEqualTo(1);
            verifyNoInteractions(sleeper);
        }

        @Test
        void waitsForTheSessionMoreThanForACommand() throws Exception {
            create(TOTAL);

            assertThat(http.readTimeouts).containsExactly(SESSION_REQUEST, ClientConfig.defaultConfig().readTimeout());
        }

        @Test
        void leavesTheClientsOfTheSessionOpen() throws Exception {
            create(TOTAL);

            http.clients.forEach(client -> verify(client, never()).close());
        }
    }

    @Nested
    class AFullQueue {

        @Test
        void isRetriedAfterAPauseThatGrows() throws Exception {
            grid.failsAtOnce(QUEUE_FULL).failsAtOnce(QUEUE_FULL).failsAtOnce(QUEUE_FULL);

            assertThat(create(TOTAL)).isSameAs(grid.session);

            InOrder pauses = inOrder(sleeper);
            pauses.verify(sleeper).sleep(Duration.ofSeconds(5));
            pauses.verify(sleeper).sleep(Duration.ofSeconds(10));
            pauses.verify(sleeper).sleep(Duration.ofSeconds(20));
            assertThat(grid.attempts).isEqualTo(4);
        }

        @Test
        void closesTheClientsOfEveryFailedAttempt() throws Exception {
            grid.failsAtOnce(QUEUE_FULL);

            create(TOTAL);

            verify(http.clients.get(0)).close();
            verify(http.clients.get(1)).close();
            verify(http.clients.get(2), never()).close();
            verify(http.clients.get(3), never()).close();
        }

        @Test
        void aFailureThatTookFiveSecondsIsRetriedWithoutPause() throws Exception {
            grid.failsAfter(5000, "Read timed out");

            create(TOTAL);

            verifyNoInteractions(sleeper);
            assertThat(grid.attempts).isEqualTo(2);
        }

        @Test
        void aFailureUnderFiveSecondsPauses() throws Exception {
            grid.failsAfter(4999, QUEUE_FULL);

            create(TOTAL);

            verify(sleeper).sleep(Duration.ofSeconds(5));
        }

        @Test
        void aSlowFailureStartsTheBackoffAgain() throws Exception {
            grid.failsAtOnce(QUEUE_FULL).failsAtOnce(QUEUE_FULL).failsAfter(30_000, "Read timed out").failsAtOnce(QUEUE_FULL);

            create(TOTAL);

            InOrder pauses = inOrder(sleeper);
            pauses.verify(sleeper).sleep(Duration.ofSeconds(5));
            pauses.verify(sleeper).sleep(Duration.ofSeconds(10));
            pauses.verify(sleeper).sleep(Duration.ofSeconds(5));
            pauses.verifyNoMoreInteractions();
        }
    }

    @Nested
    class TheDeadline {

        @Test
        void stopsRetryingWithTheLastReason() throws Exception {
            grid.failsAtOnce("first " + QUEUE_FULL).failsAtOnce("second " + QUEUE_FULL).failsAtOnce("third " + QUEUE_FULL);

            assertThatThrownBy(() -> create(Duration.ofSeconds(12)))
                    .hasMessage("second " + QUEUE_FULL)
                    .hasCauseInstanceOf(IllegalStateException.class);

            InOrder pauses = inOrder(sleeper);
            pauses.verify(sleeper).sleep(Duration.ofSeconds(5));
            pauses.verify(sleeper).sleep(Duration.ofSeconds(7));
            assertThat(grid.attempts).isEqualTo(2);
        }

        @Test
        void doesNotPauseOnceItHasPassed() throws Exception {
            grid.failsAfter(3000, QUEUE_FULL);

            assertThatThrownBy(() -> create(Duration.ofSeconds(3))).hasMessage(QUEUE_FULL);

            verifyNoInteractions(sleeper);
            assertThat(grid.attempts).isEqualTo(1);
        }

        @Test
        void neverShortensTheRequestOfTheLastAttempt() throws Exception {
            grid.failsAfter(9000, "Read timed out");

            create(Duration.ofSeconds(10));

            // Recortarla a lo que queda abandonaria una peticion que el grid aun atiende
            assertThat(http.readTimeouts.get(2)).isEqualTo(SESSION_REQUEST);
        }
    }

    @Nested
    class ARejection {

        @Test
        void isReportedAtOnceWithItsCause() throws Exception {
            grid.failsAtOnce("Authorization required: userName and accessKey capabilities are not present");

            assertThatThrownBy(() -> create(TOTAL))
                    .hasMessage("Authorization required: userName and accessKey capabilities are not present")
                    .cause().isInstanceOf(IllegalStateException.class);

            assertThat(grid.attempts).isEqualTo(1);
            verifyNoInteractions(sleeper);
            verify(http.clients.get(0)).close();
        }
    }

    @Nested
    class AnInterruption {

        @Test
        void whilePausingEndsTheCreationAndKeepsTheFlag() throws Exception {
            doThrow(new InterruptedException()).when(sleeper).sleep(any());
            grid.failsAtOnce(QUEUE_FULL);

            try {
                assertThatThrownBy(() -> create(TOTAL)).hasMessage(QUEUE_FULL);
                assertThat(Thread.currentThread().isInterrupted()).isTrue();
                assertThat(grid.attempts).isEqualTo(1);
            } finally {
                Thread.interrupted();
            }
        }
    }

    @Nested
    class TheDefaults {

        @Test
        void pauseForReal() throws Exception {
            long start = System.nanoTime();

            RemoteDriver.THREAD_SLEEP.sleep(Duration.ofMillis(200));

            assertThat(Duration.ofNanos(System.nanoTime() - start)).isGreaterThanOrEqualTo(Duration.ofMillis(200));
        }

        @Test
        void spreadTheRetriesTwentyPercentEitherWay() {
            for (int i = 0; i < 1000; i++) {
                assertThat(RemoteDriver.JITTER.getAsDouble()).isBetween(0.8, 1.2);
            }
        }
    }

    @Nested
    class CasesInParallel {

        @Test
        void eachCaseGetsItsOwnSessionAndClients() throws Exception {
            int cases = 8;
            ExecutorService pool = Executors.newFixedThreadPool(cases);
            CountDownLatch start = new CountDownLatch(1);
            List<ScriptedGrid> grids = new ArrayList<>();
            List<Future<WebDriver>> sessions = new ArrayList<>();
            try {
                for (int i = 0; i < cases; i++) {
                    ScriptedGrid caseGrid = new ScriptedGrid(clock).failsAtOnce(QUEUE_FULL).failsAtOnce(QUEUE_FULL);
                    grids.add(caseGrid);
                    Callable<WebDriver> call = () -> {
                        start.await();
                        return driver.createSession(caseGrid, new URL("http://grid.test/wd/hub"), new MutableCapabilities(), TOTAL,
                                SESSION_REQUEST, Exception::getMessage);
                    };
                    sessions.add(pool.submit(call));
                }
                start.countDown();
                for (int i = 0; i < cases; i++) {
                    assertThat(sessions.get(i).get(30, TimeUnit.SECONDS)).isSameAs(grids.get(i).session);
                }
            } finally {
                pool.shutdownNow();
            }

            assertThat(http.clients).hasSize(cases * 3 * 2);
            long closed = http.clients.stream().filter(client -> {
                try {
                    verify(client).close();
                    return true;
                } catch (AssertionError notClosed) {
                    return false;
                }
            }).count();
            assertThat(closed).isEqualTo(cases * 2 * 2);
        }
    }

    @Nested
    class AgainstARealGrid {

        private final RemoteDriver real = new RemoteDriver(System::currentTimeMillis, mock(RemoteDriver.Sleeper.class), () -> 1.0,
                HttpCommandExecutor.getDefaultClientFactory());

        @Test
        void aSessionHeldInTheQueueIsCreatedWithASingleRequest() throws Exception {
            try (FakeGrid fake = new FakeGrid().thenReply(FakeGrid.queued(1500))) {
                WebDriver session = real.createSession(RemoteDriver.WEB, fake.url(), new MutableCapabilities(), TOTAL,
                        Duration.ofSeconds(10), Exception::getMessage);

                assertThat(((RemoteWebDriver) session).getSessionId()).hasToString("session-1");
                assertThat(fake.newSessionRequests()).isEqualTo(1);
                session.quit();
            }
        }

        @Test
        void anAbandonedRequestStillEndsInASessionOnTheGrid() throws Exception {
            try (FakeGrid fake = new FakeGrid().thenReply(FakeGrid.queued(1000))) {
                assertThatThrownBy(() -> real.createSession(RemoteDriver.WEB, fake.url(), new MutableCapabilities(), Duration.ofMillis(100),
                        Duration.ofMillis(300), Exception::getMessage)).isInstanceOf(RuntimeException.class);

                // Por esto la peticion espera mas que la cola: la sesion nace sin nadie que la use
                assertThat(fake.awaitSessionsCreated(1, Duration.ofSeconds(5))).isTrue();
                assertThat(fake.newSessionRequests()).isEqualTo(1);
            }
        }

        @Test
        void aFullQueueAnsweredByTheGridIsRetried() throws Exception {
            try (FakeGrid fake = new FakeGrid().thenReply(FakeGrid.rejected(QUEUE_FULL))) {
                WebDriver session = real.createSession(RemoteDriver.WEB, fake.url(), new MutableCapabilities(), TOTAL,
                        Duration.ofSeconds(10), Exception::getMessage);

                assertThat(fake.newSessionRequests()).isEqualTo(2);
                session.quit();
            }
        }

        @Test
        void aWebConfigurationOpensARemoteWebDriverSession() throws Exception {
            try (FakeGrid fake = new FakeGrid()) {
                Browsers browsers = new Browsers().withName("CHROME").withPlatform("LINUX").withRemoteUrl(fake.url().toString());

                WebDriver session = real.createWebDriver(browsers, null);

                assertThat(session).isExactlyInstanceOf(RemoteWebDriver.class);
                assertThat(fake.sessionRequestBodies().get(0)).containsPattern("\"browserName\"\\s*:\\s*\"chrome\"");
                session.quit();
            }
        }

        @Test
        void anAndroidConfigurationOpensAnAndroidSession() throws Exception {
            try (FakeGrid fake = new FakeGrid()) {
                Browsers browsers = new Browsers().withPlatform("ANDROID").withEnvironmentUrl("https://stg.example/")
                        .withRemoteUrl(fake.url().toString());

                WebDriver session = real.createWebDriver(browsers, null);

                assertThat(session).isInstanceOf(AndroidDriver.class);
                session.quit();
            }
        }

        @Test
        void anIosConfigurationOpensAnIosSession() throws Exception {
            try (FakeGrid fake = new FakeGrid()) {
                Browsers browsers = new Browsers().withPlatform("IOS").withEnvironmentUrl("https://stg.example/")
                        .withRemoteUrl(fake.url().toString());

                WebDriver session = real.createWebDriver(browsers, null);

                assertThat(session).isInstanceOf(IOSDriver.class);
                session.quit();
            }
        }

        @Test
        void theTimeoutsComeFromTheEnvironmentProperties() throws Exception {
            try (FakeGrid fake = new FakeGrid().thenReply(FakeGrid.queued(2500))) {
                Browsers browsers = new Browsers().withName("CHROME").withPlatform("LINUX").withEnvironmentName("GridQueue")
                        .withRemoteUrl(fake.url().toString());

                assertThatThrownBy(() -> real.createWebDriver(browsers, null)).hasMessageContaining("TimeoutException").hasMessageContaining("(remote server: ");
                assertThat(fake.newSessionRequests()).isEqualTo(1);
            }
        }

        @Test
        void anAppiumRejectionIsReportedAtOnce() throws Exception {
            try (FakeGrid fake = new FakeGrid().thenReply(FakeGrid.rejected("Authorization required"))) {
                Browsers browsers = new Browsers().withPlatform("ANDROID").withEnvironmentUrl("https://stg.example/")
                        .withRemoteUrl(fake.url().toString());

                assertThatThrownBy(() -> real.createWebDriver(browsers, null))
                        .hasMessageContaining("Authorization required")
                        .hasMessageNotContaining("Capabilities sent:");
                assertThat(fake.newSessionRequests()).isEqualTo(1);
            }
        }

        @Test
        void aRejectionNamesTheCapabilitiesThatWereSent() throws Exception {
            try (FakeGrid fake = new FakeGrid().thenReply(FakeGrid.rejected("Authorization required"))) {
                Browsers browsers = new Browsers().withName("CHROME").withPlatform("LINUX").withRemoteUrl(fake.url().toString());

                assertThatThrownBy(() -> real.createWebDriver(browsers, null)).hasMessageContaining("Capabilities sent:");
                assertThat(fake.newSessionRequests()).isEqualTo(1);
            }
        }
    }
}
