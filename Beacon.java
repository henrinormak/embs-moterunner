/**
 * @author      Y6332150
 * @version     1.0
 * @since       2013-20-11
 * 
 * A simple data structure to represent beacon frames
 * that are received from the sink for synchronization purposes
 * 
 */

package embs;

public class Beacon {
    private int payload;
    private long time;
    
    /**
     * @param payload	initial sequence number
     * @param time		initial time in absolute milliseconds
     */
    public Beacon(int payload, long time) {
        this.payload = payload;
        this.time = time;
    }
    
    /**
     * @return Payload of the beacon, its' sequence number
     */
    public int getPayload() {
        return payload;
    }
    
    /**
     * @param payload	new sequence number
     */
    public void setPayload(int payload) {
        this.payload = payload;
    }
    
    /**
     * @return Time of the beacon, the time it was received, in absolute milliseconds
     */
    public long getTime() {
        return time;
    }
    
    /**
     * @param time	new timestamp, in absolute milliseconds
     */
    public void setTime(long time) {
        this.time = time;
    }
}
