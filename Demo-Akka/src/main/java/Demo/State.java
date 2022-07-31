package Demo;

import java.io.Serializable;
import java.util.LinkedList;

public class State implements Serializable {

    LinkedList<DataMessage> state;
    private static final long serialVersionUID = 1L;

    public State(){
        this.state = new LinkedList<>();
    }

    public State(LinkedList<DataMessage> state){
        this.state = state;
    }

    public void update(DataMessage m){
        state.addFirst(m);
    }

    public State copy(){
        return new State(new LinkedList<>(this.state));
    }

    public int size(){
        return this.state.size();
    }

    public LinkedList<DataMessage> getDataCopy(){
        return new LinkedList<>(this.state);
    }



}
