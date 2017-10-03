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

import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.util.ArrayList;


public class MainActivity extends Activity {

    private static final boolean debug = false;

    private static final String TAG = MainActivity.class.getSimpleName();

    // rf24 network objects
    private rf24 radio;
    private rf24Network network;
    private rf24Mesh mesh;
    Thread runner = null;

    // database connection
    private DatabaseHandler sqliteconn;

    // rf24 layout objects
    private RecyclerView recyclerView;
    private MyAdapter mAdapter;
    private RecyclerView.LayoutManager mLayoutManager;
    private Handler mHandler;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if(debug) Log.d(TAG, "onCreate");

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

        // creating database connection
        sqliteconn = new DatabaseHandler(this);

        // creating radio, network and mesh objects
        radio = new rf24((byte) 0, (byte) 1, 12000000);
        network = new rf24Network(radio);
        mesh = new rf24Mesh(radio, network, sqliteconn);


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
                /**
                 * Starting thread with the rf24network communication!
                 */
                try {
                    // Set the nodeID to 0 for the master node
                    mesh.setNodeID((byte) 0);
                    mesh.begin();
                    radio.printDetails();

                } catch (IOException e) {
                    if(debug) Log.d(TAG, "Error on initializing radio", e);
                }

                while (true) {
                    try {

                        // Call network.update as usual to keep the network updated
                        mesh.update();

                        // In addition, keep the 'DHCP service' running on the master node so addresses will
                        // be assigned to the sensor nodes
                        mesh.DHCP();

                        // Check for incoming data from the sensors
                        while (network.available()) {

                            if(debug) Log.i(TAG, "Payloads added to database: " + sqliteconn.getNrPayloads());

                            rf24NetworkHeader header = new rf24NetworkHeader();
                            network.peek(header);

                            switch (header.type) {
                                // Display the incoming millis() values from the sensor nodes
                                case 'F':        // F5529 node
                                    network.read(header, payload_data_f5529, payload_data_f5529.sizeOf());
                                    if(debug) {
                                        handle_F5529(header, payload_data_f5529);
                                        Log.i(TAG, "Rcv from " + Integer.toOctalString(header.from_node));
                                    }
                                    break;
                                case 'T':        // TMC4C1294 node
                                    network.read(header, payload_data_tm4c1294, payload_data_tm4c1294.sizeOf());
                                    if(debug) {
                                        handle_F5529(header, payload_data_tm4c1294);
                                        Log.i(TAG, "Rcv from " + Integer.toOctalString(header.from_node));
                                    }
                                    break;
                                case 'G':        // G2553 node
                                    network.read(header, payload_data_g2553, payload_data_g2553.sizeOf());
                                    if(debug) {
                                        handle_G2553(header, payload_data_g2553);
                                        Log.i(TAG, "Rcv from " + Integer.toOctalString(header.from_node));
                                    }
                                    savePayload(header, payload_data_g2553);
                                    break;
                                default:
                                    network.read(header, payload, 0);
                                    if(debug) {
                                        Log.i(TAG, "Rcv bad type " + header.type + " from " + Integer.toOctalString(header.from_node));
                                    }
                                    break;
                            }
                        }


                        // Update payload in mesh
                        mesh.updatePayloads();

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
                        if(debug) Log.d(TAG, "Error on initializing radio", e);
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
        if(debug) Log.d(TAG, "onDestroy");

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
        Log.i(TAG, "Status from sensor node("+payload_data.nodeId+") G = "+Byte.toString(payload_data.cStatus)+" size "+ Integer.toString(payload_data.sizeOf()));
        Log.i(TAG, "nodeId:"+  payload_data.nodeId);
        Log.i(TAG, "nodePower:"+  payload_data.power);
        Log.i(TAG, "ambientHumidity:"+  payload_data.temperature);
        Log.i(TAG, "airQuality:"+  payload_data.airQuality);
        Log.i(TAG, "ambientLux:"+  payload_data.lux);
        Log.i(TAG, "errorCount:"+  payload_data.errorCount);
        Log.i(TAG, "humidity:"+  payload_data.humidity);
        Log.i(TAG, "info:"+  payload_data.info[0] +  payload_data.info[1] +  payload_data.info[2]);
        Log.i(TAG,"-------------");
    }



    /**
     * Preprocess and save a defined "small" payload into database
     * @param header
     * @param payload_data
     */
    void savePayload(rf24NetworkHeader header, payload_sensordata_small payload_data){
        rf24Node tmpNode = new rf24Node();

        tmpNode.setNodeID((short)payload_data.nodeId);
        tmpNode.setType(header.type);
        tmpNode.payload.nodeID = (short)payload_data.nodeId;
        tmpNode.payload.type = header.type;
        tmpNode.payload.setPayload(payload_data.toJson());

        sqliteconn.addPayload(tmpNode);
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

}


