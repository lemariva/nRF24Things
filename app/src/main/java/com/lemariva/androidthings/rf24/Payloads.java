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
 */

package com.lemariva.androidthings.rf24;

import org.json.JSONException;
import org.json.JSONObject;

/**
 payload classes
 */
class payload_empty implements rf24NetPayloads{
    public int sizeOf() {
        return 0;
    }

    public void CastMsg(int[] msg) {}
    public int[] toInt() { return new int[0]; }
}

class payload_sensordata_big implements rf24NetPayloads{
    /**
     int         nodeid          4
     byte        cStatus         1
     byte[3]     HardwareID      3
     float       temperature     4
     byte        power           1
     byte        current         1

     byte        stateCharge     1
     byte        remainingCharge 1

     byte        humidity        1
     byte        airQuality      1
     byte        lux             1
     byte        presure         1

     byte        errorCount      1
     byte[3]     info            3
     ========
     24
     */

    private static final int PAYLOAD_SIZE = 24;

    int nodeId;
    /**
     * Status configured or not
     */
    byte cStatus;

    /**
     * Hardware unique ID
     */
    int[] HardwareID = new int[3];

    float temperature;

    byte power;
    byte current;
    byte stateCharge;
    byte remainingCharge;

    byte humidity;
    byte airQuality;
    byte lux;
    byte presure;

    byte errorCount;
    int[] info = new int[3];

    public int sizeOf() {
        return PAYLOAD_SIZE;
    }

    public void CastMsg(int[] msg)
    {
        nodeId = ((msg[3] << 32) + (msg[2] << 16) + (msg[1] << 8) + msg[0]);
        cStatus = (byte)(msg[4]);
        System.arraycopy(msg,5,HardwareID,0,3);
        temperature = (float)((msg[11] << 32) + (msg[10] << 16) + (msg[9] << 8) + msg[8]);
        power =  (byte) msg[12];
        current = (byte) msg[13];
        stateCharge = (byte) msg[14];
        remainingCharge = (byte) msg[15];
        humidity = (byte) msg[16];
        airQuality = (byte) msg[17];
        lux = (byte) msg[18];
        presure = (byte) msg[19];
        errorCount = (byte) msg[20];
        System.arraycopy(msg,21,info,0,3);
    }

    public int[] toInt() {
        //TODO: serialize the payload and return it
        return new int[PAYLOAD_SIZE];
    }

    public String toJson() {
        JSONObject jsonNodeObj = new JSONObject();
        try {
            jsonNodeObj.put("nodeStatus", this.cStatus);
            jsonNodeObj.put("payloadSize", Integer.toString(this.sizeOf()));
            jsonNodeObj.put("nodePower", this.power);
            jsonNodeObj.put("ambientTemp", this.temperature);
            jsonNodeObj.put("ambientHumidity", this.humidity);
            jsonNodeObj.put("airQuality", this.airQuality);
            jsonNodeObj.put("ambientLux", this.lux);
            jsonNodeObj.put("errorCount", this.errorCount);
            //.....
            jsonNodeObj.put("nodeInfo", this.info[0] + this.info[1] + this.info[2]);
        } catch (JSONException ex) {
            ex.printStackTrace();
        }
        return jsonNodeObj.toString();
    }

}

class payload_sensordata_small implements rf24NetPayloads {
    /**
     * int         nodeid          4
     * byte        cStatus         1
     * byte[3]     HardwareID      3
     * int         temperature     2
     * int         power           2
     * byte        humidity        1
     * byte        airQuality      1
     * byte        lux             1
     * byte        errorCount      1
     * byte[3]     info            3
     * ========
     * 19
     */

    private static final int PAYLOAD_SIZE = 19;

    int nodeId;
    /**
     * < Status configured or not
     */
    byte cStatus;
    /**
     * Hardware unique ID
     */
    int[] HardwareID = new int[3];
    int temperature;
    int power;
    byte humidity;
    byte airQuality;
    byte lux;
    byte errorCount;
    int[] info = new int[3];


