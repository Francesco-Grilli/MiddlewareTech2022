package Demo;

import java.io.Serial;
import java.io.Serializable;

public class MsgConfirm implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;
    private long id;

    public MsgConfirm(long id){
        this.id = id;
    }

    public long getId() {
        return id;
    }
}
