package Demo;

import akka.actor.Props;

import java.util.Optional;
import java.util.Random;

public class SecondOperatorActor extends OperatorActor{


    public SecondOperatorActor(int windowSize, int windowSlide, int numberReplica) {
        super(windowSize, windowSlide, numberReplica);
    }

    @Override
    public DataMessage calculateOperator() {

        Optional<DataMessage> message = this.myWindow.stream().max((a, b) -> {
            return Double.compare(a.getData().second(), b.getData().second());
        });


        Random r = new Random();
        int a = (int) 'a';
        int z = (int) 'z';
        char c = (char) (r.nextInt()*z+a);

        if(message.isPresent()){
            message.get().setData(String.valueOf(c), message.get().getData().second());
            return message.get();
        }
        else{
            return new DataMessage(String.valueOf(c), -2.0);
        }

    }

    public static Props props(int windowSize, int windowSlide, int numberReplica){
        return Props.create(SecondOperatorActor.class, () -> new SecondOperatorActor(windowSize, windowSlide, numberReplica));
    }
}
