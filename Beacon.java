//
//	A simple data structure to represent
//	a beacon received from either the sink or the sources
//
//	Main goal is to keep track of payload and time
//

package embs;

public class Beacon {
    private int payload;
    private long time;
    
    public Beacon(int payload, long time) {
        this.payload = payload;
        this.time = time;
    }
    
    public int getPayload() {
        return payload;
    }
    
    public void setPayload(int payload) {
        this.payload = payload;
    }
    
    public long getTime() {
        return time;
    }
    
    public void setTime(long time) {
        this.time = time;
    }
}
