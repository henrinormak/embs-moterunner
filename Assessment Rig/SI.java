package embs;
import com.ibm.saguaro.system.*;
import com.ibm.saguaro.logger.*;

public class SI {

    private static Timer  tsend;
    private static Timer  tstart;
    private static Timer  treceive;
    
    private static byte[] xmit;
    private static long   wait;
    static Radio radio = new Radio();
    private static int n = 6; // number of beacons of sync phase - sample only, assessment will use unknown values
    private static int nc;
    
    private static int t = 750; // milliseconds between beacons - sample only, assessment will use unknown values
    
    // settings for sink 
    // TODO: Fix the channel numbering to be the final demo version
    private static byte channel = (byte)0; // channel 11 on IEEE 802.15.4
    private static byte panid = 0x11;
    private static byte address = 0x11;
    
    private static long receptionPhaseEnd;
    private static int demoLength = 60;
    
    // Counting correct packets
    private static int inPhasePackets = 0;
    private static int outPhasePackets = 0;

    static {
        // Open the default radio
        radio.open(Radio.DID, null, 0, 0);
        
        // Set channel 
        radio.setChannel(channel);
        
        // Set the PAN ID and the short address
        radio.setPanId(panid, true);
        radio.setShortAddr(address);

        // Prepare beacon frame with source and destination addressing
        xmit = new byte[12];
        xmit[0] = Radio.FCF_BEACON;
        xmit[1] = Radio.FCA_SRC_SADDR|Radio.FCA_DST_SADDR;
        Util.set16le(xmit, 3, panid); // destination PAN address 
        Util.set16le(xmit, 5, 0xFFFF); // broadcast address 
        Util.set16le(xmit, 7, panid); // own PAN address 
        Util.set16le(xmit, 9, address); // own short address 

        xmit[11] = (byte)n;

		// register delegate for received frames
        radio.setRxHandler(new DevCallback(null){
                public int invoke (int flags, byte[] data, int len, int info, long time) {
                    return  SI.onReceive(flags, data, len, info, time);
                }
            });

        // Setup a periodic timer callback for beacon transmissions
        tsend = new Timer();
        tsend.setCallback(new TimerEvent(null){
                public void invoke(byte param, long time){
                    SI.periodicSend(param, time);
                }
            });
        
        // Setup a periodic timer callback to restart the protocol
        tstart = new Timer();
        tstart.setCallback(new TimerEvent(null){
                public void invoke(byte param, long time){
                    SI.restart(param, time);
                }
            });
            
        // Timer that moves starts the receive period
        treceive = new Timer();
        treceive.setCallback(new TimerEvent(null){
                public void invoke(byte param, long time){
                    SI.startReceive(param, time);
                }
            });
            
        // Convert the periodic delay from ms to platform ticks
        wait = Time.toTickSpan(Time.MILLISECS, t);
        tstart.setAlarmBySpan(Time.toTickSpan(Time.SECONDS, 5)); //starts the protocol 5 seconds after constructing the assembly
        
        // Start receiving indefinitely, debugging to capture any frames received outside of our reception phase
        radio.startRx(Device.ASAP, 0, Time.currentTicks()+0x7FFFFFFF);
        
        // Kill timer
        Timer killTimer = new Timer();
        
        killTimer.setCallback(new TimerEvent(null){
            public void invoke(byte param, long time){
                SI.endDemo(param, time);
            }
        });
        
        killTimer.setAlarmBySpan(Time.toTickSpan(Time.SECONDS, demoLength)); // schedule the end of the cycle, after which nothing will be done
        
       	Logger.appendString(csr.s2b("Starting demo in "));
       	Logger.appendInt(5);
       	Logger.appendString(csr.s2b(" for "));
       	Logger.appendInt(demoLength);
       	Logger.flush(Mote.WARN);
    }

    // Called when a frame is received or at the end of a reception period
    private static int onReceive (int flags, byte[] data, int len, int info, long time) {
        if (data == null) {
            // Restart the radio (debugging)
            radio.startRx(Device.ASAP, 0, Time.currentTicks()+0x7FFFFFFF);
            return 0;
        }

        // add logging code to log out the originating source (for marking)
		// frame received, blink yellow if allowed, red if not
        if (Time.currentTicks() <= receptionPhaseEnd) {
            SI.blinkLEDAtIndex(1, 100);
            inPhasePackets = inPhasePackets + 1;
        } else {
            SI.blinkLEDAtIndex(2, 100);
            outPhasePackets = outPhasePackets + 1;
            
            Logger.appendString(csr.s2b("Out of phase "));
            Logger.appendLong(Time.currentTicks());
            Logger.flush(Mote.WARN);
        }
		        
        return 0;
    }
    
    // Called when the predefined demo period passes
    private static void endDemo(byte param, long time) {
        // Stop the radio which means we won't receive any more packets
        radio.close();
        
        // Stop the restart timer (to avoid sending out any more beacons)
        tstart.cancelAlarm();
        
        // Blink all LEDs for 10s
        SI.blinkLEDAtIndex(0, 10000);
        SI.blinkLEDAtIndex(1, 10000);
        SI.blinkLEDAtIndex(2, 10000);
        
        // Log out the results
        Logger.appendString(csr.s2b("Demo ended - Correct frames "));
        Logger.appendInt(inPhasePackets);
        Logger.appendString(csr.s2b(", incorrect frames "));
        Logger.appendInt(outPhasePackets);
        Logger.flush(Mote.WARN);
    }

    // Called on a timer alarm
    public static void periodicSend(byte param, long time) {
        
        if(nc > 0){
	        // transmit a beacon 
    	    radio.transmit(Device.ASAP|Radio.TXMODE_POWER_MAX, xmit, 0, 12, 0);
        	// program new alarm
        	tsend.setAlarmBySpan(wait);
        	nc--;
        	xmit[11]--;
        }
        else{
        	// Start receive phase
            treceive.setAlarmBySpan(0);
            
            // Schedule the next sync phase
            tstart.setAlarmBySpan(6*wait);
        }
    }
    
	
	public static void startReceive(byte param, long time) {
	    // start receiving for t
        SI.blinkLEDAtIndex(0, t);
		receptionPhaseEnd = Time.currentTicks() + wait;
	}

    // Called on a timer alarm, starts the protocol
    public static void restart(byte param, long time) {
        nc=n;
        xmit[11]=(byte)n;
       	tsend.setAlarmBySpan(0);
    }

    // Blink an LED for given amount of ms
    public static void turnOffLEDAtIndex(byte index, long time) {
        LED.setState((byte)index, (byte)0);
    }
    
    public static void blinkLEDAtIndex(int index, int timeMS) {
        Timer blinkTimer = new Timer();
        blinkTimer.setParam((byte)index);
        blinkTimer.setCallback(new TimerEvent(null){
            public void invoke(byte param, long time){
                turnOffLEDAtIndex(param, time);
            }
        });
        blinkTimer.setAlarmBySpan(Time.toTickSpan(Time.MILLISECS, timeMS));
        
        // Turn on the LED
        LED.setState((byte)index, (byte)1);
    }
}
