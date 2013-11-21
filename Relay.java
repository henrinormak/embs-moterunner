/**
 * @author      Y6332150
 * @version     1.0
 * @since       2013-20-11
 * 
 * The main relay class, responsible for scanning the sources
 * and forwarding frames on to the sink
 * 
 * For future reference, changing the number of channels would have to involve adding
 * some new constants and changing the value of CHANNEL_COUNT
 * 
 * It might also involve adding some more timers and components to arrays
 * channelPeriods and channelDurations
 * 
 * The scheduling works based on a simple priority setting, the lower the channel
 * number, the higher the priority. The relay starts by syncing with the sink
 * it then "discovers" channels in the order of their priority after which
 * the timing is handled by the timers and the predefined periods
 * 
 */

package embs;

import com.ibm.saguaro.system.*;
import com.ibm.saguaro.logger.*;
import embs.Frame;
import embs.FrameBuffer;
import embs.Session;
import embs.SessionStack;

public class Relay {
	
	/**
	 * Constants
	 */
	
	/**
	 * Bounds specified by the spec
	 */
    private final static long BEACON_MIN_TIME = Time.toTickSpan(Time.MILLISECS, 500L);
    private final static long BEACON_MAX_TIME = Time.toTickSpan(Time.MILLISECS, 1500L);
    private final static byte CHANNEL_START_PAN_ID = 0x11;
    
    /**
     * Timing constants, allowing some leeway in our calculations 
     * and helping with clock drift
     */
    private final static long TIMING_BUFFER_MS = 100L;
    private final static long CHANNEL_DURATION = 200L;	// Amount of time we want to ideally spend listening to a channel
    private final static long CHANNEL_INDEFINITE_DURATION = -1L;
    
    /**
     * List of channels, higher priority channels are allowed 
     * to pre-empt the lower priority ones
     */
    private final static byte CHANNEL_OFF = (byte)-1;   // Use this to turn off the radio rx
    private final static byte CHANNEL_SINK = (byte)0;
    private final static byte CHANNEL_SOURCE_1 = (byte)1;
    private final static byte CHANNEL_SOURCE_2 = (byte)2;
    private final static byte CHANNEL_SOURCE_3 = (byte)3;
    private final static int CHANNEL_COUNT = 4;
    
    /**
     * Sync phases we require the system to look at, after these only the transmission phase is scheduled
     */
    private final static int SYNC_PHASES_REQUIRED = 1;
    
    /**
     * Variables
     */
     
    private static Radio radio = new Radio();
    private static int syncPhasesSeen = 0;
    private static SessionStack sessionStack = new SessionStack(10);
    private static Timer popTimer = new Timer(); // a timer we use to end sessions
    private static FrameBuffer frameBuffer = new FrameBuffer(5);
    
    /**
     * Timing variables
     * Keep a timer for each channel that is responsible
     *  for trying to switch the system to that channel 
     */
    private final static long[] channelPeriods = new long[]{4000L /* adjusted after sync */, 5500L, 6900L, 8100L};
    private final static long[] channelDurations = new long[]{1500L /* adjusted after sync */, CHANNEL_DURATION, CHANNEL_DURATION, CHANNEL_DURATION};
    private final static Timer[] channelTimers = new Timer[]{new Timer(), new Timer(), new Timer(), new Timer()};
    
    /**
     * Transmissions are scheduled independant of the channel switching
     * however, it does depend on CHANNEL_SINK being triggered before,
     * which ideally should happen with a TIMING_BUFFER_MS to spare
     */
    private static Timer transmissionTimer = new Timer();
    private static long transmissionDeadline = 0;
    private static byte[] transmissionFrame = new byte[12];
    
    /**
     * Sync info from the sink, store the last frame and the estimation
     * for the properties of the sink
     */
    private static Frame latestSinkFrame = new Frame((byte)0, (byte)0, 0, 0);
    /**
     * Initialise the estimate to be invalid, our logic trusts the estimate as long as it is in within given spec range
     * thus it will become trusted after a few sync frames have been received and will be affirmed by future frames
     */
    private static Frame estimatedSinkFrame = new Frame((byte)0, (byte)0, 0, 0);
        
