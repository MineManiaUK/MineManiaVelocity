package com.github.minemaniauk.velocity.minemaniavelocity;

import com.mongodb.client.*;

public class DatabaseConnection {

    private static MongoClient mongoClient;
    private static MongoDatabase mongoDatabase;

    public static void Connect(String connectionString, String dataBaseName){
        mongoClient = MongoClients.create(connectionString);
        mongoDatabase = mongoClient.getDatabase(dataBaseName);
    }

    public static MongoClient getMongoClient(){
        return mongoClient;
    }

    public static MongoDatabase getMongoDatabase(){
        return mongoDatabase;
    }


}
