/*
 Copyright (C) 2011 J. Coliz <maniacbug@ymail.com>
 This program is free software; you can redistribute it and/or
 modify it under the terms of the GNU General Public License
 version 2 as published by the Free Software Foundation.
*/

/** Rewritten for Java by:
 *  Mauro Riva <lemariva@mail.com> <lemariva.com>
 *
 *  Originally written in C/C++ by:
 *      J. Coliz < https://github.com/maniacbug/RF24Network >
 *      and optimized by: TMRh20 < https://github.com/TMRh20/RF24Network >
 *
 * Copyright [2017] [Mauro Riva <lemariva@mail.com> <lemariva.com>]
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 * The above copyright notice and this permission notice shall be
 * included in all copies or substantial portions of the Software.
 *
 * @author Mauro Riva (lemariva.com)
 * @version 0.1 beta
 * @since 27.12.2016
 *
 * This library can be used with Android Things on a RPIv3
 */

package com.lemariva.androidthings.rf24;

import android.util.Log;

import java.io.IOException;

import java.util.HashMap;
import java.util.LinkedList;
import java.util.Map;
import java.util.Queue;
import java.util.Arrays;

class rf24Network {

    private static final String TAG = rf24Network.class.getSimpleName();

    /**
     * Set debugging status!
     */
    private static final boolean debug = false;


    /* Header types range */
    public static final int MIN_USER_DEFINED_HEADER_TYPE = 0;
    public static final int MAX_USER_DEFINED_HEADER_TYPE = 127;

    // ACK Response Types
     /*
     * **Reserved network message types**
     *
     * The network will determine whether to automatically acknowledge payloads based on their general type
     *
     * **User types** (1-127) 1-64 will NOT be acknowledged
     * **System types** (128-255) 192 through 255 will NOT be acknowledged
     *
     * @defgroup DEFINED_TYPES Reserved System Message Types
     *
     * System types can also contain message data.
     *
     * @{
     */

    /**
     * A NETWORK_ADDR_RESPONSE type is utilized to manually route custom messages containing a single RF24Network address
     *
     * Used by RF24Mesh
     *
     * If a node receives a message of this type that is directly addressed to it, it will read the included message, and forward the payload
     * on to the proper recipient.
     * This allows nodes to forward multicast messages to the master node, receive a response, and forward it back to the requester.
     */
    public static final int NETWORK_ADDR_RESPONSE = 128;
    public static final int NETWORK_ADDR_CONFIRM  = 129;

    /**
     * Messages of type NETWORK_PING will be dropped automatically by the recipient. A NETWORK_ACK or automatic radio-ack will indicate to the sender whether the
     * payload was successful. The time it takes to successfully send a NETWORK_PING is the round-trip-time.
     */
    public static final int NETWORK_PING = 130;

    /**
     * External data types are used to define messages that will be passed to an external data system. This allows RF24Network to route and pass any type of data, such
     * as TCP/IP frames, while still being able to utilize standard RF24Network messages etc.
     *
     * **Linux**
     * Linux devices (defined RF24_LINUX) will buffer all data types in the user cache.
     *
     * **Arduino/AVR/Etc:** Data transmitted with the type set to EXTERNAL_DATA_TYPE will not be loaded into the user cache.
     * External systems can extract external data using the following process, while internal data types are cached in the user buffer, and accessed using network.read() :
     * {@code
     * int return_type = network.update();
     * if(return_type == EXTERNAL_DATA_TYPE){
     *     int size = network.frag_ptr->message_size;
     *     memcpy(&myDataBuffer,network.frag_ptr->message_buffer,network.frag_ptr->message_size);
     * }
     * }
     */
    private static final int EXTERNAL_DATA_TYPE = 131;

    /**
     * Messages of this type designate the first of two or more message fragments, and will be re-assembled automatically.
     */
    private static final int NETWORK_FIRST_FRAGMENT = 148;

    /**
     * Messages of this type indicate a fragmented payload with two or more message fragments.
     */
    private static final int NETWORK_MORE_FRAGMENTS = 149;

    /**
     * Messages of this type indicate the last fragment in a sequence of message fragments.
     * Messages of this type do not receive a NETWORK_ACK
     */
    private static final int NETWORK_LAST_FRAGMENT = 150;
    //#define NETWORK_LAST_FRAGMENT 201

    // NO ACK Response Types
    //#define NETWORK_ACK_REQUEST 192

    /**
     * Messages of this type are used internally, to signal the sender that a transmission has been completed.
     * RF24Network does not directly have a built-in transport layer protocol, so message delivery is not 100% guaranteed.<br>
     * Messages can be lost via corrupted dynamic payloads, or a NETWORK_ACK can fail, while the message was actually successful.
     *
     * NETWORK_ACK messages can be utilized as a traffic/flow control mechanism, since transmitting nodes will be forced to wait until
     * the payload is transmitted across the network and acknowledged, before sending additional data.
     *
     * In the event that the transmitting device will be waiting for a direct response, manually sent by the recipient, a NETWORK_ACK is not required. <br>
     * User messages utilizing a 'type' with a decimal value of 64 or less will not be acknowledged across the network via NETWORK_ACK messages.
     */
    private static final int NETWORK_ACK = 193;

    /**
     * Used by RF24Mesh
     *
     * Messages of this type are used with multi-casting , to find active/available nodes.
     * Any node receiving a NETWORK_POLL sent to a multicast address will respond directly to the sender with a blank message, indicating the
     * address of the available node via the header.
     */
    public static final int NETWORK_POLL = 194;

    /**
     * Used by RF24Mesh
     *
     * Messages of this type are used to request information from the master node, generally via a unicast (direct) write.
     * Any (non-master) node receiving a message of this type will manually forward it to the master node using an normal network write.
     */
    public static final int NETWORK_REQ_ADDRESS = 195;
    //#define NETWORK_ADDR_LOOKUP 196
    //#define NETWORK_ADDR_RELEASE 197
    /* @} */

    private static final int NETWORK_MORE_FRAGMENTS_NACK = 200;


    /** Internal defines for handling written payloads */
    private static final int TX_NORMAL = 0;
    private static final int TX_ROUTED = 1;
    private static final int USER_TX_TO_PHYSICAL_ADDRESS = 2;  //no network ACK
    private static final int USER_TX_TO_LOGICAL_ADDRESS = 3;   // network ACK
    private static final int USER_TX_MULTICAST = 4;

    private static final int MAX_FRAME_SIZE = 32;   //Size of individual radio frames
    public static final int FRAME_HEADER_SIZE = 10; //Size of RF24Network frames - data

    private static final int USE_CURRENT_CHANNEL = 255; // Use current radio channel when setting up the network

    /** Internal defines for handling internal payloads - prevents reading additional data from the radio
     * when buffers are full */
    public static final int FLAG_HOLD_INCOMING = 1;
    /** FLAG_BYPASS_HOLDS is mainly for use with RF24Mesh as follows:
     * a: Ensure no data in radio buffers, else exit
     * b: Address is changed to multicast address for renewal
     * c: Holds Cleared (bypass flag is set)
     * d: Address renewal takes place and is set
     * e: Holds Enabled (bypass flag off)
     */
    public static final int FLAG_BYPASS_HOLDS = 2;

    private static final int FLAG_FAST_FRAG = 4;

    private static final int FLAG_NO_POLL = 8;

    /** The size of the main buffer. This is the user-cache, where incoming data is stored.
     * Data is stored using Frames: Header (8-bytes) + Frame_Size (2-bytes) + Data (?-bytes)
     *
     * Note: The MAX_PAYLOAD_SIZE is (MAIN_BUFFER_SIZE - 10), and the result must be divisible by 24.
     */
    private static final int MAIN_BUFFER_SIZE = 144 + 10;
    /** Maximum size of fragmented network frames and fragmentation cache. This MUST BE divisible by 24.
     * Note: Must be a multiple of 24.
     * Note: If used with RF24Ethernet, this value is used to set the buffer sizes.
     */
    private static final int MAX_PAYLOAD_SIZE = MAIN_BUFFER_SIZE-10;

