package embs;

import com.ibm.saguaro.system.*;
import com.ibm.saguaro.logger.*;

public class Relay {
	
	static Radio radio = new Radio();
	
	static {
        // Open the default radio
        radio.open(Radio.DID, null, 0, 0);
                
        // Set the address of the relay to be out of range of the source and the sink
        radio.setShortAddr(address);
	}
}
