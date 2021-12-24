package net.pistonmaster.pistonvideo;

import com.google.gson.Gson;
import com.mongodb.MongoException;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.Projections;
import com.mongodb.client.model.UpdateOptions;
import com.mongodb.client.model.Updates;
import com.mongodb.client.result.UpdateResult;
import net.pistonmaster.pistonvideo.templates.PublicUserResponse;
import net.pistonmaster.pistonvideo.templates.VideoResponse;
import net.pistonmaster.pistonvideo.templates.auth.DataUpdateRequest;
import net.pistonmaster.pistonvideo.templates.auth.WhoisResponse;
import net.pistonmaster.pistonvideo.templates.simple.SuccessIDResponse;
import net.pistonmaster.pistonvideo.templates.simple.SuccessResponse;
import okhttp3.OkHttpClient;
import okhttp3.ResponseBody;
import org.bson.Document;
import org.bson.conversions.Bson;
import spark.Request;
import spark.Response;

import javax.servlet.MultipartConfigElement;
import javax.servlet.http.Part;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class UserManager {
    public Optional<String> getUserIdFromToken(Request request) {
        return getUserIdFromToken(request.cookie("ory_kratos_session"));
    }

    public Optional<String> getUserIdFromToken(String token) {
        return getWhoisFromToken(token).map(WhoisResponse::getId);
    }

    private Optional<WhoisResponse> getWhoisFromToken(String token) {
        if (token == null)
            return Optional.empty();

        try {
            OkHttpClient client = new OkHttpClient().newBuilder().addInterceptor(chain -> {
                final okhttp3.Request original = chain.request();
                final okhttp3.Request authorized = original.newBuilder()
                        .addHeader("Cookie", "ory_kratos_session=" + token)
                        .build();
                return chain.proceed(authorized);
            }).build();

            okhttp3.Request request = new okhttp3.Request.Builder()
                    .url("https://pistonvideo.com/api/.ory/sessions/whoami")
                    .build(); // defaults to GET

            okhttp3.Response response = client.newCall(request).execute();

            ResponseBody body = response.body();

            if (body == null || response.code() == 401) {
                return Optional.empty();
            } else {
                WhoisResponse whoisResponse = new Gson().fromJson(body.string(), WhoisResponse.class);

                return Optional.of(whoisResponse);
            }
        } catch (IOException e) {
            return Optional.empty();
        }
    }

    public PublicUserResponse generatePublicResponse(String userid) {
        try (MongoClient client = DBManager.getMongoClient()) {
            MongoDatabase database = client.getDatabase("pistonvideo");
            MongoCollection<Document> collection = database.getCollection("users");

            Bson projectionFields = Projections.fields(
                    Projections.include("userid", "username", "avatarUrl", "bioSmall"),
                    Projections.excludeId());

            Document doc = collection.find(Filters.eq("userid", userid))
                    .projection(projectionFields)
                    .first();

            if (doc == null)
                return PistonVideoApplication.DELETED_USER;

            return new PublicUserResponse(doc.getString("username"), userid, doc.getString("avatarUrl"), doc.getString("bioSmall"));
        }
    }

    public VideoResponse[] generatePublicVideosResponse(String userid) {
        try (MongoClient client = DBManager.getMongoClient()) {
            MongoDatabase database = client.getDatabase("pistonvideo");
            MongoCollection<Document> collection = database.getCollection("videos");

            Bson projectionFields = VideoManager.videoProjection();

            List<Document> list = new ArrayList<>();
            collection.find(Filters.eq("uploader", userid))
                    .projection(projectionFields)
                    .limit(20).forEach(list::add);

            if (list.isEmpty())
                return new VideoResponse[]{};

            List<VideoResponse> videoResponses = new ArrayList<>();
            for (Document doc : list) {
                videoResponses.add(VideoManager.generateResponse(doc));
            }

            return videoResponses.toArray(new VideoResponse[0]);
        }
    }

    public String updateData(Request request, Response response) {
        Optional<String> userId = getUserIdFromToken(request);
        if (userId.isEmpty())
            return new Gson().toJson(new SuccessResponse(false));

        request.attribute("org.eclipse.jetty.multipartConfig", new MultipartConfigElement("/temp"));

        DataUpdateRequest dataUpdateRequest = new DataUpdateRequest(request.queryParams("bioSmall"), request.queryParams("bioBig"));

        String avatarUrl = null;

        try {
            Part avatarFile = request.raw().getPart("avatar");

            if (avatarFile != null && avatarFile.getSubmittedFileName().endsWith(".png")) {
                String avatarId = IDGenerator.generateSixCharLong();

                try (InputStream input = avatarFile.getInputStream()) {
                    Files.copy(input, new File(VideoManager.avatarDir, avatarId + ".png").toPath(), StandardCopyOption.REPLACE_EXISTING);
                }

                avatarUrl = "/static/avatars/" + avatarId + ".png";
            }
        } catch (Exception ignored) {
        }

        try (MongoClient client = DBManager.getMongoClient()) {
            MongoDatabase database = client.getDatabase("pistonvideo");
            MongoCollection<Document> collection = database.getCollection("users");

            Document query = new Document().append("userid", userId.get());

            List<Bson> updatesList = new ArrayList<>();

            if (dataUpdateRequest.bioSmall() != null)
                updatesList.add(Updates.set("bioSmall", dataUpdateRequest.bioSmall()));

            if (dataUpdateRequest.bioBig() != null)
                updatesList.add(Updates.set("bioBig", dataUpdateRequest.bioBig()));

            if (avatarUrl != null)
                updatesList.add(Updates.set("avatarUrl", avatarUrl));

            if (!updatesList.isEmpty())
                updatesList.add(Updates.currentTimestamp("lastUpdated"));

            Bson updates = Updates.combine(updatesList);

            UpdateOptions options = new UpdateOptions().upsert(false);
            try {
                UpdateResult result = collection.updateOne(query, updates, options);
                System.out.println("Modified document count: " + result.getModifiedCount());
            } catch (MongoException me) {
                System.err.println("Unable to update due to an error: " + me);
            }
        }

        return new Gson().toJson(new SuccessResponse(true));
    }
}