    /* ############################################################## */

    /**< Our parent's node address */
    private int parent_node;
    /**< The pipe our parent uses to listen to us */
    private int parent_pipe;
    /**< The bits which contain signfificant node address information */
    private int node_mask;

//    #if defined ENABLE_NETWORK_STATS
//    static uint32_t nFails;
//    static uint32_t nOK;
//    #endif

    /**
     * Header which is sent with each message
     *
     * The frame put over the air consists of this header and a message
     *
     * Headers are addressed to the appropriate node, and the network forwards them on to their final destination.
     */

    private rf24 radio; /**< Underlying radio driver, provides link/physical layers */
    //#if defined (DUAL_HEAD_RADIO)
    private rf24 radio1; /**< Underlying radio driver, provides link/physical layers */

    //#if defined (RF24NetworkMulticast)
    private byte multicast_level;

    private short node_address; /**< Logical node address of this unit, 1 .. UINT_MAX */
    //const static int frame_size = 32; /**< How large is each frame over the air */
    private int frame_size;

    private int max_frame_payload_size = MAX_FRAME_SIZE - rf24NetworkHeader.sizeOf();

    /* @}*/
    /*
     * @name External Applications/Systems
     *
     *  Interface for External Applications and Systems ( RF24Mesh, RF24Ethernet )
     */
    /* @{*/

    /** The raw system frame buffer of received data. */
    public int[] frame_buffer = new int[MAX_FRAME_SIZE];

    /**
     * Note: This value is automatically assigned based on the node address
     * to reduce errors and increase throughput of the network.
     *
     * Sets the timeout period for individual payloads in milliseconds at staggered intervals.
     * Payloads will be retried automatically until success or timeout
     * Set to 0 to use the normal auto retry period defined by radio.setRetries()
     * Network timeout value
     */

    private int txTimeout;

    /*
     * This only affects payloads that are routed by one or more nodes.
     * This specifies how long to wait for an ack from across the network.
     * Radios sending directly to their parent or children nodes do not
     * utilize this value.
     */

    /**< Timeout for routed payloads */
    public int routeTimeout;

    private long txTime;

    /**
     * Variable to determine whether update() will return after the radio buffers have been emptied (DEFAULT), or
     * whether to return immediately when (most) system types are received.
     *
     * As an example, this is used with RF24Mesh to catch and handle system messages without loading them into the user cache.
     *
     * The following reserved/system message types are handled automatically, and not returned.
     *
     * | System Message Types <br> (Not Returned) |
     * |-----------------------|
     * | NETWORK_ADDR_RESPONSE |
     * | NETWORK_ACK           |
     * | NETWORK_PING          |
     * | NETWORK_POLL <br>(With multicast enabled) |
     * | NETWORK_REQ_ADDRESS   |
     *
     */
    public boolean returnSysMsgs;

    /**
     * Network Flags allow control of data flow
     *
     * Incoming Blocking: If the network user-cache is full, lets radio cache fill up. Radio ACKs are not sent when radio internal cache is full.<br>
     * This behaviour may seem to result in more failed sends, but the payloads would have otherwise been dropped due to the cache being full.<br>
     *
     * | FLAGS | Value | Description |
     * |-------|-------|-------------|
     * |FLAG_HOLD_INCOMING| 1(bit_1) | INTERNAL: Set automatically when a fragmented payload will exceed the available cache |
     * |FLAG_BYPASS_HOLDS| 2(bit_2) | EXTERNAL: Can be used to prevent holds from blocking. Note: Holds are disabled & re-enabled by RF24Mesh when renewing addresses. This will cause data loss if incoming data exceeds the available cache space|
     * |FLAG_FAST_FRAG| 4(bit_3) | INTERNAL: Replaces the fastFragTransfer variable, and allows for faster transfers between directly connected nodes. |
     * |FLAG_NO_POLL| 8(bit_4) | EXTERNAL/USER: Disables NETWORK_POLL responses on a node-by-node basis. |
     *
     */
    public int networkFlags;

    /**
     * Enabling this will allow this node to automatically forward received multicast frames to the next highest
     * multicast level. Duplicate frames are filtered out, so multiple forwarding nodes at the same level should
     * not interfere. Forwarded payloads will also be received.
     * @see #multicastLevel(byte)
     */

    private boolean multicastRelay;

    /**< Space for a small set of frames that need to be delivered to the app layer */
    private Queue <rf24NetworkFrame> frame_queue;

    /**
     * Data with a header type of EXTERNAL_DATA_TYPE will be loaded into a separate queue.
     * The data can be accessed as follows:
     * {@code
     * rf24NetworkFrame f;
     * while(network.external_queue.size() > 0){
     *   f = network.external_queue.peek();
     *   int dataSize = f.message_size;
     *   int[] msg = int[dataSize];
     *   //read the frame message buffer
     *   System.arraycopy(f.message_buffer,0, msg, dataSize);
     *   network.external_queue.remove();
     * }
     * }
     */
    public Queue <rf24NetworkFrame> external_queue;


    private Map<Integer, rf24NetworkFrame> frameFragmentsCache;

    rf24NetworkFrame frag_queue;

    int[] frag_queue_message_buffer = new int[MAX_PAYLOAD_SIZE]; //frame size + 1


    private  boolean dualradio = false;

    /**
     * Construct the network
     *
     * @param _radio The underlying radio driver instance
     *
     */
    public rf24Network(rf24 _radio)
    {
        radio = _radio;

        frame_size = MAX_FRAME_SIZE;
        txTime=0; networkFlags=0; returnSysMsgs=false; multicastRelay=false;
    }



    /**
     * Bring up the network using the current radio frequency/channel.
     * Calling begin brings up the network, and configures the address, which designates the location of the node within RF24Network topology.
     * Note: Node addresses are specified in Octal format, see RF24Network Addressing for more information.
     * Warning: Be sure to 'begin' the radio first.
     *
     * **Example 1:** Begin on current radio channel with address 0 (master node)
     * {@code
     * int channel = 76;
     * network.begin(channel,00);
     * }
     * **Example 2:** Begin with address 01 (child of master)
     * {@code
     * int channel = 76;
     * network.begin(channel, 01);
     * }
     * **Example 3:** Begin with address 011 (child of 01, grandchild of master)
     * {@code
     * int channel = 76;
     * network.begin(channel, 011);
     * }
     * @param _channel the radio channel {@link #radio.setChannel()}
     * @param _node_address The logical address of this node
     *
     */
    public boolean begin(int _channel, short _node_address ) throws IOException {
        if (!is_valid_address(_node_address))
            return false;

        node_address = _node_address;

        if (radio.isConnected() == 0) {
            return false;
        }

        if (!radio.isValid()) {
            return false;
        }

        // Set up the radio the way we want it to look
        if (_channel != USE_CURRENT_CHANNEL) {
            radio.setChannel(_channel);
        }
        //radio.enableDynamicAck();
        radio.setAutoAck(0, false);

        radio.enableDynamicPayloads();

        // Use different retry periods to reduce data collisions
        int retryVar = (((node_address % 6) + 1) * 2) + 3;
        radio.setRetries(retryVar, 5); // max about 85ms per attempt
        txTimeout = 25;
        routeTimeout = txTimeout * 3; // Adjust for max delay per node within a single chain

        if (dualradio) {
            radio1.setChannel(_channel);
            radio1.enableDynamicAck();
            radio1.enableDynamicPayloads();
        }

        // Setup our address helper cache
        setup_address();

        // Open up all listening pipes
        int i = 6;
        while (i-- > 0) {
            radio.openReadingPipe(i, pipe_address(_node_address, (byte)i));
        }
        radio.startListening();

        // initializing message queue
        frame_queue = new LinkedList<rf24NetworkFrame>();
        external_queue = new LinkedList<rf24NetworkFrame>();
        frameFragmentsCache = new HashMap<Integer, rf24NetworkFrame>();

        return true;
    }


