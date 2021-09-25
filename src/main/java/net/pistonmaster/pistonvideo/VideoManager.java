package net.pistonmaster.pistonvideo;

import com.google.gson.Gson;
import com.mongodb.MongoException;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.Projections;
import com.mongodb.client.result.InsertOneResult;
import net.pistonmaster.pistonvideo.templates.SuccessIDResponse;
import net.pistonmaster.pistonvideo.templates.SuccessResponse;
import net.pistonmaster.pistonvideo.templates.VideoResponse;
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
    public final File uploadDir = new File("upload");
    public final File staticDir = new File(uploadDir, "static");
    private final File videoDir = new File(staticDir, "videos");
    private final File thumbnailDir = new File(staticDir, "thumbnails");

    public VideoManager() {
        uploadDir.mkdirs();
        staticDir.mkdirs();
        videoDir.mkdirs();
        thumbnailDir.mkdirs();
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
                        .append("videoID", id)
                        .append("title", request.queryParams("title"))
                        .append("description", request.queryParams("description"))
                        .append("videoUrl", "/static/videos/" + id + ".mp4")
                        .append("thumbnailUrl", "/static/thumbnails/" + id + ".png")
                        .append("tags", List.of())
                        .append("uploader", PistonVideoApplication.getUserManager().getUserIdFromToken(request.headers("Authorization"))));
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

    private String getVideoData(String videoID) {
        try (MongoClient client = DBManager.getMongoClient()) {
            MongoDatabase database = client.getDatabase("pistonvideo");
            MongoCollection<Document> collection = database.getCollection("videos");

            Bson projectionFields = Projections.fields(
                    Projections.include("videoID", "title", "description", "videoUrl", "thumbnailUrl", "tags"),
                    Projections.excludeId());

            Document doc = collection.find(Filters.eq("videoID", videoID))
                    .projection(projectionFields)
                    .first();

            if (doc == null)
                return "{}";

            String uploader = doc.getString("uploader");

            return new Gson().toJson(new VideoResponse(videoID,
                    doc.getString("title"),
                    doc.getString("description"),
                    doc.getString("videoUrl"),
                    doc.getString("thumbnailUrl"),
                    doc.getList("tags", String.class).toArray(new String[0]),
                    PistonVideoApplication.getUserManager().generatePublicResponse(uploader)));
        }
    }
}
