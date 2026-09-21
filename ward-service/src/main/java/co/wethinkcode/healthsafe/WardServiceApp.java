package co.wethinkcode.healthsafe;


import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.javalin.Javalin;
import io.javalin.http.ServiceUnavailableResponse;
import java.io.IOException;
import java.net.URI;
import java.net.http.*;
import java.util.List;

import co.wethinkcode.healthsafe.mq.MqConfig;

public class WardServiceApp {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final HttpClient HTTP = HttpClient.newHttpClient();
    private static volatile List<Ward> wards = List.of();

    public static void main(String[] args) {
        Javalin app = Javalin.create().start(7031);

        app.get("/health", ctx -> ctx.result("OK"));

        // TODO (Provides lists of wards and departments.)
        // Add domain endpoints for ward-service here.
        app.get("/wards", ctx -> ctx.json(getWards()));

        app.get("/wards/{id}", ctx -> {
            String id = ctx.pathParam("id").toUpperCase();
            List<Ward> all = getWards();
            all.stream().filter(w -> w.wardId().equals(id)).findFirst()
                    .ifPresentOrElse(ctx::json,
                            () -> ctx.status(404).result("Ward not found: " + id));
        });

        app.get("/departments", ctx -> ctx.json(
                getWards().stream().map(Ward::department)
                        .filter(d -> d != null).distinct().sorted().toList()));
    }

    private static List<Ward> getWards() {
        if (wards.isEmpty()) {
            try {
                HttpRequest req = HttpRequest.newBuilder(URI.create("http://localhost:7030/wards")).build();
                HttpResponse<String> res = HTTP.send(req, HttpResponse.BodyHandlers.ofString());
                wards = MAPPER.readValue(res.body(), new TypeReference<List<Ward>>() {});
            } catch (IOException e) {
                throw new ServiceUnavailableResponse("ingestion-service is not reachable");
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new ServiceUnavailableResponse("Request to ingestion-service was interrupted");
            }
        }
        return wards;
    }


}

// MQ TODO: subscribes to ActiveMQ topic MqConfig.TOPIC at MqConfig.BROKER_URL (see co.wethinkcode.healthsafe.mq.MqConfig)
// MQ TODO: publishes to ActiveMQ queue MqConfig.QUEUE when it detects an equipment failure on one of its wards.
