package it.polimi.mwtech2022.project1;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import it.polimi.mwtech2022.project1.utils.LogUtils;
import org.apache.spark.api.java.function.FlatMapFunction;
import org.apache.spark.api.java.function.MapFunction;
import org.apache.spark.internal.config.R;
import org.apache.spark.sql.*;
import org.apache.spark.sql.expressions.Window;
import org.apache.spark.sql.expressions.WindowSpec;
import org.apache.spark.sql.streaming.*;
import org.json4s.jackson.Json;
import scala.Tuple2;
import scala.Tuple4;
import scala.Tuple5;

import javax.tools.JavaFileObject;
import java.sql.Date;
import java.sql.Timestamp;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.TimeoutException;

import static org.apache.spark.sql.functions.*;

public class DataAnalysis {

    // TODO: check if there's a way not to let it eat the CPU out
    // Note: if run on distributed cluster it seems to consume less CPU
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

        // input from topic of five-minute tumbling window averages
        final Dataset<Row> fiveMinInput = spark
                .readStream()
                .format("kafka")
                .option("kafka.bootstrap.servers", "localhost:9092")
                .option("subscribe", "nodered-try-ter")
                .load()
                .selectExpr("CAST(key AS STRING)","CAST(value AS STRING)")
                .map((MapFunction<Row, Tuple4<String, Timestamp, Double, Integer>>) x -> {
                    Gson gson = new Gson();
                    JsonObject inputObj = gson.fromJson(x.get(1).toString(), JsonObject.class);
                    String poi = x.get(0).toString();
                    long tsLong = inputObj.get("timestamp").getAsLong();
                    Timestamp ts = new Timestamp(tsLong);
                    double avgNoise = inputObj.get("avgNoise").getAsDouble();
                    int count = inputObj.get("count").getAsInt();
                    return new Tuple4<>(x.get(0).toString(), ts, avgNoise, count);
                }, Encoders.tuple(Encoders.STRING(), Encoders.TIMESTAMP(), Encoders.DOUBLE(), Encoders.INT()))
                .toDF("POIName", "Timestamp", "AvgNoise", "Count");

//        The backend periodically computes the following metrics:
//        1. hourly, daily, and weekly moving average of noise level, for each point of interest;
//        2. top 10 points of interest with the highest level of noise over the last hour;
//        3. point of interest with the longest streak of good noise level;
//            ○ given a maximum threshold T, a streak is the time span (in seconds) since a point of interest has last
//              measured a value higher than T;
//            ○ by definition, a point of interest with a value above T has a streak of 0;
//            ○ at any given point in time, the query should compute the point (or points) of interest with the
//              longest stream.

        // TODO: check flatmap function for exceptions
        // TODO: as of 29/04/22 tasks for this job are 200 or 201, try and reduce them (check groupBy)
        final Dataset<Row> fiveMinuteAvg = poiNoise
                .withColumn("count", lit(1))
                .withWatermark("timestamp", "1 minute")
                .groupBy(window(col("timestamp"), "1 minutes", "1 minute"),
                        col("POIName")) // assuming unique POINames
                .agg(avg("Noise"), sum("count"))
                .select(col("POIName"), col("window.start"), col("avg(Noise)"), col("sum(count)"))
                .map((MapFunction<Row, Tuple2<String, String>>) x->{
                    JsonObject outObj = new JsonObject();
                    outObj.addProperty("timestamp", ((Timestamp)(x.get(1))).getTime());
                    outObj.addProperty("avgNoise", x.get(2).toString());
                    outObj.addProperty("count", x.get(3).toString());
                    Gson gson = new Gson();
                    return new Tuple2<>((String) x.get(0), gson.toJson(outObj));
                }, Encoders.tuple(Encoders.STRING(), Encoders.STRING()))
                .toDF("key", "value");