    /**
     * Bring up the network using the current radio frequency/channel.
     * Calling begin brings up the network, and configures the address, which designates the location of the node within RF24Network topology.
     * @note Node addresses are specified in Octal format, see <a href=Addressing.html>RF24Network Addressing</a> for more information.
     * @warning Be sure to 'begin' the radio first.
     *
     * **Example 1:** Begin on current radio channel with address 0 (master node)
     * {@code
     * network.begin(00);
     * }
     * **Example 2:** Begin with address 01 (child of master)
     * {@code
     * network.begin(01);
     * }
     * **Example 3:** Begin with address 011 (child of 01, grandchild of master)
     * {@code
     * network.begin(011);
     * }
     *
     * @see #begin(int _channel, short _node_address )
     * @param _node_address The logical address of this node
     *
     */

    public void begin(short _node_address) throws IOException {
        begin(USE_CURRENT_CHANNEL,_node_address);
    }


    /**
     * Main layer loop
     *
     * This function must be called regularly to keep the layer going.  This is where payloads are
     * re-routed, received, and all the action happens.
     *
     *
     * @return Returns the type of the last received payload.
     */

    public int update() throws IOException
    {
        // if there is data ready
        byte pipe_num = 0;
        short returnVal = 0;

        // If bypass is enabled, continue although incoming user data may be dropped
        // Allows system payloads to be read while user cache is full
        // Incoming Hold prevents data from being read from the radio, preventing incoming payloads from being acked

        while ( radio.isValid() && radio.available()) {
            pipe_num = (byte) radio.available(pipe_num);

            if ((frame_size = radio.getDynamicPayloadSize()) < rf24NetworkHeader.sizeOf()) {
                delay(10);
                continue;
            }

            // Dump the payloads until we've gotten everything
            // Fetch the payload, and see if this was the last one.
            radio.read(frame_buffer, frame_size);

            // Read the beginning of the frame as the header
            // TODO: reading payload from buffer implies casting header!
            //rf24NetworkHeader header = (rf24NetworkHeader*)(&frame_buffer);
            rf24NetworkHeader header = new rf24NetworkHeader();
            header.CastMsg(frame_buffer);

            if (debug) Log.i(TAG, "MAC Received on pipe: " + pipe_num + "");
            if (frame_size > 0) {
                if (debug) Log.i(TAG, "FRG Rcv frame size " + frame_size);
            }

            // Throw it away if it's not a valid address
            if (!is_valid_address(header.to_node)) {
                continue;
            }

            returnVal = header.type;

            // Is this for us?
            if (header.to_node == node_address) {

                if (header.type == NETWORK_PING) {
                    continue;
                }
                if (header.type == NETWORK_ADDR_RESPONSE) {
                    short requester = 04444;
                    if (requester != node_address) {
                        header.to_node = requester;

                        header.ChangeHeader(frame_buffer);      // copying changes to the frame_buffer

                        write(header.to_node, (byte) USER_TX_TO_PHYSICAL_ADDRESS);
                        delay(10);
                        write(header.to_node, (byte) USER_TX_TO_PHYSICAL_ADDRESS);
                        //printf("Fwd add response to 0%o\n",requester);
                        continue;
                    }
                }
                if (header.type == NETWORK_REQ_ADDRESS && node_address != 0) {
                    //printf("Fwd add req to 0\n");
                    header.from_node = (short) node_address;
                    header.to_node = 0;

                    header.ChangeHeader(frame_buffer);      // copying changes to the frame_buffer

                    write(header.to_node, (byte)TX_NORMAL);
                    continue;
                }

                if ((returnSysMsgs && header.type > 127) || header.type == NETWORK_ACK) {
                    if (debug) Log.i(TAG, "MAC: System payload rcvd " + returnVal);
                    if (header.type != NETWORK_FIRST_FRAGMENT && header.type != NETWORK_MORE_FRAGMENTS && header.type != NETWORK_MORE_FRAGMENTS_NACK && header.type != EXTERNAL_DATA_TYPE && header.type != NETWORK_LAST_FRAGMENT) {
                        return returnVal;
                    }
                }

                if (enqueue(header) == 2) { //External data received
                    return EXTERNAL_DATA_TYPE;
                }
            } else {

                if (header.to_node == 0100) {

                    if (header.type == NETWORK_POLL) {
                        if ((networkFlags & FLAG_NO_POLL) == 0 && node_address != 04444) {
                            header.to_node = header.from_node;
                            header.from_node = node_address;

                            header.ChangeHeader(frame_buffer);      // copying changes to the frame_buffer

                            delay(parent_pipe);
                            write(header.to_node,(byte) USER_TX_TO_PHYSICAL_ADDRESS);
                        }
                        continue;
                    }
                    int val = enqueue(header);

                    if (multicastRelay) {
                        if (debug) Log.i(TAG, "MAC: FWD multicast frame from " + Integer.toOctalString(header.from_node) + "to level " + (multicast_level + 1));
                        write((short)(levelToAddress(multicast_level) << 3), (byte) 4);
                    }
                    if (val == 2) { //External data received
                        //Serial.println("ret ext multicast");
                        return EXTERNAL_DATA_TYPE;
                    }

                } else {
                    write(header.to_node, (byte) 1);    //Send it on, indicate it is a routed payload
                }

            }

        }
        return returnVal;
    }


    /**
     * Test whether there is a message available for this node
     *
     * @return Whether there is a message available for this node
     */
    public boolean available() {
        return (!frame_queue.isEmpty());
    }

    /**
     *
     * Read the next available header
     *
     * Reads the next available header without advancing to the next
     * incoming message.  Useful for doing a switch on the message type
     *
     * If there is no message available, the header is not touched
     *
     * @param[out] header The header (envelope) of the next message
     * @return Message Size
     */
    public short peek (rf24NetworkHeader header)
    {
        if ( available() )
        {
            rf24NetworkFrame frame = frame_queue.peek();
            //memcpy(&header,&frame.header,sizeof(rf24NetworkHeader));
            header.CopyHeader(frame); //Todo: copy correct?

            return (short) frame.message_size;
        }
        return 0;
    }


    /**
     * Read a message
     *
     * {@code
     * while ( network.available() )  {
     *   rf24NetworkHeader header = new rf24NetworkHeader();
     *   int[] time = int[10];
     *   network.peek(header);
     *   if(header.type == 'T'){
     *     network.read(header,time,time.length);
     *     Log.i(Tag,"Got time: " + Arrays.toString(time));
     *   }
     * }
     * }
     * @param[out] header The header (envelope) of this message
     * @param[out] message Pointer to memory where the message should be placed
     * @param maxlen The largest message size which can be held in @p message
     * @return The total number of bytes copied into @p message
     */
    short read(rf24NetworkHeader header, rf24NetPayloads payload, int maxlen)
    {
        short bufsize = 0;

        if ( available() ) {
            rf24NetworkFrame frame = frame_queue.peek();

            // How much buffer size should we actually copy?
            bufsize = (short) rf24_min(frame.message_size,maxlen);
            //memcpy(&header,&(frame.header),sizeof(rf24NetworkHeader));
            header.CopyHeader(frame); //Todo: copy correct?
            //System.arraycopy(frame.message_buffer,0,message,0,frame.message_size);
            payload.CastMsg(frame.message_buffer);

            if (debug) Log.i(TAG, "FRG message size " + (frame.message_size));

            frame_queue.remove();
        }

        return bufsize;
    }

