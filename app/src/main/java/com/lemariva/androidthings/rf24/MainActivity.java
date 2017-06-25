/*
 * Copyright 2016, The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.lemariva.androidthings.rf24;

import android.app.Activity;
import android.content.Context;
import android.media.Image;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.util.Log;

import java.io.IOException;

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
 *
 * This class starts a rf24 mesh network with its corresponding 'DHCP services' and display
 * the address nodes address releases on the UI (TV)
 *
 */

public class MainActivity extends Activity {
    private static final String TAG = MainActivity.class.getSimpleName();

    // rf24 network objects
    private rf24 radio;
    private rf24Network network;
    private rf24Mesh mesh;
    Thread runner = null;

    // rf24 layout objects
    private RecyclerView recyclerView;
    private MyAdapter mAdapter;
    private RecyclerView.LayoutManager mLayoutManager;
    private Handler mHandler;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Log.d(TAG, "onCreate");

        /**
         * UI layout
          */
        setContentView(R.layout.activity_main);
        recyclerView = (RecyclerView) findViewById(R.id.my_recycler_view);

        recyclerView.setHasFixedSize(true);

        // use a linear layout manager
        mLayoutManager = new LinearLayoutManager(this);
        recyclerView.setLayoutManager(mLayoutManager);

        // recyclerView.setItemAnimator(new DefaultItemAnimator());  // Use the default animator
        RecyclerView.ItemDecoration itemDecoration = new DividerItemDecoration(this, DividerItemDecoration.VERTICAL_LIST); // you could add item decorators
        recyclerView.addItemDecoration(itemDecoration);

        // creating handler for getting rf24Nodes
        mHandler = new Handler(getApplicationContext().getMainLooper());

        /**
         * Starting thread with the rf24network communication!
         */

        try {

            radio = new rf24((byte) 0, (byte) 1, 12000000);
            network = new rf24Network(radio);
            mesh = new rf24Mesh(radio, network, this);

            // Set the nodeID to 0 for the master node
            mesh.setNodeID((byte)0);

            mesh.begin();

            radio.printDetails();


       } catch (IOException e) {
            Log.d(TAG, "Error on initializing radio", e);
       }

       runner = new Thread() {
           payload_sensordata_big payload_data_f5529 = new payload_sensordata_big();
           payload_sensordata_big payload_data_tm4c1294 = new payload_sensordata_big();
           payload_sensordata_small payload_data_g2553 = new payload_sensordata_small();
           payload_empty payload = new payload_empty();

           Handler handler = new Handler() {
               @Override
               public void handleMessage(Message msg) {
                   // getting data from the UI
               }
           };

           public void run() {
               //Looper.prepare();
               while (true) {
                   try {

                       // Call network.update as usual to keep the network updated
                       mesh.update();

                       // In addition, keep the 'DHCP service' running on the master node so addresses will
                       // be assigned to the sensor nodes
                       mesh.DHCP();

                       // Check for incoming data from the sensors
                       while (network.available()) {
                           rf24NetworkHeader header = new rf24NetworkHeader();
                           network.peek(header);

                           switch (header.type) {
                               // Display the incoming millis() values from the sensor nodes
                               case 'F':        // F5529 node
                                   network.read(header, payload_data_f5529, payload_data_f5529.sizeOf());
                                   handle_F5529(header, payload_data_f5529);
                                   Log.i(TAG, "Rcv from " + Integer.toOctalString(header.from_node));
                                   break;
                               case 'T':        // TMC4C1294 node
                                   network.read(header, payload_data_tm4c1294, payload_data_tm4c1294.sizeOf());
                                   handle_F5529(header, payload_data_tm4c1294);
                                   Log.i(TAG, "Rcv from " + Integer.toOctalString(header.from_node));
                                   break;
                               case 'G':        // G2553 node
                                   network.read(header, payload_data_g2553, payload_data_g2553.sizeOf());
                                   handle_G2553(header, payload_data_g2553);
                                   Log.i(TAG, "Rcv from " + Integer.toOctalString(header.from_node));
                                   break;
                               default:
                                   network.read(header, payload, 0);
                                   Log.i(TAG, "Rcv bad type " + header.type + " from " + Integer.toOctalString(header.from_node));
                                   break;
                           }
                       }

                       // Updating UI over handler
                       mHandler.post(new Runnable() {
                           @Override
                           public void run() {
                               //putting data in ListView
                               mAdapter = new MyAdapter(mesh.rfNodes);
                               recyclerView.setAdapter(mAdapter);
                           }
                       });

                       delay(2);

                   } catch (IOException e) {
                       Log.d(TAG, "Error on initializing radio", e);
                   }
               }
               //Looper.loop();
           }
       };
       runner.start();

    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        Log.d(TAG, "onDestroy");

        // killing thread??
        Thread moribund = runner;
        runner = null;
        moribund.interrupt();
    }


    /**
     * Handler for 5529 Node
     * @param payload_data
     */
    void handle_F5529(rf24NetworkHeader header, payload_sensordata_big payload_data)
    {
        Log.i(TAG,"-------------");
        Log.i(TAG,"Processing info node -> handle\n\r");

        Log.i(TAG, "Status from sensor node("+payload_data.nodeId+") F = "+Integer.toString(payload_data.cStatus)+" size "+ Integer.toString(payload_data.sizeOf()));
        Log.i(TAG, "nodeId:"+  Integer.toString(payload_data.nodeId));
        Log.i(TAG, "power:"+  payload_data.power);
        Log.i(TAG, "current:"+  payload_data.current);

        Log.i(TAG,"-------------");
    }

    /**
     * Handler for G2553 Node
     * @param payload_data
     */
    void handle_G2553(rf24NetworkHeader header, payload_sensordata_small payload_data)
    {
        Log.i(TAG,"-------------");
        Log.i(TAG,"Processing info node -> handle\n\r");

        Log.i(TAG, "Status from sensor node("+payload_data.nodeId+") G = "+Integer.toString(payload_data.cStatus)+" size "+ Integer.toString(payload_data.sizeOf()));
        Log.i(TAG, "nodeId:"+  payload_data.nodeId);
        Log.i(TAG, "power:"+  payload_data.power);
        Log.i(TAG, "temperature:"+  payload_data.temperature);
        Log.i(TAG, "humidity:"+  payload_data.humidity);
        Log.i(TAG, "info:"+  payload_data.info[0] +  payload_data.info[1] +  payload_data.info[2]);

        Log.i(TAG,"-------------");
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

    /**
     * Return Milliseconds since Epoch
     * @return long
     */
    private long millis()
    {
        return System.currentTimeMillis();
    }



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
         *                          ========
         *                             19
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
    }

    class payload_command implements rf24NetPayloads{
        long nodeId;
        byte[] command = new byte[10];
        byte[] value = new byte[3];

        /**
         * byte[10]     command    10
         * byte[3]      value       3
         *                      ========
         *                         13
         */
        private static final int PAYLOAD_SIZE = 13;

        public int sizeOf() {
            return PAYLOAD_SIZE;
        }

        public void CastMsg(int[] msg) {
            for(int jArray = 0; jArray < command.length; jArray++)
                command[jArray] = (byte) msg[jArray + 4];

            for(int jArray = 0; jArray < value.length; jArray++)
                value[jArray] = (byte) msg[jArray + 4 + command.length];

            nodeId = ((msg[3] << 32) + (msg[2] << 16) + (msg[1] << 8) + msg[0]);
        }

        public int[] toInt() {
            //TODO: serialize the payload and return it
            return new int[PAYLOAD_SIZE];
        }
    };
}


