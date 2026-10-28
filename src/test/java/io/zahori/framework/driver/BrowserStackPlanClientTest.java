package io.zahori.framework.driver;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.sun.net.httpserver.HttpServer;
import io.zahori.framework.driver.BrowserStackCapacityGate.Plan;
import io.zahori.framework.driver.BrowserStackCapacityGate.Product;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class BrowserStackPlanClientTest {

    private HttpServer server;
    private final AtomicReference<String> authorization = new AtomicReference<>();
    private final AtomicReference<String> path = new AtomicReference<>();
    private volatile int status = 200;
    private volatile String body = "{}";

    @BeforeEach
    void startServer() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            authorization.set(exchange.getRequestHeaders().getFirst("Authorization"));
            path.set(exchange.getRequestURI().getPath());
            byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(status, bytes.length);
            exchange.getResponseBody().write(bytes);
            exchange.close();
        });
        server.start();
    }

    @AfterEach
    void stopServer() {
        server.stop(0);
    }

    private BrowserStackPlanClient client() {
        String base = "http://127.0.0.1:" + server.getAddress().getPort();
        return new BrowserStackPlanClient(product -> URI.create(base + URI.create(product.planUrl()).getPath()));
    }

    @Test
    void readsRunningMaxAndQueuedFromThePlan() throws Exception {
        body = "{\"automate_plan\":\"x\",\"parallel_sessions_running\":2,\"parallel_sessions_max_allowed\":4,"
                + "\"team_parallel_sessions_max_allowed\":4,\"queued_sessions\":1,\"queued_sessions_max_allowed\":5}";

        assertThat(client().read(Product.AUTOMATE, "user", "key")).isEqualTo(new Plan(2, 4, 1));
    }

    @Test
    void theSmallerOfTheTeamAndTheAccountMaximumWins() throws Exception {
        body = "{\"parallel_sessions_running\":1,\"parallel_sessions_max_allowed\":16,\"team_parallel_sessions_max_allowed\":8,"
                + "\"queued_sessions\":0}";

        assertThat(client().read(Product.APP_AUTOMATE, "user", "key").max()).isEqualTo(8);
    }

    @Test
    void aPlanWithoutTheTeamMaximumUsesTheAccountOne() throws Exception {
        body = "{\"parallel_sessions_running\":0,\"parallel_sessions_max_allowed\":16,\"queued_sessions\":0}";

        assertThat(client().read(Product.APP_AUTOMATE, "user", "key").max()).isEqualTo(16);
    }

    @Test
    void sendsTheAccountAsBasicAuthorization() throws Exception {
        body = "{\"parallel_sessions_max_allowed\":4}";

        client().read(Product.AUTOMATE, "someone", "secret");

        String expected = "Basic " + Base64.getEncoder().encodeToString("someone:secret".getBytes(StandardCharsets.UTF_8));
        assertThat(authorization.get()).isEqualTo(expected);
    }

    @Test
    void eachProductAsksItsOwnPlan() throws Exception {
        body = "{\"parallel_sessions_max_allowed\":4}";

        client().read(Product.AUTOMATE, "user", "key");
        assertThat(path.get()).isEqualTo("/automate/plan.json");

        client().read(Product.APP_AUTOMATE, "user", "key");
        assertThat(path.get()).isEqualTo("/app-automate/plan.json");
    }

    @Test
    void anErrorStatusIsReportedWithItsCode() {
        status = 401;

        assertThatThrownBy(() -> client().read(Product.AUTOMATE, "user", "wrong")).isInstanceOf(IOException.class)
                .hasMessage("HTTP 401");
    }

    @Test
    void aBodyThatIsNotJsonIsAnIoFailure() {
        body = "<html>nope</html>";

        assertThatThrownBy(() -> client().read(Product.AUTOMATE, "user", "key")).isInstanceOf(IOException.class);
    }

    @Test
    void theDefaultClientPointsAtBrowserStack() {
        assertThat(Product.AUTOMATE.planUrl()).isEqualTo("https://api.browserstack.com/automate/plan.json");
        assertThat(Product.APP_AUTOMATE.planUrl()).isEqualTo("https://api-cloud.browserstack.com/app-automate/plan.json");
    }
}
