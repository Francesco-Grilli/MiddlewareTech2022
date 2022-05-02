package it.polimi.mwtech2022.project1;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import it.polimi.mwtech2022.project1.utils.LogUtils;
import org.apache.spark.api.java.function.FlatMapFunction;
import org.apache.spark.api.java.function.MapFunction;
import org.apache.spark.sql.*;
import org.apache.spark.sql.streaming.*;
import scala.Tuple2;
import scala.Tuple3;
import scala.Tuple4;
import scala.Tuple5;

import java.sql.Timestamp;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.TimeoutException;

import static org.apache.spark.sql.functions.*;

public class DataAnalysis {

    // TODO: remove hardcoded elements; introduce dependence from configuration file

    public static void main(String[] args) throws TimeoutException {

        LogUtils.setLogLevel();

        final String master = args.length > 0 ? args[0] : "local[8]";
        final String socketHost = args.length > 1 ? args[1] : "localhost";
        final int socketPort = args.length > 2 ? Integer.parseInt(args[2]) : 9999;
        final String filePath = args.length > 3 ? args[3] : "./";
        final int threshold = args.length > 4 ? Integer.parseInt(args[4]) : 75;

        final SparkSession spark = SparkSession
                .builder()
                .master(master)
                .appName("DataAnalysis")
                .getOrCreate();

        spark.conf().set("spark.sql.shuffle.partitions", 8);

//        The backend periodically computes the following metrics:
//        1. hourly, daily, and weekly moving average of noise level, for each point of interest;
//        2. top 10 points of interest with the highest level of noise over the last hour;
//        3. point of interest with the longest streak of good noise level;
//            ○ given a maximum threshold T, a streak is the time span (in seconds) since a point of interest has last
//              measured a value higher than T;
//            ○ by definition, a point of interest with a value above T has a streak of 0;
//            ○ at any given point in time, the query should compute the point (or points) of interest with the
//              longest stream.
        // IMPORTANT!! : in streaming queries always use distinct checkpoint locations
        //               Error on deployment is thrown otherwise


        // FIRST QUERY: compute average from incoming data (grouped by POI) over 5-minute tumbling windows

        // expected input (sample)
        // {"noise":[42.662951293850554],"timestamp":1648141178047,"POIRegion":"Lazio","POIName":"Terme di Caracalla"}
        // first input to the system; receives all measurements
        // used for two queries, 5MinuteAverage and Threshold
        // may want to define splitting function aside (out of the map operator)
        // TODO needs check for cleaning purposes!!
        final Dataset<Row> input = spark
                .readStream()
                .format("kafka")
                .option("kafka.bootstrap.servers", "localhost:9092")
                .option("subscribe", "nodered-try")
                .load()
                //explicit deserialization to String
                //remove this line to print on screen also partitions, offsets, ...
                //as of 27/04/22 key is not necessary as it arrives empty
                .selectExpr("CAST(value AS STRING)");

        // may want to define splitting function aside (out of the map operator)
        // TODO needs check for cleaning purposes!!
        final Dataset<Row> poiNoise = input
                .as(Encoders.STRING())
                .flatMap((FlatMapFunction<String, Tuple5<String, String, Timestamp, Long, Double>>) x -> {
                    Gson gson = new Gson();
                    JsonObject jsonRow = gson.fromJson(x, JsonObject.class);
                    List<Tuple5<String, String, Timestamp, Long, Double>> rows = new LinkedList<>();
                    try {
                        String region = jsonRow.get("POIRegion").getAsString();
                        String name = jsonRow.get("POIName").getAsString();
                        Long tsLong = jsonRow.get("timestamp").getAsLong();
                        Timestamp ts = new Timestamp(tsLong);
                        JsonArray noises = jsonRow.get("noise").getAsJsonArray();
                        for (int i = 0; i < noises.size(); i++)
                            rows.add(new Tuple5<>(name, region, ts, tsLong, noises.get(i).getAsDouble()));
                    } catch (Exception e) {
                        System.out.println("Exception thrown by FlatMap function"); // may be useful for debugging
                        e.printStackTrace();
                    }
                    return rows.iterator();
                }, Encoders.tuple(Encoders.STRING(), Encoders.STRING(), Encoders.TIMESTAMP(), Encoders.LONG(), Encoders.DOUBLE()))
                .toDF("POIName", "POIRegion", "Timestamp", "TSLong", "Noise");

        // TODO: check flatmap function for exceptions
        final Dataset<Row> fiveMinuteAvg = poiNoise
                .withWatermark("timestamp", "2 minute")
                .groupBy(window(col("timestamp"), "5 minutes", "5 minute"),
                        col("POIName")) // assuming unique POINames
                .avg("Noise")
                .select(col("POIName"), col("window.start"), col("avg(Noise)"))
                .map((MapFunction<Row, Tuple2<String, String>>) x->{
                    JsonObject outObj = new JsonObject();
                    outObj.addProperty("timestamp", ((Timestamp)(x.get(1))).getTime());
                    outObj.addProperty("avgNoise", x.get(2).toString());
                    Gson gson = new Gson();
                    return new Tuple2<>((String) x.get(0), gson.toJson(outObj));
                }, Encoders.tuple(Encoders.STRING(), Encoders.STRING()))
                .toDF("key", "value");

        final StreamingQuery fiveMinQuery = fiveMinuteAvg
                .writeStream()
                .format("kafka")
                .outputMode("append")
                .option("checkpointLocation", "/mnt/c/tmp/new0")
                .option("kafka.bootstrap.servers", "localhost:9092")
                .option("topic", "nodered-try-ter")
                .trigger(Trigger.ProcessingTime("30 seconds")) //periodic query: query computation every /*processing time*/
                .start();

        // SECOND QUERY: select from input values the ones that overcome the threshold

        // query to find all measurements within watermark that went over the threshold
        // TODO: handle log compaction on kafka topic
        // TODO: double-check corner cases and actual correctness
        final StreamingQuery thresholdQuery = poiNoise
                .withWatermark("Timestamp", "2 minute")
                .filter(col("Noise").geq(threshold))
                .select(col("POIName"), col("TSLong"), col("Noise"))
                .map((MapFunction<Row, Tuple2<String, String>>) x -> {
                    Gson gson = new Gson();
                    JsonObject outputObj = new JsonObject();
                    outputObj.addProperty("timestamp", x.get(1).toString());
                    outputObj.addProperty("noise", x.get(2).toString());
                    return new Tuple2<>(x.get(0).toString(), gson.toJson(outputObj));
                }, Encoders.tuple(Encoders.STRING(), Encoders.STRING()))
                .toDF("key", "value")
                .writeStream()
                .format("kafka")
                .outputMode("update")
                .option("checkpointLocation", "/mnt/c/tmp/new1")
                .option("kafka.bootstrap.servers", "localhost:9092")
                .option("topic", "threshold-try")
                .trigger(Trigger.ProcessingTime("30 seconds")) //periodic query: query computation every /*processing time*/
                .start();

        // THIRD QUERY: compute moving average for hours; sliding windows, aggregate five-minute measurements

        // input from topic of five-minute tumbling window averages
        final Dataset<Row> fiveMinInput = spark
                .readStream()
                .format("kafka")
                .option("kafka.bootstrap.servers", "localhost:9092")
                .option("subscribe", "nodered-try-ter")
                .load()
                .selectExpr("CAST(key AS STRING)","CAST(value AS STRING)")
                .map((MapFunction<Row, Tuple3<String, Timestamp, Double>>) x -> {
                    Gson gson = new Gson();
                    JsonObject inputObj = gson.fromJson(x.get(1).toString(), JsonObject.class);
                    String poi = x.get(0).toString();
                    long tsLong = inputObj.get("timestamp").getAsLong();
                    Timestamp ts = new Timestamp(tsLong);
                    double avgNoise = inputObj.get("avgNoise").getAsDouble();
                    return new Tuple3<>(poi, ts, avgNoise);
                }, Encoders.tuple(Encoders.STRING(), Encoders.TIMESTAMP(), Encoders.DOUBLE()))
                .toDF("POIName", "Timestamp", "AvgNoise");

        // testing OK
        // TODO: check watermark
        final Dataset<Row> hourlyAverages = fiveMinInput
                // actual aggregation part
                .withWatermark("timestamp", "7 minutes")
                .groupBy(window(col("Timestamp"), "1 hour", "5 minutes"),
                        col("POIName"))
                .avg("AvgNoise")
                .select(col("POIName"), col("window.start"), col("avg(AvgNoise)"))
                // kafka-oriented adjustment
                .map((MapFunction<Row, Tuple2<String, String>>) x -> {
                    Gson gson = new Gson();
                    JsonObject outObj = new JsonObject();
                    long ts = ((Timestamp) x.get(1)).getTime();
                    outObj.addProperty("timestamp", ts);
                    outObj.addProperty("avgNoise", x.get(2).toString());
                    return new Tuple2<>(x.get(0).toString(), gson.toJson(outObj));
                }, Encoders.tuple(Encoders.STRING(), Encoders.STRING()))
                .toDF("key", "value");
        ;

        final StreamingQuery hourQuery = hourlyAverages
                .writeStream()
                .format("kafka")
                .outputMode("append")
                .option("checkpointLocation", "/mnt/c/tmp/new2")
                .option("kafka.bootstrap.servers", "localhost:9092")
                .option("topic", "nodered-hour")
                .trigger(Trigger.ProcessingTime("2 minutes")) //periodic query: query computation every /*processing time*/
                .start();

//        // duplicate hour query to use in another context (top k query)
//        final StreamingQuery anotherHourQuery = hourlyAverages
//                .writeStream()
//                .format("kafka")
//                .outputMode("append")
//                .option("checkpointLocation", "/mnt/c/tmp/new3")
//                .option("kafka.bootstrap.servers", "localhost:9092")
//                .option("topic", "nodered-anotherHour")
//                .trigger(Trigger.ProcessingTime("2 minutes")) //periodic query: query computation every /*processing time*/
//                .start();

        // FOURTH QUERY: compute daily average using results from hour query

        final Dataset<Row> hourInput = spark
                .readStream()
                .format("kafka")
                .option("kafka.bootstrap.servers", "localhost:9092")
                .option("subscribe", "nodered-hour")
                .load()
                .selectExpr("CAST(key AS STRING)","CAST(value AS STRING)")
                .flatMap((FlatMapFunction<Row, Tuple4<String, Timestamp, Long, Double>>) x -> {
                    Gson gson = new Gson();
                    JsonObject inputObj = gson.fromJson(x.get(1).toString(), JsonObject.class);
                    List<Tuple4<String, Timestamp, Long, Double>> outList = new LinkedList<>();
                    long tsLong = inputObj.get("timestamp").getAsLong();
                    if (tsLong%(1000*60*60) == 0){
                        String poi = x.get(0).toString();
                        Timestamp ts = new Timestamp(tsLong);
                        double avgNoise = inputObj.get("avgNoise").getAsDouble();
                        outList.add(new Tuple4<>(poi, ts, tsLong, avgNoise));
                    }
                    return outList.iterator();
                }, Encoders.tuple(Encoders.STRING(), Encoders.TIMESTAMP(), Encoders.LONG(), Encoders.DOUBLE()))
                .toDF("POIName", "Timestamp", "TSLong", "AvgNoise");

        final StreamingQuery dayQuery = hourInput
                // actual query
                .withWatermark("Timestamp", "30 minutes")
                .groupBy(window(col("Timestamp"), "1 day", "1 hour"),
                        col("POIName"))
                .avg("avgNoise")
                .select(col("POIName"), col("window.start"), col("avg(avgNoise)"))
                // kafka-oriented adjustment
                .map((MapFunction<Row, Tuple2<String, String>>) x -> {
                    Gson gson = new Gson();
                    JsonObject outObj = new JsonObject();
                    long ts = ((Timestamp) x.get(1)).getTime();
                    outObj.addProperty("timestamp", ts);
                    outObj.addProperty("avgNoise", x.get(2).toString());
                    return new Tuple2<>(x.get(0).toString(), gson.toJson(outObj));
                }, Encoders.tuple(Encoders.STRING(), Encoders.STRING()))
                .toDF("key", "value")
                // output writing
                .writeStream()
                .format("kafka")
                .outputMode("append")
                .option("checkpointLocation", "/mnt/c/tmp/new4")
                .option("kafka.bootstrap.servers", "localhost:9092")
                .option("topic", "nodered-day")
                .trigger(Trigger.ProcessingTime("30 seconds")) //periodic query: query computation every /*processing time*/
                .start();

        // FIFTH QUERY: compute weekly moving average from daily averages

        final Dataset<Row> dayInput = spark
                .readStream()
                .format("kafka")
                .option("kafka.bootstrap.servers", "localhost:9092")
                .option("subscribe", "nodered-day")
                .load()
                .selectExpr("CAST(key AS STRING)","CAST(value AS STRING)")
                .flatMap((FlatMapFunction<Row, Tuple3<String, Timestamp, Double>>) x -> {
                    Gson gson = new Gson();
                    JsonObject inputObj = gson.fromJson(x.get(1).toString(), JsonObject.class);
                    List<Tuple3<String, Timestamp, Double>> outList = new LinkedList<>();
                    long tsLong = inputObj.get("timestamp").getAsLong();
                    if (tsLong%(1000*60*60*24) == 0){
                        String poi = x.get(0).toString();
                        Timestamp ts = new Timestamp(tsLong);
                        double avgNoise = inputObj.get("avgNoise").getAsDouble();
                        outList.add(new Tuple3<>(poi, ts, avgNoise));
                    }
                    return outList.iterator();
                }, Encoders.tuple(Encoders.STRING(), Encoders.TIMESTAMP(), Encoders.DOUBLE()))
                .toDF("POIName", "Timestamp", "AvgNoise");

        final StreamingQuery weekQuery = hourInput
                // actual query
                .withWatermark("Timestamp", "1 hour")
                .groupBy(window(col("Timestamp"), "7 day", "1 day"),
                        col("POIName"))
                .avg("avgNoise")
                .select(col("POIName"), col("window.start"), col("avg(avgNoise)"))
                // kafka-oriented adjustment
                .map((MapFunction<Row, Tuple2<String, String>>) x -> {
                    Gson gson = new Gson();
                    JsonObject outObj = new JsonObject();
                    long ts = ((Timestamp) x.get(1)).getTime();
                    outObj.addProperty("timestamp", ts);
                    outObj.addProperty("avgNoise", x.get(2).toString());
                    return new Tuple2<>(x.get(0).toString(), gson.toJson(outObj));
                }, Encoders.tuple(Encoders.STRING(), Encoders.STRING()))
                .toDF("key", "value")
                // output writing
                .writeStream()
                .format("kafka")
                .outputMode("append")
                .option("checkpointLocation", "/mnt/c/tmp/new5")
                .option("kafka.bootstrap.servers", "localhost:9092")
                .option("topic", "nodered-week")
                .trigger(Trigger.ProcessingTime("30 seconds")) //periodic query: query computation every /*processing time*/
                .start();

        try {
            //await termination of all the queries which have been formerly defined
            spark.streams().awaitAnyTermination();
        } catch (final StreamingQueryException e) {
            e.printStackTrace();
        }

        spark.close();
    }


}
