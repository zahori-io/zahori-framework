package io.zahori.framework.driver;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.mockito.ArgumentCaptor;
import org.openqa.selenium.remote.HttpCommandExecutor;
import org.openqa.selenium.remote.http.ClientConfig;
import org.openqa.selenium.remote.http.Contents;
import org.openqa.selenium.remote.http.HttpClient;
import org.openqa.selenium.remote.http.HttpMethod;
import org.openqa.selenium.remote.http.HttpRequest;
import org.openqa.selenium.remote.http.HttpResponse;
import org.openqa.selenium.remote.http.WebSocket;

@Timeout(value = 30, unit = TimeUnit.SECONDS)
class SessionRequestHttpClientFactoryTest {

    private static final Duration COMMAND_TIMEOUT = Duration.ofMillis(300);
    private static final Duration SESSION_TIMEOUT = Duration.ofSeconds(10);
    private static final long GRID_QUEUE_MILLIS = 1200;

    private static HttpRequest newSession() {
        HttpRequest request = new HttpRequest(HttpMethod.POST, "/session");
        request.setContent(Contents.utf8String("{}"));
        return request;
    }

    private static HttpClient realClientFor(FakeGrid grid) throws Exception {
        return new SessionRequestHttpClientFactory(HttpCommandExecutor.getDefaultClientFactory(), SESSION_TIMEOUT)
                .createClient(ClientConfig.defaultConfig().baseUrl(grid.url()).readTimeout(COMMAND_TIMEOUT));
    }

    @Nested
    class AgainstARealGrid {

        @Test
        void aSessionInTheGridQueueIsWaitedForBeyondTheCommandTimeout() throws Exception {
            try (FakeGrid grid = new FakeGrid().thenReply(FakeGrid.queued(GRID_QUEUE_MILLIS)); HttpClient client = realClientFor(grid)) {
                HttpResponse response = client.execute(newSession());

                assertThat(response.getStatus()).isEqualTo(200);
                assertThat(grid.newSessionRequests()).describedAs("una sola peticion: ninguna huerfana").isEqualTo(1);
            }
        }

        @Test
        void aSessionInTheGridQueueIsWaitedForAlsoAsynchronously() throws Exception {
            try (FakeGrid grid = new FakeGrid().thenReply(FakeGrid.queued(GRID_QUEUE_MILLIS)); HttpClient client = realClientFor(grid)) {
                assertThat(client.executeAsync(newSession()).get().getStatus()).isEqualTo(200);
            }
        }

        @Test
        void aCommandKeepsItsOwnTimeout() throws Exception {
            try (FakeGrid grid = new FakeGrid().withCommandDelay(GRID_QUEUE_MILLIS); HttpClient client = realClientFor(grid)) {
                assertThatThrownBy(() -> client.execute(new HttpRequest(HttpMethod.GET, "/session/s/url")))
                        .isInstanceOf(org.openqa.selenium.TimeoutException.class);
            }
        }

        @Test
        void anAsynchronousCommandKeepsItsOwnTimeout() throws Exception {
            try (FakeGrid grid = new FakeGrid().withCommandDelay(GRID_QUEUE_MILLIS); HttpClient client = realClientFor(grid)) {
                assertThatThrownBy(() -> client.executeAsync(new HttpRequest(HttpMethod.GET, "/session/s/url")).get())
                        .isInstanceOf(ExecutionException.class);
            }
        }
    }

    @Nested
    class Routing {

        @Test
        void onlyTheNewSessionRequestGetsTheLongTimeout() {
            assertThat(SessionRequestHttpClientFactory.SessionRoutingHttpClient.isNewSession(newSession())).isTrue();
            assertThat(SessionRequestHttpClientFactory.SessionRoutingHttpClient.isNewSession(new HttpRequest(HttpMethod.GET, "/session"))).isFalse();
            assertThat(SessionRequestHttpClientFactory.SessionRoutingHttpClient.isNewSession(new HttpRequest(HttpMethod.POST, "/session/s/url"))).isFalse();
            assertThat(SessionRequestHttpClientFactory.SessionRoutingHttpClient.isNewSession(new HttpRequest(HttpMethod.DELETE, "/session/s"))).isFalse();
        }
    }

    @Nested
    class Delegation {

        private final HttpClient.Factory delegate = mock(HttpClient.Factory.class);
        private final HttpClient sessionClient = mock(HttpClient.class);
        private final HttpClient commandClient = mock(HttpClient.class);
        private final ClientConfig config = ClientConfig.defaultConfig().readTimeout(COMMAND_TIMEOUT);
        private final SessionRequestHttpClientFactory factory = new SessionRequestHttpClientFactory(delegate, SESSION_TIMEOUT);

        Delegation() {
            when(delegate.createClient(any(ClientConfig.class))).thenReturn(sessionClient, commandClient);
        }

        @Test
        void theSessionClientGetsTheLongTimeoutAndTheCommandClientTheConfiguredOne() {
            factory.createClient(config);

            ArgumentCaptor<ClientConfig> configs = ArgumentCaptor.forClass(ClientConfig.class);
            verify(delegate, times(2)).createClient(configs.capture());
            List<Duration> timeouts = configs.getAllValues().stream().map(ClientConfig::readTimeout).toList();
            assertThat(timeouts).containsExactly(SESSION_TIMEOUT, COMMAND_TIMEOUT);
        }

        @Test
        void requestsGoToTheirClient() {
            HttpClient routed = factory.createClient(config);
            HttpRequest command = new HttpRequest(HttpMethod.GET, "/session/s/url");

            routed.execute(newSession());
            routed.executeAsync(command);

            verify(sessionClient).execute(any());
            verify(commandClient).executeAsync(command);
            verify(commandClient, never()).execute(any());
            verify(sessionClient, never()).executeAsync(any());
        }

        @Test
        void websocketsAreCommands() {
            HttpClient routed = factory.createClient(config);
            HttpRequest request = new HttpRequest(HttpMethod.GET, "/session/s/se/bidi");
            WebSocket.Listener listener = mock(WebSocket.Listener.class);
            WebSocket socket = mock(WebSocket.class);
            when(commandClient.openSocket(request, listener)).thenReturn(socket);

            assertThat(routed.openSocket(request, listener)).isSameAs(socket);
            verify(sessionClient, never()).openSocket(any(), any());
        }

        @Test
        void closingTheRoutedClientClosesBoth() {
            factory.createClient(config).close();

            verify(sessionClient).close();
            verify(commandClient).close();
        }

        @Test
        void aFailedAttemptClosesEveryClientItOpened() {
            factory.createClient(config);

            factory.closeAll();

            verify(sessionClient).close();
            verify(commandClient).close();
        }

        @Test
        void idleCleanupIsTheDelegates() {
            factory.cleanupIdleClients();

            verify(delegate).cleanupIdleClients();
        }
    }
}
