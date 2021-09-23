package net.pistonmaster.pistonvideo;

import net.pistonmaster.pistonvideo.templates.Video;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Optional;

import static spark.Spark.*;

public class PistonVideoApplication {
    private static final Authenticator authenticator = new Authenticator();
    private static final VideoManager videoManager = new VideoManager();
    public static final Logger LOG = LoggerFactory.getLogger(PistonVideoApplication.class);
    private static final Suggester suggester = new Suggester();
    public static final Video NYAN_CAT = new Video("nyan", "Nyan Cat", "Meow meow meow", "/static/videos/nyan.mp4", "/static/thumbnails/nyan.png", new String[]{"meow", "nyan", "owo"});

    public static void main(String[] args) {
        port(3434);

        externalStaticFileLocation(videoManager.uploadDir.getAbsolutePath());

        before("/*", (q, a) -> System.out.println("A call"));
        path("/api", () -> {
            path("/auth", () -> {
                before("/*", (q, a) -> System.out.println("Auth call"));
                post("/login", (request, response) -> {
                    String email = request.queryParams("email");
                    String password = request.queryParams("password");

                    Optional<String> token = authenticator.generateToken(email, password);

                    return token.map(s -> "{\"token\": \"" + s + "\"}").orElse("{}");
                });

                post("/logout", (request, response) -> {
                    String token = request.queryParams("token");

                    authenticator.invalidate(token);

                    return "{}";
                });

                get("/user", (request, response) -> {
                    String token = request.headers("Authorization");

                    return "{user: {}}";
                });
            });
            path("/user", () -> {
                get("/register", (request, response) -> {
                    String username = request.queryParams("username");
                    String email = request.queryParams("email");
                    String password = request.queryParams("password");

                    Authenticator.RejectReason reason = authenticator.createUser(username, email, password);

                    if (reason == Authenticator.RejectReason.NONE) {
                        Optional<String> token = authenticator.generateToken(email, password);

                        if (token.isPresent()) {

                        }

                        return true;
                    } else {
                        return false;
                    }
                });
                post("/update", (request, response) -> null);
                post("/delete", (request, response) -> null);
            });
            path("/restricted", () -> {
                before((request, response) -> {
                    String token = request.queryParams("token");

                    if (!authenticator.isValid(token))
                        halt(401, "No or invalid token!");
                });
                path("/video", () -> {
                    post("/create", videoManager::upload);
                    post("/update", (request, response) -> null);
                    post("/delete", (request, response) -> null);
                });
            });
            get("/videodata", videoManager::videoData);
            get("/suggestions", suggester::getSuggestions);
        });

    }
}
