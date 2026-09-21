package co.wethinkcode.healthsafe;


import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.javalin.Javalin;
import io.javalin.http.ServiceUnavailableResponse;
import java.io.IOException;
import java.net.URI;
import java.net.http.*;
import java.util.List;
import java.util.Map;

import co.wethinkcode.healthsafe.mq.MqConfig;
import org.apache.activemq.ActiveMQConnectionFactory;

import javax.jms.*;

public class WardServiceApp {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final HttpClient HTTP = HttpClient.newHttpClient();
    private static volatile List<Ward> wards = List.of();

    public static void main(String[] args) {
        //1. Call the subscriber so it starts listening on boot
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

        // 2. Add an endpoint that triggers publishEquipmentFailure when an alert is received
        app.post("/wards/{id}/failure", ctx -> {
            String wardId = ctx.pathParam("id").toUpperCase();
            String payload = MAPPER.writeValueAsString(Map.of(
                    "wardId", wardId,
                    "status", "EQUIPMENT_FAILURE"
            ));

            publishEquipmentFailure(payload);
            ctx.status(202).result("Failure event queued for ward: " + wardId);
        });
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


    // Subscribes to ActiveMQ Topic
    private static void listenForStaffingEvents() throws Exception {
        ConnectionFactory factory = new ActiveMQConnectionFactory(MqConfig.BROKER_URL);
        Connection conn = factory.createConnection();
        conn.start(); // without start(), no messages arrive
        Session session = conn.createSession(false, Session.AUTO_ACKNOWLEDGE);
        MessageConsumer consumer = session.createConsumer(session.createTopic(MqConfig.TOPIC));
        consumer.setMessageListener(msg -> {
            try {
                System.out.println("Staffing event: " + ((TextMessage) msg).getText());
            } catch (JMSException e) {
                e.printStackTrace();
            }
        });
        // keep connection open to continue receiving messages
    }

    // Publishes equipment failure events to ActiveMQ Queue
    public static void publishEquipmentFailure(String failureJson) throws Exception {
        ConnectionFactory factory = new ActiveMQConnectionFactory(MqConfig.BROKER_URL);
        try (Connection conn = factory.createConnection()) {
            Session session = conn.createSession(false, Session.AUTO_ACKNOWLEDGE);
            MessageProducer producer = session.createProducer(session.createQueue(MqConfig.QUEUE));
            producer.send(session.createTextMessage(failureJson));
        }
    }


}

// MQ TODO: subscribes to ActiveMQ topic MqConfig.TOPIC at MqConfig.BROKER_URL (see co.wethinkcode.healthsafe.mq.MqConfig)
// MQ TODO: publishes to ActiveMQ queue MqConfig.QUEUE when it detects an equipment failure on one of its wards.
