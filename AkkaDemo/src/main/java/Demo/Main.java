package Demo;

import akka.actor.ActorRef;
import akka.actor.ActorSystem;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;

public class Main {

    public static void main(String[] args) {
        
        Config conf = null;
        
        switch(Integer.parseInt(args[0])){
            case 1 -> {
                conf = ConfigFactory.load("supervisor1.conf");
                ActorSystem system = ActorSystem.create("System", conf);
                ActorRef supervisor1 = system.actorOf(SupervisorActor.props("Supervisor2", 1, 5, 1), "Supervisor1");

                System.out.println(supervisor1.path());
                supervisor1.tell(new DataMessage("Temperature", 15.3), ActorRef.noSender());
                supervisor1.tell(new DataMessage("Temperature", 12.4), ActorRef.noSender());
                supervisor1.tell(new DataMessage("Temperature", 11.5), ActorRef.noSender());
                supervisor1.tell(new DataMessage("Temperature", 19.8), ActorRef.noSender());
                supervisor1.tell(new DataMessage("Temperature", 18.9), ActorRef.noSender());
                supervisor1.tell(new DataMessage("Temperature", 22.1), ActorRef.noSender());
                supervisor1.tell(new DataMessage("Temperature", 25.2), ActorRef.noSender());
                supervisor1.tell(new DataMessage("Temperature", 27.2), ActorRef.noSender());
                supervisor1.tell(new DataMessage("Temperature", 22.2), ActorRef.noSender());
                supervisor1.tell(new DataMessage("Temperature", 29.2), ActorRef.noSender());
                supervisor1.tell(new DataMessage("Temperature", 29.2), ActorRef.noSender());
                supervisor1.tell(new DataMessage("Temperature", 29.2), ActorRef.noSender());
                supervisor1.tell(new DataMessage("Temperature", 29.2), ActorRef.noSender());
                supervisor1.tell(new DataMessage("Temperature", 29.2), ActorRef.noSender());

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
            }
            case 4 -> {
                conf = ConfigFactory.load("printer.conf");
                ActorSystem system = ActorSystem.create("System", conf);
                ActorRef printer = system.actorOf(PrinterActor.props(), "Printer");
                System.out.println(printer.path());
            }
        }


    }

}