    static {
    	// Pretune the transmission frame we use, payload and addressing are figure out when sending
		// this allows some flexibility when it comes to source addresses and the sink address
    	transmissionFrame[0] = Radio.FCF_BEACON;
    	transmissionFrame[1] = Radio.FCA_SRC_SADDR|Radio.FCA_DST_SADDR;

        // Configure the radio
        // Open the default radio
        radio.open(Radio.DID, null, 0, 0);
        radio.setShortAddr(Radio.SADDR_BROADCAST);

        // Rx callback
        radio.setRxHandler(new DevCallback(null) {
            public int invoke(int flags, byte[] data, int len, int info, long time) {
                return Relay.onReceive(flags, data, len, info, time);
            }
        });
        
        radio.setTxHandler(new DevCallback(null) {
            public int invoke(int flags, byte[] data, int len, int info, long time) {
                return Relay.onTransmit(flags, data, len, info, time);
            }
        });
                
        // Timers
        // Pop timer, responsible for ending sessions by popping them off the stack
        popTimer.setCallback(new TimerEvent(null) {
	    	public void invoke (byte param, long time){
		    	Relay.popSession(param, time);
	    	} 
        });
        
        // Transmission timer, responsible for initiating the transmission phase
        // assumes the channel is set correctly by the channel timer
        transmissionTimer.setCallback(new TimerEvent(null){
	    	public void invoke (byte param, long time){
		    	Relay.onScheduleTransmit(param, time);
	    	} 
        });
        
        // Source/sink timers
        for (int i = 0; i < CHANNEL_COUNT; i++) {
            Timer timer = channelTimers[i];
            timer.setCallback(new TimerEvent(null){
            	public void invoke(byte param, long time){
            	    Relay.onChannelTimer(param, time);
				}
			});
            timer.setParam((byte)i);
        }
        
        // Bottom of the session stack
        // This is the 'discovery' part for each channel
        // These sessions are never to be popped, unless by the channel itself
        // when either sync completes or a frame is received        
        sessionStack.push(new Session(CHANNEL_OFF, CHANNEL_INDEFINITE_DURATION));
        sessionStack.push(new Session(CHANNEL_SOURCE_3, CHANNEL_INDEFINITE_DURATION));
        sessionStack.push(new Session(CHANNEL_SOURCE_2, CHANNEL_INDEFINITE_DURATION));
        sessionStack.push(new Session(CHANNEL_SOURCE_1, CHANNEL_INDEFINITE_DURATION));
        Relay.pushSession(new Session(CHANNEL_SINK, CHANNEL_INDEFINITE_DURATION));
        
        // DEBUGGING        
        Logger.appendString(csr.s2b("Initialising estimate: n = "));
        Logger.appendInt(estimatedSinkFrame.getPayload());
        Logger.appendString(csr.s2b(" t = "));
        Logger.appendLong(estimatedSinkFrame.getTime());
        Logger.flush(Mote.WARN);
    }
    
    
    /**
     * Radio callbacks
     */
    
    /**
     * Radio reception, receiving data, including both the sync frames and source data
     * @param flags
     * @param data
     * @param len
     * @param info
     * @param time
     * @return status
     */
    private static int onReceive(int flags, byte[] data, int len, int info, long time) {
    	if (data == null)
    		return 0;
    	    	
        byte channel = Relay.getChannel();
        if (channel == CHANNEL_SINK) {
            Relay.onSinkReceive(flags, data, len, info, time);
        } else if (channel == CHANNEL_SOURCE_1 || channel == CHANNEL_SOURCE_2 || channel == CHANNEL_SOURCE_3) {
            Relay.onSourceReceive(flags, data, len, info, time);
        }
        
        return 0;
    }
    
