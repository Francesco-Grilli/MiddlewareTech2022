package Demo;

import akka.actor.ActorRef;
import akka.persistence.AbstractPersistentActorWithAtLeastOnceDelivery;
import akka.persistence.SaveSnapshotSuccess;
import akka.persistence.SnapshotOffer;

import java.util.ArrayList;
import java.util.LinkedList;


public abstract class OperatorActor extends AbstractPersistentActorWithAtLeastOnceDelivery {

    LinkedList<DataMessage> myWindow;
    State myState;
    int windowSize;
    int windowSlide;
    int numberReplica;
    ActorRef mySupervisor;

    public OperatorActor(int windowSize, int windowSlide, int numberReplica){
        this.myState = new State();
        this.myWindow = new LinkedList<>();
        this.windowSize = windowSize;
        this.windowSlide = windowSlide;
        this.numberReplica = numberReplica;
    }

    @Override
    public Receive createReceiveRecover() {
        return receiveBuilder()
                .match(DataMessage.class, myState::update)
                .match(SnapshotOffer.class, ss -> this.myState = (State) ss.snapshot()/*TODO add method to restore the state/window */)
                .build();
    }

    @Override
    public Receive createReceive() {
        return receiveBuilder()
                .match(DataMessage.class, this::calculateWindow)
                .match(SaveSnapshotSuccess.class, System.out::println)
                .build();
    }

    @Override
    public String persistenceId() {
        return getSelf().path().name();
    }

    boolean isPipeFull(){
        return this.myWindow.size() >= this.windowSize;
    }

    void deleteSlidingElement(){

        for(int i=0; i<this.windowSlide; i++){
            this.myWindow.removeLast();
        }

    }

    public abstract DataMessage calculateOperator();

    void calculateWindow(DataMessage message){

        persistMessage(message);

        this.myWindow.addFirst(message);

        DataMessage reply;

        if(isPipeFull()){
            reply = calculateOperator();
            reply.setSendToNext(true);
            deleteSlidingElement();
            getContext().getParent().tell(reply, getSelf());
            System.out.println(getSelf().path().name() + ": " + reply.getData().second());
        }


    }

    void persistMessage(DataMessage m){

        final DataMessage event = new DataMessage(m);
        persist(event,
                (DataMessage e) -> {
                    this.myState.update(e);
                    getContext().getSystem().getEventStream().publish(e);
                    saveSnapshot(this.myState.copy());
                }
        );

    }



}
