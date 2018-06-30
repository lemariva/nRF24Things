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

import java.util.ArrayList;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.provider.BaseColumns;
import android.util.Log;


public class DatabaseHandler extends SQLiteOpenHelper {

    /**
     * Database version
     */
    private static final int DATABASE_VERSION = 33;

    /**
     * Database name
      */
    private static final String DATABASE_NAME = "SensorMesh";

    /**
     * sensors table columns names
      */
    public static class sensorsEntry implements BaseColumns {
        public static final String TABLE_NAME = "sensors";
        private static final String KEY_NODE_NAME = "name";
        private static final String KEY_NODE_TYPE = "type";
        private static final String KEY_NODE_ADDRESS = "address";
        private static final String KEY_NODE_RELEASETIME = "releasetime";
        private static final String KEY_NODE_TOPIC = "topic";
    }

    /**
     * data-sensors table name
     */
    public static class payloadEntry implements BaseColumns {
        public static final String TABLE_NAME = "payloads";
        private static final String KEY_NODE_ID = "node_idname";
        private static final String KEY_NODE_TYPE = "type";
        private static final String KEY_NODE_PAYLOAD = "payload";
        private static final String KEY_NODE_UPDATE = "lastupdate";
    }


    private static final String CREATE_NODES_TABLE = "CREATE TABLE " + sensorsEntry.TABLE_NAME + "("
                                                        + sensorsEntry._ID + " INTEGER PRIMARY KEY,"
                                                        + sensorsEntry.KEY_NODE_ADDRESS + " TEXT,"
                                                        + sensorsEntry.KEY_NODE_RELEASETIME + " INTEGER,"
                                                        + sensorsEntry.KEY_NODE_NAME + " TEXT,"
                                                        + sensorsEntry.KEY_NODE_TYPE + " INTEGER,"
                                                        + sensorsEntry.KEY_NODE_TOPIC + " TEXT" + ")";

    private static final String CREATE_DATA_NODES_TABLE = "CREATE TABLE " + payloadEntry.TABLE_NAME + "("
                                                        + payloadEntry._ID + " INTEGER PRIMARY KEY,"
                                                        + payloadEntry.KEY_NODE_ID + " INTEGER,"
                                                        + payloadEntry.KEY_NODE_TYPE + " INTEGER,"
                                                        + payloadEntry.KEY_NODE_PAYLOAD + " TEXT,"
                                                        + payloadEntry.KEY_NODE_UPDATE + " INTEGER,"
                                                        + "FOREIGN KEY("+ payloadEntry.KEY_NODE_ID + ") REFERENCES " + sensorsEntry.TABLE_NAME + "(" +sensorsEntry._ID+ ") );";

    /**
     * List of all sensors
     */
    private final ArrayList<rf24Node> nodeList = new ArrayList<rf24Node>();

    /**
     * List of all payloads
     */
    private final ArrayList<rf24NodePayload> payloadList = new ArrayList<rf24NodePayload>();

//    /**
//     *  Constructor for sensor database
//      * @param context
//     */
    public DatabaseHandler(Context context) {
        super(context, DATABASE_NAME, null, DATABASE_VERSION);
    }


    /**
     * Creating Tables
      */
    @Override
    public void onCreate(SQLiteDatabase db) {
        db.execSQL(CREATE_NODES_TABLE);
        db.execSQL(CREATE_DATA_NODES_TABLE);

    }

    /*
     * Upgrading database
      */
    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {

        // TODO: update routine
        Log.w(DatabaseHandler.class.getName(),
                "Upgrading database from version " + oldVersion + " to "
                        + newVersion + ", which will destroy all old data");

        // Drop older table if existed
        db.execSQL("DROP TABLE IF EXISTS " + payloadEntry.TABLE_NAME);
        db.execSQL("DROP TABLE IF EXISTS " + sensorsEntry.TABLE_NAME);
        // Create tables again
        onCreate(db);
    }


    /**
     * All CRUD(Create, Read, Update, Delete) Operations
     */

