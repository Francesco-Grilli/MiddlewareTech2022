package Demo;

import akka.actor.*;
import akka.util.Timeout;
import scala.concurrent.Future;

import java.util.Random;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

public class SubmitterActor extends AbstractActor {

    private ActorRef supervisor1, supervisor2, supervisor3;

    @Override
    public void preStart() throws Exception {
        super.preStart();

        getReferences();

        runTest();

    }

    private void getReferences() throws InterruptedException {

        String address = "akka.tcp://System@127.0.0.1:6151/user/";
        int numberReconnection = 50;

        for(int i=0; i<numberReconnection && this.supervisor1==null; i++) {
            try {
                Future<ActorRef> future = getContext().actorSelection("akka.tcp://System@127.0.0.1:6151/user/Supervisor1").resolveOne(Timeout.apply(5, TimeUnit.MINUTES));
                this.supervisor1 = future.result(scala.concurrent.duration.Duration.create(5, TimeUnit.MINUTES), null);
            } catch (InterruptedException | TimeoutException | ActorInitializationException | ActorNotFound e) {
                System.out.println("Error, next stage not found, waiting for the next stage to be instantiated");
                Thread.sleep(2000);
            }
        }

        System.out.println("OK 1");

        for(int i=0; i<numberReconnection && this.supervisor2==null; i++) {
            try {
                Future<ActorRef> future = getContext().actorSelection("akka.tcp://System@127.0.0.1:6152/user/Supervisor2").resolveOne(Timeout.apply(5, TimeUnit.MINUTES));
                this.supervisor2 = future.result(scala.concurrent.duration.Duration.create(5, TimeUnit.MINUTES), null);
            } catch (InterruptedException | TimeoutException | ActorInitializationException | ActorNotFound e) {
                System.out.println("Error, next stage not found, waiting for the next stage to be instantiated");
                Thread.sleep(2000);
            }
        }

        System.out.println("OK 2");

        for(int i=0; i<numberReconnection && this.supervisor3==null; i++) {
            try {
                Future<ActorRef> future = getContext().actorSelection("akka.tcp://System@127.0.0.1:6153/user/Supervisor3").resolveOne(Timeout.apply(5, TimeUnit.MINUTES));
                this.supervisor3 = future.result(scala.concurrent.duration.Duration.create(5, TimeUnit.MINUTES), null);
            } catch (InterruptedException | TimeoutException | ActorInitializationException | ActorNotFound e) {
                System.out.println("Error, next stage not found, waiting for the next stage to be instantiated");
                Thread.sleep(2000);
            }
        }

        System.out.println("OK 3");

        Thread.sleep(1000*30);

    }

    @Override
    public Receive createReceive() {
        return receiveBuilder()
                .match(ConfirmMessage.class, System.out::println)
                .build();
    }

    public static Props props(){
        return Props.create(SubmitterActor.class);
    }

    private void runTest(){

        Random rand = new Random();

        for(int i=0; i<100; i++) {
            supervisor1.tell(new DataMessage("Temperature", rand.nextDouble()*29+1), getSelf());
        }

    }
}
