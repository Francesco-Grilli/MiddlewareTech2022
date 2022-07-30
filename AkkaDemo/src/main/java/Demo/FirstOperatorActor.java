package Demo;

import akka.actor.Props;

import java.util.OptionalDouble;
import java.util.Random;

public class FirstOperatorActor extends OperatorActor{


    public FirstOperatorActor(int windowSize, int windowSlide, int numberReplica) {
        super(windowSize, windowSlide, numberReplica);
    }

    @Override
    public DataMessage calculateOperator() {
        OptionalDouble value;

        value = this.myWindow.stream().mapToDouble((a) -> a.getData().second()).average();

        Random r = new Random();
        int a = (int) 'a';
        int z = (int) 'z';
        char c = (char) (r.nextInt()*z+a);


        if(value.isPresent()) {
            return new DataMessage(String.valueOf(c), value.getAsDouble());
        }
        else{
            return new DataMessage(String.valueOf(c), -1.0);
        }
    }

    public static Props props(int windowSize, int windowSlide, int numberReplica){
        return Props.create(FirstOperatorActor.class, () -> new FirstOperatorActor(windowSize, windowSlide, numberReplica));
    }
}
