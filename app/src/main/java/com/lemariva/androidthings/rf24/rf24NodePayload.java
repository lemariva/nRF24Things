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


public class rf24NodePayload {
    /**
     *  Payload Database ID
     */
    public short payloadID;
    /**
     *  Node ID
     */
    public short nodeID;
    /**
     *  Node type
     */
    public short type;
    /**
     *  Node info
     */
    private String payload;
    /**
     *  Node update time (in milliseconds)
     */
    private long update;


    public void setPayload(String payload)
    {
        this.payload = payload;
        this.update = millis();
    }

    public void setPayload(String payload, long update)
    {
        this.payload = payload;
        this.update = update;
    }

    public String getPayload()
    {
        return payload;
    }

    public long getUpdate()
    {
        return update;
    }

    private long millis()
    {
        return System.currentTimeMillis();
    }
}
