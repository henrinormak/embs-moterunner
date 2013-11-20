//
//	A simple data structure to represent
//	a session - the time spent on a single channel
//
//	Purpose is to allow for a smoother transition
//	from one channel to the other, by keeping
//	track of when the channel is supposed to
//	be released
//

package embs;

import com.ibm.saguaro.system.*;

public class Session {
	// Channel the session is tied to
	private byte channel;
	
	// Duration of the session, in ticks
	private long duration;
	
	// Start of the session, in absolute ticks
	private long startTime;
		
	public Session(byte channel, long duration) {
		this.channel = channel;
		this.duration = duration;
		this.startTime = Time.currentTicks();
	}
	
	public byte getChannel() {
		return channel;
	}
	
	public long getDuration() {
		return duration;
	}
		
	public long getStartTime() {
		return startTime;
	}
	
	public long getEndTime() {
		return startTime + duration;
	}
}
