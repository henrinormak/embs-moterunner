embs-moterunner
===============

EMBS moterunner assessment

Initial implementation
======================

No energy saving, just keep Rx open and forward frames from queues. Keep 3 queues of frames around (in case we can send more than one during the reception phase).

* Keep a limit on the queues to avoid running out of memory (unlikely, but still capping the queues to 5 should be enough, as there will be at least on reception during that time)
* If a frame comes in, place it in a circular FIFO buffer (perhaps make a special class for this?). This will result in a FIFO relay, which drops older frames first.
* When relay receives beacon from sink, store the time and payload number. When another comes in, calculate the time difference and divide by the difference in the payload to figure out t. With two of these packets the start of reception phase can be calculated, but the system is fragile towards clock drift. Ideally an average over the entire synchronisation phase is used as t.
* As t and n never change during the lifetime, we could calculate this once and then figure out any future timings. This assumes we capture n correctly and t is accurate. Might need to allow for two of such cycles to agree on the values (i.e if we receive n = 10 twice as the first in the sequence, then it is likely true).
* Use a tx handler to make the next send (conditional on the queues and how much time it took to complete transmission of the last frame, to avoid slipping out of the reception phase), first frame is scheduled in the rx callback for the final synchronisation beacon (n = 1).

Variables
---------

* `n` FIFO circular buffers of size x, where n == no. sources, x some predefined constant, like 5
* `radio`
* `lastTxTimestamp` for last send (for comparison and predicting if another send can be squeezed in or not)
* `estimateT`, initialise to 500ms (and keep clamped to between 500-1500. Recalculate with every new beacon frame received which has a `payload` higher than the last received frame
* `estimateN`, initialise to 2 (and keep clamped between 2-10), assign whenever we see a beacon with a higher number in `payload`
* `latestBeacon`, latest beacon we have received, updated with each beacon we get

Notes
-----

It might make sense to make a simple data object for the `beacon` type, this would replace the `latestBeacon` and `estimateT`/`estimateN` variables with just two of these objects
Values needed for future reference `time` when received, `payload` i.e the n value and `t` i.e the predicted t for that particular beacon.

The relay does not need any timers, as the timing can be figured out using values we get back from Tx and Rx. Potentially the logic can be made even smarter and thus the radio might be turned off when no incoming data is scheduled for the next x seconds.

Keep track of four timings, one for each channel we have to switch to (an absolute time), this time will be recalculated when the channel session finishes (if a frame is captured or when the session ends without a capture)

Testing
-------
Based on the code samples given with the assessment, create a test SI that logs out all received frames and whether it was within correct phase or not. Also count the number of frames received from each address and log that out when the entire cycle ends (60s in the demo).