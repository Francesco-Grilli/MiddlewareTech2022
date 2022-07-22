package Demo;

import akka.actor.ActorRef;
import akka.actor.ActorSelection;
import akka.actor.ActorSystem;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;

import java.util.Random;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadPoolExecutor;

public class Main {

    public static void main(String[] args) throws InterruptedException {
        
        Config conf = null;
        
        switch(Integer.parseInt(args[0])){
            case 1 -> {
                conf = ConfigFactory.load("supervisor1.conf");
                ActorSystem system = ActorSystem.create("System", conf);
                ActorRef supervisor1 = system.actorOf(SupervisorActor.props("Supervisor2", 1, 5, 1), "Supervisor1");

                ActorSelection supervisor2 = system.actorSelection("akka.tcp://System@127.0.0.1:6152/user/Supervisor2");

                System.out.println(supervisor1.path());
                /*supervisor1.tell(new DataMessage("Temperature", 15.3), ActorRef.noSender());
                supervisor1.tell(new DataMessage("Temperature", 12.4), ActorRef.noSender());
                supervisor1.tell(new DataMessage("Temperature", 11.5), ActorRef.noSender());
                supervisor1.tell(new DataMessage("Temperature", 19.8), ActorRef.noSender());
                supervisor1.tell(new DataMessage("Temperature", 18.9), ActorRef.noSender());
                supervisor1.tell(new DataMessage("Temperature", 22.1), ActorRef.noSender());
                supervisor1.tell(new DataMessage("Temperature", 25.2), ActorRef.noSender());
                supervisor1.tell(new DataMessage("Temperature", 27.2), ActorRef.noSender());
                //Thread.sleep(1000);
                //supervisor2.tell(new ErrorMessage(), ActorRef.noSender());
                //supervisor1.tell(new ErrorMessage(), ActorRef.noSender());
                supervisor1.tell(new DataMessage("Temperature", 22.2), ActorRef.noSender());
                supervisor1.tell(new DataMessage("Temperature", 24.2), ActorRef.noSender());
                supervisor1.tell(new DataMessage("Temperature", 29.2), ActorRef.noSender());
                supervisor1.tell(new DataMessage("Temperature", 21.2), ActorRef.noSender());
                supervisor1.tell(new DataMessage("Temperature", 22.2), ActorRef.noSender());
                supervisor1.tell(new DataMessage("Temperature", 24.2), ActorRef.noSender());*/



            }
            case 2 -> {
                conf = ConfigFactory.load("supervisor2.conf");
                ActorSystem system = ActorSystem.create("System", conf);
                ActorRef supervisor2 = system.actorOf(SupervisorActor.props("Supervisor3", 2, 5, 1), "Supervisor2");
                System.out.println(supervisor2.path());
            }
            case 3 -> {
                conf = ConfigFactory.load("supervisor3.conf");
                ActorSystem system = ActorSystem.create("System", conf);
                ActorRef supervisor3 = system.actorOf(SupervisorActor.props("Printer", 3, 5, 1), "Supervisor3");
                System.out.println(supervisor3.path());

                /*supervisor3.tell(new DataMessage("Temperature", 15.3), ActorRef.noSender());
                supervisor3.tell(new DataMessage("Temperature", 12.4), ActorRef.noSender());
                supervisor3.tell(new DataMessage("Temperature", 11.5), ActorRef.noSender());
                supervisor3.tell(new DataMessage("Temperature", 19.8), ActorRef.noSender());
                supervisor3.tell(new DataMessage("Temperature", 18.9), ActorRef.noSender());
                supervisor3.tell(new DataMessage("Temperature", 22.1), ActorRef.noSender());
                supervisor3.tell(new DataMessage("Temperature", 25.2), ActorRef.noSender());
                supervisor3.tell(new DataMessage("Temperature", 27.2), ActorRef.noSender());
                Thread.sleep(1000);
                supervisor3.tell(new ErrorMessage(), ActorRef.noSender());
                supervisor3.tell(new DataMessage("Temperature", 22.2), ActorRef.noSender());
                supervisor3.tell(new DataMessage("Temperature", 24.2), ActorRef.noSender());
                supervisor3.tell(new DataMessage("Temperature", 29.2), ActorRef.noSender());
                supervisor3.tell(new DataMessage("Temperature", 21.2), ActorRef.noSender());
                supervisor3.tell(new DataMessage("Temperature", 22.2), ActorRef.noSender());
                supervisor3.tell(new DataMessage("Temperature", 24.2), ActorRef.noSender());*/

            }
            case 4 -> {
                conf = ConfigFactory.load("printer.conf");
                ActorSystem system = ActorSystem.create("System", conf);
                ActorRef printer = system.actorOf(PrinterActor.props(), "Printer");
                System.out.println(printer.path());
            }

            case 5 -> {
                ActorSystem system = ActorSystem.create("System", ConfigFactory.load("test.conf"));
                ActorSelection supervisor1 = system.actorSelection("akka.tcp://System@127.0.0.1:6151/user/Supervisor1");

                ActorSelection supervisor2 = system.actorSelection("akka.tcp://System@127.0.0.1:6152/user/Supervisor2");


                Random r = new Random();
                for(int i=0; i<100; i++){
                    supervisor1.tell(new DataMessage("Temperature", r.nextDouble()*29+1), ActorRef.noSender());
                    if(r.nextInt()>=0.95) {
                        supervisor2.tell(new ErrorMessage(), ActorRef.noSender());
                    }
                }
                Thread.sleep(1000);
                for(int i=0; i<20; i++){
                    supervisor1.tell(new DataMessage("Temperature", r.nextDouble()*29+1), ActorRef.noSender());
                    if(r.nextInt()>=0.95) {
                        supervisor2.tell(new ErrorMessage(), ActorRef.noSender());
                    }
                }


            }
        }


    }

}
