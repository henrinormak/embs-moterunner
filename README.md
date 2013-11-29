embs-moterunner
===============

> The Mote Runner on-mote run-time platform is based on a virtual machine tailored from scratch for resource-constraint hardware environments. For this, it introduces a new byte-code language that, besides being compact and efficient, provides native support for reactive programming by means of delegates. Together with the run-time library built on top, Mote Runner provides a purely event-driven and thread-free programming model.

[Mote Runner](http://www.zurich.ibm.com/moterunner/) is IBM's infrastructure platform for wireless sensor networks (WSN), aimed at embedded systems with little to no resources, to monitor and communicate readings from the environment or affect actuators connected to physical motes wirelessly.

This repository contains a simple implementation of a relay node built as part of the Embedded Systems Design and Implementation (EMBS) module at the University of York, designed to forward frames from multiple sources on different channels to one sink node. The nodes communicate using the IEEE 801.15.4 protocol, on top of which sits a further communication protocol to simplify the communication:

1.	Each channel is assumed to contain only one other mote, the channels are prioritised based on their numbers - lower channel number leads to a higher priority.
2.	The PAN ID used by each mote is tied to the channel number they are on, for example channel 0 => PAN ID 0x11, channel 10 => 0x21 and so on.
3.	The sink node should be on the lowest channel of the sources.

A further overview of how the relay operates is provided in `Report/Report_PDF.pdf`.


Implementation
==============

`Relay` contains code for a node that is capable of acting as a relay in a network of nodes. Receiving from multiple channels, priority ordered based on their channel number, and sending to one channel, the sink.
Relay operates in two modes, first it starts by determining the exact timings of the sink and the sources, after which it enters the second phase - periodic event handling for transmission and reception. Code for the other nodes in the network is in the `Assessment Rig` directory.

Relay uses an internal data structure called `Frame` to represent frames received from the sources/sink, it stores frames received from sources in a `FrameBuffer`, a circular buffer with fixed size specified by `Relay`. Additionally, `Relay` uses an internal representation for the period of time it has to spend on a specific channel called `Session`. In order to enforce the priority and offer efficient channel switching the `Session` objects are stored in a `SessionStack`, acting as a FILO queue. The bottom of this stack consists of special `Session` objects, that are never to be popped based on time, these form the discovery phase of `Relay`.

Adding nodes to the list the Relay listens to can be done by following these steps:
1) Adding new constants to `Relay.java`, following the form of `CHANNEL_SOURCE_1` ... `CHANNEL_SOURCE_N`
2) Increment the `CHANNEL_COUNT` constant to match the number of sources
3) Adding new items to the `channelPeriods`, `channelDurations` and `channelTimers` lists to represent the added channels
4) Pushing indefinite `Session` objects to the stack around `Relay.java:167`, note that these sessions should be in priority order on the stack.


Energy efficiency
=================

There are certain aspects to `Relay` that offer ways to save energy. First of them is the `SessionStack` structure. After the discovery of every source and the sink has completed, the stack will contain only one indeterminate `Session` representing CHANNEL_OFF, a special internal constant used to indicate that the radio should be turned off. This means that during the majority of the runtime, the radio will be turned off thus saving energy.

In addition, the signal strength for transmission is determined during the discovery phase based on the RSSI of the sink node frames. This means that the transmission is not as strong in cases where the sink is closer to the `Relay`. The downside to this approach is the fact that the signal strength is determined during the discovery phase and not updated afterwards, meaning the sink is not expected to move in relation to `Relay`. This could potentially be overcome, by increasing the `SYNC_PHASES_REQUIRED` constant in `Relay` to be more than 1. This constant determines how many sync periods from the sink the `Relay` has to process. Note, the discovery phase lasts for exactly 1 of those sync phases, so increasing this number does not necessarily mean a longer discovery phase. It could, however, mean that more frames are dropped as the sink channel has the highest priority and thus pre-empts other channels.


Testing
=======

`Assessment Rig/SI.java` contains two variables, that affect the performance of the `Relay`, `n` and `t` for the number of frames sent during a sync phase and the duration of each of those frames respectively. The `Relay` is able to determine these two values, with `t` being constrained to 500ms <= `t` <= 1500ms, but can be configured to tolerate other values by adjusting the `BEACON_MIN_TIME` and `BEACON_MAX_TIME` constants in `Relay.java`.

In ideal scenarios, the `Relay` is able to forward almost all frames after the initial discovery phase has ended, in case of collisions between two source events, the one with higher priority is chosen (the one which has a lower channel number).
