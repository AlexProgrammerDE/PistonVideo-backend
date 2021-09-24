package net.pistonmaster.pistonvideo;

import ch.qos.logback.classic.Level;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.pistonmaster.pistonvideo.templates.*;
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
        ((ch.qos.logback.classic.Logger)LoggerFactory.getLogger(ch.qos.logback.classic.Logger.ROOT_LOGGER_NAME)).setLevel(Level.INFO);
        DBManager.init(args[0], args[1], args[2], Integer.parseInt(args[3]));

        port(3434);

        externalStaticFileLocation(videoManager.uploadDir.getAbsolutePath());

        before("/*", (q, a) -> System.out.println("A call"));
        path("/api", () -> {
            path("/auth", () -> {
                before("/*", (q, a) -> System.out.println("Auth call"));
                post("/login", (request, response) -> {
                    LoginRequest loginRequest = new Gson().fromJson(request.body(), LoginRequest.class);

                    Optional<String> token = authenticator.generateToken(loginRequest.getEmail(), loginRequest.getPassword());

                    return token.map(s -> new Gson().toJson(new TokenResponse(s))).orElse("{}");
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
                post("/register", (request, response) -> {
                    SignupRequest signupRequest = new Gson().fromJson(request.body(), SignupRequest.class);

                    Authenticator.RejectReason reason = authenticator.createUser(signupRequest.getUsername(), signupRequest.getEmail(), signupRequest.getPassword());
                    if (reason == Authenticator.RejectReason.NONE) {
                        return new Gson().toJson(new SuccessResponse(true));
                    } else {
                        return new Gson().toJson(new SuccessErrorResponse(false, reason.getErrorMessage()));
                    }
                });
                post("/forgotpassword", (request, response) -> null);
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
            get("/suggestions", suggester::suggestions);
        });
    }
}
