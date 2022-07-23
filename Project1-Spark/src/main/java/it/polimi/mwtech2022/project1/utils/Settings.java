package it.polimi.mwtech2022.project1.utils;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import lombok.Getter;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;

public class Settings {

    @Getter
    private String master;
    @Getter
    private String kafkaServer;
    @Getter
    private String checkpointLocation;
    @Getter
    private String inputTopic;
    @Getter
    private String thresholdTopic;
    @Getter
    private String lastThresholdTopic;
    @Getter
    private String fiveMinTopic;
    @Getter
    private String hourTopic;
    @Getter
    private String dayTopic;
    @Getter
    private String weekTopic;
    @Getter
    private double threshold;
    @Getter
    private int shufflePartitions;

    public Settings(){
        try{
            InputStream input = Settings.class.getResourceAsStream("/settings.json");
            BufferedReader jsonReader = new BufferedReader(new InputStreamReader(input));
            //JsonParser makes the spark-submit launch errors (NoSuchMethodError)
            //Thus parsing methodology has been changed
            String jsonString = "";
            while (jsonReader.ready()){
                jsonString = jsonString + jsonReader.readLine();
            }
            Gson gson = new Gson();
            JsonObject object = gson.fromJson(jsonString, JsonObject.class);
            jsonReader.close();
            input.close();
            threshold = object.get("threshold").getAsDouble();
            master = object.get("spark-master").getAsString();
            kafkaServer = object.get("kafka-server").getAsString();
            checkpointLocation = object.get("checkpoint-location").getAsString();
            inputTopic = object.get("input-topic").getAsString();
            thresholdTopic = object.get("threshold-topic").getAsString();
            lastThresholdTopic = object.get("last-threshold-topic").getAsString();
            fiveMinTopic = object.get("5min-topic").getAsString();
            hourTopic = object.get("hour-topic").getAsString();
            dayTopic = object.get("day-topic").getAsString();
            weekTopic = object.get("week-topic").getAsString();
            shufflePartitions = object.get("shuffle.partitions").getAsInt();
        } catch (Exception e) {
            System.err.println("An accidental error occurred while importing settings");
            e.printStackTrace();
            System.exit(1);
        }

    }

}