        // testing OK
        final Dataset<Row> hourlyAverages = fiveMinInput
                .withWatermark("timestamp", "1 minutes")
                .withColumn("mult", col("AvgNoise").multiply(col("Count")))
                .groupBy(window(col("Timestamp"), "2 minutes", "1 minutes"),
                        col("POIName"))
                .agg(sum("Count"), sum("mult"))
                .withColumn("average", col("sum(mult)").divide(col("sum(count)")))
                .select(col("POIName"), col("window.start"), col("average"), col("sum(count)"));

//        // TODO: needs fixing
//        final Dataset<Row> lastHour = hourlyAverages
////                .withColumn("w_start", col("window.start"))
//                .withColumn("w_end", col("window.end"))
////                .select(col("w_start"), col("w_end"), col("POIName"), col("max(Noise)"))
//                .select(col("w_end"))
//                .orderBy(desc("w_end"))
//                .distinct();
////                .limit(1);
//
//
//        // how to test this thing?? all the values I can get are in the last hour
//        // OK
////        final Dataset<Row> lastHourAverages = hourlyAverages
////                .filter(col("window").equals(lastHour.first().get(0)))
////                .filter(col("TSLong").geq(System.currentTimeMillis() - 60 * 60 * 1000)) //millis in an hour
////                .groupBy(col("POIName"))
////                .avg("Noise");
////                .select(col("POIName"), col("avg(Noise)"));
//
////        final Dataset<Row> lastHourTop10 = lastHourAverages
////                .orderBy(col("avg(noise-value)"))
////                .limit(10);
////
//        // testing OK
//        final Dataset<Row> dailyAverages = poiNoise
//                .groupBy(window(col("Timestamp"), "1 day", "1 hour"),
//                        col("POIName"))
//                .avg("Noise");
//
//
//        // testing OK
//        final Dataset<Row> weeklyAverages = poiNoise
//                .groupBy(window(col("Timestamp"), "1 week", "1 day"),
//                        col("POIName"))
//                .avg("Noise");
//
        // testing OK
//        final Dataset<Row> overThreshold = poiNoise
//                .withWatermark("timestamp", "5 minutes")
//                .filter(col("Noise").geq(threshold))
//                .groupBy("POIName")
//                .max("TSLong")
//                .orderBy(asc("max(TSLong)"))
//                .limit(1);

        // IMPORTANT!! : in streaming queries always use distinct checkpoint locations
        //               Error on deploying is thrown otherwise

        final StreamingQuery fiveMinuteQuery = fiveMinuteAvg
                .writeStream()
                .format("kafka")
                .outputMode("append")
                .option("checkpointLocation", "/mnt/c/tmp/new0")
                .option("kafka.bootstrap.servers", "localhost:9092")
                .option("topic", "nodered-try-ter")
                .trigger(Trigger.ProcessingTime("30 seconds")) //periodic query: query computation every /*processing time*/
                .start();

        // query to find all measurements within watermark that went over the threshold
        // TODO: handle log compaction on kafka topic
        final StreamingQuery thresholdQuery = poiNoise
                .withWatermark("Timestamp", "5 minutes")
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

        final StreamingQuery hourQuery = hourlyAverages
                .map((MapFunction<Row, Tuple2<String, String>>) x -> {
                    Gson gson = new Gson();
                    JsonObject outObj = new JsonObject();
                    long ts = ((Timestamp) x.get(1)).getTime();
                    outObj.addProperty("timestamp", ts);
                    outObj.addProperty("avgNoise", x.get(2).toString());
                    outObj.addProperty("count", x.get(3).toString());
                    return new Tuple2<>(x.get(0).toString(), gson.toJson(outObj));
                }, Encoders.tuple(Encoders.STRING(), Encoders.STRING()))
                .toDF("key", "value")
                .writeStream()
                .format("kafka")
                .outputMode("append")
                .option("checkpointLocation", "/mnt/c/tmp/new2")
                .option("kafka.bootstrap.servers", "localhost:9092")
                .option("topic", "nodered-hour")
                .trigger(Trigger.ProcessingTime("1 minutes")) //periodic query: query computation every /*processing time*/
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