    /**
     * Adding new node
     * @param node
     */
    public void addNode(rf24Node node) {
        // check if node exists
        rf24Node tmp = getNode(node.getNodeID());

        // adding
        if (tmp.getNodeID() == 0) {
            SQLiteDatabase db = this.getWritableDatabase();
            ContentValues values = new ContentValues();
            // Putting values in ContentValues
            values.put(sensorsEntry._ID, node.getNodeID());
            values.put(sensorsEntry.KEY_NODE_NAME, node.getName());
            values.put(sensorsEntry.KEY_NODE_TYPE, node.getType());
            values.put(sensorsEntry.KEY_NODE_RELEASETIME, node.getReleaseTimeAddr());
            values.put(sensorsEntry.KEY_NODE_ADDRESS, node.getAddress());
            values.put(sensorsEntry.KEY_NODE_TOPIC, node.getTopic());
            // Inserting Row
            db.insert(sensorsEntry.TABLE_NAME, null, values);
            db.close(); // Closing database connection


        }else  // updating
        {
            tmp.setAddress(node.getAddress());
            tmp.setName(node.getName());
            tmp.setTopic(node.getTopic());
            // updating node
            updateNode(tmp);
        }
    }

    /**
     * Getting single node
     * @param nodeid: node id to get from database
     * @return
     */
    rf24Node getNode(int nodeid) {
        rf24Node node = new rf24Node();
        SQLiteDatabase db = this.getReadableDatabase();

        Cursor cursor = db.query(sensorsEntry.TABLE_NAME, new String[] {sensorsEntry._ID, sensorsEntry.KEY_NODE_ADDRESS, sensorsEntry.KEY_NODE_RELEASETIME, sensorsEntry.KEY_NODE_NAME, sensorsEntry.KEY_NODE_TYPE, sensorsEntry.KEY_NODE_TOPIC }, sensorsEntry._ID + "=?",
                new String[] { String.valueOf(nodeid) }, null, null, null, null);

        if (cursor.getCount() != 0) {
            cursor.moveToFirst();
            //node = new rf24Node(Integer.parseInt(cursor.getString(0)), (short) Integer.parseInt(cursor.getString(1)), (short) Integer.parseInt(cursor.getString(2)),
            //        cursor.getString(3), cursor.getString(4));
            node = new rf24Node();
            node.setNodeID((short)Integer.parseInt(cursor.getString(0)));
            node.setAddress((short)Integer.parseInt(cursor.getString(1)), cursor.getLong(2));
            node.setName(cursor.getString(3));
            node.setType((short)Integer.parseInt(cursor.getString(4)));
            node.setTopic(cursor.getString(5));

        }
        // return contact
        cursor.close();
        db.close();

        return node;
    }

    /**
     * Getting all added nodes
     * @return
     */
    public ArrayList<rf24Node> getNodes() {
        rf24Node empty = new rf24Node();

        try {
            nodeList.clear();
            // Select All Query
            String selectQuery = "SELECT  * FROM " + sensorsEntry.TABLE_NAME;

            SQLiteDatabase db = this.getWritableDatabase();
            Cursor cursor = db.rawQuery(selectQuery, null);

            // looping through all rows and adding to list
            if (cursor.moveToFirst()) {
                do {

                    rf24Node node = new rf24Node();
                    node.setNodeID((short)Integer.parseInt(cursor.getString(0)));
                    node.setAddress((short)Integer.parseInt(cursor.getString(1)), cursor.getLong(2));
                    node.setName(cursor.getString(3));
                    node.setType((short)Integer.parseInt(cursor.getString(4)));
                    node.setTopic(cursor.getString(5));

                    // Get last payload
                    node.payload = getNodePayload(node.getNodeID());

                    // Adding contact to list
                    nodeList.add(node);
                } while (cursor.moveToNext());
            }

            nodeList.add(empty);

            // return contact list
            cursor.close();
            db.close();
            return nodeList;
        } catch (Exception e) {
            // TODO: handle exception
            nodeList.add(empty);
            Log.e("all_contact", "" + e);
        }

        return nodeList;
    }

