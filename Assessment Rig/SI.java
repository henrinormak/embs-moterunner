package embs;
import com.ibm.saguaro.system.*;
import com.ibm.saguaro.logger.*;

public class SI {

    private static Timer  tsend;
    private static Timer  tstart;
    private static Timer  treceive;
    private static Timer  treceiveEnd;
    private static Timer  blinkTimer = new Timer();

    private static byte[] xmit;
    private static long   wait;
    static Radio radio = new Radio();
    private static int n = 3; // number of beacons of sync phase - sample only, assessment will use unknown values
    private static int nc;

    private static int t = 750; // milliseconds between beacons - sample only, assessment will use unknown values

    // settings for sink
    private static byte channel = (byte)0; // channel 11 on IEEE 802.15.4
    private static byte panid = 0x11;
    private static byte address = 0x11;

    private static boolean receiving = false;
    private static int demoLength = 60;

    // Counting correct packets
    private static int inPhasePackets = 0;
    private static int outPhasePackets = 0;
	private static int receptionPhaseCount = 0;

	// Calculating predicted score
	private static int inPhasePacktesAtReception = 0;
	private static int outPhasePacketsAtReception = 0;
	private static int marksForMessages = 0;
	private static int[] sourcesSeen = new int[]{0, 0, 0};
	private final static int MARKS_PER_SOURCE = 5;
	private final static int MARKS_PER_CORRECT_PHASE = 3;
	private final static int MARKS_PER_INCORRECT_MESSAGE = -2;

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

        // Timer that starts the receive period
        treceive = new Timer();
        treceive.setCallback(new TimerEvent(null){
                public void invoke(byte param, long time){
                    SI.startReceive(param, time);
                }
            });

        // Timer that ends the receive period
        treceiveEnd = new Timer();
        treceiveEnd.setCallback(new TimerEvent(null){
                public void invoke(byte param, long time){
                    SI.endReceive(param, time);
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

        // Turn off all LEDs
        LED.setState((byte)0, (byte)0);
        LED.setState((byte)1, (byte)0);
        LED.setState((byte)2, (byte)0);

        Logger.appendString(csr.s2b("Channel, network, address "));
        Logger.appendByte(channel);
        Logger.appendString(csr.s2b(" "));
        Logger.appendByte(panid);
        Logger.appendString(csr.s2b(" "));
        Logger.appendByte(address);
       	Logger.appendString(csr.s2b(" Starting demo in "));
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

        Logger.appendString(csr.s2b("Received frame, len "));
        Logger.appendInt(len);
        Logger.appendString(csr.s2b(" bytes "));
        for (int i = 0; i < len - 11; i++) {
	        Logger.appendHexByte(data[11+i]);
	        Logger.appendString(csr.s2b(" "));
        }
        Logger.flush(Mote.WARN);

        // Mark the source as seen
        int source = Util.get16le(data, 9) - address - 1;
        sourcesSeen[source] = MARKS_PER_SOURCE;

        // add logging code to log out the originating source (for marking)
		// frame received, blink yellow if allowed, red if not
        if (receiving) {
            inPhasePackets = inPhasePackets + 1;
        } else {
            outPhasePackets = outPhasePackets + 1;
        }

        return 0;
    }

    // Called when the predefined demo period passes
    private static void endDemo(byte param, long time) {
        // Stop the radio which means we won't receive any more packets
        radio.close();

        // Stop the restart timer (to avoid sending out any more beacons)
        tstart.cancelAlarm();

        // Turn off all LEDs
        LED.setState((byte)0, (byte)0);
        LED.setState((byte)1, (byte)0);
        LED.setState((byte)2, (byte)0);

        // Depending on values, turn on a suitable LED
        if (inPhasePackets > 0 && outPhasePackets == 0) {
        	LED.setState((byte)1, (byte)1);
        } else if (inPhasePackets > 0 && inPhasePackets > outPhasePackets) {
        	LED.setState((byte)0, (byte)1);
        } else {
        	LED.setState((byte)2, (byte)1);
        }

        // Add up the final score
        int score = marksForMessages;
        for (int i = 0; i < 3; i++) {
	        score += sourcesSeen[i];
        }

        // Log out the results
        Logger.appendString(csr.s2b("Demo ended - Correct frames "));
        Logger.appendInt(inPhasePackets);
        Logger.appendString(csr.s2b(", incorrect frames "));
        Logger.appendInt(outPhasePackets);
        Logger.appendString(csr.s2b(". Total phases "));
        Logger.appendInt(receptionPhaseCount);
        Logger.appendString(csr.s2b(" FINAL SCORE "));
        Logger.appendInt(score);
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

			// schedule end of receive
			treceiveEnd.setAlarmBySpan(wait);

            // Schedule the next sync phase
            tstart.setAlarmBySpan(6*wait);
        }
    }


	public static void startReceive(byte param, long time) {
		// Store current state for marking
		inPhasePacktesAtReception = inPhasePackets;
		outPhasePacketsAtReception = outPhasePackets;

	    // start receiving for t
        SI.blinkLEDAtIndex(0, t);
		receiving = true;
		receptionPhaseCount++;
	}

	public static void endReceive(byte param, long time) {
		receiving = false;

		// Calculate points
		if ((inPhasePackets - inPhasePacktesAtReception) > 0) {
			marksForMessages += MARKS_PER_CORRECT_PHASE;
		}

		int diff;
		if ((diff = outPhasePackets - outPhasePacketsAtReception) > 0) {
			marksForMessages += (diff * MARKS_PER_INCORRECT_MESSAGE);
		}
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
