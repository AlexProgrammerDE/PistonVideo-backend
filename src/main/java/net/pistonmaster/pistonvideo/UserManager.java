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
import net.pistonmaster.pistonvideo.templates.kratos.IdentityResponse;
import net.pistonmaster.pistonvideo.templates.kratos.WhoisResponse;
import net.pistonmaster.pistonvideo.templates.simple.SuccessIDResponse;
import net.pistonmaster.pistonvideo.templates.simple.SuccessResponse;
import net.pistonmaster.pistonvideo.templates.user.DataUpdateRequest;
import org.bson.Document;
import org.bson.conversions.Bson;
import spark.Request;
import spark.Response;

import javax.servlet.MultipartConfigElement;
import javax.servlet.http.Part;
import java.io.File;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static net.pistonmaster.pistonvideo.PistonVideoApplication.DEFAULT_USER;

public class UserManager {
    public Optional<String> getUserIdFromToken(Request request) {
        return getUserIdFromToken(request.cookie("ory_kratos_session"));
    }

    public Optional<String> getUserIdFromToken(String token) {
        return OryManager.getWhoisFromToken(token).map(WhoisResponse::getIdentity).map(IdentityResponse::getId);
    }

    public PublicUserResponse generatePublicResponse(String userid) {
        Optional<IdentityResponse> optional = OryManager.getIdentity(userid);

        if (optional.isPresent()) {
            IdentityResponse identityResponse = optional.get();

            try (MongoClient client = DBManager.getMongoClient()) {
                MongoDatabase database = client.getDatabase("pistonvideo");
                MongoCollection<Document> collection = database.getCollection("users");

                Bson projectionFields = Projections.fields(
                        Projections.include("userId", "avatarUrl", "bioSmall", "bioBig", "badges"),
                        Projections.excludeId());

                Document doc = collection.find(Filters.eq("userId", userid))
                        .projection(projectionFields)
                        .first();

                System.out.println(doc);

                if (doc == null) {
                    return new PublicUserResponse(identityResponse.getTraits().getUsername(), userid, DEFAULT_USER.avatarUrl(), DEFAULT_USER.bioSmall(), DEFAULT_USER.bioBig(), DEFAULT_USER.badges());
                } else {
                    String avatarUrl = Optional.ofNullable(doc.getString("avatarUrl")).map(VideoManager::formatAvatarToURL).orElse(DEFAULT_USER.avatarUrl());
                    String bioSmall = Optional.ofNullable(doc.getString("bioSmall")).orElse(DEFAULT_USER.bioSmall());
                    String bioBig = Optional.ofNullable(doc.getString("bioBig")).orElse(DEFAULT_USER.bioBig());
                    List<String> badges = Optional.ofNullable(doc.getList("badges", String.class)).orElse(DEFAULT_USER.badges());

                    System.out.println(badges);

                    return new PublicUserResponse(identityResponse.getTraits().getUsername(), userid, avatarUrl, bioSmall, bioBig, badges);
                }
            }
        } else {
            return PistonVideoApplication.DELETED_USER;
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

                avatarUrl = avatarId + ".png";
            }
        } catch (Exception ignored) {
        }

        try (MongoClient client = DBManager.getMongoClient()) {
            MongoDatabase database = client.getDatabase("pistonvideo");
            MongoCollection<Document> collection = database.getCollection("users");

            Document query = new Document().append("userId", userId.get());

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

            UpdateOptions options = new UpdateOptions().upsert(true);
            try {
                UpdateResult result = collection.updateOne(query, updates, options);
                System.out.println("Modified document count: " + result.getModifiedCount());
            } catch (MongoException me) {
                System.err.println("Unable to update due to an error: " + me);
            }
        }

        return new Gson().toJson(new SuccessIDResponse(true, userId.get()));
    }
}
