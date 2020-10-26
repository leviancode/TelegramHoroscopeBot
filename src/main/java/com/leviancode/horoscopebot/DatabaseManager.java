package com.leviancode.horoscopebot;

import com.mongodb.MongoClient;
import com.mongodb.MongoClientURI;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.Updates;
import org.bson.Document;
import org.bson.conversions.Bson;
import org.telegram.telegrambots.meta.api.objects.User;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.TimeUnit;

public class DatabaseManager implements Runnable{
    private final MongoDatabase database;
    private final ArrayBlockingQueue<User> queue;

    public DatabaseManager(String mongoUri) {
        MongoClientURI uri = new MongoClientURI(mongoUri);
        queue = new ArrayBlockingQueue<>(20);

        MongoClient mongoClient = new MongoClient(uri);
        database = mongoClient.getDatabase("horoscopeBot");

        try {
            database.getCollection("users");
        } catch (IllegalArgumentException e) {
            database.createCollection("users");
        }
    }

    @Override
    public void run() {
        MongoCollection<Document> collection = database.getCollection("users");
        while (true){
            try {
                User user = queue.take();

                Bson filter = Filters.eq("user_id", user.getId());
                Bson update = Updates.inc("requestCount", 1);

                Document updateDoc = collection.findOneAndUpdate(filter, update);

                if (updateDoc == null){
                    Document document = new Document();
                    document.put("user_id", user.getId());
                    document.put("firstName", user.getFirstName());
                    document.put("lastName", user.getLastName());
                    document.put("username", user.getUserName());
                    document.put("requestCount", 1);

                    collection.insertOne(document);
                }
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
    }

    public void insertData(User user){
        try {
            queue.offer(user,3, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }
}
