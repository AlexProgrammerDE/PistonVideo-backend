package net.pistonmaster.pistonvideo;

import com.mongodb.MongoException;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import com.mongodb.client.MongoDatabase;
import org.bson.BsonDocument;
import org.bson.BsonInt64;
import org.bson.Document;
import org.bson.conversions.Bson;

public class DBManager {
    private static String uri;

    public static MongoClient getMongoClient() {
        return MongoClients.create(uri);
    }

    public static void init(String username, String password, String host, int port) {
        uri = "mongodb://" + username + ":" + password + "@" + host + ":" + port + "/?maxPoolSize=20&w=majority";

        MongoDatabase database = getMongoClient().getDatabase("admin");
        try {
            Bson command = new BsonDocument("ping", new BsonInt64(1));
            Document commandResult = database.runCommand(command);
            System.out.println("Connected successfully to server.");
        } catch (MongoException me) {
            System.err.println("An error occurred while attempting to run a command: " + me);
        }
    }
}