    /**
     * Send a message
     *
     * @note RF24Network now supports fragmentation for very long messages, send as normal. Fragmentation
     * may need to be enabled or configured by editing the RF24Network_config.h file. Default max payload size is 120 bytes.
     *
     * {@code
     * msg[] time = new int[2];
     * short to = 00; // Send to master
     * rf24NetworkHeader header(to, 'T'); // Send header type 'T'
     * network.write(header, time, time.length);
     * }
     * @param[in,out] header The header (envelope) of this message.  The critical
     * thing to fill in is the @p to_node field so we know where to send the
     * message.  It is then updated with the details of the actual header sent.
     * @param message Pointer to memory where the message is located
     * @param len The size of the message
     * @return Whether the message was successfully received
     */
    public boolean write(rf24NetworkHeader header, int[] message, short len) throws IOException{
        return write(header, message, len, (short) 070);
    }

    /* @}*/
    /*
     * @name Advanced Configuration
     *
     *  For advanced configuration of the network
     */
    /* @{*/


    /**
     * Construct the network in dual head mode using two radio modules.
     * Note: Not working on RPi yet. Radios will share MISO, MOSI and SCK pins, but require separate CE,CS pins.
     * {@code
     * 	RF24 radio(7,8);
     * 	RF24 radio1(4,5);
     * 	RF24Network(radio.radio1);
     * }
     * @param _radio The underlying radio driver instance
     * @param _radio1 The second underlying radio driver instance
     */
    public rf24Network( rf24 _radio, rf24 _radio1 )
    {
        radio = _radio;
        radio1 = _radio1;
        frame_size = MAX_FRAME_SIZE;
        txTime=0; networkFlags=0; returnSysMsgs=false; multicastRelay=false;

        dualradio = true;
    }

    /**
     * By default, multicast addresses are divided into levels.
     *
     * Nodes 1-5 share a multicast address, nodes n1-n5 share a multicast address, and nodes n11-n55 share a multicast address.<br>
     *
     * This option is used to override the defaults, and create custom multicast groups that all share a single
     * address. <br>
     * The level should be specified in decimal format 1-6 <br>
     * @see #multicastRelay
     * @param level Levels 1 to 6 are available. All nodes at the same level will receive the same
     * messages if in range. Messages will be routed in order of level, low to high by default, with the
     * master node (00) at multicast Level 0
     */
    private void multicastLevel(byte level) throws IOException {
        multicast_level = level;
        //radio.stopListening();
        radio.openReadingPipe(0, pipe_address(levelToAddress(level), (byte) 0));
        //radio.startListening();
    }

    /**
     * Set up the watchdog timer for sleep mode using the number 0 through 10 to represent the following time periods:<br>
     * wdt_16ms = 0, wdt_32ms, wdt_64ms, wdt_128ms, wdt_250ms, wdt_500ms, wdt_1s, wdt_2s, wdt_4s, wdt_8s
     * {@code
     * 	setup_watchdog(7);   // Sets the WDT to trigger every second
     * ]
     * @param prescalar The WDT prescaler to define how often the node will wake up. When defining sleep mode cycles, this time period is 1 cycle.
     * Not implemented yet
     */
    public void setup_watchdog(int prescalar){
        //Todo: implement a android watchdog
    }

    /*@}*/
    /*
     * @name Advanced Operation
     *
     *  For advanced operation of the network
     */
    /*@{*/

    /**
     * Return the number of failures and successes for all transmitted payloads, routed or sent directly
     * //Todo: implement some statistics
     *
     * {@code
     * bool fails, success;
     * network.failures(fails,success);
     * }
     *
     */

//    void RF24Network::failures(uint32_t *_fails, uint32_t *_ok){
//	*_fails = nFails;
//	*_ok = nOK;
//    }


    /**
     * Send a multicast message to multiple nodes at once
     * Allows messages to be rapidly broadcast through the network
     *
     * Multicasting is arranged in levels, with all nodes on the same level listening to the same address
     * Levels are assigned by network level ie: nodes 01-05: Level 1, nodes 011-055: Level 2
     * @see #multicastLevel
     * @see #multicastRelay
     * @param message Pointer to memory where the message is located
     * @param len The size of the message
     * @param level Multicast level to broadcast to
     * @return Whether the message was successfully sent
     */
    boolean multicast(rf24NetworkHeader header, int[] message, short len, byte level)  throws IOException {
        // Fill out the header
        header.to_node = 0100;
        header.from_node = node_address;
        return write(header, message, len, levelToAddress(level));
    }


    /**
     * Writes a direct (unicast) payload. This allows routing or sending messages outside of the usual routing paths.
     * The same as write, but a physical address is specified as the last option.
     * The payload will be written to the physical address, and routed as necessary by the recipient
     */
    public boolean write(rf24NetworkHeader header, int[] message, short len, short writeDirect) throws IOException{

        // writing changes in header in the frame_buffer
        header.ChangeHeader(frame_buffer);

        //Allows time for requests (RF24Mesh) to get through between failed writes on busy nodes
        while(millis()-txTime < 25){ if(update() > 127){break;} }
        delayMicroseconds(200);


        if(len <= max_frame_payload_size){
            //Normal Write (Un-Fragmented)
            frame_size = len + rf24NetworkHeader.sizeOf();
            if(_write(header, message, len, writeDirect)){
                return true;
            }
            txTime = millis();
            return false;
        }
        //Check payload size
        if (len > MAX_PAYLOAD_SIZE) {
            if (debug) Log.i(TAG, "NET write message failed. Given 'len' "+len+" is bigger than the MAX Payload size "+ MAX_PAYLOAD_SIZE);
            return false;
        }

        //Divide the message payload into chunks of max_frame_payload_size
        byte fragment_id = (byte)((len ) / max_frame_payload_size);
        if(len % max_frame_payload_size != 0)
            fragment_id += 1 ;  //the number of fragments to send = ceil(len/max_frame_payload_size)

        byte msgCount = 0;

        if (debug) Log.i(TAG, "FRG Total message fragments "+ fragment_id);

        if(header.to_node != 0100) {
            networkFlags |= FLAG_FAST_FRAG;

            if (!dualradio)
                radio.stopListening();

        }

        byte retriesPerFrag = 0;
        short type = header.type;
        boolean ok = false;

        while (fragment_id > 0) {

            //Copy and fill out the header
            //rf24NetworkHeader fragmentHeader = header;
            header.reserved = fragment_id;

            if (fragment_id == 1) {
                header.type = (byte) NETWORK_LAST_FRAGMENT;  //Set the last fragment flag to indicate the last fragment
                header.reserved = type; //The reserved field is used to transmit the header type
            } else {
                if (msgCount == 0) {
                    header.type = (byte) NETWORK_FIRST_FRAGMENT;
                } else {
                    header.type = (byte) NETWORK_MORE_FRAGMENTS; //Set the more fragments flag to indicate a fragmented frame
                }
            }

            short offset = (short) (msgCount * max_frame_payload_size);
            short fragmentLen = (short) rf24_min((len - offset), max_frame_payload_size);

            //Try to send the payload chunk with the copied header
            frame_size = rf24NetworkHeader.sizeOf() + fragmentLen;

            int[] messagetmp = new int[fragmentLen];
            System.arraycopy(message, offset, messagetmp, 0, rf24_min(fragmentLen, message.length - offset));

            ok = _write(header, messagetmp, (short) messagetmp.length, writeDirect);

            if (!ok) {
                delay(2);
                ++retriesPerFrag;

            } else {
                retriesPerFrag = 0;
                fragment_id--;
                msgCount++;
            }

            //if(writeDirect != 070){ delay(2); } //Delay 2ms between sending multicast payloads

            if (!ok && retriesPerFrag >= 3) {
                if (debug)
                    Log.i(TAG, "FRG TX with fragmentID '" + fragment_id + "' failed after " + msgCount + " fragments. Abort.");
                break;
            }

            //Message was successful sent
            if (debug)
                Log.i(TAG, "FRG message transmission with fragmentID '" + fragment_id + "' sucessfull.");

        }
        header.type = type;
        if(dualradio) {
            if ((networkFlags & FLAG_FAST_FRAG) == FLAG_FAST_FRAG) {
                ok = radio.txStandBy(txTimeout);
                radio.startListening();
                radio.setAutoAck(0, false);
            }
            networkFlags &= ~FLAG_FAST_FRAG;

            if (!ok) {
                return false;
            }
        }
        //int frag_delay = uint8_t(len/48);
        //delay( rf24_min(len/48,20));

        //Return true if all the chunks where sent successfully

        if (debug) Log.i(TAG, "FRG total message fragments sent "+ msgCount);
        if(fragment_id > 0){
            txTime = millis();
            return false;
        }
        return true;

    }

