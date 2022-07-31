package Demo;

import akka.actor.AbstractActor;
import akka.actor.Props;

public class PrinterActor extends AbstractActor {
    @Override
    public Receive createReceive() {
        return receiveBuilder()
                .match(DataMessage.class, this::executeMessage)
                .build();
    }

    void executeMessage(DataMessage m){

        System.out.println("Printer - " + m.getData().first() +": " + m.getData().second());
        getSender().tell(new ConfirmMessage(m.getId()), getSelf());

    }

    public static Props props(){
        return Props.create(PrinterActor.class);
    }
}
