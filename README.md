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
* n FIFO circular buffers of size x, where n == no. sources, x some predefined constant, like 5
* radio
* timestamp for last send (for comparison and predicting if another send can be squeezed in or not)
* predictions for t and n, start off with n = 2 and t = 500ms (minimums), n will be assigned whenever a beacon is received with a higher value. t is recalculated with each beacon that is received and that has n lower than the stored last beacon (i.e with first beacon in sequence the value is not recalculated)
* last beacon received (time when it was received and its’ n)
