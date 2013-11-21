/**
 * @author      Y6332150
 * @version     1.0
 * @since       2013-20-11
 * 
 * A simple data structure to represent
 * a session - the time spent on a single channel
 * 
 * Purpose is to allow for a smoother transition
 * from one channel to the other, by keeping
 * track of when the channel is supposed to
 * be released
 * 
 */

package embs;
import com.ibm.saguaro.system.*;

public class Session {
	/**
	 * Channel the session is tied to
	 */
	private byte channel;
	
	/**
	 * Duration of the session, in milliseconds
	 */
	private long duration;
	
	/**
	 * Start of the session, in absolute milliseconds
	 */
	private long startTime;
	
	/**
	 * @param channel	channel to tie this session to
	 * @param duration	intended duration of the session, used to calculate the end time
	 */
	public Session(byte channel, long duration) {
		this.channel = channel;
		this.duration = duration;
		this.startTime = Time.currentTime(Time.MILLISECS);
	}
	
	/**
	 * @return channel for this session
	 */
	public byte getChannel() {
		return channel;
	}
	
	/**
	 * @return duration of this session
	 */
	public long getDuration() {
		return duration;
	}
		
	/**
	 * @return start time of this session, which is set automatically in the constructor
	 */
	public long getStartTime() {
		return startTime;
	}
}
