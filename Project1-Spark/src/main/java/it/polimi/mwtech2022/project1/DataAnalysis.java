package it.polimi.mwtech2022.project1;

import it.polimi.mwtech2022.project1.utils.LogUtils;
import it.polimi.mwtech2022.project1.utils.Settings;
import org.apache.spark.api.java.function.MapFunction;
import org.apache.spark.sql.*;
import org.apache.spark.sql.streaming.*;
import scala.Tuple2;
import scala.Tuple4;

import java.sql.Timestamp;
import java.util.concurrent.TimeoutException;

import static org.apache.spark.sql.functions.*;

public class DataAnalysis {

    //TODO: Serious testing on actual times (hours, days, weeks); should work for average is the same used for 5-min

    // TODO: watermark on result tables (but likely not only)
    // TODO: check watermarks, on window.start or window.end?
    // TODO: timestamp for kafka?
    // TODO: check semantics: can we guarantee EOS? (spark guarantees writing on kafka with ALOS)
    // TODO: Threshold-query on kafka --> timestamp, log compaction, a consumer that reads messages and updates a list
    // TODO: other solution for threshold-query on kafka: simple consumer that reads all messages on the threshold topic and updates a list
    // TODO: top-10 query: group by hour and extract most recent hour, filter input on that hour, sort desc, limit 10 (append)
    // TODO: adapt queries to keep also ending of the sliding window

