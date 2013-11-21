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
import embs.Session;
import embs.SessionStack;

public class Relay {
    
	/**
	 * Constants
	 */
	
	/**
	 * Bounds specified by the spec, used for sanity checking
	 */
    private final static long BEACON_MIN_TIME = 500L;
    private final static long BEACON_MAX_TIME = 1500L;
    
    /**
     * Timing constants, allowing some leeway in our calculations 
     * and helping with clock drift
     */
    private final static long TIMING_BUFFER_MS = 100L;
    private final static long CHANNEL_DURATION = 200L;	// Amount of time we want to ideally spend listening to a channel
    
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
    private final static int SYNC_PHASES_REQUIRED = 2;
    
    /**
     * Variables
     */
    
    /**
     * Timing variables
     * Keep a timer for each channel that is responsible
     *  for trying to switch the system to that channel 
     */
    private final static long[] channelPeriods = new long[]{4000L /* adjusted after sync */, 5500L, 6900L, 8100L};
    private final static long[] channelDurations = new long[]{1500L /* adjusted after sync */, CHANNEL_DURATION, CHANNEL_DURATION, CHANNEL_DURATION};
    private final static Timer[] channelTimers = new Timer[]{new Timer(), new Timer(), new Timer(), new Timer()};
    
    /**
     * Session management
     * The bottom of the stack is for the "discovery" of
     * channels, this is the time where we aim to determine
     * the exact timing for each channel
     */
    private static SessionStack sessionStack = new SessionStack(10);
    private static Timer popTimer = new Timer(); // a timer we use to end sessions
    
    /**
     * Transmissions are scheduled independant of the channel switching
     * however, it does depend on CHANNEL_SINK being triggered before,
     * which ideally should happen with a TIMING_BUFFER_MS to spare
     */
    private static Timer transmissionTimer = new Timer();
    
    static Radio radio = new Radio();
    
    /**
     * Sync phases we have seen this far
     */
    private static int syncPhasesSeen = 0;

    /**
     * Sync info from the sink, store the last beacon and the estimation
     * for the properties of the sink
     */
    private static Beacon latestSinkBeacon = new Beacon(0, 0);
    /**
     * Initialise the estimate to be invalid, our logic trusts the estimate as long as it is in within given spec range
     * thus it will become trusted after a few sync frames have been received and will be affirmed by future frames
     */
    private static Beacon estimatedSinkBeacon = new Beacon(0, 0);
        
