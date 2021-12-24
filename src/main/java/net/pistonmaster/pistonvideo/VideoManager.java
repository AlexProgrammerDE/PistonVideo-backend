package net.pistonmaster.pistonvideo;

import com.google.gson.Gson;
import com.mongodb.MongoException;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.Projections;
import com.mongodb.client.result.InsertOneResult;
import net.pistonmaster.pistonvideo.templates.VideoResponse;
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
import java.util.List;

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
                doc.getString("videoUrl"),
                doc.getString("thumbnailUrl"),
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
                        .append("videoUrl", "/static/videos/" + id + ".mp4")
                        .append("thumbnailUrl", "/static/thumbnails/" + id + ".png")
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

    public String videoData(Request request, Response response) throws Exception {
        return getVideoData(request.queryParams("id"));
    }

    public String privateVideoData(Request request, Response response) throws Exception { // TODO
        return getVideoData(request.queryParams("id"));
    }

    private String getVideoData(String videoId) {
        try (MongoClient client = DBManager.getMongoClient()) {
            MongoDatabase database = client.getDatabase("pistonvideo");
            MongoCollection<Document> collection = database.getCollection("videos");

            Bson projectionFields = videoProjection();

            Document doc = collection.find(Filters.eq("videoId", videoId))
                    .projection(projectionFields)
                    .first();

            if (doc == null)
                return new Gson().toJson(PistonVideoApplication.DELETED_VIDEO);

            return new Gson().toJson(generateResponse(doc));
        }
    }
}