    /**
     * Sub routine for handling data from the sink
     * @param flags
     * @param data
     * @param len
     * @param info
     * @param time
     */
    private static void onSinkReceive(int flags, byte[] data, int len, int info, long time) {
        // SINK node, synch phase
        // Read out the values from the data
        byte fca = data[1];
        int srcPanID = 0;
        int srcAddr = 0;
        int payload = 0;
        
        if ((fca & Radio.FCA_DST_SADDR) == Radio.FCA_DST_SADDR) {
	        srcPanID = Util.get16le(data, 7);
	        srcAddr = Util.get16le(data, 9);
	        payload = (int)data[11];
        } else {
	        srcPanID = Util.get16le(data, 3);
	        srcAddr = Util.get16le(data, 5);
	        payload = (int)data[7];
        }   
        
        // The estimated n is simply the highest n value we have seen thus far
        if (payload > estimatedSinkFrame.getPayload()) {
            estimatedSinkFrame.setPayload(payload);
        }
        
        // The source address and PAN ID are the latest we have seen, so always update
        estimatedSinkFrame.setPanID(srcPanID);
        estimatedSinkFrame.setAddress(srcAddr);
        
        // If this package is part of the same sequence (or likely to be part of the same sequence)
        if (payload < latestSinkFrame.getPayload()) {
            // Figure out the delta from the last frame
            long currentEstimate = estimatedSinkFrame.getTime();
            long deltaT = time - latestSinkFrame.getTime();
            long packetT = deltaT / (latestSinkFrame.getPayload() - payload);
            
            // Store the new estimated time
            estimatedSinkFrame.setTime(packetT);
        } else {
        	// We have begun a new sync phase, so there is nothing we can update
            syncPhasesSeen = syncPhasesSeen + 1;
        }
        
        if (payload == 1) {
            // Last frame of the sync phase, start transmitting (if our estimate is trustworthy)
            // Also schedule the next sync phase if we have not seen enough sync phases
            long currentEstimate = estimatedSinkFrame.getTime();
            
            if (currentEstimate >= Relay.BEACON_MIN_TIME && currentEstimate <= Relay.BEACON_MAX_TIME) {
                Timer sinkTimer = channelTimers[CHANNEL_SINK];
                
                // Update the period we predict for the sink
                // 6 = 1 reception + 5 sleep
                long period = currentEstimate * (6 + estimatedSinkFrame.getPayload());
				
				// Calculate the duration of the channel and the next time we have to open it
                long timeTilNext = period + currentEstimate - Time.toTickSpan(Time.MILLISECS, TIMING_BUFFER_MS);
                long duration = currentEstimate;
                if (syncPhasesSeen < SYNC_PHASES_REQUIRED) {
					timeTilNext = currentEstimate * 7 - Time.toTickSpan(Time.MILLISECS, TIMING_BUFFER_MS);
					duration += currentEstimate * estimatedSinkFrame.getPayload();
				}
				
				channelPeriods[CHANNEL_SINK] = Time.fromTickSpan(Time.MILLISECS, period);
				channelDurations[CHANNEL_SINK] = Time.fromTickSpan(Time.MILLISECS, duration);
				
				Logger.appendString(csr.s2b("Update sink period "));
				Logger.appendLong(channelPeriods[CHANNEL_SINK]);
				Logger.flush(Mote.WARN);
				
                sinkTimer.setAlarmBySpan(timeTilNext);

                // Start transmitting in t (as this is the last n)
                // Adding t puts us well into the reception phase, which should avoid sending
                // frames too early
                transmissionTimer.setAlarmBySpan(currentEstimate);
                
                // Re-schedule the session end (in case our estimates improved or in case this was the first sync)
                popTimer.setAlarmBySpan(2 * currentEstimate);
            }
        }
                        
        // Update the latest frame reference
        latestSinkFrame.setPanID(srcPanID);
        latestSinkFrame.setAddress(srcAddr);
        latestSinkFrame.setTime(time);
        latestSinkFrame.setPayload(payload);
    }
    
    /**
     * Sub routine for handling data from any source
     * @param flags
     * @param data
     * @param len
     * @param info
     * @param time
     */
    private static void onSourceReceive(int flags, byte[] data, int len, int info, long time) {
    	// Cheat a bit and use the channel of our radio
    	// it would be more correct to read it out of 'data'
	    int index = (int)Relay.getChannel();
		
		// We can immediately reschedule the timer as well (as we don't know for how long we have been listening to this channel)
		Timer timer = channelTimers[index];
		timer.setAlarmBySpan(Time.toTickSpan(Time.MILLISECS, channelPeriods[index] - TIMING_BUFFER_MS));
		
		// Read out the values from the data
        byte fca = data[1];
        int srcPanID = 0;
        int srcAddr = 0;
        int payload = 0;
        
        if ((fca & Radio.FCA_DST_SADDR) == Radio.FCA_DST_SADDR) {
	        srcPanID = Util.get16le(data, 7);
	        srcAddr = Util.get16le(data, 9);
	        payload = (int)data[11];
        } else {
	        srcPanID = Util.get16le(data, 3);
	        srcAddr = Util.get16le(data, 5);
	        payload = (int)data[7];
        }
        
        Frame frame = new Frame(srcPanID, srcAddr, payload, time);
        frameBuffer.push(frame);
		
		// Terminate the session immediately
		popTimer.setAlarmBySpan(0);
    }
    
    private static int onTransmit(int flags, byte[] data, int len, int info, long time) {
    	// Check if we still have time to send more
    	if (Time.currentTicks() < transmissionDeadline) {
	    	// Schedule another send
	    	Relay.transmitFromBuffer();
    	}
    	
    	return 0;
    }
    
    
    /**
     * Transmission
     */
    
