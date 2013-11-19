package embs;

import com.ibm.saguaro.system.*;


public class SO3 {

    private static Timer  tsend;
    private static byte[] xmit;
    private static long   xmitDelay;
    private static boolean light=false;
    
    private static byte panid = 0x14;
    private static byte address = 0x14;
    private static byte ch = (byte)3; // channel 14 on IEEE 802.15.4
    private static long interval = 8100;
    
    
    
    static Radio radio = new Radio();

    static {
        // Open the default radio and set channel
        radio.open(Radio.DID, null, 0, 0);
        radio.setChannel(ch);
        
        // Set the PAN ID and the short address
        radio.setPanId(panid, false);
        radio.setShortAddr(address);

        // Prepare beacon frame with source and destination addressing
        xmit = new byte[12];
        xmit[0] = Radio.FCF_BEACON;
        xmit[1] = Radio.FCA_SRC_SADDR|Radio.FCA_DST_SADDR;
        Util.set16le(xmit, 3, panid); // destination PAN address 
        Util.set16le(xmit, 5, 0xFFFF); // broadcast address 
        Util.set16le(xmit, 7, panid); // own PAN address 
        Util.set16le(xmit, 9, address); // own short address 
        
        xmit[11] = 0x00;


        // Setup a periodic timer callback for transmissions
        tsend = new Timer();
        tsend.setCallback(new TimerEvent(null){
                public void invoke(byte param, long time){
                    SO3.periodicSend(param, time);
                }
            });
        // Convert the periodic delay from seconds to platform ticks
        xmitDelay = Time.toTickSpan(Time.MILLISECS, interval);
        // Start the timer
        tsend.setAlarmBySpan(xmitDelay);
    }


    // Called on a timer alarm
    public static void periodicSend(byte param, long time) {
        
        // blink yellow LED
        if(light){
         	LED.setState((byte)0, (byte)1);
        }
        else{
        	LED.setState((byte)0, (byte)0);
		}
		light=!light;
        
        // increment payload
        xmit[11]++;
        
        // send the message
        radio.transmit(Device.ASAP|Radio.TXMODE_CCA, xmit, 0, 12, 0);
        // Setup a new alarm
        tsend.setAlarmBySpan(xmitDelay);
    }

}