    /**
     * Updating single node
     * @param node rf24Node to be updated in the database
     * @return 1 if updated, otherwise error
     */
    public int updateNode(rf24Node node) {
        SQLiteDatabase db = this.getWritableDatabase();

        ContentValues values = new ContentValues();
        // Putting values in ContentValues
        values.put(sensorsEntry._ID, node.getNodeID());
        values.put(sensorsEntry.KEY_NODE_NAME, node.getName());
        values.put(sensorsEntry.KEY_NODE_ADDRESS, node.getAddress());
        values.put(sensorsEntry.KEY_NODE_RELEASETIME, node.getReleaseTimeAddr());
        values.put(sensorsEntry.KEY_NODE_TOPIC, node.getTopic());
        values.put(sensorsEntry.KEY_NODE_TYPE, node.getType());

        // updating row
        return db.update(sensorsEntry.TABLE_NAME, values, sensorsEntry._ID + " = ?",
                new String[] { String.valueOf(node.getNodeID()) });
    }

    /**
     * Deleting single node
     * @param id: node id to be deleted
     */
    public void deleteNode(int id) {
        SQLiteDatabase db = this.getWritableDatabase();
        db.delete(sensorsEntry.TABLE_NAME, sensorsEntry._ID + " = ?",
                new String[] { String.valueOf(id) });
        db.close();
    }

    /**
     * Getting the number of sensors saved in the database
     * @return number of sensors added in database
     */
    public int getNrNodes() {
        int ret;
        String countQuery = "SELECT  * FROM " + sensorsEntry.TABLE_NAME;
        SQLiteDatabase db = this.getReadableDatabase();
        Cursor cursor = db.rawQuery(countQuery, null);
        // return count
        ret = cursor.getCount();
        cursor.close();
        db.close();
        return ret;
    }

    /**
     * Adding a payload to the payload database table
     * @param node: rf24Node including payload
     */
    public void addPayload(rf24Node node) {

        SQLiteDatabase db = this.getWritableDatabase();
        ContentValues values = new ContentValues();
        // Putting values in ContentValues
        values.put(payloadEntry.KEY_NODE_ID, node.getNodeID());
        values.put(payloadEntry.KEY_NODE_TYPE, node.getType());
        values.put(payloadEntry.KEY_NODE_PAYLOAD, node.payload.getPayload());
        values.put(payloadEntry.KEY_NODE_UPDATE, node.payload.getUpdate());
        // Inserting Row
        db.insert(payloadEntry.TABLE_NAME, null, values);
        db.close(); // Closing database connection
    }


    /**
     * Getting a payload from database
     * @param payloadid: payload id
     * @return rf24NodePayload
     */
    rf24NodePayload getPayload(int payloadid) {
        rf24NodePayload payload = new rf24NodePayload();
        SQLiteDatabase db = this.getReadableDatabase();

        Cursor cursor = db.query(payloadEntry.TABLE_NAME, new String[] {payloadEntry._ID,
                        payloadEntry.KEY_NODE_ID, payloadEntry.KEY_NODE_TYPE, payloadEntry.KEY_NODE_PAYLOAD, payloadEntry.KEY_NODE_UPDATE}, payloadEntry._ID + "=?",
                new String[] { String.valueOf(payloadid) }, null, null, null, null);

        if (cursor.getCount() != 0) {
            cursor.moveToFirst();

            payload = new rf24NodePayload();
            payload.payloadID = (short)Integer.parseInt(cursor.getString(0));
            payload.nodeID = (short)Integer.parseInt(cursor.getString(1));
            payload.type = (short)Integer.parseInt(cursor.getString(2));
            payload.setPayload(cursor.getString(3), (short)Integer.parseInt(cursor.getString(4)));
        }
        // return contact
        cursor.close();
        db.close();

        return payload;
    }


