package net.pistonmaster.pistonvideo;

import com.mongodb.MongoException;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.Projections;
import com.mongodb.client.result.InsertOneResult;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.bson.Document;
import org.bson.conversions.Bson;
import org.bson.types.ObjectId;

import java.util.Optional;
import java.util.UUID;

public class Authenticator {
    public boolean isValid(String token) {
        return true;
    }

    public Optional<String> generateToken(String email, String password) {
        if (isValid(email, password)) {
            return Optional.of(UUID.randomUUID().toString().replace("-", ""));
        } else {
            return Optional.empty();
        }
    }

    public void invalidate(String token) {

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
                                .append("userid", UUID.randomUUID().toString()));
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
}