    /**
     * This node's parent address
     *
     * @return This node's parent address, or -1 if this is the base
     */
    private short parent()
    {
        if ( node_address == 0 )
            return -1;
        else
            return (short) parent_node;
    }

    /**
     * Provided a node address and a pipe number, will return the RF24Network address of that child pipe for that node
     * @param node node number
     * @param pipeNo pipe number
     * @return RF24Network address
     */
    private int addressOfPipe( short node, byte pipeNo )
    {
        //Say this node is 013 (1011), mask is 077 or (00111111)
        //Say we want to use pipe 3 (11)
        //6 bits in node mask, so shift pipeNo 6 times left and | into address
        short m = (short)(node_mask >> 3);
        byte i=0;

        while (m > 0){ 	   //While there are bits left in the node mask
            m >>= 1;     //Shift to the right
            i++;       //Count the # of increments
        }
        return node | (pipeNo << i);
    }


    /**
     * @note Addresses are specified in octal: 011, 034
     * @return True if a supplied address is valid
     */
    private boolean is_valid_address( int node ) {
        boolean result = true;

        while (node > 0) {
            int digit = node & 0x07;
            if (digit < 0 || digit > 5) {
                //Allow our out of range multicast address
                result = false;
                if (debug) Log.i(TAG, "*** WARNING *** Invalid address" + node);
                break;
            }
            node >>= 3;
        }
        return result;
    }


    private void setup_address() {
        // First, establish the node_mask
        short node_mask_check = (short) 0xFFFF;
        byte count = 0;


        while ((node_address & node_mask_check) != 0) {
            node_mask_check <<= 3;
            count++;
        }
        multicast_level = count;

        node_mask = ~node_mask_check;

        // parent mask is the next level down
        int parent_mask = node_mask >> 3;

        // parent node is the part IN the mask
        parent_node = node_address & parent_mask;

        // parent pipe is the part OUT of the mask
        int i = node_address;
        int m = parent_mask;
        while (m != 0) {
            i >>= 3;
            m >>= 3;
        }
        parent_pipe = i;

        if (debug) Log.i(TAG, "setup_address node=" + Integer.toOctalString(node_address) + " mask=" + Integer.toHexString(node_mask) + " parent=" + Integer.toOctalString(parent_node) + " pipe=" + parent_pipe);

    }


    private int[] pipe_address( int node, byte pipe ) {

        final short[] address_translation =  { 0xc3,0x3c,0x33,0xce,0x3e,0xe3,0xec };
        long result = 0xCCCCCCCCCCL;

        int[] out = new int[5];
        for (int idx = 0; idx < 5; idx++) {
            out[idx] = (byte) (0x00000000000000FF & result);
            result = result >> 8;
        }

        // Translate the address to use our optimally chosen radio address bytes
        byte count = 1;
        int dec = node;

        while (dec != 0) {
            if (pipe != 0 || node == 0)
                out[count] = (byte) address_translation[(dec % 8)];        // Convert our decimal values to octal, translate them to address bytes, and set our address

            dec /= 8;
            count++;
        }

        if (pipe != 0 || node == 0)
            out[0] = (byte) address_translation[pipe];
        else
            out[1] = address_translation[count-1];


        if (debug) Log.i(TAG, "NET Pipe " + pipe + " on node " + Integer.toOctalString(node) + " has address " + Arrays.toString(out));

//        int[] outi = new int[5];
//        for (int idx=0; idx < out.length; idx++)
//            outi[idx] = out[idx];

        return out;
    }










    /******************************************************************/

    boolean _write(rf24NetworkHeader header, int[] message, short len, short writeDirect)  throws IOException {
        // Fill out the header
        header.from_node = node_address;

        // Build the full frame to send
        int[] buffheader = header.toIntArray();  //memcpy(frame_buffer,&header,sizeof(rf24NetworkHeader));
        System.arraycopy(buffheader, 0, frame_buffer, 0, rf24NetworkHeader.sizeOf());

        //Log.i(TAG, "NET Sending %s\n\r"),millis(),header.toString()));

        if (len > 0){
            System.arraycopy(message, 0, frame_buffer, header.sizeOf(), rf24_min(frame_size-rf24NetworkHeader.sizeOf(),len));  //memcpy(frame_buffer + sizeof(rf24NetworkHeader),message,rf24_min(frame_size-sizeof(rf24NetworkHeader),len));
            if (debug) Log.i(TAG, "FRG frame size "+ frame_size);
        }

        // If the user is trying to send it to himself
          /*if ( header.to_node == node_address ){
            #if defined (RF24_LINUX)
              rf24NetworkFrame frame = rf24NetworkFrame(header,message,rf24_min(MAX_FRAME_SIZE-sizeof(rf24NetworkHeader),len));
            #else
              rf24NetworkFrame frame(header,len);
            #endif
            // Just queue it in the received queue
            return enqueue(frame);
          }*/
        // Otherwise send it out over the air


        if(writeDirect != 070){
            byte sendType = USER_TX_TO_LOGICAL_ADDRESS; // Payload is multicast to the first node, and routed normally to the next

            if(header.to_node == 0100){
                sendType = USER_TX_MULTICAST;
            }
            if(header.to_node == writeDirect){
                sendType = USER_TX_TO_PHYSICAL_ADDRESS; // Payload is multicast to the first node, which is the recipient
            }
            return write(writeDirect,sendType);
        }
        return write(header.to_node,(byte) TX_NORMAL);

    }


