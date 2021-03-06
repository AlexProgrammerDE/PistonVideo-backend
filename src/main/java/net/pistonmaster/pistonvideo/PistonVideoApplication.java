package net.pistonmaster.pistonvideo;

import ch.qos.logback.classic.Level;
import com.google.gson.Gson;
import lombok.Getter;
import net.pistonmaster.pistonvideo.templates.PublicUserResponse;
import net.pistonmaster.pistonvideo.templates.UserIDResponse;
import net.pistonmaster.pistonvideo.templates.VideoResponse;
import net.pistonmaster.pistonvideo.templates.errors.InvalidQueryError;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Optional;

import static net.pistonmaster.pistonvideo.VideoManager.formatAvatarToURL;
import static spark.Spark.*;

public class PistonVideoApplication {
    public static final Logger LOG = LoggerFactory.getLogger(PistonVideoApplication.class);
    public static final PublicUserResponse DELETED_USER = new PublicUserResponse("Deleted User", "deleted", formatAvatarToURL("blank.png"), "", "", new ArrayList<>());
    public static final PublicUserResponse DEFAULT_USER = new PublicUserResponse("Default User", "default", formatAvatarToURL("blank.png"), "", "", new ArrayList<>());
    public static final VideoResponse DELETED_VIDEO = new VideoResponse("deleted", "Deleted Video", "", "", "", new String[]{}, DELETED_USER, 0);
    @Getter
    private static final UserManager userManager = new UserManager();
    private static final VideoManager videoManager = new VideoManager();
    private static final Suggester suggester = new Suggester();
    public static final String FFMPEG_PATH = Helpers.findExecutableOnPath("ffmpeg");
    public static final String FFPROBE_PATH = Helpers.findExecutableOnPath("ffprobe");

    public static void main(String[] args) {
        ((ch.qos.logback.classic.Logger) LoggerFactory.getLogger(ch.qos.logback.classic.Logger.ROOT_LOGGER_NAME)).setLevel(Level.INFO);
        String username = System.getenv("USERNAME");
        String password = System.getenv("PASSWORD");

        String host = Optional.ofNullable(System.getenv("HOST")).orElse("localhost");
        int port = Optional.ofNullable(System.getenv("PORT")).map(Integer::parseInt).orElse(27017);

        DBManager.init(username, password, host, port);

        int maxThreads = 8;
        int minThreads = 2;
        int timeOutMillis = 30000;
        threadPool(maxThreads, minThreads, timeOutMillis);

        port(3434);

        externalStaticFileLocation(VideoManager.uploadDir.getAbsolutePath());

        before("/*", (q, a) -> System.out.println("A call"));

        get("/me", (request, response) -> {
            Optional<String> userId = userManager.getUserIdFromToken(request);
            if (userId.isEmpty())
                halt(401, "Invalid token!");

            return new Gson().toJson(userManager.generatePublicResponse(userId.get()));
        });
        path("/user", () -> {
            get("/id", (request, response) -> {
                Optional<String> userId = userManager.getUserIdFromToken(request);
                if (userId.isEmpty())
                    halt(401, "Invalid token!");

                return new Gson().toJson(new UserIDResponse(userId.get()));
            });
            post("/updatedata", userManager::updateData);
        });
        path("/restricted", () -> {
            before((request, response) -> {
                if (userManager.getUserIdFromToken(request).isEmpty())
                    halt(401, "No or invalid token!");
            });
            path("/video", () -> {
                post("/create", videoManager::upload);
                post("/update", (request, response) -> null); // TODO
                post("/delete", (request, response) -> null); // TODO
            });
            get("/privatevideodata", videoManager::privateVideoData);
        });
        get("/videodata", videoManager::videoData, new Gson()::toJson);
        get("/suggestions", suggester::suggestions, new Gson()::toJson);
        get("/userdata", (request, response) -> {
            String userId = request.queryParams("id");

            if (userId == null)
                return new InvalidQueryError("id missing");

            return userManager.generatePublicResponse(userId);
        }, new Gson()::toJson);
        get("/uservideos", (request, response) -> {
            String userId = request.queryParams("id");

            if (userId == null)
                return new InvalidQueryError("id");

            return userManager.generatePublicVideosResponse(userId);
        }, new Gson()::toJson);
        get("/watch", videoManager::watch, new Gson()::toJson);
    }
}
