package it.polimi.mwtech2022.project1;

import it.polimi.mwtech2022.project1.utils.LogUtils;
import org.apache.spark.api.java.function.MapFunction;
import org.apache.spark.sql.Dataset;
import org.apache.spark.sql.Encoders;
import org.apache.spark.sql.Row;
import org.apache.spark.sql.SparkSession;
import org.apache.spark.sql.streaming.StreamingQuery;
import org.apache.spark.sql.streaming.StreamingQueryException;
import scala.Tuple2;
import scala.Tuple3;

import java.sql.Timestamp;
import java.util.Arrays;
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
        final int threshold = args.length > 4 ? Integer.parseInt(args[4]) : 50;

        final SparkSession spark = SparkSession
                .builder()
                .master(master)
                .appName("DataAnalysis")
                .getOrCreate();

        // requires inputs as pairs (POI, noise level)

        final Dataset<Row> input = spark
                .readStream()
                .format("socket")
                .option("host", socketHost)
                .option("port", socketPort)
//                .format("rate")
//                .option("rowPerSecond", 5)
//                .option("delimiter", ",")
                .load();
//        input.printSchema();

        // may want to define splitting function aside (out of the map operator)
        // works correctly with strings like "[string],[int]"
        // TODO needs check for cleaning purposes!!
        final Dataset<Row> poiNoise = input
                .as(Encoders.STRING())
                .map((MapFunction<String, Tuple3<String, Integer, Timestamp>>) x -> {
                    List<String> stringList = Arrays.asList(x.split(","));
                    try{
                        if (stringList.size() == 2)
                            return new Tuple3<>(stringList.get(0),
                                    Integer.parseInt(stringList.get(1)),
                                    new Timestamp(System.currentTimeMillis())); // TODO replace with actual ts
                    } catch (Exception e){}
                    return null;
                }, Encoders.tuple(Encoders.STRING(), Encoders.INT(), Encoders.TIMESTAMP()))
                .toDF("poi", "noise-value", "timestamp");

        poiNoise.printSchema();

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

        //TODO: serious testing of these functions (the first one seems to work)
        //TODO: need these functions with windows or related to specific hours/day/weeks?
        final Dataset<Row> hourlyAverages = poiNoise
                .groupBy(window(col("timestamp"), "1 hour", "5 minutes"),
                            col("poi"))
                .avg("noise-value");
        hourlyAverages.printSchema();

        final Dataset<Row> lastHourAverages = poiNoise.
                filter(col("timestamp").leq(new Timestamp(System.currentTimeMillis())))
                .filter(col("timestamp").geq(new Timestamp(System.currentTimeMillis()-60*60*1000))) //millis in an hour
                .groupBy(col("poi"))
                .avg("noise-value")
                .select(col("poi"), col("avg(noise-value)"));

        final Dataset<Row> lastHourTop10 = lastHourAverages
                .orderBy(col("avg(noise-value)"))
                .limit(10);

        final Dataset<Row> dailyAverages = poiNoise
                .groupBy(window(col("timestamp"), "1 day", "1 hour"),
                        col("poi"))
                .avg("noise-value");

        final Dataset<Row> weeklyAverages = poiNoise
                .groupBy(window(col("timestamp"), "1 week", "1 day"),
                        col("poi"))
                .avg("noise-value");

        final Timestamp lastTS = (Timestamp) poiNoise
                .orderBy("timestamp")
                .first().get(0);

        // TODO: streak seems not to get updated --> this part needs fixing
        final Dataset<Row> lastTimeOverThreshold = poiNoise
                .filter(col("noise-value").geq(threshold))
                .select("timestamp", "poi")
                .as(Encoders.tuple(Encoders.TIMESTAMP(), Encoders.STRING()))
                .map((MapFunction<Tuple2<Timestamp, String>, Tuple2<String, Long>>) t -> {
                    long tsDiff = (new Timestamp(System.currentTimeMillis())).getTime() - t._1.getTime();
                    return new Tuple2<>(t._2, tsDiff);
                }, Encoders.tuple(Encoders.STRING(), Encoders.LONG()))
                .toDF("poi", "streak")
                .groupBy("poi")
                .min("streak");

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
