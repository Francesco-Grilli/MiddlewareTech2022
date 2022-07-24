package Demo;

import akka.actor.ActorRef;
import akka.persistence.*;

import java.lang.annotation.RetentionPolicy;
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
                .match(SnapshotOffer.class, this::restoreState)
                .build();
    }

    @Override
    public Receive createReceive() {
        return receiveBuilder()
                .match(DataMessage.class, this::calculateWindow)
                .match(SaveSnapshotSuccess.class, (ss) -> {
                    System.out.println(ss);
                    deleteSnapshots(new SnapshotSelectionCriteria(scala.Long.MaxValue(), ss.metadata().timestamp()-1, 0, 0));
                })
                .match(DeleteSnapshotSuccess.class, System.out::println)
                .match(DeleteMessagesSuccess.class, System.out::println)
                .match(ErrorMessage.class, (m) -> {throw new Exception("Error message arrived");})
                .match(ConfirmMessage.class, this::confirmMessage)
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

        getSender().tell(new ConfirmMessage(message.getId()), getSelf());

        this.myWindow.addFirst(message);

        System.out.println("|||||||||||||||||||||");
        for(DataMessage m : this.myWindow){
            System.out.println(m.getData().second());
        }
        System.out.println("|||||||||||||||||||||");

        DataMessage reply;

        if(isPipeFull()){
            reply = calculateOperator();
            reply.setSendToNext(true);
            deleteSlidingElement();
            deliver(getContext().getParent().path(), longId -> new DataMessage(reply, true, longId));
            //getContext().getParent().tell(reply, getSelf());
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

    void restoreState(SnapshotOffer ss){

        this.myState = (State) ss.snapshot();

        LinkedList<DataMessage> temporaryList = ((State)ss.snapshot()).getDataCopy();

        System.out.println("In recovery method");
        for(DataMessage m : temporaryList){
            System.out.println(m.getData().second());
        }

        LinkedList<DataMessage> emptyList = new LinkedList<>();

        int i;
        for(i=0; i < temporaryList.size() && i<this.windowSize; i++){
            emptyList.addLast(temporaryList.get(i));
        }

        if(((temporaryList.size()-i)%this.windowSlide)!=0 && i>0){

            for(int k=0; k<((temporaryList.size()-i)%this.windowSlide) && (i-k)>=0; k++){
                emptyList.addLast(temporaryList.get(i-k));
            }

        }

        this.myWindow = emptyList;

        System.out.println("Printing recovered state");
        for(int k=0; k<this.myWindow.size(); k++){
            System.out.println(this.myWindow.get(k).getData().second());
        }

        DataMessage reply;
        if(isPipeFull()){
            reply = calculateOperator();
            reply.setSendToNext(true);
            deleteSlidingElement();
            getContext().getParent().tell(reply, getSelf());
            System.out.println(getSelf().path().name() + ": " + reply.getData().second());
        }

        System.out.println("Printing recovered state after sending message");
        for(int k=0; k<this.myWindow.size(); k++){
            System.out.println(this.myWindow.get(k).getData().second());
        }

    }

    void confirmMessage(ConfirmMessage m){

        persist(new MsgConfirm(m.getId()),
                (e) -> confirmDelivery(e.getId())
        );

    }


}
