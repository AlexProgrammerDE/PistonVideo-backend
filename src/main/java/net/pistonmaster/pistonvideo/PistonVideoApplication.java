package net.pistonmaster.pistonvideo;

import ch.qos.logback.classic.Level;
import com.google.gson.Gson;
import lombok.Getter;
import net.pistonmaster.pistonvideo.templates.*;
import net.pistonmaster.pistonvideo.templates.auth.LoginRequest;
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
    public static final VideoResponse NYAN_CAT = new VideoResponse("nyan", "Nyan Cat", "Meow meow meow", "/static/videos/nyan.mp4", "/static/thumbnails/nyan.png", new String[]{"meow", "nyan", "owo"}, new PublicUserResponse("Pistonmaster", "", "/avatars/", ""));
    public static final PublicUserResponse DELETED_USER = new PublicUserResponse("Deleted User", "deleted", "/static/avatars/blank.png", "");
    public static final VideoResponse DELETED_VIDEO = new VideoResponse("deleted", "Deleted Video", "", "", "", new String[]{}, DELETED_USER);

    public static void main(String[] args) {
        ((ch.qos.logback.classic.Logger) LoggerFactory.getLogger(ch.qos.logback.classic.Logger.ROOT_LOGGER_NAME)).setLevel(Level.INFO);
        DBManager.init(args[0], args[1], args[2], Integer.parseInt(args[3]));

        int maxThreads = 8;
        int minThreads = 2;
        int timeOutMillis = 30000;
        threadPool(maxThreads, minThreads, timeOutMillis);

        port(3434);

        externalStaticFileLocation(VideoManager.uploadDir.getAbsolutePath());

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
                        halt(401, "No token!");

                    if (userManager.getUserIdFromToken(token).isEmpty())
                        halt(401, "Invalid token!");

                    return "{user: {}}";
                });
            });
            path("/user", () -> {
                post("/register", userManager::register);
                post("/forgotpassword", (request, response) -> null); // TODO
                post("/updatedata", userManager::updateData);
                post("/updateinfo", userManager::updateInfo);
                post("/delete", (request, response) -> null); // TODO
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
                    post("/update", (request, response) -> null); // TODO
                    post("/delete", (request, response) -> null); // TODO
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
            get("/uservideos", (request, response) -> {
                String userId = request.queryParams("id");

                if (userId == null)
                    throw new IllegalArgumentException("id missing");

                return new Gson().toJson(userManager.generatePublicVideosResponse(userId));
            });
        });
    }
}
