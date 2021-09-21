package net.pistonmaster.pistonvideo;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.util.Optional;

import static spark.Spark.*;

public class PistonVideoApplication {
    private static final Authenticator authenticator = new Authenticator();
    private static final VideoManager videoManager = new VideoManager();
    public static final Logger LOG = LoggerFactory.getLogger(PistonVideoApplication.class);

    public static void main(String[] args) {
        port(3434);

        File uploadDir = new File("upload");
        uploadDir.mkdir();

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

            path("/watch", () -> {
                post("/delete", (request, response) -> null);
            });
        });

    }
}
