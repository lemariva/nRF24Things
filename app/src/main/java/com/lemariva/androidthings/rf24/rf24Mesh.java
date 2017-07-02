package com.lemariva.androidthings.rf24;

import android.content.Context;
import android.nfc.Tag;
import android.util.Log;

import java.io.IOException;
import java.util.ArrayList;

/** Rewritten for Java by:
 *  Mauro Riva <lemariva@mail.com> <lemariva.com>
 *
 *  Originally written in C/C++ by:
 *      TMRh20 < https://github.com/TMRh20/RF24Network >
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

public class rf24Mesh {
    /**
     * Set debugging status!
     */
    private static final boolean debug = false;


    private static final String TAG = rf24Mesh.class.getSimpleName();

    /* User Configuration */
    /** Set 1 to 4 (Default: 4) Restricts the maximum children per node. **/
    private static final int MESH_MAX_CHILDREN = 4;
    //#define MESH_NOMASTER /** This can be set to 0 for all nodes except the master (nodeID 0) to save pgm space **/

    /* Advanced User Config */
    /** How long mesh write will retry address lookups before giving up. This is not used when sending to or from the master node. **/
    private static final int MESH_LOOKUP_TIMEOUT = 3000;
    /** UNUSED - How long mesh.write will retry failed payloads. */
    private static final int MESH_WRITE_TIMEOUT  = 5550;
    /** Radio channel to operate on 1-127. This is normally modified by calling mesh.setChannel() */
    private static final int MESH_DEFAULT_CHANNEL = 76;
    /** How long to attempt address renewal */
    private static final int MESH_RENEWAL_TIMEOUT = 60000;

    /*       Debug        */
    //#define MESH_DEBUG_MINIMAL /** Uncomment for the Master Node to print out address assignments as they are assigned */
    //#define MESH_DEBUG         /** Uncomment to enable debug output to serial **/

    /* Other Configuration */
    /** Minimum time required before changing nodeID. Prevents excessive writing to EEPROM */
    private static final int MESH_MIN_SAVE_TIME = 30000;
    private static final short MESH_DEFAULT_ADDRESS = 04444;
    /** Determines the max size of the array used for storing addresses on the Master Node */
    private static final int MESH_MAX_ADDRESSES = 255;

    /*
     * Network/Mesh Response Types
     * The network will determine whether to automatically acknowledge payloads based on their type
     * RF24Mesh uses pre-defined system types for interacting with RF24Network at the system level
     *
     */
    // Network ACK types
    private static final short MESH_ADDR_CONFIRM = 129;
    // No Network ACK types
    private static final int MESH_ADDR_LOOKUP = 196;
    private static final int MESH_ADDR_RELEASE = 197;
    private static final int MESH_ID_LOOKUP = 198;

    private static final int MESH_BLANK_ID = 65535;

    private static final int MESH_MAXPOLLS = 4;

    private rf24 radio;
    private rf24Network network;

    private DatabaseHandler sqliteconn;

    /**< Indicator that an address request is available */
    boolean doDHCP;
    int lastSaveTime;
    int lastFileSave;

    byte radio_channel;
    short lastID,lastAddress;

    // Pointer used for dynamic memory allocation of address list
    /**< See the rfNodesStruct class reference */
    public ArrayList<rf24Node> rfNodes;
    /**< The number of entries in the assigned address list */
    int nodeTop;

    /**
     * The assigned RF24Network (Octal) address of this node
     * @return Returns an unsigned 16-bit integer containing the RF24Network address in octal format
     */
    short mesh_address;

    short _nodeID;

    /**
     * Construct the mesh:
     *
     * {@code
     * RF24 radio(7,8);
     * RF24Network network(radio);
     * RF24Mesh mesh(radio,network);
     * }
     * @param _radio The underlying radio driver instance
     * @param _network The underlying network instance
     *
     */
    public rf24Mesh( rf24 _radio, rf24Network _network, Context context) {
        radio = _radio;
        network = _network;

        sqliteconn = new DatabaseHandler(context);
    }

    public rf24Mesh(rf24 _radio, rf24Network _network, DatabaseHandler sqliteconn) {
        radio = _radio;
        network = _network;

        this.sqliteconn = sqliteconn;
    }

    /**
     * Call this in setup() to configure the mesh and request an address.  <br>
     *
     * {@code mesh.begin(); }
     * This may take a few moments to complete.
     *
     * The following parameters are optional:
     * @param channel The radio channel (1-127)
     * @param data_rate The data rate (rf24.rf24_datarate_e) (RF24_250KBPS,RF24_1MBPS,RF24_2MBPS)
     * @param timeout How long to attempt address renewal in milliseconds
     * @return true if no error found, otherwise false
     * @throws IOException when write / read on spi doesn't work
     */
    public boolean begin(byte channel, rf24.rf24_datarate_e data_rate, int timeout) throws IOException {
        //delay(1); // Found problems w/SPIDEV & ncurses. Without this, getch() returns a stream of garbage
        radio.begin();
        radio_channel = channel;
        radio.setChannel(radio_channel);
        radio.setDataRate(data_rate);
        network.returnSysMsgs = true;

        if (getNodeID() != 0) { //Not master node
            mesh_address = MESH_DEFAULT_ADDRESS;
            if (renewAddress(timeout) == 0) {
                return false;
            }
        } else {
            //(addrListStruct*)malloc(2 * sizeof(addrListStruct));
            rf24Node tmp = new rf24Node();
            rfNodes= new ArrayList<rf24Node>();
            rfNodes.add(tmp);

            nodeTop = 0;
            loadDHCP();
            mesh_address = 0;
            network.begin(mesh_address);

        }
        return true;
    }

    /**
     * @see #begin(byte, rf24.rf24_datarate_e, int)
     * with
     *      channel = 76
     *      data_rate = RF24_1MBPS
     *      timeout = 60000
     * @return true if no error found, otherwise false
     * @throws IOException throws IOException when write / read on spi doesn't work
     */
    public boolean begin() throws IOException {

        boolean ret = begin((byte) MESH_DEFAULT_CHANNEL, rf24.rf24_datarate_e.RF24_1MBPS, MESH_RENEWAL_TIMEOUT);
        return ret;
    }

    /**
     * Very similar to network.update(), it needs to be called regularly to keep the network
     * and the mesh going.
     * @see #network.update()
     * @throws IOException when write / read on spi doesn't work
     */
    public int update() throws IOException {

        int[] tmpAddress = new int[2];

        int type = network.update();
        if (mesh_address == MESH_DEFAULT_ADDRESS) {
            return type;
        }

        if (type == rf24Network.NETWORK_REQ_ADDRESS) {
            doDHCP = true;
        }

        if (getNodeID() == 0) {
            if ((type == MESH_ADDR_LOOKUP || type == MESH_ID_LOOKUP)) {
                rf24NetworkHeader header = new rf24NetworkHeader();
                header.CastMsg(network.frame_buffer);
                header.to_node = header.from_node;

                header.ChangeHeader(network.frame_buffer);

                if (type == MESH_ADDR_LOOKUP) {
                    short returnAddr = getAddress((byte) network.frame_buffer[rf24NetworkHeader.sizeOf()]);

                    tmpAddress[0] = 0x00FF & returnAddr;
                    tmpAddress[1] = 0x00FF & (returnAddr >> 8);

                    network.write(header, tmpAddress, (short) tmpAddress.length);
                } else {
                    short returnAddr = getNodeID((byte) network.frame_buffer[rf24NetworkHeader.sizeOf()]);

                    tmpAddress[0] = 0x00FF & returnAddr;
                    tmpAddress[1] = 0x00FF & (returnAddr >> 8);

                    network.write(header, tmpAddress, (short) tmpAddress.length);
                }
                //printf("Returning lookup 0%o to 0%o   \n",returnAddr,header.to_node);
                //network.write(header,&returnAddr,sizeof(returnAddr));
            } else if (type == MESH_ADDR_RELEASE) {
                short fromAddr = (short) ((network.frame_buffer[1] << 8) + network.frame_buffer[0]);

                for (byte i = 0; i < nodeTop; i++) {
                    if (rfNodes.get(i).getAddress() == fromAddr) {
                        rfNodes.get(i).setAddress((short)0);
                    }
                }
            }
        }
        return type;
    }

    /**
     * Update the payloads of the node in rfNodes
     */
    void updatePayloads(){
        for (int i = 0; i < rfNodes.size(); i++) {
            rf24NodePayload tmp = sqliteconn.getNodePayload(rfNodes.get(i).getNodeID());
            rfNodes.get(i).payload.setPayload(tmp.getPayload(),tmp.getUpdate());
        }
    }

    /**
     * Automatically construct a header and send a payload
     * Very similar to the standard network.write() function, which can be used directly.
     *
     * @note Including the nodeID parameter will result in an automatic address lookup being performed.
     * @note Message types 1-64 (decimal) will NOT be acknowledged by the network, types 65-127 will be. Use as appropriate to manage traffic:
     * if expecting a response, no ack is needed.
     *
     * @param data Send any type of data of any length (Max length determined by RF24Network layer)
     * @param msg_type The user-defined (1-127) message header_type to send. Used to distinguish between different types of data being transmitted.
     * @param size The size of the data being sent
     * @param nodeID **Optional**: The nodeID of the recipient if not sending to master
     * @return True if success, False if failed
     * @throws IOException when write / read on spi doesn't work
     */
    boolean write(int[] data, byte msg_type, short size, byte nodeID) throws IOException {
        if (mesh_address == MESH_DEFAULT_ADDRESS) {
            return false;
        }

        short toNode = 0;
        long lookupTimeout = millis() + MESH_LOOKUP_TIMEOUT;
        int retryDelay = 50;

        if (nodeID != 0) {

            while ((toNode = getAddress(nodeID)) < 0) {
                if (millis() > lookupTimeout || toNode == -2) {
                    return false;
                }
                retryDelay += 50;
                delay(retryDelay);
            }
        }
        return write(toNode, data, msg_type, size);
    }

    /**
     * Write to a specific node by RF24Network address.
     *
     */
    boolean write(short to_node, int[] data, byte msg_type, short size ) throws IOException {
        if (mesh_address == MESH_DEFAULT_ADDRESS) {
            return false;
        }
        rf24NetworkHeader header = new rf24NetworkHeader(to_node, msg_type);
        return network.write(header, data, size);
    }

    /**
     * Set a unique nodeID for this node. This value is stored in program memory, so is saved after loss of power.
     *
     * This should be called before mesh.begin(), or set via serial connection or other methods if configuring a large number of nodes...
     * @note If using RF24Gateway and/or RF24Ethernet, nodeIDs 0 & 1 are used by the master node.
     * @param nodeID Can be any unique value ranging from 1 to 255.
     */

    public void setNodeID(short nodeID) {
        _nodeID = nodeID;
    }

    /**
     * Only to be used on the master node. Provides automatic configuration for sensor nodes, similar to DHCP.
     * Call immediately after calling network.update() to ensure address requests are handled appropriately
     * @throws IOException when write / read on spi doesn't work
     */
    public void DHCP() throws IOException {

        if (doDHCP) {
            doDHCP = false;
        } else {
            return;
        }
        rf24NetworkHeader header = new rf24NetworkHeader();
        header.CastMsg(network.frame_buffer); //memcpy(&header,network.frame_buffer,sizeof(rf24NetworkHeader));

        short newAddress;

        // Get the unique id of the requester
        short from_id = header.reserved;
        if (from_id == 0) {
            if (debug) Log.i(TAG, "MSH: Invalid id 0 rcvd");
            return;
        }

        short fwd_by = 0;
        byte shiftVal = 0;
        byte extraChild = 0;

        if (header.from_node != MESH_DEFAULT_ADDRESS) {
            fwd_by = header.from_node;
            short m = fwd_by;
            byte count = 0;

            while (m > 0) {  //Octal addresses convert nicely to binary in threes. Address 03 = B011  Address 033 = B011011
                m >>= 3;   //Find out how many digits are in the octal address
                count++;
            }
            shiftVal = (byte) (count * 3); //Now we know how many bits to shift when adding a child node 1-5 (B001 to B101) to any address
        } else {
            //If request is coming from level 1, add an extra child to the master
            extraChild = 1;
        }


        for (int iChild = MESH_MAX_CHILDREN + extraChild; iChild > 0; iChild--) { // For each of the possible addresses (5 max)

            boolean found = false;
            newAddress = (short) (fwd_by | (iChild << shiftVal));
            if (newAddress == 0) { /*printf("dumped 0%o\n",newAddress);*/
                continue;
            }

            for (byte i = 0; i < nodeTop; i++) {
                if (debug)
                    Log.i(TAG, "ID: " + Integer.toOctalString(rfNodes.get(i).getNodeID()) + " ADDR: " + Integer.toHexString(rfNodes.get(i).getAddress()));
                if ((rfNodes.get(i).getAddress() == newAddress && rfNodes.get(i).getNodeID() != from_id) || newAddress == MESH_DEFAULT_ADDRESS) {
                    found = true;
                    break;
                }
            }

            if (!found) {
                header.type = rf24Network.NETWORK_ADDR_RESPONSE;
                header.to_node = header.from_node;


                // casting address to int[]
                int[] newAddress_i = new int[2];
                newAddress_i[0] = newAddress & 0x00FF;
                newAddress_i[1] = (newAddress & 0xFF00) >> 8;

                //This is a routed request to 00
                if (header.from_node != MESH_DEFAULT_ADDRESS) { //Is NOT node 01 to 05
                    delay(2);

                    network.write(header, newAddress_i, (short) 2);
//                    if( network.write(header, newAddress, (short)2) ){
//                        //addrMap[from_id] = newAddress; //????
//                    }else{
//                        network.write(header,newAddress,sizeof(newAddress));
//                    }
                } else {
                    delay(2);
                    network.write(header, newAddress_i, (short) 2, header.to_node);
                    //addrMap[from_id] = newAddress;
                }
                long timer = millis();
                lastAddress = newAddress;
                lastID = from_id;
                while (network.update() != MESH_ADDR_CONFIRM) {
                    if (millis() - timer > network.routeTimeout) {
                        return;
                    }

                }
                setAddress(from_id, newAddress);

                if (debug)
                    Log.i(TAG, "Sent to " + Integer.toOctalString(header.to_node) + " phys: " + Integer.toOctalString(MESH_DEFAULT_ADDRESS) + " new: " + Integer.toHexString(newAddress) + " id: " + header.reserved);

                break;
            } else {
                if (debug) Log.i(TAG, "not allocated\n");
            }
        }

        //}else{
        //break;
    }

    /*@}*/
    /*
     * @name Advanced Operation
     *
     *  For advanced configuration and usage of the mesh
     */
    /*@{*/

    /**
     * Convert an RF24Network address into a nodeId.
     * @param address If no address is provided, returns the local nodeID, otherwise a lookup request is sent to the master node
     * @return Returns the unique identifier (1-255) or -1 if not found.
     * @throws IOException when write / read on spi doesn't work
     */
    private short getNodeID(int address)  throws IOException {

        int[] TmpAddress = new int[2];

        if (address == MESH_BLANK_ID) {
            return _nodeID;
        } else if (address == 0) {
            return 0;
        }

        if (mesh_address == 0) { //Master Node
            for (byte i = 0; i < nodeTop; i++) {
                if (rfNodes.get(i).getAddress() == address) {
                    return rfNodes.get(i).getNodeID();
                }
            }
        } else {
            if (mesh_address == MESH_DEFAULT_ADDRESS) {
                return -1;
            }
            rf24NetworkHeader header = new rf24NetworkHeader((short) 00, (byte) MESH_ID_LOOKUP);

            TmpAddress[0] = 0x00FF & address;
            TmpAddress[1] = 0x00FF & (address >> 8);

            if (network.write(header, TmpAddress, (short)TmpAddress.length) ){
                long timer = millis(), timeout = 500;
                while (network.update() != MESH_ID_LOOKUP) {
                    if (millis() - timer > timeout) {
                        return -1;
                    }
                }
                short ID;
                System.arraycopy(network.frame_buffer, rf24NetworkHeader.sizeOf(), TmpAddress, 0,  2); //memcpy( & ID,&network.frame_buffer[sizeof(rf24NetworkHeader)], sizeof(ID));

                ID = (short)((TmpAddress[1] << 8) + TmpAddress[0]);

                return ID;
            }
        }
        return -1;
    }

    private short getNodeID()  throws IOException {
        return getNodeID(MESH_BLANK_ID);
    }

    /**
     * Tests connectivity of this node to the mesh.
     * @note If this function fails, the radio will be put into standby mode, and will not receive payloads until the address is renewed.
     * @return Return 1 if connected, 0 if mesh not responding after up to 1 second
     * @throws IOException when write / read on spi doesn't work
     */
    boolean checkConnection() throws IOException{

        byte count = 3;
        boolean ok = false;
        while(count-- > 0 && mesh_address != MESH_DEFAULT_ADDRESS){
            update();
            if(radio.rxFifoFull() || ((network.networkFlags & 1) == 1)){
                return true;
            }
            rf24NetworkHeader header = new rf24NetworkHeader((short)00, (byte)rf24Network.NETWORK_PING);
            int[] empty = new int[0];
            ok = network.write(header,empty,(short)0);
            if(ok){break;}
            delay(103);
        }
        if(!ok){ radio.stopListening(); }
        return ok;

    }

    /**
     * Reconnect to the mesh and renew the current RF24Network address. Used to re-establish a connection to the mesh if physical location etc. has changed, or
     * a routing node goes down.
     * @note Currently times out after 1 minute if address renewal fails. Network writes should not be attempted if address renewal fails.
     *
     * @note If all nodes are set to verify connectivity/reconnect at a specified period, leaving the master offline for this length of time should result
     * in complete network/mesh reconvergence.
     * @param timeout How long to attempt address renewal in milliseconds default:60000

     * @return Returns the newly assigned RF24Network address
     * @throws IOException when write / read on spi doesn't work
     */
    private short renewAddress(int timeout) throws IOException {

        if (radio.available()) {
            return 0;
        }
        byte reqCounter = 0;
        byte totalReqs = 0;
        radio.stopListening();

        network.networkFlags |= 2;
        delay(10);

        network.begin(MESH_DEFAULT_ADDRESS);
        mesh_address = MESH_DEFAULT_ADDRESS;

        long start = millis();
        while (!requestAddress(reqCounter)) {
            if (millis() - start > timeout) {
                return 0;
            }
            delay(50 + ((totalReqs + 1) * (reqCounter + 1)) * 2);
            reqCounter++;
            reqCounter = (byte) (reqCounter % 4);
            totalReqs++;
            totalReqs = (byte) (totalReqs % 10);

        }
        network.networkFlags &= ~2;
        return mesh_address;
    }


    /**
     * Releases the currently assigned address lease. Useful for nodes that will be sleeping etc.
     * @note Nodes should ensure that addresses are releases successfully prior to renewal.
     * @return Returns True if successfully released, False if not
     * @throws IOException when write / read on spi doesn't work
     */

    boolean releaseAddress() throws IOException {

        if (mesh_address == MESH_DEFAULT_ADDRESS) {
            return false;
        }

        rf24NetworkHeader header = new rf24NetworkHeader((short) 00, (byte) MESH_ADDR_RELEASE);
        int[] empty = new int[0];
        if (network.write(header, empty, (short) 0)) {
            network.begin(MESH_DEFAULT_ADDRESS);
            mesh_address = MESH_DEFAULT_ADDRESS;
            return true;
        }
        return false;
    }

    /**
     * Convert a nodeID into an RF24Network address
     * @note If printing or displaying the address, it needs to be converted to octal format: Serial.println(address,OCT);
     *
     * Results in a lookup request being sent to the master node.
     * @param nodeID - The unique identifier (1-255) of the node
     * @return Returns the RF24Network address of the node or -1 if not found or lookup failed.
     * @throws IOException when write / read on spi doesn't work
     */
    short getAddress(byte nodeID) throws IOException {

        int[] TmpAddress = new int[2];
        if (getNodeID() == 0) { //Master Node
            short address = 0;
            for (byte i = 0; i < nodeTop; i++) {
                if (rfNodes.get(i).getNodeID() == nodeID) {
                    address = rfNodes.get(i).getAddress();
                    return address;
                }
            }
            return -1;
        }

        if (mesh_address == MESH_DEFAULT_ADDRESS) {
            return -1;
        }
        if (nodeID == 0) {
            return 0;
        }
        rf24NetworkHeader header = new rf24NetworkHeader((short) 00, (byte) MESH_ADDR_LOOKUP);
        TmpAddress[0] = 0;
        TmpAddress[1] = (int) nodeID;

        if (network.write(header, TmpAddress, (short) 2)) {
            long timer = millis(), timeout = 150;
            while (network.update() != MESH_ADDR_LOOKUP) {
                if (millis() - timer > timeout) {
                    return -1;
                }
            }
        } else {
            return -1;
        }
        short address = 0;

        System.arraycopy(network.frame_buffer, rf24NetworkHeader.sizeOf(), TmpAddress, 0, 2); //memcpy(&address,network.frame_buffer+sizeof(rf24NetworkHeader),sizeof(address));

        return address >= 0 ? address : -2;
    }

    /**
     * Change the active radio channel after the mesh has been started.
     * @param _channel Radio channel
     * @throws IOException when write / read on spi doesn't work
     */

    private void setChannel(byte _channel) throws IOException {

        radio_channel = _channel;
        radio.setChannel(radio_channel);
        radio.startListening();
    }

    /**
     * Allow child nodes to discover and attach to this node.
     * @param allow True to allow children, False to prevent children from attaching automatically.
     */
    void setChild(boolean allow) {
        //TODO: rewrite this function
        //Prevent old versions of RF24Network from throwing an error
        //Note to remove this ""if defined"" after a few releases from 1.0.1
//    #if defined FLAG_NO_POLL
//        network.networkFlags = allow ? network.networkFlags & ~FLAG_NO_POLL : network.networkFlags | FLAG_NO_POLL;
//    #endif
    }


    /**
     * Set/change a nodeID/RF24Network Address pair manually on the master node.
     *
     * @code
     * Set a static address for node 02, with nodeID 23, since it will just be a static routing node for example
     * running on an ATTiny chip.
     *
     * mesh.setStaticAddress(23,02);
     * @endcode
     * @param nodeID The nodeID to assign
     * @param address The octal RF24Network address to assign
     * @return If the nodeID exists in the list,
     */

    private void setAddress(short nodeID, short address) {

        int position = nodeTop;

        for (byte i = 0; i < nodeTop; i++) {
            if (rfNodes.get(i).getNodeID() == nodeID) {
                position = i;
                break;
            }
        }

        rfNodes.get(position).setNodeID(nodeID);
        rfNodes.get(position).setAddress(address);

        if (position == nodeTop) {
            ++nodeTop;
            rf24Node tmp = new rf24Node();
            rfNodes.add(tmp);
        }

        //if(millis()-lastFileSave > 300){
        //	lastFileSave = millis();
        saveDHCP();
        //}
    }

    /**
     * @deprecated
     * Calls setAddress()
     */
    public void setStaticAddress(byte nodeID, short address) {
        setAddress(nodeID, address);
    }



    /**
     * Actual requesting of the address once a contact node is discovered or supplied
     * @param level
     * @return True if address is obtained, otherwise false
     * @throws IOException when write / read on spi doesn't work
     */
    private boolean requestAddress(byte level) throws IOException {

        rf24NetworkHeader header = new rf24NetworkHeader((short) 0100, (byte) rf24Network.NETWORK_POLL);
        //Find another radio, starting with level 0 multicast
        if (debug) Log.i(TAG, " MSH: Poll ");

        int[] empty = new int[0];
        network.multicast(header, empty, (short) 0, level);

        long timr = millis();

        short[] contactNode = new short[MESH_MAXPOLLS];
        byte pollCount = 0;

        while (true) {
            boolean goodSignal;
            if (debug) {
                goodSignal = radio.testRPD();
            }

            if (network.update() == rf24Network.NETWORK_POLL) {
                short tmp = (short) ((network.frame_buffer[1] << 8) + network.frame_buffer[0]);  //memcpy(&contactNode[pollCount],&network.frame_buffer[0],sizeof(uint16_t)); ???
                contactNode[pollCount] = tmp;
                pollCount++;

                if (debug) {
                    if (goodSignal) {
                        Log.i(TAG, "MSH: Poll > -64dbm");
                    } else {
                        Log.i(TAG, "MSH: Poll < -64dbm");
                    }
                }
            }

            if (millis() - timr > 55 || pollCount >= MESH_MAXPOLLS) {
                if (pollCount == 0) {
                    if (debug) Log.i(TAG, "MSH: No poll from level " + level);
                    return false;
                } else {
                    if (debug) Log.i(TAG, "MSH: Poll OK");
                    break;
                }
            }
        }
        if (debug)
            Log.i(TAG, "MSH: Got poll from level " + level + "count " + pollCount);


        short type = 0;
        for (byte i = 0; i < pollCount; i++) {
            // Request an address via the contact node
            header.type = rf24Network.NETWORK_REQ_ADDRESS;
            header.reserved = (short) getNodeID();
            header.to_node = contactNode[i];

            // Do a direct write (no ack) to the contact node. Include the nodeId and address.
            network.write(header, empty, (short) 0, contactNode[i]);
            if (debug) Log.i(TAG, "MSH: Request address from: " + Integer.toOctalString(contactNode[i]));

            timr = millis();

            while (millis() - timr < 225) {
                type = (short) network.update();
                if (type == rf24Network.NETWORK_ADDR_RESPONSE) {
                    i = pollCount;
                    break;
                }
            }
            delay(5);
        }
        if (type != rf24Network.NETWORK_ADDR_RESPONSE) {
            return false;
        }


        byte registerAddrCount = 0;

        short newAddress = 0;
        //memcpy(&addrResponse,network.frame_buffer+sizeof(rf24NetworkHeader),sizeof(addrResponse));//
        // memcpy(&newAddress,network.frame_buffer+sizeof(rf24NetworkHeader),sizeof(newAddress));

        newAddress = (short) ((network.frame_buffer[rf24NetworkHeader.sizeOf() + 1] << 8) + network.frame_buffer[rf24NetworkHeader.sizeOf()]);

        if (newAddress == 0 || network.frame_buffer[7] != getNodeID()) {
            if (debug)
                Log.i(TAG, "Response discarded, wrong node " + Integer.toHexString(newAddress) + " from node " + Integer.toOctalString(header.from_node) + " sending node " + Integer.toOctalString(MESH_DEFAULT_ADDRESS) + " id " + (network.frame_buffer[7]));
            return false;
        }
        if (debug)
            Log.i(TAG, "Set address " + Integer.toHexString(mesh_address) + " rcvd " + Integer.toHexString(newAddress));

        mesh_address = newAddress;

        radio.stopListening();
        delay(10);
        network.begin(mesh_address);
        header.to_node = 00;
        header.type = (byte) MESH_ADDR_CONFIRM;

        while (!network.write(header, empty, (short) 0)) {
            if (registerAddrCount++ >= 6) {
                network.begin(MESH_DEFAULT_ADDRESS);
                mesh_address = MESH_DEFAULT_ADDRESS;
                return false;
            }
            delay(3);
        }

        return true;
    }

    private void loadDHCP() {
        rfNodes = sqliteconn.getNodes();
        nodeTop = rfNodes.size() - 1;
    }

    /*****************************************************/

    private void saveDHCP() {

        for (int iNode=0; iNode< nodeTop; iNode++)
        {
            rf24Node tmp = rfNodes.get(iNode);
            sqliteconn.addNode(tmp);
            Log.i(TAG, "Adding to database: Node ID: "+Integer.toOctalString(tmp.getNodeID())+"  ADDR: "+Integer.toHexString(tmp.getAddress()));
        }
    }

    /**
     * Delay in milliseconds
     * @param milliseconds Delay in milliseconds
     */
    private void delay(int milliseconds) {
        long start = millis();
        long end = 0;
        do {
            end = millis();
        } while (start + milliseconds >= end);
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

    /*** Return Milliseconds since Epoch0,
     * @return long
     */
    private long millis() {
        return System.currentTimeMillis();
    }
}
