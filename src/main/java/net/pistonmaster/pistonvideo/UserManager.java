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
import com.mongodb.client.result.InsertOneResult;
import com.mongodb.client.result.UpdateResult;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import net.pistonmaster.pistonvideo.templates.PublicUserResponse;
import net.pistonmaster.pistonvideo.templates.VideoResponse;
import net.pistonmaster.pistonvideo.templates.auth.SignupRequest;
import net.pistonmaster.pistonvideo.templates.auth.UpdateRequest;
import net.pistonmaster.pistonvideo.templates.simple.SuccessErrorResponse;
import net.pistonmaster.pistonvideo.templates.simple.SuccessResponse;
import org.bson.Document;
import org.bson.conversions.Bson;
import org.bson.types.ObjectId;
import spark.Request;
import spark.Response;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public class UserManager {
    public Optional<String> generateToken(String email, String password) {
        if (isValid(email, password)) {
            String token = UUID.randomUUID().toString();

            try (MongoClient client = DBManager.getMongoClient()) {
                MongoDatabase database = client.getDatabase("pistonvideo");
                MongoCollection<Document> collection = database.getCollection("tokens");

                collection.insertOne(new Document()
                        .append("_id", new ObjectId())
                        .append("token", token)
                        .append("userid", getUserIdFromEmail(email)));

                return Optional.of(token);
            }
        } else {
            return Optional.empty();
        }
    }

    public void invalidate(String token) {
        try (MongoClient client = DBManager.getMongoClient()) {
            MongoDatabase database = client.getDatabase("pistonvideo");
            MongoCollection<Document> collection = database.getCollection("tokens");

            // TODO
        }
    }

    private String getUserIdFromEmail(String email) {
        try (MongoClient client = DBManager.getMongoClient()) {
            MongoDatabase database = client.getDatabase("pistonvideo");
            MongoCollection<Document> collection = database.getCollection("users");

            Bson projectionFields = Projections.fields(
                    Projections.include("email", "userid"),
                    Projections.excludeId());

            Document doc = collection.find(Filters.eq("email", email))
                    .projection(projectionFields)
                    .first();

            if (doc == null)
                return null;

            return doc.getString("userid");
        }
    }

    public Optional<String> getUserIdFromToken(String token) {
        try (MongoClient client = DBManager.getMongoClient()) {
            MongoDatabase database = client.getDatabase("pistonvideo");
            MongoCollection<Document> collection = database.getCollection("tokens");

            Bson projectionFields = Projections.fields(
                    Projections.include("userid", "token"),
                    Projections.excludeId());

            Document doc = collection.find(Filters.eq("token", token))
                    .projection(projectionFields)
                    .first();

            if (doc == null)
                return Optional.empty();

            return Optional.of(doc.getString("userid"));
        }
    }

    private boolean isValid(String email, String password) {
        try (MongoClient client = DBManager.getMongoClient()) {
            MongoDatabase database = client.getDatabase("pistonvideo");
            MongoCollection<Document> collection = database.getCollection("users");

            Bson projectionFields = Projections.fields(
                    Projections.include("email", "password"),
                    Projections.excludeId());

            Document doc = collection.find(Filters.eq("email", email))
                    .filter(Filters.eq("password", password))
                    .projection(projectionFields)
                    .first();

            return doc != null;
        }
    }

    private boolean isValidPasswordForId(String userId, String password) {
        try (MongoClient client = DBManager.getMongoClient()) {
            MongoDatabase database = client.getDatabase("pistonvideo");
            MongoCollection<Document> collection = database.getCollection("users");

            Bson projectionFields = Projections.fields(
                    Projections.include("userId", "password"),
                    Projections.excludeId());

            Document doc = collection.find(Filters.eq("userId", userId))
                    .filter(Filters.eq("password", password))
                    .projection(projectionFields)
                    .first();

            return doc != null;
        }
    }

    public RejectReason createUser(String username, String email, String password) {
        try (MongoClient client = DBManager.getMongoClient()) {
            MongoDatabase database = client.getDatabase("pistonvideo");
            MongoCollection<Document> collection = database.getCollection("users");

            Bson projectionFields = Projections.fields(
                    Projections.include("username", "email"),
                    Projections.excludeId());

            Document doc = collection.find(Filters.eq("username", username))
                    .projection(projectionFields)
                    .first();

            System.out.println(doc);
            if (doc == null) {
                Document doc2 = collection.find(Filters.eq("email", email))
                        .projection(projectionFields)
                        .first();

                if (doc2 == null) {
                    try {
                        InsertOneResult result = collection.insertOne(new Document()
                                .append("_id", new ObjectId())
                                .append("username", username)
                                .append("email", email)
                                .append("password", password)
                                .append("userid", IDGenerator.generateSixCharLong())
                                .append("avatarUrl", "/static/avatars/blank.png"));
                        System.out.println("Success! Inserted document id: " + result.getInsertedId());
                        return RejectReason.NONE;
                    } catch (MongoException me) {
                        System.err.println("Unable to insert due to an error: " + me);
                        return RejectReason.UNKNOWN;
                    }
                } else {
                    return RejectReason.ALREADY_EXISTS_EMAIL;
                }
            } else {
                return RejectReason.ALREADY_EXISTS_USERNAME;
            }
        }
    }

    @RequiredArgsConstructor
    public enum RejectReason {
        NONE,
        INVALID_EMAIL("Invalid email!"),
        INVALID_PASSWORD("Invalid password!"),
        INVALID_USERNAME("Invalid username!"),
        ALREADY_EXISTS_USERNAME("A user with this name already exists!"),
        ALREADY_EXISTS_EMAIL("A user with this email already exists! Is this you? If yes reset your password."),
        UNKNOWN("A unknown error has happened!");

        @Getter
        private final String errorMessage;

        RejectReason() {
            errorMessage = "";
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

    public String register(Request request, Response response) {
        SignupRequest signupRequest = new Gson().fromJson(request.body(), SignupRequest.class);

        UserManager.RejectReason reason = createUser(signupRequest.getUsername(), signupRequest.getEmail(), signupRequest.getPassword());
        if (reason == UserManager.RejectReason.NONE) {
            return new Gson().toJson(new SuccessResponse(true));
        } else {
            return new Gson().toJson(new SuccessErrorResponse(false, reason.getErrorMessage()));
        }
    }

    public String update(Request request, Response response) {
        String token = request.headers("Authorization");

        if (token == null)
            return new Gson().toJson(new SuccessResponse(false));

        Optional<String> userId = getUserIdFromToken(token);
        if (userId.isEmpty())
            return new Gson().toJson(new SuccessResponse(false));

        UpdateRequest updateRequest = new Gson().fromJson(request.body(), UpdateRequest.class);

        try (MongoClient client = DBManager.getMongoClient()) {
            MongoDatabase database = client.getDatabase("pistonvideo");
            MongoCollection<Document> collection = database.getCollection("users");

            Document query = new Document().append("userId", userId.get());

            List<Bson> updatesList = new ArrayList<>();

            if (updateRequest.getUsername() != null)
                updatesList.add(Updates.set("username", updateRequest.getUsername()));

            if (updateRequest.getEmail() != null)
                updatesList.add(Updates.set("email", updateRequest.getEmail()));

            if (updateRequest.getBioSmall() != null)
                updatesList.add(Updates.set("bioSmall", updateRequest.getBioSmall()));

            if (updateRequest.getBioBig() != null)
                updatesList.add(Updates.set("bioBig", updateRequest.getBioBig()));

            if (updateRequest.getOldPassword() != null && updateRequest.getNewPassword() != null && isValidPasswordForId(userId.get(), updateRequest.getOldPassword()))
                updatesList.add(Updates.set("password", updateRequest.getNewPassword()));

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
