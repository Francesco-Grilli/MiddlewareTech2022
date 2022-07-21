package Demo;

import akka.japi.Pair;

import java.io.Serial;
import java.io.Serializable;

public class DataMessage implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;
    private Pair<String, Double> data;
    private boolean sendToNext = false;
    private long id;

    public DataMessage(DataMessage m){
        this.data = m.getData();
    }

    public DataMessage(Pair<String, Double> data) {
        this.data = data;
    }

    public DataMessage(String s, Double d){
        data = new Pair<>(s, d);
    }

    public Pair<String, Double> getData() {
        return data;
    }

    public boolean isSendToNext() {
        return sendToNext;
    }

    public void setSendToNext(boolean sendToNext) {
        this.sendToNext = sendToNext;
    }

    public long getId() {
        return id;
    }

    public void setId(long id) {
        this.id = id;
    }
}
