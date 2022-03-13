MQTT-Test

Read data from a prova.txt file that contains some data and publish them on a MQTT broker
Then another node read the data from the broker and print in the debug console


ReadData

Read data from the prova.txt file and just print it whenever the Running node is pressed


POI-Generate-Data

Load from the POI.txt file some POI.
Generate some data with random position and noise and attach also a timestamp
Every x seconds generate the data and print them in the debug console

//CODE TO USE MQTT

import it.polimi.middleware.spark.utils.LogUtils;
import org.apache.spark.SparkConf;
import org.apache.spark.streaming.Durations;
import org.apache.spark.streaming.api.java.JavaReceiverInputDStream;
import org.apache.spark.streaming.api.java.JavaStreamingContext;
import org.apache.spark.streaming.mqtt.MQTTUtils;

public class TestConnection {
    public static void main(String[] args) {
        LogUtils.setLogLevel();

        final String master = args.length > 0 ? args[0] : "local[4]";
        final String socketHost = args.length > 1 ? args[1] : "localhost";
        final int socketPort = args.length > 2 ? Integer.parseInt(args[2]) : 9999;

        final SparkConf conf = new SparkConf().setMaster(master).setAppName("StreamingWordCountSum");
        final JavaStreamingContext sc = new JavaStreamingContext(conf, Durations.seconds(1));

        final JavaReceiverInputDStream<String> counts = MQTTUtils.createStream(sc, "tcp://localhost:1883", "prova");

        counts.foreachRDD(rdd -> rdd
                .collect()
                .forEach(System.out::println)
        );



        sc.start();

        try {
            sc.awaitTermination();
        } catch (final InterruptedException e) {
            e.printStackTrace();
        }
        sc.close();
    }
}