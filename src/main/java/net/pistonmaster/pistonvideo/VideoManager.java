package net.pistonmaster.pistonvideo;

import com.google.gson.Gson;
import com.mongodb.MongoException;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import com.mongodb.client.model.Projections;
import com.mongodb.client.result.InsertOneResult;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import net.pistonmaster.pistonvideo.templates.VideoResponse;
import net.pistonmaster.pistonvideo.templates.errors.InvalidQueryError;
import net.pistonmaster.pistonvideo.templates.errors.NotAuthenticatedError;
import net.pistonmaster.pistonvideo.templates.errors.RateLimitError;
import net.pistonmaster.pistonvideo.templates.errors.VideoNotFoundError;
import net.pistonmaster.pistonvideo.templates.simple.SuccessIDResponse;
import net.pistonmaster.pistonvideo.templates.simple.SuccessResponse;
import org.bson.Document;
import org.bson.conversions.Bson;
import org.bson.types.ObjectId;
import spark.Request;
import spark.Response;

import javax.servlet.MultipartConfigElement;
import javax.servlet.http.Part;
import java.io.File;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static com.mongodb.client.model.Filters.eq;

public class VideoManager {
    public static final File uploadDir = new File("upload");
    public static final File staticDir = new File(uploadDir, "static");
    public static final File avatarDir = new File(staticDir, "avatars");
    private static final File videoDir = new File(staticDir, "videos");
    private static final File thumbnailDir = new File(staticDir, "thumbnails");

    public VideoManager() {
        uploadDir.mkdirs();
        staticDir.mkdirs();
        avatarDir.mkdirs();
        videoDir.mkdirs();
        thumbnailDir.mkdirs();
    }

    public static Bson videoProjection() {
        return Projections.fields(
                Projections.include("videoId", "title", "description", "videoUrl", "thumbnailUrl", "tags", "uploader"),
                Projections.excludeId());
    }

    public static VideoResponse generateResponse(Document doc) {
        String uploader = doc.getString("uploader");

        return new VideoResponse(doc.getString("videoId"),
                doc.getString("title"),
                doc.getString("description"),
                formatVideoToURL(doc.getString("videoUrl")),
                formatThumbnailToURL(doc.getString("thumbnailUrl")),
                doc.getList("tags", String.class).toArray(new String[0]),
                PistonVideoApplication.getUserManager().generatePublicResponse(uploader));
    }

    public String upload(Request request, Response response) throws Exception {
        String id = IDGenerator.generateSixCharLong();

        request.attribute("org.eclipse.jetty.multipartConfig", new MultipartConfigElement("/temp"));

        Part video = request.raw().getPart("video");
        if (video == null || !video.getSubmittedFileName().endsWith(".mp4")) {
            return new Gson().toJson(new SuccessResponse(false));
        }

        Part thumbnail = request.raw().getPart("thumbnail");
        if (thumbnail == null || !thumbnail.getSubmittedFileName().endsWith(".png")) {
            return new Gson().toJson(new SuccessResponse(false));
        }

        try (InputStream input = video.getInputStream()) {
            Files.copy(input, new File(videoDir, id + ".mp4").toPath(), StandardCopyOption.REPLACE_EXISTING);
        }

        try (InputStream input = thumbnail.getInputStream()) {
            Files.copy(input, new File(thumbnailDir, id + ".png").toPath(), StandardCopyOption.REPLACE_EXISTING);
        }

        try (MongoClient client = DBManager.getMongoClient()) {
            MongoDatabase database = client.getDatabase("pistonvideo");
            MongoCollection<Document> collection = database.getCollection("videos");

            try {
                InsertOneResult result = collection.insertOne(new Document()
                        .append("_id", new ObjectId())
                        .append("videoId", id)
                        .append("title", request.queryParams("title"))
                        .append("description", request.queryParams("description"))
                        .append("videoUrl", id + ".mp4")
                        .append("thumbnailUrl", id + ".png")
                        .append("tags", List.of())
                        .append("uploader", PistonVideoApplication.getUserManager().getUserIdFromToken(request).get()));
                System.out.println("Success! Inserted document id: " + result.getInsertedId());
            } catch (MongoException me) {
                System.err.println("Unable to insert due to an error: " + me);
                return new Gson().toJson(new SuccessResponse(false));
            }
        }

        return new Gson().toJson(new SuccessIDResponse(true, id));
    }

