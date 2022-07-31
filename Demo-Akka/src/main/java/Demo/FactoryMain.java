package Demo;

import akka.actor.ActorRef;
import akka.actor.ActorSystem;
import com.typesafe.config.ConfigFactory;

public class FactoryMain {

    public static void instantiateSupervisor(int stage, int windowSize, int windowSlide, int maxNumberReplica){

        ActorRef actor = null;
        switch(stage){
            case 1 -> {

                ActorSystem system = ActorSystem.create("System", ConfigFactory.load("supervisor1.conf"));
                actor = system.actorOf(SupervisorActor.props("Supervisor2", stage, windowSize, windowSlide, maxNumberReplica), "Supervisor1");

            }
            case 2 -> {

                ActorSystem system = ActorSystem.create("System", ConfigFactory.load("supervisor2.conf"));
                actor = system.actorOf(SupervisorActor.props("Supervisor3", stage, windowSize, windowSlide, maxNumberReplica), "Supervisor2");

            }
            case 3 -> {

                ActorSystem system = ActorSystem.create("System", ConfigFactory.load("supervisor3.conf"));
                actor = system.actorOf(SupervisorActor.props("Printer", stage, windowSize, windowSlide, maxNumberReplica), "Supervisor3");

            }
            case 4 -> {
                ActorSystem system = ActorSystem.create("System", ConfigFactory.load("printer.conf"));
                actor = system.actorOf(PrinterActor.props(), "Printer");
            }
            case 5 -> {
                ActorSystem system = ActorSystem.create("System", ConfigFactory.load("submitter.conf"));
                actor = system.actorOf(SubmitterActor.props(), "Submitter");
            }
        }

        System.out.println(actor.path());

    }

}
