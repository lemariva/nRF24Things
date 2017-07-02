/**
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
 */


package com.lemariva.androidthings.rf24;

public class rf24Node {
    /**
     *  Node unique id
     */
    private short nodeID;
    /**
     *  Node address
     */
    private short address;

    /**
     *  Node type
     */
    private short type;

    /**
     *  Node name
     */
    private String name;

    /**
     *  Node info
     */
    private String info;

    /**
     *  Node release time (in milliseconds)
     */
    private long releasetime;

    /**
     *  Node topic
     */
    private String topic;

    /**
     * Node payload
     */
    public rf24NodePayload payload;


    public rf24Node() {
        payload = new rf24NodePayload();
    };

    public rf24Node(short nodeID, short address, short type, String name, String topic)
    {
        this.nodeID = nodeID;
        this.address = address;
        this.type = type;
        this.name = name;
        this.topic = topic;
    }

    public short getNodeID() {
        return nodeID;
    }

    public void setNodeID(short nodeID) {
        this.nodeID = nodeID;
    }


    public short getAddress() {
        return address;
    }

    public long getReleaseTimeAddr() {
        return releasetime;
    }

    public void setAddress(short address) {
        this.address = address;
        this.releasetime = millis();
    }

    public void setAddress(short address, long time) {
        this.address = address;
        this.releasetime = time;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public short getType(){
        return type;
    }

    public void setType(short type) {
        this.type = type;
    }

    public String getTopic() {
        return topic;
    }

    public void setTopic(String topic) {
        this.topic = topic;
    }

    /**
     * Return Milliseconds since Epoch
     * @return long
     */
    private long millis()
    {
        return System.currentTimeMillis();
    }

}