    static {
        // Configure the radio
        // Open the default radio
        radio.open(Radio.DID, null, 0, 0);
        
        // Keep the PAN ID as broadcast, this way we only have to change the channel
        radio.setPanId(Radio.PAN_BROADCAST, false);
        
        // Rx callback
        radio.setRxHandler(new DevCallback(null) {
            public int invoke(int flags, byte[] data, int len, int info, long time) {
                return Relay.onReceive(flags, data, len, info, time);
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
        long longDuration = Time.toTickSpan(Time.SECONDS, 600);
        long startTime = Time.currentTicks();
        
        Logger.appendString(csr.s2b("Long duration "));
        Logger.appendLong(longDuration);
        Logger.flush(Mote.WARN);
        
        sessionStack.push(new Session(CHANNEL_OFF, longDuration));
        sessionStack.push(new Session(CHANNEL_SOURCE_3, longDuration));
        sessionStack.push(new Session(CHANNEL_SOURCE_2, longDuration));
        sessionStack.push(new Session(CHANNEL_SOURCE_1, longDuration));
        Relay.pushSession(new Session(CHANNEL_SINK, longDuration));
        
        // DEBUGGING        
        Logger.appendString(csr.s2b("Initialising estimate: n = "));
        Logger.appendInt(estimatedSinkBeacon.getPayload());
        Logger.appendString(csr.s2b(" t = "));
        Logger.appendLong(estimatedSinkBeacon.getTime());
        Logger.flush(Mote.WARN);
        
        // Turn on the LEDs to signify that we are operational
        LED.setState((byte)0, (byte)1);
        LED.setState((byte)1, (byte)1);
        LED.setState((byte)2, (byte)1);
    }
    
    /**
     * Radio reception
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
    	
    	Logger.appendString(csr.s2b("Data received"));
    	Logger.flush(Mote.WARN);
    	
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
        int n = (int)data[11];
        long currentTime = Time.currentTime(Time.MILLISECS);
        long currentEstimate = estimatedSinkBeacon.getTime();
        
        // The estimated n is simply the highest n value we have seen thus far
        if (n > estimatedSinkBeacon.getPayload()) {
            estimatedSinkBeacon.setPayload(n);
        }
        
        // If this package is part of the same sequence (or likely to be part of the same sequence)
        if (n < latestSinkBeacon.getPayload()) {
            // Figure out the delta from the last beacon
            long deltaT = currentTime - latestSinkBeacon.getTime();
            long packetT = deltaT / (latestSinkBeacon.getPayload() - n);
            
            if (currentEstimate >= Relay.BEACON_MIN_TIME && currentEstimate <= Relay.BEACON_MAX_TIME) {
                // New time is a balanced avg of the existing time and the newest estimate
                long predictedT = (packetT * 3) / 5 + (estimatedSinkBeacon.getTime() * 2) / 5;
                estimatedSinkBeacon.setTime(predictedT);
            } else {
                // Likely second beacon frame we are seeing
                estimatedSinkBeacon.setTime(packetT);
            }
        } else {
            syncPhasesSeen = syncPhasesSeen + 1;
        }
        
        if (n == 1) {
            // Last frame of the sync phase, start transmitting (if our estimate is trustworthy)
            // Also schedule the next sync phase if we have not seen enough sync phases
            currentEstimate = estimatedSinkBeacon.getTime();
            
            if (currentEstimate >= Relay.BEACON_MIN_TIME && currentEstimate <= Relay.BEACON_MAX_TIME) {
                Timer sinkTimer = channelTimers[CHANNEL_SINK];
                
                // Update the period we predict for the sink
                // 6 = 1 reception + 5 sleep
                channelPeriods[CHANNEL_SINK] = currentEstimate * (6 + estimatedSinkBeacon.getPayload());
				
				// Calculate the duration of the channel and the next time we have to open it
                long timeTilNext = channelPeriods[CHANNEL_SINK];
                long duration = currentEstimate;
                if (syncPhasesSeen < SYNC_PHASES_REQUIRED) {
					timeTilNext = currentEstimate * 6;
					duration += currentEstimate * estimatedSinkBeacon.getPayload();
				}
				                
                sinkTimer.setAlarmBySpan(Time.toTickSpan(Time.MILLISECS, timeTilNext));
                channelDurations[CHANNEL_SINK] = duration;

                // Start transmitting in t (as this is the last n)
                transmissionTimer.setAlarmBySpan(Time.toTickSpan(Time.MILLISECS, currentEstimate));
                
                // Re-schedule the session end (in case our estimates improved or in case this was the first sync)
                popTimer.setAlarmBySpan(Time.toTickSpan(Time.MILLISECS, 2 * currentEstimate));
            }
        }
        
        // DEBUGGING        
        Logger.appendString(csr.s2b("Sync frame received, updated estimate: n = "));
        Logger.appendInt(estimatedSinkBeacon.getPayload());
        Logger.appendString(csr.s2b(" t = "));
        Logger.appendLong(estimatedSinkBeacon.getTime());
        Logger.flush(Mote.WARN);
                
        // Move the latest beacon up
        latestSinkBeacon.setTime(currentTime);
        latestSinkBeacon.setPayload(n);
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
	    Logger.appendString(csr.s2b("Received frame "));
	    Logger.appendByte(Relay.getChannel());
	    Logger.flush(Mote.WARN);
	    
	    int index = (int)Relay.getChannel();
	    
	    // Update the period/duration of the given timer
	    channelDurations[index] = CHANNEL_DURATION;
		
		// We can immediately reschedule the timer as well (as we don't know for how long we have been listening to this channel)
		Timer timer = channelTimers[index];
		timer.setAlarmBySpan(Time.toTickSpan(Time.MILLISECS, channelPeriods[index] - TIMING_BUFFER_MS));
		
		// TODO: Store the frame for transmission
		
		// Terminate the session immediately
		popTimer.setAlarmBySpan(0);
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
        Relay.transmitBySpan(Time.toTickSpan(Time.MILLISECS, estimatedSinkBeacon.getTime() - TIMING_BUFFER_MS));
    }
    
    /**
     * Transmit any existing data until either the deadline or there is no data remaining
     * @param tickSpan	relative deadline in ticks
     */
    private static void transmitBySpan(long tickSpan) {
        long currentTick = Time.currentTicks();
        long endTick = currentTick + tickSpan;
        
/*         while (Time.currentTicks() < endTick) { */
            // Add a test for whether there is something to send or not
	        // Because if there is nothing to send we are better off listening
	        // to other channels than wasting our time here
			// Put together a frame to send DEBUGGING
			byte[] xmit = new byte[12];
	        xmit[0] = Radio.FCF_BEACON;
	        xmit[1] = Radio.FCA_SRC_SADDR|Radio.FCA_DST_SADDR;
	        Util.set16le(xmit, 3, 0x11); // destination PAN address 
	        Util.set16le(xmit, 5, 0x11); // broadcast address 
	        Util.set16le(xmit, 7, 0x11); // own PAN address 
	        Util.set16le(xmit, 9, 0x15); // own short address 
	        xmit[11] = (byte)0;
	        
		    radio.transmit(Device.ASAP|Radio.TXMODE_POWER_MAX, xmit, 0, 12, 0);
/*         } */
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
		popTimer.setAlarmTime(session.getEndTime());
    }
    
    /**
     * Push a new session, starts the session and schedules its' end
     * @param session
     */
    private static void pushSession(Session session) {
	    sessionStack.push(session);
	    
	    // Change the channel and schedule the end
	    Relay.setChannel(session.getChannel());    
	    popTimer.setAlarmTime(session.getEndTime());
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
        	Relay.pushSession(new Session(param, Time.toTickSpan(Time.MILLISECS, channelDurations[index])));
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
            radio.startRx(Device.ASAP, 0, Time.currentTicks()+0x7FFFFFFF);
            
            // DEBUGGING        
            Logger.appendString(csr.s2b("Radio channel set "));
            Logger.appendByte(radio.getChannel());
            Logger.flush(Mote.WARN);
        }
    }
}
