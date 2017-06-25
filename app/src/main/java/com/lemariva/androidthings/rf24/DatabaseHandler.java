package com.lemariva.androidthings.rf24;

import java.util.ArrayList;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.util.Log;

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

public class DatabaseHandler extends SQLiteOpenHelper {

    /**
     * Database version
     */
    private static final int DATABASE_VERSION = 23;

    /**
     * Database name
      */
    private static final String DATABASE_NAME = "SensorMesh";

    /**
     * Sensor table name
      */
    private static final String TABLE_NODES = "Sensors";

    /**
     * Sensor table columns names
      */
    private static final String KEY_ID = "id";
    private static final String KEY_NODE_ID = "sensor_id";
    private static final String KEY_NODE_NAME = "name";
    private static final String KEY_NODE_TYPE = "type";
    private static final String KEY_NODE_ADDRESS = "address";
    private static final String KEY_NODE_RELEASETIME = "releasetime";
    private static final String KEY_NODE_TOPIC = "topic";

    private static final String CREATE_NODES_TABLE = "CREATE TABLE " + TABLE_NODES + "("
                                                        + KEY_ID + " INTEGER PRIMARY KEY,"
                                                        + KEY_NODE_ID + " INTEGER,"
                                                        + KEY_NODE_ADDRESS + " TEXT,"
                                                        + KEY_NODE_RELEASETIME + " INTEGER,"
                                                        + KEY_NODE_NAME + " TEXT,"
                                                        + KEY_NODE_TYPE + " TEXT,"
                                                        + KEY_NODE_TOPIC + " TEXT" + ")";
    /**
     * List of all sensors
     */
    private final ArrayList<rf24Node> nodeList = new ArrayList<rf24Node>();

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
        db.execSQL("DROP TABLE IF EXISTS " + TABLE_NODES);

        // Create tables again
        onCreate(db);
    }


    /**
     * All CRUD(Create, Read, Update, Delete) Operations
     */

    // Adding new node
    public void addNode(rf24Node node) {
        // check if node exists
        rf24Node tmp = getNode(node.getNodeID());

        // adding
        if (tmp.getID() == 0) {
            SQLiteDatabase db = this.getWritableDatabase();
            ContentValues values = new ContentValues();
            // Putting values in ContentValues
            values.put(KEY_NODE_ID, node.getNodeID());
            values.put(KEY_NODE_NAME, node.getName());
            values.put(KEY_NODE_TYPE, node.getType());
            values.put(KEY_NODE_RELEASETIME, node.getReleaseTimeAddr());
            values.put(KEY_NODE_ADDRESS, node.getAddress());
            values.put(KEY_NODE_TOPIC, node.getTopic());
            // Inserting Row
            db.insert(TABLE_NODES, null, values);
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

    // Getting single node
    rf24Node getNode(int nodeid) {
        rf24Node node = new rf24Node();
        SQLiteDatabase db = this.getReadableDatabase();

        Cursor cursor = db.query(TABLE_NODES, new String[] { KEY_ID,
                        KEY_NODE_ID, KEY_NODE_ADDRESS, KEY_NODE_RELEASETIME, KEY_NODE_NAME, KEY_NODE_TYPE, KEY_NODE_TOPIC }, KEY_NODE_ID + "=?",
                new String[] { String.valueOf(nodeid) }, null, null, null, null);

        if (cursor.getCount() != 0) {
            cursor.moveToFirst();
            //node = new rf24Node(Integer.parseInt(cursor.getString(0)), (short) Integer.parseInt(cursor.getString(1)), (short) Integer.parseInt(cursor.getString(2)),
            //        cursor.getString(3), cursor.getString(4));
            node = new rf24Node();
            node.setID(Integer.parseInt(cursor.getString(0)));
            node.setNodeID((short)Integer.parseInt(cursor.getString(1)));
            node.setAddress((short)Integer.parseInt(cursor.getString(2)), cursor.getLong(3));
            node.setName(cursor.getString(4));
            node.setType(cursor.getString(5));
            node.setTopic(cursor.getString(6));

        }
        // return contact
        cursor.close();
        db.close();

        return node;
    }

    // Getting All Nodes
    public ArrayList<rf24Node> getNodes() {
        rf24Node empty = new rf24Node();

        try {
            nodeList.clear();
            // Select All Query
            String selectQuery = "SELECT  * FROM " + TABLE_NODES;

            SQLiteDatabase db = this.getWritableDatabase();
            Cursor cursor = db.rawQuery(selectQuery, null);

            // looping through all rows and adding to list
            if (cursor.moveToFirst()) {
                do {

                    //rf24Node node = new rf24Node(Integer.parseInt(cursor.getString(0)), (short)Integer.parseInt(cursor.getString(1)), (short)Integer.parseInt(cursor.getString(2)),
                    //        cursor.getString(3), cursor.getString(4));
                    rf24Node node = new rf24Node();
                    node.setID(Integer.parseInt(cursor.getString(0)));
                    node.setNodeID((short)Integer.parseInt(cursor.getString(1)));
                    node.setAddress((short)Integer.parseInt(cursor.getString(2)), cursor.getLong(3));
                    node.setName(cursor.getString(4));
                    node.setType(cursor.getString(5));
                    node.setTopic(cursor.getString(6));

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

    // Updating single node
    public int updateNode(rf24Node node) {
        SQLiteDatabase db = this.getWritableDatabase();

        ContentValues values = new ContentValues();
        // Putting values in ContentValues
        values.put(KEY_NODE_ID, node.getNodeID());
        values.put(KEY_NODE_NAME, node.getName());
        values.put(KEY_NODE_ADDRESS, node.getAddress());
        values.put(KEY_NODE_RELEASETIME, node.getReleaseTimeAddr());
        values.put(KEY_NODE_TOPIC, node.getTopic());
        values.put(KEY_NODE_TYPE, node.getType());

        // updating row
        return db.update(TABLE_NODES, values, KEY_ID + " = ?",
                new String[] { String.valueOf(node.getID()) });
    }

    // Deleting single node
    public void deleteNode(int id) {
        SQLiteDatabase db = this.getWritableDatabase();
        db.delete(TABLE_NODES, KEY_ID + " = ?",
                new String[] { String.valueOf(id) });
        db.close();
    }

    // Getting node count
    public int getNrNodes() {
        int ret;
        String countQuery = "SELECT  * FROM " + TABLE_NODES;
        SQLiteDatabase db = this.getReadableDatabase();
        Cursor cursor = db.rawQuery(countQuery, null);
        // return count
        ret = cursor.getCount();
        cursor.close();
        db.close();
        return ret;
    }
}