    private boolean write(short to_node, byte directTo) throws IOException   // Direct To: 0 = First Payload, standard routing, 1=routed payload, 2=directRoute to host, 3=directRoute to Route
    {
        boolean ok = false;
        boolean isAckType = false;

        short acktmp = (short)(0x00FF & frame_buffer[6]);
        //if (frame_buffer[6] > 64 && frame_buffer[6] < 192) { // reading reserverd?
        if ((acktmp > 64) && (acktmp < 192)) { // reading reserverd?
            isAckType = true;
        }

  /*if( ( (frame_buffer[7] % 2) && frame_buffer[6] == NETWORK_MORE_FRAGMENTS) ){
	isAckType = 0;
  }*/

        // Throw it away if it's not a valid address
        if (!is_valid_address(to_node))
            return false;

        //Load info into our conversion structure, and get the converted address info
        logicalToPhysical conversion = new logicalToPhysical();
        conversion.send_node = to_node;
        conversion.send_pipe = directTo;
        conversion.multicast = false;

        logicalToPhysicalAddress(conversion);

        if (debug) Log.i(TAG, "MAC Sending to " + Integer.toOctalString(to_node) + " via " + Integer.toOctalString(conversion.send_node) + " on pipe " + conversion.send_pipe);

        /* Write it */
        ok = write_to_pipe(conversion.send_node, conversion.send_pipe, conversion.multicast);

        if (!ok) {
            if (debug) Log.i(TAG, " MAC Send fail to " + Integer.toOctalString(to_node) + " via " + Integer.toOctalString(conversion.send_node) + " on pipe " + conversion.send_pipe);
        }


        if (directTo == TX_ROUTED && ok && conversion.send_node == to_node && isAckType) {

            rf24NetworkHeader header = new rf24NetworkHeader();
            header.CastMsg(frame_buffer);

            header.type = (byte) NETWORK_ACK;            // Set the payload type to NETWORK_ACK
            header.to_node = header.from_node;           // Change the 'to' address to the 'from' address

            header.ChangeHeader(frame_buffer);

            conversion.send_node = header.from_node;
            conversion.send_pipe = TX_ROUTED;
            conversion.multicast = false;

            logicalToPhysicalAddress(conversion);

            //Write the data using the resulting physical address
            frame_size = rf24NetworkHeader.sizeOf();

            write_to_pipe(conversion.send_node, conversion.send_pipe, conversion.multicast);

            //dynLen=0;
            if (debug) Log.i(TAG, "MAC: Route OK to " + Integer.toOctalString(to_node) + " ACK sent to " + Integer.toOctalString(header.from_node));

        }

        if (ok && conversion.send_node != to_node && (directTo == 0 || directTo == 3) && isAckType) {
            if (!dualradio) {
                // Now, continue listening
                if ((networkFlags & FLAG_FAST_FRAG) == FLAG_FAST_FRAG) {
                    radio.txStandBy(txTimeout);
                    networkFlags &= ~FLAG_FAST_FRAG;
                    radio.setAutoAck(0, false);
                }
                radio.startListening();
            }
            long reply_time = millis();

            while (update() != NETWORK_ACK) {
                delayMicroseconds(900);
                if (millis() - reply_time > routeTimeout) {
                    if (debug) Log.i(TAG, "MAC Network ACK fail from " + Integer.toOctalString(to_node) + " via " + Integer.toOctalString(conversion.send_node) + " on pipe " + conversion.send_pipe);
                    ok = false;
                    break;
                }
            }
        }
        if ((networkFlags & FLAG_FAST_FRAG) == 0) {
            if (!dualradio)
                // Now, continue listening
                radio.startListening();
        }
        //#if defined ENABLE_NETWORK_STATS
        //  if(ok == true){
        //        ++nOK;
        //    }else{	++nFails;
        //    }
        //#endif
        return ok;
    }

    private boolean write_to_pipe( short node, byte pipe, boolean multicast )  throws IOException
    {
        boolean ok = false;
        int[] out_pipe = pipe_address( node, pipe );

        if(!dualradio) {
            // Open the correct pipe for writing.
            // First, stop listening so we can talk
            if ((networkFlags & FLAG_FAST_FRAG) != FLAG_FAST_FRAG) {
                radio.stopListening();
            }

            if (multicast) {
                radio.setAutoAck(0, false);
            } else {
                radio.setAutoAck(0, true);
            }

            radio.openWritingPipe(out_pipe);

            ok = radio.writeFast(frame_buffer, frame_size, false);

            if ((networkFlags & FLAG_FAST_FRAG) != FLAG_FAST_FRAG) {
                ok = radio.txStandBy(txTimeout);
                radio.setAutoAck(0, false);
            }
        }
        else {
            radio1.openWritingPipe(out_pipe);
            radio1.writeFast(frame_buffer, frame_size);
            ok = radio1.txStandBy(txTimeout, multicast);

        }

        /*  #if defined (__arm__) || defined (RF24_LINUX)
          IF_SERIAL_DEBUG(printf_P(PSTR("%u: MAC Sent on %x %s\n\r"),millis(),(uint32_t)out_pipe,ok?PSTR("ok"):PSTR("failed")));
          #else
          IF_SERIAL_DEBUG(printf_P(PSTR("%lu: MAC Sent on %lx %S\n\r"),millis(),(uint32_t)out_pipe,ok?PSTR("ok"):PSTR("failed")));
          #endif
        */
        return ok;
    }


    private int enqueue(rf24NetworkHeader header) {
        int result = 0;

        rf24NetworkFrame frame = new rf24NetworkFrame(header, frame_buffer, frame_buffer.length - rf24NetworkHeader.sizeOf());

        boolean isFragment = (frame.header.type == NETWORK_FIRST_FRAGMENT || frame.header.type == NETWORK_MORE_FRAGMENTS || frame.header.type == NETWORK_LAST_FRAGMENT || frame.header.type == NETWORK_MORE_FRAGMENTS_NACK);

        // This is sent to itself
        if (frame.header.from_node == node_address) {
            if (isFragment) {
                if (debug) Log.i(TAG, "Cannot enqueue multi-payload frames to self");
                result = 0;
            } else {
                frame_queue.add(frame);
                result = 1;
            }
        } else if (isFragment) {

            //TODO: fragmented payload check!
            //The received frame contains the a fragmented payload
            //Set the more fragments flag to indicate a fragmented frame
            if (debug) Log.i(TAG, "FRG Payload type " + Integer.toHexString(frame.header.type) + " of size " + frame.message_size + " Bytes with fragmentID '" + frame.header.reserved + "' received.");
            //Append payload
            result = appendFragmentToFrame(frame);

            //The header.reserved contains the actual header.type on the last fragment
            if ((result == 1) && frame.header.type == NETWORK_LAST_FRAGMENT) {
                if (debug) Log.i(TAG, "FRG Last fragment received. ");
                if (debug) Log.i(TAG, "NET Enqueue assembled frame @" + frame_queue.size());

                Integer from_node = new Integer (frame.header.from_node);
                rf24NetworkFrame f = frameFragmentsCache.get(from_node);

                result = f.header.type == EXTERNAL_DATA_TYPE ? 2 : 1;

                //Load external payloads into a separate queue on linux
                if (result == 2) {
                    external_queue.add(f);
                } else {
                    frame_queue.add(f);
                }
                frameFragmentsCache.remove(from_node);
            }

        } else {//  if (frame.header.type <= MAX_USER_DEFINED_HEADER_TYPE) {
            //This is not a fragmented payload but a whole frame.

            if (debug) Log.i(TAG, "NET Enqueue @" + frame_queue.size());
            // Copy the current frame into the frame queue
            result = frame.header.type == EXTERNAL_DATA_TYPE ? 2 : 1;

            //Load external payloads into a separate queue on linux
            if (result == 2) {
                external_queue.add(frame);
            } else {
                frame_queue.add(frame);
            }
        }/* else {
            //Undefined/Unknown header.type received. Drop frame!
            IF_SERIAL_DEBUG_MINIMAL( printf("%u: FRG Received unknown or system header type %d with fragment id %d\n",millis(),frame.header.type, frame.header.reserved); );
            //The frame is not explicitly dropped, but the given object is ignored.
            //FIXME: does this causes problems with memory management?
        }*/

        if (result == 1) {
            //Log.i(TAG, "ok");
        } else {
            if (debug) Log.i(TAG, "failed");
        }

        return result;
    }




