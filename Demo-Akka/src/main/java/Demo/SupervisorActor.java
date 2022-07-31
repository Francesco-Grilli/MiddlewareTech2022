package Demo;

import akka.actor.*;
import akka.japi.pf.DeciderBuilder;
import akka.persistence.AbstractPersistentActorWithAtLeastOnceDelivery;
import akka.util.Timeout;
import scala.Char;
import scala.concurrent.Future;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Locale;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

public class SupervisorActor extends AbstractPersistentActorWithAtLeastOnceDelivery {

    ArrayList<ActorRef> children;
    ActorRef nextStage;
    String nextStageString;
    int numberStage;
    long currentId;
    int windowSize;
    int windowSlide;
    int numberReplica;
    int maxNumberReplica;

    public SupervisorActor(String nextStageString, int numberStage, int windowSize, int windowSlide, int maxNumberReplica) {
        this.children = new ArrayList<>();
        this.nextStageString = nextStageString;
        this.numberStage = numberStage;
        this.windowSize = windowSize;
        this.windowSlide = windowSlide;
        this.maxNumberReplica = maxNumberReplica;
    }

    @Override
    public Receive createReceiveRecover() {
        return receiveBuilder().build();
    }

    @Override
    public Receive createReceive() {
        return receiveBuilder()
                .match(DataMessage.class, this::reRoute)
                .match(ErrorMessage.class, this::reRouteErrorMessage)
                .match(ConfirmMessage.class, this::confirmMessage)
                .build();
    }

    @Override
    public String persistenceId() {
        return getSelf().path().name();
    }

    @Override
    public void preStart() throws Exception {
        super.preStart();

        String address = "akka.tcp://System@127.0.0.1:615"+String.valueOf(this.numberStage+1)+"/user/"+this.nextStageString;

        int numberReconnection = 50;

        for(int i=0; i<numberReconnection && this.nextStage==null; i++) {
            try {
                Future<ActorRef> future = getContext().actorSelection(address).resolveOne(Timeout.apply(5, TimeUnit.MINUTES));
                this.nextStage = future.result(scala.concurrent.duration.Duration.create(5, TimeUnit.MINUTES), null);
            } catch (InterruptedException | TimeoutException | ActorInitializationException | ActorNotFound e) {
                System.out.println("Error, next stage not found, waiting for the next stage to be instantiated");
                Thread.sleep(2000);
            }
        }


        for(int i=0; i<this.maxNumberReplica; i++) {
            this.children.add(FactoryChildren.getChildrenByStage(getContext(), this.numberStage, this.windowSize, this.windowSlide, this.numberReplica));
            this.numberReplica++;
        }

    }

    @Override
    public SupervisorStrategy supervisorStrategy() {
        return strategy;
    }

    void reRoute(DataMessage message){

        getSender().tell(new ConfirmMessage(message.getId()), getSelf());

        if(message.isSendToNext()){
            message.setSendToNext(false);
            deliver(this.nextStage.path(), longId -> new DataMessage(message, false, longId));
            //this.nextStage.tell(message, getSelf());
        }
        else{
            ActorRef child = getChildren(message);
            deliver(child.path(), longId -> new DataMessage(message, false, longId));
            //child.tell(message, getSelf());
        }
    }

    ActorRef getChildren(DataMessage m){

        String key = m.getData().first();
        key = key.toLowerCase(Locale.ROOT);
        char c = key.charAt(0);
        int variance = (int) 'a';
        int position = ((int) c)-variance;

        int index = position%this.numberReplica;

        return this.children.get(index);

    }

    void reRouteErrorMessage(ErrorMessage m){
        ActorRef child = this.children.get(0);
        child.tell(m, getSelf());
    }

    public static Props props(String nextStageString, int numberStage, int windowSize, int windowSlide, int maxNumberReplica){
        return Props.create(SupervisorActor.class, () -> new SupervisorActor(nextStageString, numberStage, windowSize, windowSlide, maxNumberReplica));
    }

    private static SupervisorStrategy strategy = new OneForOneStrategy(100, Duration.ofMinutes(1),
            DeciderBuilder.match(Exception.class, e -> SupervisorStrategy.restart()).build());

    void confirmMessage(ConfirmMessage m){
        persist(new MsgConfirm(m.getId()),
                (e) -> confirmDelivery(e.getId()));
    }

}
