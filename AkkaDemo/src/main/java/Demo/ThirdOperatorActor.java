package Demo;

import akka.actor.Props;

public class ThirdOperatorActor extends OperatorActor{


    public ThirdOperatorActor(int windowSize, int windowSlide, int numberReplica) {
        super(windowSize, windowSlide, numberReplica);
    }

    @Override
    public DataMessage calculateOperator() {

        double sum=0;
        double mean;
        double std=0;

        sum = this.myWindow.stream().mapToDouble((a) -> a.getData().second()).sum();
        mean = sum/this.myWindow.size();

        for(DataMessage m : this.myWindow){
            std += Math.pow(m.getData().second() - mean, 2);
        }
        std=std/this.myWindow.size();
        std = Math.sqrt(std);
        return new DataMessage("Amperature", std);

    }

    public static Props props(int windowSize, int windowSlide, int numberReplica){
        return Props.create(ThirdOperatorActor.class, () -> new ThirdOperatorActor(windowSize, windowSlide, numberReplica));
    }
}
