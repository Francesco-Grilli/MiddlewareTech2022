package Demo;

import akka.actor.*;
import akka.japi.pf.DeciderBuilder;
import akka.persistence.AbstractPersistentActorWithAtLeastOnceDelivery;

import java.time.Duration;
import java.util.ArrayList;

public class SupervisorActor extends AbstractPersistentActorWithAtLeastOnceDelivery {

    ArrayList<ActorRef> children;
    ActorSelection nextStage;
    String nextStageString;
    int numberStage;
    long currentId;
    int windowSize;
    int windowSlide;
    int numberReplica;

    public SupervisorActor(String nextStageString, int numberStage, int windowSize, int windowSlide) {
        this.children = new ArrayList<>();
        this.nextStageString = nextStageString;
        this.numberStage = numberStage;
        this.windowSize = windowSize;
        this.windowSlide = windowSlide;
        this.numberReplica = 0;
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
                .build();
    }

    @Override
    public String persistenceId() {
        return getSelf().path().name();
    }

    void reRoute(DataMessage message){
        if(message.isSendToNext()){
            message.setSendToNext(false);
            this.nextStage.tell(message, getSelf());
        }
        else{
            ActorRef child = this.children.get(0);
            child.tell(message, getSelf());
        }
    }

    void reRouteErrorMessage(ErrorMessage m){
        ActorRef child = this.children.get(0);
        child.tell(m, getSelf());
    }

    @Override
    public void preStart() throws Exception, Exception {
        super.preStart();

        String address = "akka.tcp://System@127.0.0.1:615"+String.valueOf(this.numberStage+1)+"/user/"+this.nextStageString;
        this.nextStage = getContext().actorSelection(address);

        switch (this.numberStage) {
            case 1 -> {
                this.children.add(getContext().actorOf(FirstOperatorActor.props(this.windowSize, this.windowSlide, this.numberReplica),
                        "FirstOperator" + this.numberStage + "." + this.numberReplica));
                numberReplica++;
            }
            case 2 -> {
                this.children.add(getContext().actorOf(SecondOperatorActor.props(this.windowSize, this.windowSlide, this.numberReplica),
                        "SecondOperator" + this.numberStage + "." + this.numberReplica));
                numberReplica++;
            }
            case 3 -> {
                this.children.add(getContext().actorOf(ThirdOperatorActor.props(this.windowSize, this.windowSlide, this.numberReplica),
                        "ThirdOperator" + this.numberStage + "." + this.numberReplica));
                numberReplica++;
            }
            default -> System.out.println("Default case???");
        }
    }

    @Override
    public SupervisorStrategy supervisorStrategy() {
        return strategy;
    }

    public static Props props(String nextStageString, int numberStage, int windowSize, int windowSlide){
        return Props.create(SupervisorActor.class, () -> new SupervisorActor(nextStageString, numberStage, windowSize, windowSlide));
    }

    private static SupervisorStrategy strategy = new OneForOneStrategy(10, Duration.ofMinutes(1),
            DeciderBuilder.match(Exception.class, e -> SupervisorStrategy.restart()).build());
}
