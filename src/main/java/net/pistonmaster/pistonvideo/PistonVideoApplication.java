package net.pistonmaster.pistonvideo;

import ch.qos.logback.classic.Level;
import com.google.gson.Gson;
import lombok.Getter;
import net.pistonmaster.pistonvideo.templates.*;
import net.pistonmaster.pistonvideo.templates.auth.LoginRequest;
import net.pistonmaster.pistonvideo.templates.auth.SignupRequest;
import net.pistonmaster.pistonvideo.templates.simple.SuccessErrorResponse;
import net.pistonmaster.pistonvideo.templates.simple.SuccessResponse;
import net.pistonmaster.pistonvideo.templates.simple.TokenResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Optional;

import static spark.Spark.*;

public class PistonVideoApplication {
    @Getter
    private static final UserManager userManager = new UserManager();
    private static final VideoManager videoManager = new VideoManager();
    public static final Logger LOG = LoggerFactory.getLogger(PistonVideoApplication.class);
    private static final Suggester suggester = new Suggester();
    public static final VideoResponse NYAN_CAT = new VideoResponse("nyan", "Nyan Cat", "Meow meow meow", "/static/videos/nyan.mp4", "/static/thumbnails/nyan.png", new String[]{"meow", "nyan", "owo"}, new PublicUserResponse("Pistonmaster", "", "/avatars/"));

    public static void main(String[] args) {
        ((ch.qos.logback.classic.Logger) LoggerFactory.getLogger(ch.qos.logback.classic.Logger.ROOT_LOGGER_NAME)).setLevel(Level.INFO);
        DBManager.init(args[0], args[1], args[2], Integer.parseInt(args[3]));

        int maxThreads = 8;
        int minThreads = 2;
        int timeOutMillis = 30000;
        threadPool(maxThreads, minThreads, timeOutMillis);

        port(3434);

        externalStaticFileLocation(videoManager.uploadDir.getAbsolutePath());

        before("/*", (q, a) -> System.out.println("A call"));
        path("/api", () -> {
            path("/auth", () -> {
                post("/login", (request, response) -> {
                    LoginRequest loginRequest = new Gson().fromJson(request.body(), LoginRequest.class);

                    Optional<String> token = userManager.generateToken(loginRequest.getEmail(), loginRequest.getPassword());

                    return token.map(s -> new Gson().toJson(new TokenResponse(s))).orElse("{}");
                });

                post("/logout", (request, response) -> {
                    String token = request.queryParams("token");

                    userManager.invalidate(token);

                    return "{}";
                });

                get("/user", (request, response) -> {
                    String token = request.headers("Authorization");

                    if (token == null)
                        halt(401, "No or invalid token!");

                    if (userManager.getUserIdFromToken(token).isEmpty())
                        halt(401, "No or invalid token!");

                    return "{user: {}}";
                });
            });
            path("/user", () -> {
                post("/register", (request, response) -> {
                    SignupRequest signupRequest = new Gson().fromJson(request.body(), SignupRequest.class);

                    UserManager.RejectReason reason = userManager.createUser(signupRequest.getUsername(), signupRequest.getEmail(), signupRequest.getPassword());
                    if (reason == UserManager.RejectReason.NONE) {
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
                    String token = request.headers("Authorization");

                    if (token == null)
                        halt(401, "No or invalid token!");

                    if (userManager.getUserIdFromToken(token).isEmpty())
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
            get("/userdata", (request, response) -> {
                String userId = request.queryParams("id");

                if (userId == null)
                    throw new IllegalArgumentException("id missing");

                return new Gson().toJson(userManager.generatePublicResponse(userId));
            });
        });
    }
}
