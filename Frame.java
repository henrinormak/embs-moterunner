/**
 * @author      Y6332150
 * @version     1.0
 * @since       2013-20-11
 * 
 * A simple data structure to represent frames
 * that are received on the network, allowing
 * for easier manipulation and storing in buffers
 * 
 */

package embs;

public class Frame {
    private int panID;
    private int address;
    private int payload;
    private long time;
    
    /**
     * @param panid		PAN ID of the network this frame is from
     * @param address	address of the source
     * @param payload	initial sequence number
     * @param time		initial time
     */
    public Frame(int panID, int address, int payload, long time) {
		this.panID = panID;
		this.address = address;
        this.payload = payload;
        this.time = time;
    }
    
    /**
     * @return PAN ID of the source of this frame
     */
	public int getPanID() {
		return panID;
	}
	
	/**
	 * @param panID		new PAN ID for the frame
	 */
	public void setPanID(int panID) {
		this.panID = panID;
	}
	
	/**
	 * @return Address of the source node
	 */
	public int getAddress() {
		return address;
	}
	
	/**
	 * @param address	new address for the frame
	 */
	public void setAddress(int address) {
		this.address = address;
	}
    
    /**
     * @return Payload of the beacon, its' sequence number usually
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
     * @return Time of the frame
     */
    public long getTime() {
        return time;
    }
    
    /**
     * @param time	new timestamp
     */
    public void setTime(long time) {
        this.time = time;
    }
}
