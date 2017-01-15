package com.lemariva.androidthings.rf24;

import java.sql.Date;
import java.util.ArrayList;

import android.icu.text.SimpleDateFormat;
import android.support.v7.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.widget.TextView;

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

public class MyAdapter extends RecyclerView.Adapter<MyAdapter.ViewHolder> {
    private ArrayList<rf24Node> values;

    // Provide a reference to the views for each data item
    // Complex data items may need more than one view per item, and
    // you provide access to all the views for a data item in a view holder
    public class ViewHolder extends RecyclerView.ViewHolder {
        // each data item is just a string in this case
        public TextView txtHeader;
        public TextView txtAddress;
        public TextView txtAddressRelease;
        public View layout;

        public ViewHolder(View v) {
            super(v);
            layout = v;
            txtHeader = (TextView) v.findViewById(R.id.nodeID);
            txtAddress = (TextView) v.findViewById(R.id.nodeAddress);
            txtAddressRelease = (TextView) v.findViewById(R.id.nodeAddressRelease);
        }
    }

    public void add(int position, rf24Node item) {
        values.add(position, item);
        notifyItemInserted(position);
    }

    public void remove(int position) {
        values.remove(position);
        notifyItemRemoved(position);
    }

    // Provide a suitable constructor (depends on the kind of dataset)
    public MyAdapter(ArrayList<rf24Node> myDataset) {
        values = myDataset;
    }

    // Create new views (invoked by the layout manager)
    @Override
    public MyAdapter.ViewHolder onCreateViewHolder(ViewGroup parent,
                                                   int viewType) {
        // create a new view
        LayoutInflater inflater = LayoutInflater.from(
                parent.getContext());
        View v =
                inflater.inflate(R.layout.rowlayout, parent, false);
        // set the view's size, margins, paddings and layout parameters
        ViewHolder vh = new ViewHolder(v);
        return vh;
    }

    // Replace the contents of a view (invoked by the layout manager)
    @Override
    public void onBindViewHolder(ViewHolder holder, final int position) {
        SimpleDateFormat sdf = new SimpleDateFormat("MMM dd, yyyy - HH:mm");

        // - get element from your dataset at this position
        // - replace the contents of the view with that element
        final String nodeID = "Node ID: " + Integer.toOctalString(values.get(position).getNodeID());
        final String nodeAddress = "Address: " + values.get(position).getAddress();

        Date resultdate = new Date( values.get(position).getReleaseTimeAddr());
        final String dateRelease = "Address released on: " + sdf.format(resultdate);

        holder.txtHeader.setText(nodeID);
        holder.txtHeader.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                remove(position);
            }
        });

        holder.txtAddress.setText(nodeAddress);
        holder.txtAddressRelease.setText(dateRelease);
    }

    // Return the size of your dataset (invoked by the layout manager)
    @Override
    public int getItemCount() {
        return values.size() - 1;
    }

}