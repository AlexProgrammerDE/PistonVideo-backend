package net.pistonmaster.pistonvideo;

import com.google.gson.Gson;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import com.mongodb.client.model.Projections;
import net.pistonmaster.pistonvideo.templates.VideoResponse;
import org.bson.Document;
import org.bson.conversions.Bson;
import spark.Request;
import spark.Response;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import static net.pistonmaster.pistonvideo.VideoManager.formatThumbnailToURL;
import static net.pistonmaster.pistonvideo.VideoManager.formatVideoToURL;

public class Suggester {
    public Object suggestions(Request request, Response response) {
        String param = request.queryParams("amount");
        int amount = param == null ? 20 : Integer.parseInt(param);
        List<VideoResponse> videos = new ArrayList<>();

        try (MongoClient client = DBManager.getMongoClient()) {
            MongoDatabase database = client.getDatabase("pistonvideo");
            MongoCollection<Document> collection = database.getCollection("videos");

            Bson projectionFields = Projections.fields(
                    Projections.include("videoId", "title", "description", "videoUrl", "thumbnailUrl", "tags", "uploader"),
                    Projections.excludeId());

            for (Document doc : collection.find().projection(projectionFields).limit(amount)) {
                videos.add(VideoManager.generateResponse(doc));
            }

            Random random = new Random();
            while (!videos.isEmpty() && videos.size() < amount) {
                videos.add(videos.get(random.nextInt(videos.size())));
            }

            return videos;
        }
    }
}