    /**
     * Transmission, started by a timer and then done for the estimated t value of the sink
     * @param param
     * @param time
     */
    private static void onScheduleTransmit(byte param, long time) {
        transmissionTimer.setAlarmBySpan(Time.toTickSpan(Time.MILLISECS, channelPeriods[CHANNEL_SINK]));
        transmissionDeadline = Time.currentTicks() + estimatedSinkFrame.getTime() - Time.toTickSpan(Time.MILLISECS, TIMING_BUFFER_MS); 
		
		// Transmit from buffer, the Tx handler takes care of continuing transmission for as long as possible
        Relay.transmitFromBuffer();
    }
    
    private static void transmitFromBuffer() {
	    // If buffer has been emptied, then bail and take the channel with us
	    if (frameBuffer.isEmpty()) {
	        popTimer.setAlarmBySpan(0);
	        return;
        }
        
	    Frame nextFrame = frameBuffer.pull();
		
		// Tx handler will take care of the recursion (i.e sending more frames than 1)
		Util.set16le(transmissionFrame, 3, estimatedSinkFrame.getPanID());
		Util.set16le(transmissionFrame, 5, estimatedSinkFrame.getAddress());
		Util.set16le(transmissionFrame, 7, 0x11);
		Util.set16le(transmissionFrame, 9, 0x11);
		transmissionFrame[11] = (byte)0;//nextFrame.getPayload();
		
		radio.transmit(Device.ASAP|Radio.TXMODE_POWER_MAX, transmissionFrame, 0, 12, 0);
    }
        
        
    /**
     * Session management
     */
    
    /**
     * Pop a session, the way of ending a session
     * @param param
     * @param time
     */
    private static void popSession(byte param, long time) {
    	// An empty stack should never occur (or at least the bottom items have such a long deadline that it is very unlikely to occur)
    	if (sessionStack.isEmpty())
	    	return;
    	
		Session poppedSession = sessionStack.pop();
		
		// Move to the previous channel, schedule the next pop (which might happen immediately)	    	
		Session session = sessionStack.peek();
		Relay.setChannel(session.getChannel());
		
		// Calculate the remaining time in the session
		if (session.getDuration() != CHANNEL_INDEFINITE_DURATION) {
			long span = session.getStartTime() + session.getDuration() - Time.currentTime(Time.MILLISECS);
			if (span < 0)
				span = 0;
			
			popTimer.setAlarmBySpan(Time.toTickSpan(Time.MILLISECS, span));
		}
    }
    
    /**
     * Push a new session, starts the session and schedules its' end
     * @param session
     */
    private static void pushSession(Session session) {
	    sessionStack.push(session);
	    
	    // Change the channel and schedule the end
	    Relay.setChannel(session.getChannel());  
	    
    	// Schedule the end of the session if this is not an indefinite session
	    if (session.getDuration() != CHANNEL_INDEFINITE_DURATION) {
		    popTimer.setAlarmBySpan(Time.toTickSpan(Time.MILLISECS, session.getDuration()));
	    }
    }
    
    /**
     * Channel timer, fired when there is a scheduled channel change. Channel is determined from the timer that fires
     * @param param	param from the timer, treated as the channel number to switch to
     * @param time
     */
    private static void onChannelTimer(byte param, long time) {
        // Schedule next of this timer, remember, callback means that we have seen at least
        // one communication from the channel represented by this timer
        int index = (int)param;
        Timer timer = channelTimers[index];
        timer.setAlarmBySpan(Time.toTickSpan(Time.MILLISECS, channelPeriods[index]));

        // Switch to the channel if we can
        byte currentChannel = Relay.getChannel();
        if (currentChannel >= param || currentChannel == CHANNEL_OFF) {
        	Relay.pushSession(new Session(param, channelDurations[index]));
        }
    }
    
    /**
     * Current channel
     */
    
    /**
     * @return the current channel of the radio or CHANNEL_OFF if radio is not receiving
     */
    private static byte getChannel() {
        if (radio.getState() == Device.S_RXEN)
            return radio.getChannel();
        else
            return CHANNEL_OFF;
    }
    
    /**
     * Setting a new channel, stops Rx, switches channel and the restarts Rx
     * @param channel	new channel to switch to
     */
    private static void setChannel(byte channel) {
    	if (Relay.getChannel() == channel)
	    	return;
	    	    
    	if (radio.getState() == Device.S_RXEN) {
	    	radio.stopRx();
    	}
	    
        if (channel >= 0) {        	
            radio.setChannel(channel);
            radio.setPanId(CHANNEL_START_PAN_ID + channel, false);
            radio.startRx(Device.ASAP, 0, Time.currentTicks() + 0x7FFFFFFF);
        }
    }
}
