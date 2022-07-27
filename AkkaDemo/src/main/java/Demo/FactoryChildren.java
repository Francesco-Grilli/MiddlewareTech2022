package Demo;

import akka.actor.ActorContext;
import akka.actor.ActorRef;

public class FactoryChildren {

    public static ActorRef getChildrenByStage(ActorContext context, int stage, int windowSize, int windowSlide, int replica){

        switch(stage){
            case 1 -> {
                return context.actorOf(FirstOperatorActor.props(windowSize, windowSlide, replica), "FirstOperator" + stage + "." + replica);
            }

            case 2 -> {
                return context.actorOf(SecondOperatorActor.props(windowSize, windowSlide, replica), "SecondOperator" + stage + "." + replica);
            }
            case 3 ->{
                return context.actorOf(ThirdOperatorActor.props(windowSize, windowSlide, replica), "ThirdOperator" + stage + "." + replica);
            }
            default -> {
                return null;
            }
        }

    }

}
