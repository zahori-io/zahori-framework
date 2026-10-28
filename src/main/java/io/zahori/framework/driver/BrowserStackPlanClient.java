package io.zahori.framework.driver;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.zahori.framework.driver.BrowserStackCapacityGate.Plan;
import io.zahori.framework.driver.BrowserStackCapacityGate.PlanSource;
import io.zahori.framework.driver.BrowserStackCapacityGate.Product;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import java.util.function.Function;

/** Lee el plan de sesiones en paralelo de una cuenta de BrowserStack con su API REST. */
final class BrowserStackPlanClient implements PlanSource {

    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(5);
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(10);
    private static final ObjectMapper JSON = new ObjectMapper();

    private final HttpClient http;
    private final Function<Product, URI> planUris;

    BrowserStackPlanClient() {
        this(product -> URI.create(product.planUrl()));
    }

    BrowserStackPlanClient(Function<Product, URI> planUris) {
        this.http = HttpClient.newBuilder().connectTimeout(CONNECT_TIMEOUT).build();
        this.planUris = planUris;
    }

    @Override
    public Plan read(Product product, String user, String key) throws IOException, InterruptedException {
        String credentials = Base64.getEncoder().encodeToString((user + ":" + key).getBytes(StandardCharsets.UTF_8));
        HttpRequest request = HttpRequest.newBuilder(planUris.apply(product)).timeout(REQUEST_TIMEOUT)
                .header("Authorization", "Basic " + credentials).GET().build();
        HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() != 200) {
            throw new IOException("HTTP " + response.statusCode());
        }
        return parse(response.body());
    }

    static Plan parse(String body) throws IOException {
        JsonNode plan = JSON.readTree(body);
        int accountMax = plan.path("parallel_sessions_max_allowed").asInt(0);
        int teamMax = plan.path("team_parallel_sessions_max_allowed").asInt(0);
        // El tope que manda es el menor: un equipo puede tener menos que la cuenta
        int max = accountMax > 0 && teamMax > 0 ? Math.min(accountMax, teamMax) : Math.max(accountMax, teamMax);
        return new Plan(plan.path("parallel_sessions_running").asInt(0), max, plan.path("queued_sessions").asInt(0));
    }
}