    public int sizeOf() {
        return PAYLOAD_SIZE;
    }

    public void CastMsg(int[] msg) {
        nodeId = (((msg[3] & 0xFF) << 24) + ((msg[2] & 0xFF) << 16) + ((msg[1] & 0xFF) << 8) + (msg[0] & 0xFF));
        cStatus = (byte) (msg[4] & 0xFF);
        System.arraycopy(msg, 5, HardwareID, 0, 3);
        temperature = ((msg[9] & 0xFF) << 8) + (msg[8] & 0xFF);
        power = ((msg[11] & 0xFF) << 8) + (msg[10] & 0xFF);
        humidity = (byte) msg[12];
        airQuality = (byte) msg[13];
        lux = (byte) msg[14];
        errorCount = (byte) msg[15];
        System.arraycopy(msg, 16, info, 0, 3);
    }


    public int unsignedToBytes(byte b, int position) {
        int ret = (b & 0xFF) << position;
        return ret;
    }


    public int[] toInt() {
        //TODO: serialize the payload and return it
        return new int[PAYLOAD_SIZE];
    }

    public String toJson() {
        JSONObject jsonNodeObj = new JSONObject();
        try {
            jsonNodeObj.put("nodeStatus", this.cStatus);
            jsonNodeObj.put("payloadSize", Integer.toString(this.sizeOf()));
            jsonNodeObj.put("nodePower", this.power);
            jsonNodeObj.put("ambientTemp", this.temperature);
            jsonNodeObj.put("ambientHumidity", this.humidity);
            jsonNodeObj.put("airQuality", this.airQuality);
            jsonNodeObj.put("ambientLux", this.lux);
            jsonNodeObj.put("errorCount", this.errorCount);
            jsonNodeObj.put("nodeInfo", this.info[0]*100 + this.info[1]*10 + this.info[2]);
        } catch (JSONException ex) {
            ex.printStackTrace();
        }
        return jsonNodeObj.toString();
    }
}

class payload_command implements rf24NetPayloads{
    long nodeId;
    long command;
    long value;
    //byte[] command = new byte[10];
    //byte[] value = new byte[3];

    /**
     * long         nodeID      4
     * long         command     4
     * long         value       4
     *                      ========
     *                         12
     */
    private static final int PAYLOAD_SIZE = 12;

    public int sizeOf() {
        return PAYLOAD_SIZE;
    }

    public void CastMsg(int[] msg) {
        /*
        for(int jArray = 0; jArray < command.length; jArray++)
            command[jArray] = (byte) msg[jArray + 4];

        for(int jArray = 0; jArray < value.length; jArray++)
            value[jArray] = (byte) msg[jArray + 4 + command.length];
        */
        nodeId = ((msg[3] << 32) + (msg[2] << 16) + (msg[1] << 8) + msg[0]);
        command = ((msg[7] << 32) + (msg[6] << 16) + (msg[5] << 8) + msg[4]);
        value = ((msg[11] << 32) + (msg[10] << 16) + (msg[9] << 8) + msg[8]);
    }

    public int[] toInt() {
        //TODO: serialize the payload and return it
        int[] ret = new int[PAYLOAD_SIZE];

        ret[0] = (int)(nodeId & 0xFFL);
        ret[1] = (int)((nodeId << 8)  & 0xFFL);
        ret[2] = (int)((nodeId << 16) & 0xFFL);
        ret[3] = (int)((nodeId << 32) & 0xFFL);

        ret[4] = (int)(command & 0xFFL);
        ret[5] = (int)((command << 8)  & 0xFFL);
        ret[6] = (int)((command << 16) & 0xFFL);
        ret[7] = (int)((command << 32) & 0xFFL);

        ret[8] = (int)(value & 0xFFL);
        ret[9] = (int)((value << 8)  & 0xFFL);
        ret[10] = (int)((value << 16) & 0xFFL);
        ret[11] = (int)((value << 32) & 0xFFL);

        return ret;
    }
};