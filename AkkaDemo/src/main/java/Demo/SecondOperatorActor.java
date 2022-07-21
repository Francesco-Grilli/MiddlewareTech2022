package Demo;

import akka.actor.Props;

import java.util.Optional;

public class SecondOperatorActor extends OperatorActor{


    public SecondOperatorActor(int windowSize, int windowSlide, int numberReplica) {
        super(windowSize, windowSlide, numberReplica);
    }

    @Override
    public DataMessage calculateOperator() {

        Optional<DataMessage> message = this.myWindow.stream().max((a, b) -> {
            return Double.compare(a.getData().second(), b.getData().second());
        });

        return message.orElseGet(() -> new DataMessage("Temperature", -2.0));

    }

    public static Props props(int windowSize, int windowSlide, int numberReplica){
        return Props.create(SecondOperatorActor.class, () -> new SecondOperatorActor(windowSize, windowSlide, numberReplica));
    }
}
