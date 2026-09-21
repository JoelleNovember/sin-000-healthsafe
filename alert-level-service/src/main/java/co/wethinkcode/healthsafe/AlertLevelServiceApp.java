package co.wethinkcode.healthsafe;

import io.javalin.Javalin;
import io.javalin.http.BadRequestResponse;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

public class AlertLevelServiceApp {

    public record LevelRequest(int level) {}

    public static void main(String[] args) {
        AtomicInteger level = new AtomicInteger(0); // thread-safe number
        Javalin app = Javalin.create().start(7032);

        app.get("/health", ctx -> ctx.result("OK"));

        app.get("/alert-level", ctx -> ctx.json(Map.of("level", level.get())));

        app.put("/alert-level", ctx -> {
            int newLevel = ctx.bodyAsClass(LevelRequest.class).level();
            if (newLevel < 0 || newLevel > 8) {
                throw new BadRequestResponse("level must be between 0 and 8");
            }
            level.set(newLevel);
            ctx.json(Map.of("level", newLevel));
        });


        // TODO (Tracks the hospital Emergency Status (0-8, 8 = full Code Blue).)
        // Add domain endpoints for alert-level-service here.
    }
}