    private byte appendFragmentToFrame(rf24NetworkFrame frame) {

        // This is the first of 2 or more fragments.
        if (frame.header.type == NETWORK_FIRST_FRAGMENT){

            Integer from_node = new Integer (frame.header.from_node);
            rf24NetworkFrame fragments = frameFragmentsCache.get(from_node);

            if( fragments != null ){
                rf24NetworkFrame f = fragments;

                //Already rcvd first frag
                if (f.header.id == frame.header.id){
                    return 0;
                }
            }
            if(frame.header.reserved > ((short)(MAX_PAYLOAD_SIZE) / max_frame_payload_size) ){
                if (debug) Log.i(TAG,"FRG Too many fragments in payload "+ frame.header.reserved +", dropping...");
                // If there are more fragments than we can possibly handle, return
                return 0;
            }
            from_node = new Integer(frame.header.from_node);

            frameFragmentsCache.put(from_node, frame);

            return 1;
        }else

        if ( frame.header.type == NETWORK_MORE_FRAGMENTS || frame.header.type == NETWORK_MORE_FRAGMENTS_NACK ){

            Integer from_node = new Integer (frame.header.from_node);
            rf24NetworkFrame fragments = frameFragmentsCache.get(from_node);

            if( fragments == null ){
                return 0;
            }

            rf24NetworkFrame f = fragments;

            if( f.header.reserved - 1 == frame.header.reserved && f.header.id == frame.header.id){
                // Cache the fragment
                f.AttachMsg(frame.message_buffer);
                f.message_size += frame.message_size;  //Increment message size
                f.header = frame.header; //Update header
                return 1;

            } else {
                if (debug) Log.i(TAG,"FRG Dropping fragment for frame with header id:"+ frame.header.id+", out of order fragment(s)");
                return 0;
            }

        }else
        if ( frame.header.type == NETWORK_LAST_FRAGMENT ){

            Integer from_node = new Integer (frame.header.from_node);
            rf24NetworkFrame fragments = frameFragmentsCache.get(from_node);

            //We have received the last fragment
            if(fragments == null){
                return 0;
            }
            // the cached frame
            rf24NetworkFrame f = fragments;

            if( f.message_size + frame.message_size > MAX_PAYLOAD_SIZE){        // needed???
                if (debug) Log.i(TAG,"FRG Frame of size "+ frame.message_size+" plus enqueued frame of size "+f.message_size+" exceeds max payload size");
                return 0;
            }
            //Error checking for missed fragments and payload size
            if ( f.header.reserved-1 != 1 || f.header.id != frame.header.id) {
                if (debug) Log.i(TAG,"FRG Duplicate or out of sequence frame "+frame.header.reserved+", expected "+f.header.reserved+". Cleared.");
                //frameFragmentsCache.erase( std::make_pair(frame.header.id,frame.header.from_node) );
                return 0;
            }
            //The user specified header.type is sent with the last fragment in the reserved field
            frame.header.type = frame.header.reserved;
            frame.header.reserved = 1;

            //Append the received fragment to the cached frame
            f.AttachMsg(frame.message_buffer);
            f.message_size += frame.message_size;  //Increment message size
            f.header = frame.header; //Update header
            return 1;
        }
        return 0;
    }


    // Provided the to_node and directTo option, it will return the resulting node and pipe
    private boolean logicalToPhysicalAddress(logicalToPhysical conversionInfo) {

        //Create pointers so this makes sense.. kind of
        //We take in the to_node(logical) now, at the end of the function, output the send_node(physical) address, etc.
        //back to the original memory address that held the logical information.
        short to_node = conversionInfo.send_node;
        byte directTo = conversionInfo.send_pipe;
        boolean multicast = conversionInfo.multicast;

        // Where do we send this?  By default, to our parent
        short pre_conversion_send_node = (short) parent_node;

        // On which pipe
        byte pre_conversion_send_pipe = (byte) parent_pipe;

        if (directTo > TX_ROUTED) {
            pre_conversion_send_node = to_node;
            multicast = true;
            //if(*directTo == USER_TX_MULTICAST || *directTo == USER_TX_TO_PHYSICAL_ADDRESS){
            pre_conversion_send_pipe = 0;
            //}
        }
        // If the node is a direct child,
        else if (is_direct_child(to_node)) {
            // Send directly
            pre_conversion_send_node = to_node;
            // To its listening pipe
            pre_conversion_send_pipe = 5;
        }
        // If the node is a child of a child
        // talk on our child's listening pipe,
        // and let the direct child relay it.
        else if (is_descendant(to_node)) {
            pre_conversion_send_node = direct_child_route_to(to_node);
            pre_conversion_send_pipe = 5;
        }

        conversionInfo.send_node = pre_conversion_send_node;
        conversionInfo.send_pipe = pre_conversion_send_pipe;

        return true;

    }

    /**
     * Check if the node is a child (directly connected)
     * @param node node number
     * @return True if it is a child, otherwise false
     */
    private boolean is_direct_child( short node ) {
        boolean result = false;

        // A direct child of ours has the same low numbers as us, and only
        // one higher number.
        //
        // e.g. node 0234 is a direct child of 034, and node 01234 is a
        // descendant but not a direct child

        // First, is it even a descendant?
        if (is_descendant(node)) {
            // Does it only have ONE more level than us?
            short child_node_mask = (short) ((~node_mask) << 3);
            result = (node & child_node_mask) == 0;
        }
        return result;
    }


    /**
     * Check if the node is child or grand-child, etc...
     * @param node
     * @return
     */
    private boolean is_descendant( short node ) {
        return (node & node_mask) == node_address;
    }

    /**
     *
     * @param node
     * @return
     */
    private short direct_child_route_to( short node ) {
        // Presumes that this is in fact a child!!
        short child_mask = (short)((node_mask << 3) | 0x07);
        return (short)(node & child_mask);
    }

    /**
     *
     * @param level
     * @return
     */
    short levelToAddress(byte level) {

        short levelAddr = 1;
        if (level != 0) {
            levelAddr = (short)(levelAddr << ((level - 1) * 3));
        } else {
            return 0;
        }
        return levelAddr;
    }


    /**
     * Stands for Bit Value where you pass it a bit and it gives you the byte value with that bit set. (avr function)
     * @param x input integer to make bit value
     * @return bit value for x
     */
    private int _BV(int x) {
        int y = (int) (1 << (x));
        return y;
    }

    /**
     * Function determines which value is greater
     * @param a Integer to compare with b
     * @param b Integer to compare with a
     * @return a if a > b, otherwise b
     */
    private int rf24_min(int a, int b) {
        if (a < b)
            return a;
        else
            return b;
    }


    /**
     * Delay in milliseconds
     * @param milliseconds Delay in milliseconds
     */
    private void delay(int milliseconds) {
        delayMicroseconds(milliseconds * 1000);
    }

    /**
     * Delay in microseconds
     * @param microseconds Delay in microseconds
     */
    public void delayMicroseconds(long microseconds) {
        long start = System.nanoTime();
        long end = 0;
        do {
            end = System.nanoTime();
        } while (start + microseconds >= end);
    }

    /**
     * Return Milliseconds since Epoch
     * @return long
     */
    private long millis()
    {
        return System.currentTimeMillis();
    }



