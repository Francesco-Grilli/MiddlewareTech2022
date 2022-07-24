package Demo;

import com.typesafe.config.Config;
import java.io.File;
import java.io.IOException;
import org.apache.commons.io.FileUtils;

public class Main {

    public static void main(String[] args) throws InterruptedException {

        Main.run(args);

    }

    public static void run(String[] args){

        try {
            FileUtils.cleanDirectory(new File("../AkkaDemo/snapshots"));
        } catch (IOException e) {
            e.printStackTrace();
        }

        if(args.length<8) {
            System.err.println("Not enough command line data!");
            return;
        }

        int stage = Integer.parseInt(args[1]);
        int windowSize = Integer.parseInt(args[3]);
        int windowSlide = Integer.parseInt(args[5]);
        int maxNUmberReplica = Integer.parseInt(args[7]);

        FactoryMain.instantiateSupervisor(stage, windowSize, windowSlide, maxNUmberReplica);

    }

}