    public Object watch(Request request, Response response) {
        String videoId = request.queryParams("id");
        if (videoId == null) {
            return new InvalidQueryError("id");
        }

        String userId = PistonVideoApplication.getUserManager().getUserIdFromToken(request).orElse(null);
        if (userId == null) {
            return new NotAuthenticatedError();
        }

        // Check if the video even exists
        Optional<VideoResponse> optional = getVideoData(videoId);
        if (optional.isEmpty()) {
            return new VideoNotFoundError();
        }

        try (MongoClient client = DBManager.getMongoClient()) {
            MongoDatabase database = client.getDatabase("pistonvideo");
            MongoCollection<Document> collection = database.getCollection("views");

            Bson projectionFields = Projections.fields(
                    Projections.include("videoId", "userId", "timestamp"),
                    Projections.excludeId());

            List<View> list = new ArrayList<>();
            for (Document document : collection.find(eq("videoId", videoId)).projection(projectionFields)) {
                if (document.getString("userId").equals(userId)) {
                    Long timestamp = document.getLong("timestamp");
                    if (timestamp != null) {
                        list.add(new View(userId, videoId, timestamp));
                    }
                }
            }

            Instant threeHoursAgo = Instant.now().minus(3, ChronoUnit.HOURS);
            Instant oneWeekAgo = Instant.now().minus(7, ChronoUnit.DAYS);
            int threeHoursCount = 0;
            int oneWeekCount = 0;
            for (View view : list) {
                Instant instant = Instant.ofEpochMilli(view.timestamp());

                if (instant.isAfter(threeHoursAgo)) {
                    threeHoursCount++;
                }

                if (instant.isAfter(oneWeekAgo)) {
                    oneWeekCount++;
                }
            }

            if (threeHoursCount >= 2) {
                return new RateLimitError();
            } else if (oneWeekCount >= 20) {
                return new RateLimitError();
            }

            try {
                collection.insertOne(new Document()
                        .append("_id", new ObjectId())
                        .append("videoId", videoId)
                        .append("userId", userId)
                        .append("timestamp", Instant.now().toEpochMilli()));

                return new SuccessResponse(true);
            } catch (MongoException me) {
                System.err.println("Unable to insert due to an error: " + me);
                return new SuccessResponse(false);
            }
        }
    }

    public Object videoData(Request request, Response response) {
        String videoId = request.queryParams("id");

        if (videoId == null)
            return new InvalidQueryError("id missing");

        return getVideoData(videoId).orElse(PistonVideoApplication.DELETED_VIDEO);
    }

    public VideoResponse privateVideoData(Request request, Response response) { // TODO
        return getVideoData(request.queryParams("id")).orElseThrow();
    }

    private Optional<VideoResponse> getVideoData(String videoId) {
        try (MongoClient client = DBManager.getMongoClient()) {
            MongoDatabase database = client.getDatabase("pistonvideo");
            MongoCollection<Document> collection = database.getCollection("videos");

            Bson projectionFields = videoProjection();

            Document doc = collection.find(eq("videoId", videoId))
                    .projection(projectionFields)
                    .first();

            if (doc == null)
                return Optional.empty();

            return Optional.of(generateResponse(doc));
        }
    }

    public static String formatVideoToURL(String video) {
        return "/backend/static/videos/" + video;
    }

    public static String formatThumbnailToURL(String image) {
        return "/backend/static/thumbnails/" + image;
    }

    public static String formatAvatarToURL(String avatar) {
        return "/backend/static/avatars/" + avatar;
    }

    private record View(String userId, String videoId, Long timestamp) {
    }
}
