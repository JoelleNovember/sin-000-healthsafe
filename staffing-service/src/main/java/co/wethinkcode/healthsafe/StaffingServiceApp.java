package co.wethinkcode.healthsafe;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.javalin.Javalin;
import io.javalin.http.ServiceUnavailableResponse;
import java.io.IOException;
import java.net.URI;
import java.net.http.*;
import java.util.*;


public class StaffingServiceApp {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final HttpClient HTTP = HttpClient.newHttpClient();

    private static final Map<String, List<String>> ROSTER = Map.of(
            "Cardiology",  List.of("Dr Ada", "Dr Ben", "Dr Chipo", "Dr Dan"),
            "Paediatrics", List.of("Dr Eve", "Dr Farai", "Dr Gugu"),
            "Oncology",    List.of("Dr Hugo", "Dr Ines", "Dr Jabu"),
            "Radiology",   List.of("Dr Kim", "Dr Lwazi"),
            "ICU",         List.of("Dr Mo", "Dr Naledi", "Dr Omar", "Dr Pam"),
            "Maternity",   List.of("Dr Quinn", "Dr Rudo", "Dr Sipho"));


    public static void main(String[] args) {
        Javalin app = Javalin.create().start(7033);

        app.get("/health", ctx -> ctx.result("OK"));

        // TODO (Provides on-call schedules for doctors based on ward and status.)
        // Add domain endpoints for staffing-service here.
        app.get("/schedule/{wardId}", ctx -> {
            Map<String, Object> schedule = buildSchedule(ctx.pathParam("wardId"));
            if (schedule == null) {
                ctx.status(404).result("Unknown ward: " + ctx.pathParam("wardId"));
            } else {
                ctx.json(schedule);
            }
        });
    }

    // Returns null if the ward does not exist
    private static Map<String, Object> buildSchedule(String wardId) throws Exception {
        try {
            // W: does the ward exist?
            HttpResponse<String> wardRes = get("http://localhost:7031/wards/" + wardId);
            if (wardRes.statusCode() == 404) return null;
            String dept = MAPPER.readTree(wardRes.body()).get("department").asText();

            // L: what is the alert level?
            HttpResponse<String> levelRes = get("http://localhost:7032/alert-level");
            int level = MAPPER.readTree(levelRes.body()).get("level").asInt();

            // R: who is on the roster?
            List<String> doctors = ROSTER.getOrDefault(dept, List.of());
            int needed = level >= 8 ? doctors.size()
                    : Math.min(doctors.size(), 1 + level / 3);

            return Map.of("wardId", wardId.toUpperCase(), "department", dept,
                    "alertLevel", level, "onCall", doctors.subList(0, needed));
        } catch (IOException e) {
            throw new ServiceUnavailableResponse("a dependent service is not reachable");
        }
    }

    private static HttpResponse<String> get(String url) throws Exception {
        return HTTP.send(HttpRequest.newBuilder(URI.create(url)).build(),
                HttpResponse.BodyHandlers.ofString());
    }
}

// MQ TODO: publishes to ActiveMQ topic MqConfig.TOPIC at MqConfig.BROKER_URL (see co.wethinkcode.healthsafe.mq.MqConfig)
