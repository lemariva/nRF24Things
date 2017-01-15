
package com.lemariva.androidthings.rf24;

import com.google.android.things.pio.PeripheralManagerService;
import android.os.Build;
import java.util.List;

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

@SuppressWarnings("WeakerAccess")

public class BoardDefaults {
    private static final String DEVICE_EDISON_ARDUINO = "edison_arduino";
    private static final String DEVICE_EDISON = "edison";
    private static final String DEVICE_RPI3 = "rpi3";
    private static final String DEVICE_NXP = "imx6ul";

    private static String sBoardVariant = "";
    /**
     * Return the preferred SPI port for each board.
     */
    public static String getSPIPort1() {
        switch (Build.DEVICE) {
            // same for Edison Arduino breakout and Edison SOM
            case DEVICE_RPI3:
                return "SPI0.1";
            default:
                throw new IllegalStateException("Unknown Build.DEVICE " + Build.DEVICE);
        }
    }

    public static String getSPIPort0() {
        switch (Build.DEVICE) {
            // same for Edison Arduino breakout and Edison SOM
            case DEVICE_RPI3:
                return "SPI0.0";
            default:
                throw new IllegalStateException("Unknown Build.DEVICE " + Build.DEVICE);
        }
    }

    /**
     * Return the GPIO pin that CE pin is connected
     */
    public static String getGPIOce() {
        switch (getBoardVariant())  {
            case DEVICE_RPI3:
                return "BCM22";
            default:
                throw new IllegalStateException("Unknown Build.DEVICE " + Build.DEVICE);
        }
    }

    /**
     * Return the GPIO pin that CSN pin is connected
     */
    public static String getGPIOcsn() {
        switch (getBoardVariant())  {
            case DEVICE_RPI3:
                return "BCM7";
            default:
                throw new IllegalStateException("Unknown Build.DEVICE " + Build.DEVICE);
        }
    }

    private static String getBoardVariant() {
        if (!sBoardVariant.isEmpty()) {
            return sBoardVariant;
        }
        sBoardVariant = Build.DEVICE;
        // For the edison check the pin prefix
        // to always return Edison Breakout pin name when applicable.
        if (sBoardVariant.equals(DEVICE_EDISON)) {
            PeripheralManagerService pioService = new PeripheralManagerService();
            List<String> gpioList = pioService.getGpioList();
            if (gpioList.size() != 0) {
                String pin = gpioList.get(0);
                if (pin.startsWith("IO")) {
                    sBoardVariant = DEVICE_EDISON_ARDUINO;
                }
            }
        }
        return sBoardVariant;
    }
}