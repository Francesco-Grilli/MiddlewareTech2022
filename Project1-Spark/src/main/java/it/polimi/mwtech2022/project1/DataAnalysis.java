package it.polimi.mwtech2022.project1;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import it.polimi.mwtech2022.project1.utils.LogUtils;
import org.apache.spark.api.java.function.FlatMapFunction;
import org.apache.spark.api.java.function.MapFunction;
import org.apache.spark.sql.*;
import org.apache.spark.sql.expressions.Window;
import org.apache.spark.sql.expressions.WindowSpec;
import org.apache.spark.sql.streaming.*;
import scala.Tuple2;
import scala.Tuple5;

import java.sql.Date;
import java.sql.Timestamp;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.TimeoutException;

import static org.apache.spark.sql.functions.*;

public class DataAnalysis {

    // TODO: check if there's a way not to let it eat the CPU out

    public static void main(String[] args) throws TimeoutException {
        LogUtils.setLogLevel();

        final String master = args.length > 0 ? args[0] : "local[4]";
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

        final Dataset<String> input = spark
                .readStream()
                .format("socket")
                .option("host", socketHost)
                .option("port", socketPort)
                .load()
                .as(Encoders.STRING());
//        input.printSchema();

        // may want to define splitting function aside (out of the map operator)
        // TODO needs check for cleaning purposes!!
        // TODO: testing for actual noise array case
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

        poiNoise.printSchema();

        // needs timestamp type column
        poiNoise.withWatermark("timestamp", "1 hour");


//        The backend periodically computes the following metrics:
//        1. hourly, daily, and weekly moving average of noise level, for each point of interest;
//        2. top 10 points of interest with the highest level of noise over the last hour;
//        3. point of interest with the longest streak of good noise level;
//            ○ given a maximum threshold T, a streak is the time span (in seconds) since a point of interest has last
//              measured a value higher than T;
//            ○ by definition, a point of interest with a value above T has a streak of 0;
//            ○ at any given point in time, the query should compute the point (or points) of interest with the
//              longest stream.

        // TODO: serious testing of these functions (the first one seems to work)
        // testing OK
        final Dataset<Row> hourlyAverages = poiNoise
                .groupBy(window(col("Timestamp"), "1 hour", "5 minutes"),
                        col("POIName"))
                .avg("Noise");
        hourlyAverages.printSchema();

        //needs fixing
//        final Dataset<Row> lastHour = hourlyAverages
//                .select("window.end")
//                .filter((col("window.end").cast("long")).leq(System.currentTimeMillis()))
//                .orderBy(asc("window.end"));
//                .limit(1);

        // how to test this thing?? all the values I can get are in the last hour
        // OK
//        final Dataset<Row> lastHourAverages = hourlyAverages
//                .filter(col("window").equals(lastHour.first().get(0)))
//                .filter(col("TSLong").geq(System.currentTimeMillis() - 60 * 60 * 1000)) //millis in an hour
//                .groupBy(col("POIName"))
//                .avg("Noise");
//                .select(col("POIName"), col("avg(Noise)"));

//        final Dataset<Row> lastHourTop10 = lastHourAverages
//                .orderBy(col("avg(noise-value)"))
//                .limit(10);
//
        // testing OK
        final Dataset<Row> dailyAverages = poiNoise
                .groupBy(window(col("Timestamp"), "1 day", "1 hour"),
                        col("POIName"))
                .avg("Noise");


        // testing OK
        final Dataset<Row> weeklyAverages = poiNoise
                .groupBy(window(col("Timestamp"), "1 week", "1 day"),
                        col("POIName"))
                .avg("Noise");

        // testing OK
        final Dataset<Row> lastTimeOverThreshold = poiNoise
                .filter(col("Noise").geq(threshold))
                .groupBy("POIName")
                .max("TSLong")
                .orderBy(asc("max(TSLong)"))
                .limit(1);

        final StreamingQuery query = lastTimeOverThreshold
                .writeStream()
                .format("console")
                .outputMode("complete")
                .start();

        try {
            query.awaitTermination();
        } catch (final StreamingQueryException e) {
            e.printStackTrace();
        }

        spark.close();
    }


}