    /**
     * Getting the last payload from a node with nodeid
     * @param nodeid: node id
     * @return rf24NodePayload
     */
    rf24NodePayload getNodePayload(int nodeid) {
        rf24NodePayload payload = new rf24NodePayload();
        SQLiteDatabase db = this.getReadableDatabase();

        Cursor cursor = db.query(payloadEntry.TABLE_NAME, new String[] {payloadEntry._ID,
                        payloadEntry.KEY_NODE_ID, payloadEntry.KEY_NODE_TYPE, payloadEntry.KEY_NODE_PAYLOAD, payloadEntry.KEY_NODE_UPDATE}, payloadEntry.KEY_NODE_ID + "=?",
                new String[] { String.valueOf(nodeid) }, null, null, null, null);

        if (cursor.getCount() != 0) {
            cursor.moveToLast();

            payload = new rf24NodePayload();
            payload.payloadID = (short)Integer.parseInt(cursor.getString(0));
            payload.nodeID = (short)Integer.parseInt(cursor.getString(1));
            payload.type = (short)Integer.parseInt(cursor.getString(2));
            payload.setPayload(cursor.getString(3), Long.parseLong(cursor.getString(4)));
        }
        // return contact
        cursor.close();
        db.close();

        return payload;
    }

    /**
     * Getting all added payloads
     * @return
     */
    public ArrayList<rf24NodePayload> getNodesPayloads() {
        return getNodesPayloads(0);
    }

    /**
     * Getting all added payloads from nodeID
     * @param nodeID: id of the node
     * @return
     */
    public ArrayList<rf24NodePayload> getNodesPayloads(int nodeID) {
        rf24NodePayload empty = new rf24NodePayload();
        SQLiteDatabase db = this.getWritableDatabase();

        try {
            payloadList.clear();
            // Select All Query
            Cursor cursor;

            if(nodeID == 0)
                cursor = db.rawQuery("SELECT  * FROM " + payloadEntry.TABLE_NAME, null);
            else
                cursor = db.query(payloadEntry.TABLE_NAME, new String[] {payloadEntry._ID,
                            payloadEntry.KEY_NODE_ID, payloadEntry.KEY_NODE_TYPE, payloadEntry.KEY_NODE_PAYLOAD, payloadEntry.KEY_NODE_UPDATE}, payloadEntry.KEY_NODE_ID + "=?",
                    new String[] { String.valueOf(nodeID) }, null, null, null, null);

            // looping through all rows and adding to list
            if (cursor.moveToFirst()) {
                do {
                    rf24NodePayload payload = new rf24NodePayload();
                    payload.payloadID = (short)Integer.parseInt(cursor.getString(0));
                    payload.type = (short)Integer.parseInt(cursor.getString(1));
                    payload.setPayload(cursor.getString(2), cursor.getLong(3));
                    // Adding contact to list
                    payloadList.add(payload);
                } while (cursor.moveToNext());
            }

            payloadList.add(empty);

            // return contact list
            cursor.close();
            db.close();
            return payloadList;
        } catch (Exception e) {
            // TODO: handle exception
            payloadList.add(empty);
            Log.e("all_contact", "" + e);
        }

        return payloadList;
    }

    /**
     * Get number of total payloads added to the database
     * @return
     */
    public int getNrPayloads()
    {
        return getNrPayloads(0);
    }
    /**
     * Get number of payloads added from node
     * @param nodeID: id of the node
     * @return
     */
    public int getNrPayloads(int nodeID) {
        int ret;
        SQLiteDatabase db = this.getReadableDatabase();
        Cursor cursor;
        if(nodeID == 0)
            cursor = db.rawQuery("SELECT  * FROM " + payloadEntry.TABLE_NAME, null);
        else
            cursor = db.query(payloadEntry.TABLE_NAME,
                    null, payloadEntry.KEY_NODE_ID + "=?",
                    new String[] { String.valueOf(nodeID) }, null, null, null, null);
        // return count
        ret = cursor.getCount();
        cursor.close();
        db.close();
        return ret;
    }
}