    private class logicalToPhysical{
        public short send_node;
        public byte send_pipe;
        public boolean multicast;
    };
}

    /**
     * Header which is sent with each message
     *
     * The frame put over the air consists of this header and a message
     *
     * Headers are addressed to the appropriate node, and the network forwards them on to their final destination.
     */

 class rf24NetworkHeader
 {
        /** Header size defined by:
         *
         *   from_node -> 2 bytes
         *   to_node ->   2 bytes
         *   id ->        2 bytes
         *   type ->      2 byte (but taken as 1 byte)
         +   reserved ->  2 byte (but taken as 1 byte)
         *               =========
         *                8 bytes
         *
         *   (next_id ->   2 bytes  // static => not in object)
        */

        private static final int SIZE_OF_HEADER = 8;

        /**< Logical address where the message was generated */
        short from_node;
        /**< Logical address where the message is going */
        short to_node;
        /**< Sequential message ID, incremented every time a new frame is constructed */
        short id;
        /**
         * Message Types:
         * User message types 1 through 64 will NOT be acknowledged by the network, while message types 65 through 127 will receive a network ACK.
         * System message types 192 through 255 will NOT be acknowledged by the network. Message types 128 through 192 will receive a network ACK. <br>
         *
         * Important: Type of the packet. </b> 0-127 are user-defined types, 128-255 are reserved for system */
        short type;

        /**
         * During fragmentation, it carries the fragment_id, and on the last fragment
         * it carries the header_type.<br>
         * Important: Reserved for system use
         */
        short reserved;

        /**< The message ID of the next message to be sent (unused)*/
        static short next_id;

        /**
         * Default constructor
         *
         * Simply constructs a blank header
         */
         rf24NetworkHeader() {}

        /**
         * Send constructor
         *
         * @note Now supports automatic fragmentation for very long messages, which can be sent as usual if fragmentation is enabled.
         *
         * Fragmentation is enabled by default for all devices except ATTiny <br>
         * Configure fragmentation and max payload size in RF24Network_config.h
         *
         * Use this constructor to create a header and then send a message
         *
         * {@code
         *  int recipient_address = 011;
         *
         *  rf24NetworkHeader header(recipient_address,'t');
         *
         *  network.write(header,&message,sizeof(message));
         * }
         *
         * @param _to The Octal format, logical node address where the message is going
         * @param _type The type of message which follows.  Only 0-127 are allowed for
         * user messages. Types 1-64 will not receive a network acknowledgement.
         */
        public rf24NetworkHeader(short _to, byte _type) {
            to_node = _to;
            id = next_id++;
            type = _type;
        }

        /**
         * Cast a message from a integer array to a header object
         * @param msg integer array containing the header
         */
        public void CastMsg(int[] msg)
        {
            from_node = (short)((msg[1] << 8) + msg[0]);
            to_node = (short)((msg[3] << 8) + msg[2]);
            id = (short)((msg[5] << 8) + msg[4]);
            type = (short)(0x000000FF & msg[6]);
            reserved = (short)(0x000000FF & msg[7]);
            //next_id = (short)((msg[10] << 8) + msg[9]); // -> static not from object
        }

        /**
         * Modify the header value, according to the new header
         * @param msg integer array containing new header
         */
        public void ChangeHeader(int[] msg)
        {
            msg[0] = 0x00FF & from_node;
            msg[1] = 0x00FF & (from_node >> 8);
            msg[2] = 0x00FF & to_node;
            msg[3] = 0x00FF & (to_node >> 8);
            msg[4] = 0x00FF & id;
            msg[5] = 0x00FF & (id >> 8);
            msg[6] = 0x000000FF & type;
            msg[7] = 0x000000FF & reserved;
        }

        /**
         * copy header from a frame
         * @param copy frame in which the header {@see rf24NetworkFrame} is contained
         */
        public void CopyHeader(rf24NetworkFrame copy) {
            this.from_node = copy.header.from_node;
            this.to_node = copy.header.to_node;
            this.id = copy.header.id;
            this.type = copy.header.type;
            this.reserved = copy.header.reserved;
        }

        /**
         * 'Serialize' the header object into an integer array to be sended
         * @return integer array containing the header
         */
        public int[] toIntArray(){
            int[] ret = new int[SIZE_OF_HEADER];
            ret[0] = 0x00FF & from_node;
            ret[1] = 0x00FF & (from_node >> 8);
            ret[2] = 0x00FF & to_node;
            ret[3] = 0x00FF & (to_node >> 8);
            ret[4] = 0x00FF & id;
            ret[5] = 0x00FF & (id >> 8);
            ret[6] = 0x00FF & type;
            ret[7] = 0x00FF & reserved;
            return ret;
        }

        /**
         * Return the header size
         * @return header size
         */
        public static int sizeOf() {
            return SIZE_OF_HEADER;
        }



        /**
         * @param _to The Octal format, logical node address where the message is going
         * user messages. Types 1-64 will not receive a network acknowledgement.
         */
        public rf24NetworkHeader(short _to) {
            type = 0;
            to_node = _to;
            id = next_id++;
        }

        /**
         * Create debugging string
         *
         * Useful for debugging.  Dumps all members into a single string, using
         * internal static memory.  This memory will get overridden next time
         * you call the method.
         *
         * @return String representation of this object
         */
        public String toString()
        {
            String buffer;
            buffer = "Header info: id "+ id +" from "+Integer.toOctalString(from_node)+" to "+Integer.toOctalString(to_node)+" type "+Integer.toHexString(type);
            return buffer;
        }


 }


/**
 * Frame structure for internal message handling, and for use by external applications
 *
 * The actual frame put over the air consists of a header (8-bytes) and a message payload (Up to 24-bytes)<br>
 * When data is received, it is stored using the rf24NetworkFrame structure, which includes:
 * 1. The header
 * 2. The size of the included message
 * 3. The 'message' or data being received
 *
 *
 */
 class rf24NetworkFrame {

     /** The size of the main buffer. This is the user-cache, where incoming data is stored.
      * Data is stored using Frames: Header (8-bytes) + Frame_Size (2-bytes) + Data (?-bytes)
      *
      * Note: The MAX_PAYLOAD_SIZE is (MAIN_BUFFER_SIZE - 10), and the result must be divisible by 24.
      */
     private static final int MAIN_BUFFER_SIZE = 144 + 10;
     /** Maximum size of fragmented network frames and fragmentation cache. This MUST BE divisible by 24.
      * Note: Must be a multiple of 24.
      * Note: If used with RF24Ethernet, this value is used to set the buffer sizes.
      */
     private static final int MAX_PAYLOAD_SIZE = MAIN_BUFFER_SIZE-10;

    /**< Header which is sent with each message */
     rf24NetworkHeader header;
    /**< The size in bytes of the payload length */
     int message_size;

     /**
      * On Arduino, the message buffer is just a pointer, and can be pointed to any memory location.
      * On Linux the message buffer is a standard byte array, equal in size to the defined MAX_PAYLOAD_SIZE
      */
     int[] message_buffer = new int[MAX_PAYLOAD_SIZE]; //< Array to store the message
     /**
      * Default constructor
      *
      * Simply constructs a blank frame. Frames are generally used internally. See rf24NetworkHeader.
      */
     rf24NetworkFrame() {}

     /**
      * Constructor - create a network frame with data
      * Frames are constructed and handled differently on Arduino/AVR and Linux devices (defined RF24_LINUX)
      *
      * <br>
      * **Linux:**
      * @param _header The RF24Network header to be stored in the frame
      * @param _message The 'message' or data.
      * @param _len The size of the 'message' or data.
      *
      * <br>
      * **Arduino/AVR/Etc.**
      * @see RF24Network.frag_ptr
      * @param _header The RF24Network header to be stored in the frame
      * @param _message Integer array containing the message
      * @param _len The size of the 'message' or data
      *
      *
      * Frames are used internally and by external systems. See rf24NetworkHeader.
      */
     rf24NetworkFrame(rf24NetworkHeader _header, int[] _message, int _len)
     {
         header = _header;
         message_size = _len;
            if (_message.length > 0 && _len > 0) {
                //memcpy(message_buffer,_message,_len);
                System.arraycopy(_message, _header.sizeOf(), message_buffer, 0, message_size);
            }
     }

    /**
     * Attach a message to this frame (use by fragmentation)
     * @param _message message to be attached
     */
    public void AttachMsg(int[] _message)
    {
        int[] temp = new int[message_buffer.length + _message.length];
        System.arraycopy(message_buffer, 0, temp, 0, message_buffer.length);
        System.arraycopy(_message, 0, temp, message_buffer.length, _message.length);
        message_buffer = temp;
    }


     /**
      * Create debugging string
      *
      * Useful for debugging.  Dumps all members into a single string, using
      * internal static memory.  This memory will get overridden next time
      * you call the method.
      *
      * @return String representation of this object
      */
      //const char* toString(void) const;




}





interface rf24NetPayloads{
    /**
     * Returns the size of the payload
     * @return size of the payload (variables)
     */
    int sizeOf();

    /**
     * Receives a msg (message_buffer) and fills the fields of the payload.
     * @param msg
     */
    void CastMsg(int[] msg);

    /**
     * Serialize the object, meaning returns the fields of this object in an int[] array
     */
    int[] toInt();
}