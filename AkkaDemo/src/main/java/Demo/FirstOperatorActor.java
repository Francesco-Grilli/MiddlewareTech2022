package Demo;

import akka.actor.Props;

import java.util.OptionalDouble;

public class FirstOperatorActor extends OperatorActor{


    public FirstOperatorActor(int windowSize, int windowSlide, int numberReplica) {
        super(windowSize, windowSlide, numberReplica);
    }

    @Override
    public DataMessage calculateOperator() {
        OptionalDouble value;

        value = this.myWindow.stream().mapToDouble((a) -> a.getData().second()).average();

        if(value.isPresent()) {
            return new DataMessage("Temperature", value.getAsDouble());
        }
        else{
            return new DataMessage("Temperature", -1.0);
        }
    }

    public static Props props(int windowSize, int windowSlide, int numberReplica){
        return Props.create(FirstOperatorActor.class, () -> new FirstOperatorActor(windowSize, windowSlide, numberReplica));
    }
}