    public static void main(String[] args) throws TimeoutException {

        LogUtils.setLogLevel();
        Settings settings = new Settings();

        final SparkSession spark = SparkSession
                .builder()
                .master(settings.getMaster())
                .appName("DataAnalysis")
                .getOrCreate();

        spark.conf().set("spark.sql.shuffle.partitions", settings.getShufflePartitions());

//        The backend periodically computes the following metrics:
//        1. hourly, daily, and weekly moving average of noise level, for each point of interest;
//        2. top 10 points of interest with the highest level of noise over the last hour;
//        3. point of interest with the longest streak of good noise level;
//            ○ given a maximum threshold T, a streak is the time span (in seconds) since a point of interest has last
//              measured a value higher than T;
//            ○ by definition, a point of interest with a value above T has a streak of 0;
//            ○ at any given point in time, the query should compute the point (or points) of interest with the
//              longest streak.
        // IMPORTANT!! : in streaming queries always use distinct checkpoint locations
        //               Error on deployment is thrown otherwise


        // FIRST QUERY: compute average from incoming data (grouped by POI) over 5-minute tumbling windows

        // expected input (sample)
        // PoiRegion (key), "noise,timestamp" (value) <-- all values on Kafka topics share this form
        // first input to the system; receives all measurements
        final Dataset<Row> input = spark
                .readStream()
                .format("kafka")
                .option("kafka.bootstrap.servers", settings.getKafkaServer())
                .option("subscribe", settings.getInputTopic())
                .load()
                //explicit deserialization to Strings
                //remove this line to print on screen also partitions, offsets, ...
                .selectExpr("CAST(key AS STRING)", "CAST(value AS STRING)");

        //MapFunction to convert input as "Key","Value,Timestamp" in Key - Value - Timestamp
        //Requires key as string and value as string from kafka topics
        MapFunction<Tuple2<String, String>, Tuple4<String, Double, Timestamp, Long>> inputToRow  = (x -> {
            String key = x._1();
            String[] splitValue = x._2().split(",");
            Double value = Double.parseDouble(splitValue[0]);
            long tsLong = Long.parseLong(splitValue[1]); //needed only from hour queries on
            Timestamp ts = new Timestamp(tsLong);
            return new Tuple4<>(key, value, ts, tsLong);
        });

        //MapFunction to convert row as Key - Value - Timestamp into output "Key","Value,Timestamp"
        //Produces key as string and value as string for kafka topics
        MapFunction<Row, Tuple2<String, String>> rowToOutput = (x -> {
            long ts = ((Timestamp) (x.get(2))).getTime();
            return new Tuple2<>(x.get(0).toString(), x.get(1).toString() + "," + ts);
        });

        // used for two queries, 5MinuteAverage and Threshold
        final Dataset<Row> poiNoise = input
                .as(Encoders.tuple(Encoders.STRING(), Encoders.STRING()))
                .map(inputToRow, Encoders.tuple(Encoders.STRING(), Encoders.DOUBLE(), Encoders.TIMESTAMP(), Encoders.LONG()))
                .toDF("POI", "Noise", "Timestamp", "TSLong");

        final Dataset<Row> fiveMinuteAvg = poiNoise
                .withWatermark("Timestamp", "2 seconds")
                //decibel to linear conversion: 10^(value/10)
                .withColumn("Noise-linear", pow(10, col("Noise").divide(10)))
                .groupBy(window(col("Timestamp"), "5 minutes", "5 minutes"),
                        col("POI")) // assuming unique POINames
                //average on linear values
                .avg("Noise-linear")
                //convert back linear to decibel values
                .withColumn("logAvg", log(10, col("avg(noise-linear)")).multiply(10))
                .select(col("POI"), col("logAvg"), col("window.start"))
                //produce output row; Kafka requires value to be either String or binary
                .map(rowToOutput, Encoders.tuple(Encoders.STRING(), Encoders.STRING()))
                .toDF("key", "value");

        final StreamingQuery fiveMinQuery = fiveMinuteAvg
                .writeStream()
                .format("kafka")
                .outputMode("append")
                .option("checkpointLocation", settings.getCheckpointLocation() + "/fiveMinQuery")
                .option("kafka.bootstrap.servers", settings.getKafkaServer())
                .option("topic", settings.getFiveMinTopic())
                .trigger(Trigger.ProcessingTime("10 seconds")) //periodic query: query computation every /*processing time*/
                .start();

        // SECOND QUERY: select from input values the ones that overcome the threshold

//        // query to find all measurements within watermark that went over the threshold
//        // TODO: handle log compaction on kafka topic
//        // TODO: double-check corner cases and actual correctness
//        final StreamingQuery thresholdQuery = poiNoise
//                .withWatermark("Timestamp", "2 minute")
//                .filter(col("Noise").geq(settings.getThreshold()))
//                .select(col("POIName"), col("TSLong"), col("Noise"))
//                .map((MapFunction<Row, Tuple2<String, String>>) x -> {
//                    Gson gson = new Gson();
//                    JsonObject outputObj = new JsonObject();
//                    outputObj.addProperty("timestamp", x.get(1).toString());
//                    outputObj.addProperty("noise", x.get(2).toString());
//                    return new Tuple2<>(x.get(0).toString(), gson.toJson(outputObj));
//                }, Encoders.tuple(Encoders.STRING(), Encoders.STRING()))
//                .toDF("key", "value")
//                .writeStream()
//                .format("kafka")
//                .outputMode("update")
//                .option("checkpointLocation", settings.getCheckpointLocation() + "/thresholdQuery")
//                .option("kafka.bootstrap.servers", settings.getKafkaServer())
//                .option("topic", settings.getThresholdTopic())
//                .trigger(Trigger.ProcessingTime("30 seconds")) //periodic query: query computation every /*processing time*/
//                .start();

        // THIRD QUERY: compute moving average for hours; sliding windows, aggregate five-minute measurements

        // input from topic of five-minute tumbling window averages
        final Dataset<Row> fiveMinInput = spark
                .readStream()
                .format("kafka")
                .option("kafka.bootstrap.servers", settings.getKafkaServer())
                .option("subscribe", settings.getFiveMinTopic())
                .load()
                .selectExpr("CAST(key AS STRING)", "CAST(value AS STRING)")
                .as(Encoders.tuple(Encoders.STRING(), Encoders.STRING()))
                .map(inputToRow, Encoders.tuple(Encoders.STRING(), Encoders.DOUBLE(), Encoders.TIMESTAMP(), Encoders.LONG()))
                .toDF("POI", "Noise", "Timestamp", "TSLong");

        // testing OK
        // TODO: check watermark
        final Dataset<Row> hourlyAverages = fiveMinInput
                // actual aggregation part
                .withWatermark("timestamp", "2 minutes")
                .withColumn("Noise-linear", pow(10, col("Noise").divide(10)))
                .groupBy(window(col("Timestamp"), "1 hour", "5 minutes"),
                        col("POI")) // assuming unique POINames
                .avg("Noise-linear")
                .withColumn("logAvg", log(10, col("avg(noise-linear)")).multiply(10))
                .select(col("POI"), col("logAvg"), col("window.start"))
                // kafka-oriented adjustment
                .map(rowToOutput, Encoders.tuple(Encoders.STRING(), Encoders.STRING()))
                .toDF("key", "value");

        final StreamingQuery hourQuery = hourlyAverages
                .writeStream()
                .format("kafka")
                .outputMode("append")
                .option("checkpointLocation", settings.getCheckpointLocation() + "/hourQuery")
                .option("kafka.bootstrap.servers", settings.getKafkaServer())
                .option("topic", settings.getHourTopic())
                .trigger(Trigger.ProcessingTime("2 minutes")) //periodic query: query computation every /*processing time*/
                .start();

//        // duplicate hour query to use in another context (top k query)
//        final StreamingQuery anotherHourQuery = hourlyAverages
//                .writeStream()
//                .format("kafka")
//                .outputMode("append")
//                .option("checkpointLocation", "/mnt/c/tmp/new3")
//                .option("kafka.bootstrap.servers", settings.getKafkaServer())
//                .option("topic", "nodered-anotherHour")
//                .trigger(Trigger.ProcessingTime("2 minutes")) //periodic query: query computation every /*processing time*/
//                .start();

        // FOURTH QUERY: compute daily average using results from hour query

        // TODO: convert flatmap into map
        final Dataset<Row> hourInput = spark
                .readStream()
                .format("kafka")
                .option("kafka.bootstrap.servers", settings.getKafkaServer())
                .option("subscribe", settings.getHourTopic())
                .load()
                .selectExpr("CAST(key AS STRING)", "CAST(value AS STRING)")
                .as(Encoders.tuple(Encoders.STRING(), Encoders.STRING()))
                .map(inputToRow, Encoders.tuple(Encoders.STRING(), Encoders.DOUBLE(), Encoders.TIMESTAMP(), Encoders.LONG()))
                .toDF("POI", "Noise", "Timestamp", "TSLong");

        final StreamingQuery dayQuery = hourInput
                // actual query
                .withWatermark("Timestamp", "10 minutes")
                .filter(col("TSLong").mod(1000 * 60 * 60).equalTo(0))
                .withColumn("Noise-linear", pow(10, col("Noise").divide(10)))
                .groupBy(window(col("Timestamp"), "1 day", "1 hour"),
                        col("POI")) // assuming unique POINames
                .avg("Noise-linear")
                .withColumn("logAvg", log(10, col("avg(noise-linear)")).multiply(10))
                .select(col("POI"), col("logAvg"), col("window.start"))
                .map(rowToOutput, Encoders.tuple(Encoders.STRING(), Encoders.STRING()))
                .toDF("key", "value")
                // output writing
                .writeStream()
                .format("kafka")
                .outputMode("append")
                .option("checkpointLocation", settings.getCheckpointLocation() + "/dayQuery")
                .option("kafka.bootstrap.servers", settings.getKafkaServer())
                .option("topic", settings.getDayTopic())
                .trigger(Trigger.ProcessingTime("5 minutes")) //periodic query: query computation every /*processing time*/
                .start();

        // FIFTH QUERY: compute weekly moving average from daily averages

        final Dataset<Row> dayInput = spark
                .readStream()
                .format("kafka")
                .option("kafka.bootstrap.servers", settings.getKafkaServer())
                .option("subscribe", "nodered-day")
                .load()
                .selectExpr("CAST(key AS STRING)", "CAST(value AS STRING)")
                .as(Encoders.tuple(Encoders.STRING(), Encoders.STRING()))
                .map(inputToRow, Encoders.tuple(Encoders.STRING(), Encoders.DOUBLE(), Encoders.TIMESTAMP(), Encoders.LONG()))
                .toDF("POI", "Noise", "Timestamp", "TSLong");

        final StreamingQuery weekQuery = hourInput
                // actual query
                .withWatermark("Timestamp", "1 hour")
                .withColumn("Noise-linear", pow(10, col("Noise").divide(10)))
                .groupBy(window(col("Timestamp"), "7 days", "1 day"),
                        col("POI")) // assuming unique POINames
                .avg("Noise-linear")
                .withColumn("logAvg", log(10, col("avg(noise-linear)")).multiply(10))
                .select(col("POI"), col("logAvg"), col("window.start"))
                .map(rowToOutput, Encoders.tuple(Encoders.STRING(), Encoders.STRING()))
                .toDF("key", "value")
                // output writing
                .writeStream()
                .format("kafka")
                .outputMode("append")
                .option("checkpointLocation", settings.getCheckpointLocation() + "/weekQuery")
                .option("kafka.bootstrap.servers", settings.getKafkaServer())
                .option("topic", settings.getWeekTopic())
                .trigger(Trigger.ProcessingTime("1 hour")) //periodic query: query computation every /*processing time*/
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
