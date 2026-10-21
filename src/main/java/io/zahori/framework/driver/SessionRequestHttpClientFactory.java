package io.zahori.framework.driver;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import org.openqa.selenium.remote.http.ClientConfig;
import org.openqa.selenium.remote.http.HttpClient;
import org.openqa.selenium.remote.http.HttpMethod;
import org.openqa.selenium.remote.http.HttpRequest;
import org.openqa.selenium.remote.http.HttpResponse;
import org.openqa.selenium.remote.http.WebSocket;

/**
 * Plazo largo solo para crear la sesion: en cola del grid (BrowserStack, hasta 15 min) el cliente
 * no la abandona, y el grid no crea una sesion que ya no espera nadie. Los comandos, el normal.
 */
final class SessionRequestHttpClientFactory implements HttpClient.Factory {

    private final HttpClient.Factory delegate;
    private final Duration sessionReadTimeout;
    private final List<HttpClient> created = new CopyOnWriteArrayList<>();

    SessionRequestHttpClientFactory(HttpClient.Factory delegate, Duration sessionReadTimeout) {
        this.delegate = delegate;
        this.sessionReadTimeout = sessionReadTimeout;
    }

    @Override
    public HttpClient createClient(ClientConfig config) {
        HttpClient routed = new SessionRoutingHttpClient(delegate.createClient(config.readTimeout(sessionReadTimeout)),
                delegate.createClient(config));
        created.add(routed);
        return routed;
    }

    @Override
    public void cleanupIdleClients() {
        delegate.cleanupIdleClients();
    }

    /** Un intento fallido no deja sus clientes abiertos: sin sesion nadie los cierra. */
    void closeAll() {
        created.forEach(HttpClient::close);
    }

    static final class SessionRoutingHttpClient implements HttpClient {
        private final HttpClient newSession;
        private final HttpClient commands;

        SessionRoutingHttpClient(HttpClient newSession, HttpClient commands) {
            this.newSession = newSession;
            this.commands = commands;
        }

        static boolean isNewSession(HttpRequest request) {
            return request.getMethod() == HttpMethod.POST && "/session".equals(request.getUri());
        }

        @Override
        public HttpResponse execute(HttpRequest request) {
            return clientFor(request).execute(request);
        }

        @Override
        public CompletableFuture<HttpResponse> executeAsync(HttpRequest request) {
            return clientFor(request).executeAsync(request);
        }

        @Override
        public WebSocket openSocket(HttpRequest request, WebSocket.Listener listener) {
            return commands.openSocket(request, listener);
        }

        @Override
        public void close() {
            newSession.close();
            commands.close();
        }

        private HttpClient clientFor(HttpRequest request) {
            return isNewSession(request) ? newSession : commands;
        }
    }
}
