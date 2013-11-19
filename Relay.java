package embs;

import com.ibm.saguaro.system.*;
import com.ibm.saguaro.logger.*;

public class Relay {
	
	// Constants
	// Bounds specified by the assessment
	private final static int BEACON_MIN_PAYLOAD = 2;
	private final static int BEACON_MAX_PAYLOAD = 10;
	private final static long BEACON_MIN_TIME = 500L;
	private final static long BEACON_MAX_TIME = 1500L;
	
	// List of channels
	private final static byte CHANNEL_SINK = (byte)5;
	private final static byte CHANNEL_SOURCE_1 = (byte)1;
	private final static byte CHANNEL_SOURCE_2 = (byte)2;
	private final static byte CHANNEL_SOURCE_3 = (byte)3;
	
	// Variables
	static Radio radio = new Radio();
	
	// Latest info from the sink
	private static Beacon latestSinkBeacon = new Beacon(0, 0);
	// Predicted behavior of the sink, payload represents n and time t
	// Initialise the estimates to be the tightest ones given
	private static Beacon estimatedSinkBeacon = new Beacon(Relay.BEACON_MIN_PAYLOAD, Relay.BEACON_MIN_TIME);
	
	static {
        // Open the default radio
        radio.open(Radio.DID, null, 0, 0);
        
        // Configure the radio
        // Keep the PAN ID as broadcast, this way we only have to change the channel
        radio.setPanId(Radio.PAN_BROADCAST, false);
        
        // Channel, we have to keep changing this to communicate with each channel
        // Start up by listening to the sink (to sync up and get an estimate for the first send phase)
        radio.setChannel(CHANNEL_SINK);
        
        // Rx callback
        radio.setRxHandler(new DevCallback(null){
            public int invoke (int flags, byte[] data, int len, int info, long time) {
                return  Relay.onReceive(flags, data, len, info, time);
            }
        });
        
        radio.startRx(Device.ASAP, 0, Time.currentTicks()+0x7FFFFFFF);
        
        Logger.appendString(csr.s2b("Radio started"));
        Logger.flush(Mote.WARN);
        
		Logger.appendString(csr.s2b("Initialising estimate: n = "));
		Logger.appendInt(estimatedSinkBeacon.getPayload());
		Logger.appendString(csr.s2b(" t = "));
		Logger.appendLong(Relay.BEACON_MAX_TIME);
		Logger.flush(Mote.WARN);
	}
	
	private static int onReceive(int flags, byte[] data, int len, int info, long time) {
		if (data == null) {
			// Reception ended, restart the radio
	        radio.startRx(Device.ASAP, 0, Time.currentTicks()+0x7FFFFFFF);
		} else {
			int channel = (int)radio.getChannel();
			if (channel == Relay.CHANNEL_SINK) {
				int n = (int)data[11];
				long currentTime = Time.currentTime(Time.MILLISECS);
				
				// The estimated n is simply the highest n value we have seen thus far
				if (n > estimatedSinkBeacon.getPayload()) {
					Logger.appendInt(estimatedSinkBeacon.getPayload());
					Logger.flush(Mote.WARN);
					estimatedSinkBeacon.setPayload(n);
				}
				
				// If this package is part of the same sequence (or likely to be part of the same sequence)
				if (n < latestSinkBeacon.getPayload()) {
					// Figure out the delta from the last beacon
					long deltaT = currentTime - latestSinkBeacon.getTime();
					long packetT = deltaT / (latestSinkBeacon.getPayload() - n);
					
					if (estimatedSinkBeacon.getTime() >= Relay.BEACON_MIN_PAYLOAD) {
						// New time is the avg of the existing estimate and the current estimate (which in turn is dependant on the frames we have just seen)
						long predictedT = (packetT + estimatedSinkBeacon.getTime()) / 2;
						estimatedSinkBeacon.setTime(predictedT);
					} else {
						// Likely second beacon frame we are seeing
						estimatedSinkBeacon.setTime(packetT);
					}
				}
				
				// Move the latest beacon up
				latestSinkBeacon.setTime(currentTime);
				latestSinkBeacon.setPayload(n);
								
				Logger.appendString(csr.s2b("Updated current estimates to: n = "));
				Logger.appendInt(estimatedSinkBeacon.getPayload());
				Logger.appendString(csr.s2b(" t = "));
				Logger.appendLong(estimatedSinkBeacon.getTime());
				Logger.flush(Mote.WARN);
			}
		}
		
		return 0;
	}
}
