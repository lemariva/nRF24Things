

package com.lemariva.androidthings.rf24;

import com.google.android.things.pio.SpiDevice;
import com.google.android.things.pio.Gpio;
import com.google.android.things.pio.PeripheralManagerService;

import java.io.IOException;
import android.util.Log;

import java.util.Arrays;

/*
 Copyright (C) 2011 J. Coliz <maniacbug@ymail.com>
 This program is free software; you can redistribute it and/or
 modify it under the terms of the GNU General Public License
 version 2 as published by the Free Software Foundation.
*/

/** Rewritten for Java by:
 *  Mauro Riva <lemariva@mail.com> <lemariva.com>
 *
 *  Originally written in C/C++ by:
 *      J. Coliz < https://github.com/maniacbug/RF24 >
 *      and optimized by: TMRh20 < https://github.com/TMRh20/RF24 >
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

@SuppressWarnings({"unused", "WeakerAccess"})
public class rf24 implements AutoCloseable {

    private static final String TAG = rf24.class.getSimpleName();

    /**
     * Pin status LOW = 0 (0v), HIGH = 1 (3.3v)
     */
    public static final boolean LOW  = false;
    public static final boolean HIGH = true;

    /**
     * Power Amplifier level.
     * For use with
     * @see #setPALevel(int)
     * @see #getPALevel()
     */
    public enum rf24_pa_dbm_e { RF24_PA_MIN,RF24_PA_LOW, RF24_PA_HIGH, RF24_PA_MAX, RF24_PA_ERROR }

    /**
     * Data rate.  How fast data moves through the air.
     * For use with
     * @see #setDataRate(rf24_datarate_e)
     * @see #getDataRate()
     */
    public enum rf24_datarate_e { RF24_1MBPS, RF24_2MBPS, RF24_250KBPS }

    /**
     * CRC Length.  How big (if any) of a CRC is included.
     * For use with
     * @see #setChannel(int)
     * @see #getChannel()
     */
    public enum rf24_crclength_e { RF24_CRC_DISABLED, RF24_CRC_8, RF24_CRC_16 }

    /**
     *  enabled RX addresses, address data pipe #, number of bytes in RX payload in data pipe #
     */
    public static final int[] child_pipe_enable = { nRF24L01.ERX_P0, nRF24L01.ERX_P1, nRF24L01.ERX_P2, nRF24L01.ERX_P3, nRF24L01.ERX_P4, nRF24L01.ERX_P5 };
    public static final int child_pipe[] = { nRF24L01.RX_ADDR_P0, nRF24L01.RX_ADDR_P1, nRF24L01.RX_ADDR_P2, nRF24L01.RX_ADDR_P3, nRF24L01.RX_ADDR_P4, nRF24L01.RX_ADDR_P5 };
    public static final int child_payload_size[] = { nRF24L01.RX_PW_P0, nRF24L01.RX_PW_P1, nRF24L01.RX_PW_P2, nRF24L01.RX_PW_P3, nRF24L01.RX_PW_P4, nRF24L01.RX_PW_P5 };

    /*
     * Android things libraries
     */

    /** PeripheralManagerService */
    PeripheralManagerService pioService;
    /** SPI device */
    private SpiDevice mDevice;
    /** CE (GPIO 'device') */
    private Gpio mCEpin;

    /** "Chip Enable" pin, activates the RX or TX role */
    private byte ce_pin;
    /** SPI Chip select */
    private byte csn_pin;
    /** SPI Bus Speed */
    private int spi_speed;

    /** SPI receive buffer (payload max 32 bytes) */
    byte[] spi_rxbuff = new byte[32+1] ;

    /** SPI transmit buffer (payload max 32 bytes + 1 byte for the command) */
    byte[] spi_txbuff = new byte[32+1] ;

    /** False for RF24L01 and true for RF24L01P */
    boolean p_variant;
    /** Fixed size of payloads */
    int payload_size;
    /** Whether dynamic payloads are enabled. */
    boolean dynamic_payloads_enabled;
    /** Last address set on pipe 0 for reading. */
    int[] pipe0_reading_address = new int[5];
    /** The address width to use - 3,4 or 5 bytes. */
    int addr_width;

    /** Radio channnel selected*/
    int _channel;
    /**
     * This is intended to minimise the speed of SPI polling due to radio commands
     * If using interrupts or timed requests, this can be set to 0 Default:5
     */
    int csDelay;
    /**
     * The driver will delay for this duration when stopListening() is called
     *
     * When responding to payloads, faster devices like ARM(RPi) are much faster than Arduino:
     * 1. Arduino sends data to RPi, switches to RX mode
     * 2. The RPi receives the data, switches to TX mode and sends before the Arduino radio is in RX mode
     * 3. If AutoACK is disabled, this can be set as low as 0. If AA/ESB enabled, set to 100uS minimum on RPi
     *
     * If set to 0, ensure 130uS delay after stopListening() and before any sends
     */
    int txDelay;
    /**
     * Enable error detection by un-commenting #define FAILURE_HANDLING in RF24_config.h
     * If a failure has been detected, it usually indicates a hardware issue. By default the library
     * will cease operation when a failure is detected.
     * This should allow advanced users to detect and resolve intermittent hardware issues.
     *
     * In most cases, the radio must be re-enabled via radio.begin(); and the appropriate settings
     * applied after a failure occurs, if wanting to re-enable the device immediately.
     *
     * Usage: (Failure handling must be enabled per above)
     * { @code
     *  if(radio.failureDetected){
     *    radio.begin();                       // Attempt to re-configure the radio with defaults
     *    radio.failureDetected = 0;           // Reset the detection value
     *	radio.openWritingPipe(addresses[1]); // Re-configure pipe addresses
     *    radio.openReadingPipe(1,addresses[0]);
     *    report_failure();                    // Blink leds, send a message, etc. to indicate failure
     *  }
     *  }
     */
    boolean failureDetected;

    /**
     * Constructor
     *
     * Creates a new instance of this driver.  Before using, you create an instance
     * and send in the unique pins that this chip is connected to.
     *
     * @param _cepin The pin attached to Chip Enable on the RF module (not implemented yet) //TODO: allow other pin selection for CE
     * @param _cspin The pin attached to Chip Select (can be only 0(CS0) or 1(CS1))
     * @param _spi_speed For RPi, the SPI speed in MHZ ie: 16000000
     */
    public rf24(byte _cepin, byte _cspin, int _spi_speed) {
        ce_pin  = _cepin;
        csn_pin = _cspin;
        spi_speed = _spi_speed;
        p_variant = false;
        payload_size = 32;
        dynamic_payloads_enabled = false;
        addr_width = 5;
        csDelay = 5;
        pipe0_reading_address[0]=0;

        try {
            pioService = new PeripheralManagerService();
        }catch(Exception e)
        {
            Log.e(TAG, "Unable to access PeripheralManagerService", e);
        }
    }

    /**
     * Begin operation of the nrf24l01+ with standard configuration
     *
     * Call this in setup(), before calling any other methods.
     * @code radio.begin() @endcode
     * @return true if correctly configured, false otherwise (SPI problems, GPIO problems, etc...)
     * throws IOException if write/read on spi bus doesn't work if write/read on spi bus doesn't work
     */

    public boolean begin() throws IOException {
        int setup;
        String SpiPort = "SPI0.0"; // default

        switch (csn_pin) {     //Ensure valid hardware CS pin
            case 0:
                SpiPort = BoardDefaults.getSPIPort0();
                break;
            case 1:
                SpiPort = BoardDefaults.getSPIPort1();
                break;
        }

        // Initializing SPI
        try {
            mDevice = pioService.openSpiDevice(SpiPort);

            // Low clock, leading edge transfer
            mDevice.setMode(SpiDevice.MODE0);

            mDevice.setFrequency(spi_speed);     // _spi_speed MHz
            mDevice.setBitsPerWord(8);           // 8 BPW
            mDevice.setBitJustification(false);  // MSB first

        } catch (IOException e) {
            Log.w(TAG, "Unable to access SPI device", e);
            return false;
        }


        // Initializing CE GPIO
        try {
            //TODO: allow other pin selection for CE
            String pinName = BoardDefaults.getGPIOce();
            mCEpin = pioService.openGpio(pinName);
            mCEpin.setDirection(Gpio.DIRECTION_OUT_INITIALLY_LOW);
            Log.i(TAG, "CE pin initialized!");

        } catch (IOException e) {
            Log.e(TAG, "Error on initializing CE pin", e);
            return false;
        }

        delay(100);

        // Must allow the radio time to settle else configuration bits will not necessarily stick.
        // This is actually only required following power up but some settling time also appears to
        // be required after resets too. For full coverage, we'll always assume the worst.
        // Enabling 16b CRC is by far the most obvious case if the wrong timing is used - or skipped.
        // Technically we require 4.5ms + 14us as a worst case. We'll just call it 5ms for good measure.
        // WARNING: Delay is based on P-variant whereby non-P *may* require different timing.
        delay(5);

        // Reset NRF_CONFIG and enable 16-bit CRC.
        write_register(nRF24L01.NRF_CONFIG, (0x0C));

        // Set 1500uS (minimum for 32B payload in ESB@250KBPS) timeouts, to make testing a little easier
        // WARNING: If this is ever lowered, either 250KBS mode with AA is broken or maximum packet
        // sizes must never be used. See documentation for a more complete explanation.
        setRetries(5, 15);

        // Reset value is MAX
        //setPALevel( RF24_PA_MAX ) ;

        // check for connected module and if this is a p nRF24l01 variant
        //
        if (setDataRate(rf24_datarate_e.RF24_250KBPS)) {
            p_variant = true;
        }
        setup = read_register(nRF24L01.RF_SETUP);

        // Then set the data rate to the slowest (and most reliable) speed supported by all
        // hardware.
        setDataRate(rf24_datarate_e.RF24_1MBPS);

        // Initialize CRC and request 2-byte (16bit) CRC
        //setCRCLength( RF24_CRC_16 ) ;

        // Disable dynamic payloads, to match dynamic_payloads_enabled setting - Reset value is 0
        toggle_features();
        write_register(nRF24L01.FEATURE, 0);
        write_register(nRF24L01.DYNPD, 0);

        // Reset current status
        // Notice reset and flush is the last thing we do
        write_register(nRF24L01.NRF_STATUS, (_BV(nRF24L01.RX_DR) | _BV(nRF24L01.TX_DS) | _BV(nRF24L01.MAX_RT)));

        // Set up default configuration.  Callers can always change it later.
        // This channel should be universally safe and not bleed over into adjacent
        // spectrum.
        setChannel(76);

        // Flush buffers
        flush_rx();
        flush_tx();

        powerUp(); //Power up by default when begin() is called

        // Enable PTX, do not write CE high so radio will remain in standby I mode ( 130us max to transition to RX or TX instead of 1500us from powerUp )
        // PTX should use only 22uA of power
        write_register(nRF24L01.NRF_CONFIG, ((read_register(nRF24L01.NRF_CONFIG)) & ~_BV(nRF24L01.PRIM_RX)));

        // if setup is 0 or ff then there was no response from module
        return (setup != 0 && setup != 0xff);
    }

    /**
     * Start listening on the pipes opened for reading.
     *
     * 1. Be sure to call openReadingPipe() first.
     * 2. Do not call write() while in this mode, without first calling stopListening().
     * 3. Call available() to check for incoming traffic, and read() to get it.
     *
     *  Open reading pipe 1 using address given by openReadingPipe
     * {@code
     * byte address[] = { 0x##,0x##,0x##,0x##,0x## };
     * radio.openReadingPipe(1,address);
     * radio.startListening();
     * }
     * @throws IOException if write on spi doesn't work
     */
    public void startListening()  throws IOException {

        powerUp();

        write_register(nRF24L01.NRF_CONFIG, read_register(nRF24L01.NRF_CONFIG) | _BV(nRF24L01.PRIM_RX));
        write_register(nRF24L01.NRF_STATUS, _BV(nRF24L01.RX_DR) | _BV(nRF24L01.TX_DS) | _BV(nRF24L01.MAX_RT) );
        ce(HIGH);

        // Restore the pipe0 adddress, if exists
        if (pipe0_reading_address[0] != 0){
            write_register(nRF24L01.RX_ADDR_P0, pipe0_reading_address, (short)addr_width);
        }else{
            closeReadingPipe(0);
        }

        // Flush buffers
        //flush_rx();
        if((read_register(nRF24L01.FEATURE) & _BV(nRF24L01.EN_ACK_PAY)) == _BV(nRF24L01.EN_ACK_PAY)){
            flush_tx();
        }
        // Go!
        //delayMicroseconds(100);
    }

    /**
     * Stop listening for incoming messages, and switch to transmit mode.
     *
     * Do this before calling write().
     * {}@code
     * radio.stopListening();
     * radio.write(data, data.length);
     * }
     * @throws IOException when write/read on spi bus doesn't work
     */

    public void stopListening() throws IOException {
        ce(LOW);

        delayMicroseconds(txDelay);

        if((read_register(nRF24L01.FEATURE) & _BV(nRF24L01.EN_ACK_PAY)) == _BV(nRF24L01.EN_ACK_PAY)){
            delayMicroseconds(txDelay); //200
            flush_tx();
        }
        //flush_rx();
        write_register(nRF24L01.NRF_CONFIG, ( read_register(nRF24L01.NRF_CONFIG) ) & ~_BV(nRF24L01.PRIM_RX) );
        write_register(nRF24L01.EN_RXADDR,read_register(nRF24L01.EN_RXADDR) | _BV(child_pipe_enable[0])); // Enable RX on pipe0

        delayMicroseconds(1000);
    }

    /**
     * Test whether there are bytes available to be read in the
     * FIFO buffers.
     *
     * {@code
     * int pipeNum;
     * if(radio.available(pipeNum)){
     *   radio.read(data, data.length);
     *   Log.i("TAG","Got data on pipe " + Integer.toString(pipeNum));
     * }
     * }
     * @return pipe_num which pipe has the payload available
     * @throws IOException when write/read on spi bus doesn't work
     */
    public int available(byte pipe_num) throws IOException {
        byte status;
        status = read_register(nRF24L01.FIFO_STATUS);
        if ((status & _BV(nRF24L01.RX_EMPTY)) != _BV(nRF24L01.RX_EMPTY)) {
            // If the caller wants the pipe number, include that
            status = get_status();
            return (status >> nRF24L01.RX_P_NO) & 0x07;
        }
        return 0;
    }




    /**
     * Check whether there are bytes available to be read
     * {@code
     * if(radio.available()){
     *   radio.read(data,data.length);
     * }
     * }
     * @return true if there is a payload available, false if none is
     * @throws IOException when write/read on spi bus doesn't work
     */
    public boolean available() throws IOException {
        byte status;
        status = read_register(nRF24L01.FIFO_STATUS);

        return ((status & _BV(nRF24L01.RX_EMPTY)) == 0);
    }


    /**
     * Read the available payload
     *
     * The size of data read is the fixed payload size, see getPayloadSize()
     *
     *
     * No longer boolean. Use available to determine if packets are
     * available. Interrupt flags are now cleared during reads instead of
     * when calling available().
     *
     * No return value. Use available().
     * @see #available()
     *
     * @param buf Integer buffer where the data should be written
     * @param len Maximum number of bytes to read into the buffer
     *
     * {@code
     * if(radio.available()){
     *   radio.read(data,data.length);
     * }
     * }
     * @throws IOException when write/read on spi bus doesn't work
     */
    public void read( int buf[], int len ) throws IOException {

        // Fetch the payload
        read_payload(buf, len);

        //Clear the two possible interrupt flags with one command
        write_register(nRF24L01.NRF_STATUS, _BV(nRF24L01.RX_DR) | _BV(nRF24L01.MAX_RT) | _BV(nRF24L01.TX_DS));

    }

    /**
     * Be sure to call {@link #openWritingPipe(int[])} first to set the destination
     * of where to write to.
     *
     * This blocks until the message is successfully acknowledged by
     * the receiver or the timeout/retransmit maxima are reached.  In
     * the current configuration, the max delay here is 60-70ms.
     *
     * The maximum size of data written is the fixed payload size,
     * @see #getDynamicPayloadSize()
     * However, you can write less, and the remainder will just be filled with zeroes.
     *
     * TX/RX/RT interrupt flags will be cleared every time write is called
     *
     * @param buf Integer array to be sent
     * @param len Number of bytes(int) to be sent
     * {@code
     * radio.stopListening();
     * radio.write(&data,sizeof(data));
     * }
     * @return True if the payload was delivered successfully false if not
     */
    public boolean write( int buf[], int len ) throws IOException {
        return write(buf, len, false);
    }
    /**
     * Same definition as
     * @see #write(int[], int)
     * but using buf as byte array for compatibility purposes
     *
     * @param buf Byte array to be sent
     * @param len Number of bytes to be sent
     * {@code
     * radio.stopListening();
     * radio.write(data, data.length);
     * }
     * @return True if the payload was delivered successfully false if not
     * @throws IOException when write/read on spi bus doesn't work
     */
    //TODO: int or byte, needed?
    public boolean write( byte buf[], int len ) throws IOException {
        int size = buf.length;

        int[] bufi = new int[size];

        for (int idx = 0; idx < size; idx++) {
            bufi[idx] = buf[idx];
        }

        return write(bufi, len, false);
    }

    /**
     * Write for single NOACK writes. Optionally disables acknowledgements/autoretries for a single write.
     *
     * {@link #enableDynamicAck()} must be called to enable this feature
     *
     * Can be used with enableAckPayload() to request a response
     * @see #enableDynamicAck()
     * @see #setAutoAck(boolean)
     * @see #setAutoAck(int, boolean)
     * @see #write(int[], int)
     *
     * @param buf Integer array to be sent
     * @param len Number of bytes(int) to be sent
     * @param multicast Request ACK (0), NOACK (1)
     * @throws IOException when write/read on spi bus doesn't work
     */

    private boolean write(int buf[], int len, boolean multicast ) throws IOException {
        //Start Writing
        startFastWrite(buf, len, multicast);

        //Wait until complete or failed
        long timer = millis();

        //TODO: check this status
        while( ((byte)(get_status() & ( _BV(nRF24L01.TX_DS) | _BV(nRF24L01.MAX_RT) ))) == 0) {
            long diff = millis() - timer;
            if((millis() - timer) > 95){
                errNotify();
                return false;
            }
        }

        ce(LOW);

        byte status = write_register(nRF24L01.NRF_STATUS,_BV(nRF24L01.RX_DR) | _BV(nRF24L01.TX_DS) | _BV(nRF24L01.MAX_RT) );

        //Max retries exceeded
        //TODO: check this status
        if((status & _BV(nRF24L01.MAX_RT))== _BV(nRF24L01.MAX_RT)){
            flush_tx(); //Only going to be 1 packet int the FIFO at a time using this method, so just flush
            return false;
        }
        //TX OK 1 or 0
        return true;
    }


    /**
     * New: Open a pipe for writing via byte array. Old addressing format retained
     * for compatibility.
     *
     * Only one writing pipe can be open at once, but you can change the address
     * you'll write to. Call stopListening() first.
     *
     * Addresses are assigned via a int array, default is 5 int address length
     *
     * {@code
     *   int addresses[6] = {"1Node","2Node"};
     *   radio.openWritingPipe(addresses[0]);
     * }
     * {@code
     *  int address[] = { 0xCC,0xCE,0xCC,0xCE,0xCC };
     *  radio.openWritingPipe(address);
     *  address[0] = 0x33;
     *  radio.openReadingPipe(1,address);
     * }
     * @see #setAddressWidth(int)
     *
     * @param address The address of the pipe to open. Coordinate these pipe
     * addresses amongst nodes on the network.
     * @throws IOException when write/read on spi bus doesn't work
     */

    public void openWritingPipe(int[] address) throws IOException {
        // Note that AVR 8-bit uC's store this LSB first, and the NRF24L01(+)
        // expects it LSB first too, so we're good.

/*        int[] address2 = new int[5];
        for (int idx=0; idx < 5; idx++) {
            address2[idx] = address[4-idx];
        }*/

        write_register(nRF24L01.RX_ADDR_P0, address, (short) addr_width);
        write_register(nRF24L01.TX_ADDR, address, (short) addr_width);

        //const uint8_t max_payload_size = 32;
        //write_register(RX_PW_P0,rf24_min(payload_size,max_payload_size));
        write_register(nRF24L01.RX_PW_P0, payload_size);


    }




    /**
     * Open a pipe for reading
     *
     * Up to 6 pipes can be open for reading at once.  Open all the required
     * reading pipes, and then call startListening().
     *
     * @see #openWritingPipe(int[])
     * @see #setAddressWidth
     *
     * Pipes 0 and 1 will store a full 5-byte address. Pipes 2-5 will technically
     * only store a single byte, borrowing up to 4 additional bytes from pipe #1 per the
     * assigned address width.
     *
     * Pipes 1-5 should share the same address, except the first byte.
     * Only the first byte in the array should be unique, e.g.
     * {@code
     *   int addresses[6] = {"1Node","2Node"};
     *   openReadingPipe(1,addresses[0]);
     *   openReadingPipe(2,addresses[1]);
     * }
     *
     * Pipe 0 is also used by the writing pipe.  So if you open
     * pipe 0 for reading, and then startListening(), it will overwrite the
     * writing pipe.  Ergo, do an openWritingPipe() again before write().
     *
     * @param child Which pipe# to open, 0-5.
     * @param address The 24, 32 or 40 bit address of the pipe to open.
     * @throws IOException when write/read on spi bus doesn't work
     */
    public void openReadingPipe(int child, int[] address) throws IOException {
        // If this is pipe 0, cache the address.  This is needed because
        // openWritingPipe() will overwrite the pipe 0 address, so
        // startListening() will have to restore it.
        if (child == 0){
//            memcpy(pipe0_reading_address,&address,addr_width);
//            for (int idx=0; idx < addr_width; idx++)
//                pipe0_reading_address[idx] = address[idx];
            System.arraycopy(address, 0, pipe0_reading_address, 0, addr_width);
        }

        if (child <= 6)
        {
            // For pipes 2-5, only write the LSB
            if ( child < 2 )
                write_register(child_pipe[child], address, (short) addr_width);
            else
                write_register(child_pipe[child], address, (short)  1);

            write_register(child_payload_size[child], payload_size);

            // Note it would be more efficient to set all of the bits for all open
            // pipes at once.  However, I thought it would make the calling code
            // more simple to do it this way.
            write_register(nRF24L01.EN_RXADDR, read_register(nRF24L01.EN_RXADDR) | _BV(child_pipe_enable[child]));
        }
    }

    /*@}*/
    /*
      @name Advanced Operation
     *
     *  Methods you can use to drive the chip in more advanced ways
     */
    /*@{*/

    /**
     * Print a giant block of debugging information
     *
     * {@code
     * import android.util.Log;
     * setup(){
     *  printDetails();
     *  ...
     * }
     * }
     */
    public void printDetails() throws IOException {

        Log.i(TAG, "================ SPI Configuration ================");
        Log.i(TAG, "CSN Pin = Custom GPIO " + csn_pin);
        Log.i(TAG, "CE Pin = Custom GPIO" + ce_pin);
        Log.i(TAG, "Clock Speed = " + spi_speed);
        Log.i(TAG, "================ NRF Configuration ================");
        print_status(get_status());
//
        print_address_register("RX_ADDR_P0-1 ",nRF24L01.RX_ADDR_P0, 2);
        print_byte_register("RX_ADDR_P2-5 ",nRF24L01.RX_ADDR_P2, 4);
        print_address_register("TX_ADDR ", nRF24L01.TX_ADDR, 1);
//
        print_byte_register("RX_PW_P0-6 ", nRF24L01.RX_PW_P0, 6);
        print_byte_register("EN_AA ", nRF24L01.EN_AA, 1);
        print_byte_register("EN_RXADDR ", nRF24L01.EN_RXADDR, 1);
        print_byte_register("RF_CH ", nRF24L01.RF_CH, 1);
        print_byte_register("RF_SETUP ", nRF24L01.RF_SETUP, 1);
        print_byte_register("CONFIG ", nRF24L01.NRF_CONFIG, 1);
        print_byte_register("DYNPD/FEATURE ", nRF24L01.DYNPD, 2);
//
//        printf_P(PSTR("Data Rate\t = " PRIPSTR "\r\n"),pgm_read_word(&rf24_datarate_e_str_P[getDataRate()]));
//        printf_P(PSTR("Model\t\t = " PRIPSTR "\r\n"),pgm_read_word(&rf24_model_e_str_P[isPVariant()]));
//        printf_P(PSTR("CRC Length\t = " PRIPSTR "\r\n"),pgm_read_word(&rf24_crclength_e_str_P[getCRCLength()]));
//        printf_P(PSTR("PA Power\t = " PRIPSTR "\r\n"),  pgm_read_word(&rf24_pa_dbm_e_str_P[getPALevel()]));

    }

    /**
     * Check if the radio needs to be read. Can be used to prevent data loss
     * @return True if all three 32-byte radio buffers are full
     * @throws IOException when write/read on spi bus doesn't work
     */
    public boolean rxFifoFull() throws IOException {
        //TODO: check this return
        return ((read_register(nRF24L01.FIFO_STATUS) & _BV(nRF24L01.RX_FULL)) == _BV(nRF24L01.RX_FULL));
    }

    /**
     * Enter low-power mode
     *
     * To return to normal power mode, call powerUp().
     *
     * After calling {@link #startListening()}, a basic radio will consume about 13.5mA
     * at max PA level.
     * During active transmission, the radio will consume about 11.5mA, but this will
     * be reduced to 26uA (.026mA) between sending.
     * In full powerDown mode, the radio will consume approximately 900nA (.0009mA)
     *
     * {@code
     * radio.powerDown();
     * radio.powerUp();
     * }
     * @throws IOException when write/read on spi bus doesn't work
     */
    private void powerDown() throws IOException {
        ce(LOW); // Guarantee CE is low on powerDown
        write_register(nRF24L01.NRF_CONFIG, (read_register(nRF24L01.NRF_CONFIG) & ~_BV(nRF24L01.PWR_UP)));
    }

    /**
     * Leave low-power mode - required for normal radio operation after calling powerDown()
     *
     * To return to low power mode, call {@link #powerDown()}.
     * This will take up to 5ms for maximum compatibility
     */

    private void powerUp() throws IOException {
        //Power up now. Radio will not power down unless instructed by MCU for config changes etc.
        byte cfg;
        cfg = read_register(nRF24L01.NRF_CONFIG);

        // if not powered up then power up and wait for the radio to initialize
        if ((cfg & _BV(nRF24L01.PWR_UP)) !=  _BV(nRF24L01.PWR_UP)){
            write_register(nRF24L01.NRF_CONFIG, (cfg | _BV(nRF24L01.PWR_UP)));

            // For nRF24L01+ to go from power down mode to TX or RX mode it must first pass through stand-by mode.
            // There must be a delay of Tpd2stby (see Table 16.) after the nRF24L01+ leaves power down mode before
            // the CEis set high. - Tpd2stby can be up to 5ms per the 1.0 datasheet
            delay(5);
        }
    }

    /**
     * This will not block until the 3 FIFO buffers are filled with data.
     * Once the FIFOs are full, writeFast will simply wait for success or
     * timeout, and return 1 or 0 respectively. From a user perspective, just
     * keep trying to send the same data. The library will keep auto retrying
     * the current payload using the built in functionality.
     *
     * It is important to never keep the nRF24L01 in TX mode and FIFO full for more than 4ms at a time. If the auto
     * retransmit is enabled, the nRF24L01 is never in TX mode long enough to disobey this rule. Allow the FIFO
     * to clear by issuing {@link #txStandBy()} or ensure appropriate time between transmissions.
     *
     * {@code
     * Example (Partial blocking):
     *
     *			radio.writeFast(buf,32);   // Writes 1 payload to the buffers
     *			txStandBy();     		   // Returns 0 if failed. 1 if success. Blocks only until MAX_RT timeout or success. Data flushed on fail.
     *
     *			radio.writeFast(buf,32);   // Writes 1 payload to the buffers
     *			txStandBy(1000);		   // Using extended timeouts, returns 1 if success. Retries failed payloads for 1 seconds before returning 0.
     * }
     *
     * @see #txStandBy()
     * @see #write(int[], int)
     * @see #writeBlocking(int[], int, int)
     *
     * @param buf Integer array of data to be sent
     * @param len Number of bytes(int) to be sent
     * @return True if the payload was delivered successfully false if not
     * @throws IOException when write/read on spi bus doesn't work
     */
    public boolean writeFast( int buf[], int len ) throws IOException {
        return writeFast(buf, len, false);
    }

    /**
     * WriteFast for single NOACK writes. Disables acknowledgements/autoretries for a single write.
     *
     * {@link #enableDynamicAck()} must be called to enable this feature
     * @see #enableDynamicAck()
     * @see #setAutoAck(boolean)
     *
     * @param buf Integer array of data to be sent
     * @param len Number of bytes(int) to be sent
     * @param multicast Request ACK (0) or NOACK (1)
     * @throws IOException when write/read on spi bus doesn't work
     */

    public boolean writeFast( int buf[], int len, boolean multicast ) throws IOException {
        //Block until the FIFO is NOT full.
        //Keep track of the MAX retries and set auto-retry if seeing failures
        //Return 0 so the user can control the retrys and set a timer or failure counter if required
        //The radio will auto-clear everything in the FIFO as long as CE remains high

        long timer = millis();

        //TODO: check this status

        while ((get_status() & _BV(nRF24L01.TX_FULL)) ==  _BV(nRF24L01.TX_FULL)) {   //Blocking only if FIFO is full. This will loop and block until TX is successful or fail
            //TODO: check this status
            if ((get_status() & _BV(nRF24L01.MAX_RT)) == _BV(nRF24L01.MAX_RT)) {
                //reUseTX();										            //Set re-transmit
                write_register(nRF24L01.NRF_STATUS, _BV(nRF24L01.MAX_RT));      //Clear max retry flag
                return false;                                                   //Return 0. The previous payload has been retransmitted
                //From the user perspective, if you get a 0, just keep trying to send the same payload
            }
            if (millis() - timer > 95) {
                errNotify();
                return false;
            }
        }
        //Start Writing
        startFastWrite(buf, len, multicast);

        return true;
    }

    public boolean writeFast( byte buf[], int len, boolean multicast ) throws IOException {
        int[] tmp = new int[len];

        for(int idx=0; idx < len; idx++)
            tmp[idx] = buf[idx];

        return writeFast( tmp, len, multicast );
    }

    /**
     * This function extends the auto-retry mechanism to any specified duration.
     * It will not block until the 3 FIFO buffers are filled with data.
     * If so the library will auto retry until a new payload is written
     * or the user specified timeout period is reached.
     *
     * It is important to never keep the nRF24L01 in TX mode and FIFO full for more than 4ms at a time. If the auto
     * retransmit is enabled, the nRF24L01 is never in TX mode long enough to disobey this rule. Allow the FIFO
     * to clear by issuing txStandBy() or ensure appropriate time between transmissions.
     *
     * {@code
     * Example (Full blocking):
     *
     *			radio.writeBlocking(buf,32,1000);  //Wait up to 1 second to write 1 payload to the buffers
     *			txStandBy(1000);     			   //Wait up to 1 second for the payload to send. Return 1 if ok, 0 if failed.
     *					  				   		   //Blocks only until user timeout or success. Data flushed on fail.
     * }
     * If used from within an interrupt, the interrupt should be disabled until completion, and sei(); called to enable millis().
     * @see #txStandBy()
     * @see #write(int[], int)
     * @see #writeFast(int[], int)
     *
     * @param buf Integer array of data to be sent
     * @param len Number of bytes(int) to be sent
     * @param timeout User defined timeout in milliseconds.
     * @return True if the payload was loaded into the buffer successfully false if not
     * @throws IOException when write/read on spi bus doesn't work
     */
    //For general use, the interrupt flags are not important to clear
    private boolean writeBlocking( int buf[], int len, int timeout ) throws IOException {
        //Block until the FIFO is NOT full.
        //Keep track of the MAX retries and set auto-retry if seeing failures
        //This way the FIFO will fill up and allow blocking until packets go through
        //The radio will auto-clear everything in the FIFO as long as CE remains high

        long timer = millis();                              //Get the time that the payload transmission started

        //TODO: check this status
        while ((get_status() & (_BV(nRF24L01.TX_FULL))) == _BV(nRF24L01.TX_FULL)) {          //Blocking only if FIFO is full. This will loop and block until TX is successful or timeout
            if ((get_status() & _BV(nRF24L01.MAX_RT)) == _BV(nRF24L01.MAX_RT)) {                   //If MAX Retries have been reached
                reUseTX();                                                      //Set re-transmit and clear the MAX_RT interrupt flag
                if ((millis() - timer) > timeout) {
                    return false;
                }                  //If this payload has exceeded the user-defined timeout, exit and return 0
            }
            if (millis() - timer > (timeout + 95)) {
                errNotify();
                return false;
            }

        }
        //Start Writing
        startFastWrite(buf, len, false);                                  //Write the payload if a buffer is clear

        return true;                                                  //Return 1 to indicate successful transmission
    }

    /**
     * This function should be called as soon as transmission is finished to
     * drop the radio back to STANDBY-I mode. If not issued, the radio will
     * remain in STANDBY-II mode which, per the data sheet, is not a recommended
     * operating mode.
     *
     * When transmitting data in rapid succession, it is still recommended by
     * the manufacturer to drop the radio out of TX or STANDBY-II mode if there is
     * time enough between sends for the FIFOs to empty. This is not required if auto-ack
     * is enabled.
     *
     * Relies on built-in auto retry functionality.
     *
     * {@code
     * Example (Partial blocking):
     *
     *			radio.writeFast(buf,32);
     *			radio.writeFast(buf,32);
     *			radio.writeFast(buf,32);   //Fills the FIFO buffers up
     *			bool ok = txStandBy();     //Returns 0 if failed. 1 if success.
     *					  				   //Blocks only until MAX_RT timeout or success. Data flushed on fail.
     * }
     * @see #txStandBy(int)
     * @return True if transmission is successful
     * @throws IOException when write/read on spi bus doesn't work
     */
    private boolean txStandBy() throws IOException {

        long timeout = millis();

        while( (read_register(nRF24L01.FIFO_STATUS) & _BV(nRF24L01.TX_EMPTY)) != _BV(nRF24L01.TX_EMPTY)){
            if( (get_status() & _BV(nRF24L01.MAX_RT)) == _BV(nRF24L01.MAX_RT)){
                write_register(nRF24L01.NRF_STATUS,_BV(nRF24L01.MAX_RT) );
                ce(LOW);
                flush_tx();    //Non blocking, flush the data
                return false;
            }
            if( millis() - timeout > 95){
                errNotify();
                return false;
            }
        }

        ce(LOW);			   //Set STANDBY-I mode
        return true;
    }

    /**
     * This function allows extended blocking and auto-retries per a user defined timeout
     * {@code
     *	Fully Blocking Example:
     *
     *			radio.writeFast(buf,32);
     *			radio.writeFast(buf,32);
     *			radio.writeFast(buf,32);            //Fills the FIFO buffers up
     *			bool ok = txStandBy(1000, true);    //Returns 0 if failed after 1 second of retries. 1 if success.
     *					  				            //Blocks only until user defined timeout or success. Data flushed on fail.
     * }
     *
     * If used from within an interrupt, the interrupt should be disabled until completion, and sei(); called to enable millis().
     * @param timeout Number of milliseconds to retry failed payloads
     * @param startTx Start transmission (CE-> HIGH)
     * @return True if transmission is successful
     * @throws IOException when write/read on spi bus doesn't work
     */
    public boolean txStandBy(int timeout, boolean startTx) throws IOException {

        if(startTx){
            stopListening();
            ce(HIGH);
        }

        long start = millis();

        //while( (read_register(nRF24L01.FIFO_STATUS) & _BV(nRF24L01.TX_EMPTY)) == _BV(nRF24L01.TX_EMPTY)){
        while( (read_register(nRF24L01.FIFO_STATUS) & _BV(nRF24L01.TX_EMPTY)) == 0){
            if( (get_status() & _BV(nRF24L01.MAX_RT)) == _BV(nRF24L01.MAX_RT)){
                write_register(nRF24L01.NRF_STATUS, _BV(nRF24L01.MAX_RT) );
                ce(LOW);										  //Set re-transmit
                //delayMicroseconds(10);      // needed?
                ce(HIGH);
                if(millis() - start >= timeout){
                    ce(LOW); flush_tx(); return false;
                }
            }
            if( millis() - start > (timeout+95)){
                flush_tx();    //Non blocking, flush the data
                errNotify();
                return false;
            }
        }

        ce(LOW);				   //Set STANDBY-I mode
        return true;

    }

    /**
     * This function allows extended blocking and auto-retries per a user defined timeout
     * {@code
     *	Fully Blocking Example:
     *
     *			radio.writeFast(buf,32);
     *			radio.writeFast(buf,32);
     *			radio.writeFast(buf,32);   //Fills the FIFO buffers up
     *			bool ok = txStandBy(1000);  //Returns 0 if failed after 1 second of retries. 1 if success.
     *					  				    //Blocks only until user defined timeout or success. Data flushed on fail.
     * }
     *
     * If used from within an interrupt, the interrupt should be disabled until completion, and sei(); called to enable millis().
     * @param timeout Number of milliseconds to retry failed payloads
     * @return True if transmission is successful
     * @throws IOException when write/read on spi bus doesn't work
     */
    public boolean txStandBy(int timeout) throws IOException {
        return txStandBy(timeout, false);
    }

    /**
     * Write an ack payload for the specified pipe
     *
     * The next time a message is received on @p pipe, the data in @p buf will
     * be sent back in the acknowledgement.
     * @see #enableAckPayload()
     * @see #enableDynamicPayloads()
     *
     * Only three of these can be pending at any time as there are only 3 FIFO buffers.<br> Dynamic payloads must be enabled.
     *
     * Ack payloads are handled automatically by the radio chip when a payload is received. Users should generally
     * write an ack payload as soon as startListening() is called, so one is available when a regular payload is received.
     *
     * Ack payloads are dynamic payloads. This only works on pipes 0&1 by default. Call
     * enableDynamicPayloads() to enable on all pipes.
     *
     * @param pipe Which pipe# (typically 1-5) will get this response.
     * @param buf Integer array of data that is sent
     * @param len Length of the data to send, up to 32 bytes max.  Not affected
     * by the static payload set by setPayloadSize().
     * @throws IOException when write/read on spi bus doesn't work
     */
    public void writeAckPayload(int pipe, int buf[], int len)  throws IOException {

        //const uint8_t* current = reinterpret_cast<const uint8_t*>(buf);

        int data_len = rf24_min(len,32);

        beginTransaction();
        byte[] ptx = spi_txbuff;
        int size = data_len + 1 ; // Add register value to transmit buffer
        ptx[0] = (byte) (0x000000FF & (nRF24L01.W_ACK_PAYLOAD | ( pipe & 0x07 )));

        int idx = 0;
        while ( data_len-- > 0){
            ptx[idx + 1] = (byte) (0x000000FF &  buf[idx++]);
        }
        transfer(spi_txbuff, size);
        endTransaction();

    }

    /**
     * Determine if an ack payload was received in the most recent call to
     * write(). The regular available() can also be used.
     *
     * Call read() to retrieve the ack payload.
     *
     * @return True if an ack payload is available.
     * @throws IOException when write/read on spi bus doesn't work
     */
    public boolean isAckPayloadAvailable()  throws IOException {
        return (read_register(nRF24L01.FIFO_STATUS) & _BV(nRF24L01.RX_EMPTY)) == _BV(nRF24L01.RX_EMPTY);
    }

    /**
     * Call this when you get an interrupt to find out why
     *
     * Tells you what caused the interrupt, and clears the state of
     * interrupts.
     *
     * @return boolean array = [tx_ok, tx_fail, rx_ready]
     * tx_ok The send was successful (TX_DS)
     * tx_fail The send failed, too many retries (MAX_RT)
     * rx_ready There is a message waiting to be read (RX_DS)
     * @throws IOException when write/read on spi bus doesn't work
     */
    private boolean[] whatHappened() throws IOException {
        boolean ret[] = new boolean[3];
        // Read the status & reset the status in one easy call
        // Or is that such a good idea?
        int status = write_register(nRF24L01.NRF_STATUS,_BV(nRF24L01.RX_DR) | _BV(nRF24L01.TX_DS) | _BV(nRF24L01.MAX_RT) );

        // Report to the user what happened
        boolean tx_ok   = (status & _BV(nRF24L01.TX_DS)) == _BV(nRF24L01.TX_DS);
        boolean tx_fail = (status & _BV(nRF24L01.MAX_RT)) == _BV(nRF24L01.MAX_RT);
        boolean rx_ready = (status & _BV(nRF24L01.RX_DR)) ==  _BV(nRF24L01.RX_DR);

        ret[0] = tx_ok;
        ret[1] = tx_fail;
        ret[2] = rx_ready;

        return ret;
    }

    /**
     * Non-blocking write to the open writing pipe used for buffered writes
     *
     * Optimization: This function now leaves the CE pin high, so the radio
     * will remain in TX or STANDBY-II Mode until a txStandBy() command is issued. Can be used as an alternative to startWrite()
     * if writing multiple payloads at once.
     *
     * It is important to never keep the nRF24L01 in TX mode with FIFO full for more than 4ms at a time. If the auto
     * retransmit/autoAck is enabled, the nRF24L01 is never in TX mode long enough to disobey this rule. Allow the FIFO
     * to clear by issuing txStandBy() or ensure appropriate time between transmissions.
     *
     * @see #write(int[], int)
     * @see #writeFast(int[], int)
     * @see #startWrite(int[], int, boolean)
     * @see #writeBlocking(int[], int, int)
     *
     * For single noAck writes see:
     * @see #enableDynamicAck()
     * @see #setAutoAck(boolean)
     *
     * @param buf Integer array of data to be sent
     * @param len Number of bytes(int) to be sent
     * @param multicast Request ACK (0) or NOACK (1)
     *
     * @throws IOException when write/read on spi bus doesn't work
     */
    private void startFastWrite(int buf[], int len, boolean multicast) throws IOException { //TMRh20
        //Per the documentation, we want to set PTX Mode when not listening. Then all we do is write data and set CE high
        //In this mode, if we can keep the FIFO buffers loaded, packets will transmit immediately (no 130us delay)
        //Otherwise we enter Standby-II mode, which is still faster than standby mode
        //Also, we remove the need to keep writing the config register over and over and delaying for 150 us each time if sending a stream of data

        //write_payload( buf,len);
        startFastWrite(buf, len, multicast, true);

    }

    /**
     * Non-blocking write to the open writing pipe used for buffered writes
     *
     * Note:  Optimization: This function now leaves the CE pin high, so the radio
     * will remain in TX or STANDBY-II Mode until a txStandBy() command is issued. Can be used as an alternative to startWrite()
     * if writing multiple payloads at once.
     *
     * Warning:  It is important to never keep the nRF24L01 in TX mode with FIFO full for more than 4ms at a time. If the auto
     * retransmit/autoAck is enabled, the nRF24L01 is never in TX mode long enough to disobey this rule. Allow the FIFO
     * to clear by issuing txStandBy() or ensure appropriate time between transmissions.
     *
     *
     * @param buf Integer array of data to be sent
     * @param len Number of bytes(int) to be sent
     * @param multicast Request ACK (0) or NOACK (1)
     * @param startTx start transmit -> CE (HIGH)
     * @throws IOException when write/read on spi bus doesn't work
     */
    private void startFastWrite(int buf[], int len, boolean multicast, boolean startTx) throws IOException { //TMRh20
        //write_payload( buf,len);
        write_payload(buf, len, multicast ? nRF24L01.W_TX_PAYLOAD_NO_ACK : nRF24L01.W_TX_PAYLOAD);
        if (startTx) {
            ce(HIGH);
        }

    }

    /**
     * Non-blocking write to the open writing pipe
     *
     * Just like write(), but it returns immediately. To find out what happened
     * to the send, catch the IRQ and then call whatHappened().
     *
     * @see #write(int[], int)
     * @see #writeFast(int[], int)
     * @see #startFastWrite(int[], int, boolean)
     * @see #whatHappened()
     *
     * For single noAck writes see:
     * @see #enableDynamicAck()
     * @see #setAutoAck(boolean)
     *
     * @param buf Integer array of data to be sent
     * @param len Number of bytes to be sent
     * @param multicast Request ACK (0) or NOACK (1)
     * @throws IOException when write / read on spi doesn't work
     *
     */
    private void startWrite( int buf[], int len, boolean multicast ) throws IOException {
        //Added the original startWrite back in so users can still use interrupts, ack payloads, etc
        //Allows the library to pass all tests

        // Send the payload
        write_payload(buf, len, multicast ? nRF24L01.W_TX_PAYLOAD_NO_ACK : nRF24L01.W_TX_PAYLOAD);
        ce(HIGH);
        //#if defined(CORE_TEENSY) || !defined(ARDUINO) || defined (RF24_SPIDEV) || defined (RF24_DUE)
        //delayMicroseconds(10);
        //#endif
        ce(LOW);
    }


    /**
     * This function is mainly used internally to take advantage of the auto payload
     * re-use functionality of the chip, but can be beneficial to users as well.
     *
     * The function will instruct the radio to re-use the data in the FIFO buffers,
     * and instructs the radio to re-send once the timeout limit has been reached.
     * Used by writeFast and writeBlocking to initiate retries when a TX failure
     * occurs. Retries are automatically initiated except with the standard write().
     * This way, data is not flushed from the buffer until switching between modes.
     *
     * Note: This is to be used AFTER auto-retry fails if wanting to resend
     * using the built-in payload reuse features.
     * After issuing reUseTX(), it will keep reending the same payload forever or until
     * a payload is written to the FIFO, or a flush_tx command is given.
     */

    private void reUseTX()  throws IOException {
        write_register(nRF24L01.NRF_STATUS, _BV(nRF24L01.MAX_RT));              //Clear max retry flag
        spiTrans((byte) nRF24L01.REUSE_TX_PL);
        ce(LOW);                                          //Re-Transfer packet
        ce(HIGH);
    }

    /**
     * Empty the transmit buffer. This is generally not required in standard operation.
     * May be required in specific cases after stopListening() , if operating at 250KBPS data rate.
     *
     * @return Current value of status register
     */
    private byte flush_tx() throws IOException {
        return spiTrans((byte) nRF24L01.FLUSH_TX);
    }

    /**
     * Test whether there was a carrier on the line for the
     * previous listening period.
     *
     * Useful to check for interference on the current channel.
     *
     * @return true if was carrier, false if not
     */
    public boolean testCarrier() throws IOException {
        return ((read_register(nRF24L01.CD) & 1) == 1);
    }

    /**
     * Test whether a signal (carrier or otherwise) greater than
     * or equal to -64dBm is present on the channel. Valid only
     * on nRF24L01P (+) hardware. On nRF24L01, use testCarrier().
     *
     * Useful to check for interference on the current channel and
     * channel hopping strategies.
     *
     * {@code
     * bool goodSignal = radio.testRPD();
     * if(radio.available()){
     *    Serial.println(goodSignal ? "Strong signal > 64dBm" : "Weak signal < 64dBm" );
     *    radio.read(0,0);
     * }
     * }
     * @return true if signal => -64dBm, false if not
     */
    public boolean testRPD() throws IOException {
        return (read_register(nRF24L01.RPD) & 1) == 1;
    }


    /**
     * Test whether this is a real radio, or a mock shim for
     * debugging.  Setting either pin to 0xff is the way to
     * indicate that this is not a real radio.
     *
     * @return true if this is a legitimate radio
     */
    boolean isValid() {
        return (ce_pin != 0xff && csn_pin != 0xff);
    }



    /**
     * Checks if the chip is connected to the SPI bus
     * @return true if chip is connected, false if not
     */
    public boolean isChipConnected() throws IOException {
        short setup = read_register(nRF24L01.SETUP_AW);
        if (setup >= 1 && setup <= 3) {
            return true;
        } else {
            return false;
        }
    }

    public int isConnected() throws IOException
    {
        short aw;
        aw = read_register(nRF24L01.SETUP_AW);
        return (int)(aw & 0x0E);
        //return((aw & 0xFC) == 0x00 && (aw & 0x03) != 0x00);
    }


    /**
     * Close a pipe after it has been previously opened.
     * Can be safely called without having previously opened a pipe.
     * @param pipe Which pipe # to close, 0-5.
     * @throws IOException when write / read on spi doesn't work
     */

    private void closeReadingPipe( int pipe ) throws IOException {
        write_register(nRF24L01.EN_RXADDR,read_register(nRF24L01.EN_RXADDR) & ~_BV(child_pipe_enable[pipe]));
    }


    /*@}*/
    /*
     * @name Optional Configurators
     *
     *  Methods you can use to get or set the configuration of the chip.
     *  None are required.  Calling begin() sets up a reasonable set of
     *  defaults.
     */
    /*@{*/



    /**
     * Set the address width from 3 to 5 bytes (24, 32 or 40 bit)
     *
     * @param a_width The address width to use: 3,4 or 5
     */
    // TODO: implement setAddressWidth function
    private void setAddressWidth(int a_width){
//        if(a_width -= 2){
//            write_register(SETUP_AW,a_width%4);
//            addr_width = (a_width%4) + 2;
//        }else{
//            write_register(SETUP_AW,0);
//            addr_width = 2;
//        }
    }


    /**
     * Set the number and delay of retries upon failed submit
     *
     * @param delay How long to wait between each retry, in multiples of 250us,
     * max is 15.  0 means 250us, 15 means 4000us.
     * @param count How many retries before giving up, max 15
     * @throws IOException when write / read on spi doesn't work
     */
    public void setRetries(int delay, int count) throws IOException
    {
        write_register(nRF24L01.SETUP_RETR,((delay&0xf)<<nRF24L01.ARD | (count&0xf)<<nRF24L01.ARC));
    }

    /**
     * Set RF communication channel
     *
     * @param channel Which RF channel to communicate on, 0-125
     */
    public void setChannel(int channel) throws IOException
    {
        short max_channel = 125;
        write_register(nRF24L01.RF_CH,rf24_min(channel,max_channel));
        _channel = channel;
    }

    /**
     * Get RF communication channel
     *
     * @return The currently configured RF Channel
     */
    private byte getChannel() throws IOException
    {
        return read_register(nRF24L01.RF_CH);
    }

    /**
     * Set Static Payload Size
     *
     * This implementation uses a pre-stablished fixed payload size for all
     * transmissions.  If this method is never called, the driver will always
     * transmit the maximum payload size (32 bytes), no matter how much
     * was sent to {@link #write(int[], int)}.
     *
     * @param size The number of bytes in the payload
     *
     * @todo Implement variable-sized payloads feature
     */
    private void setPayloadSize(short size) {
        payload_size = rf24_min(size, 32);
    }


    /**
     * Get Static Payload Size
     *
     * @see #setPayloadSize(short)
     *
     * @return The number of bytes in the payload
     */
    private int getPayloadSize()
    {
        return payload_size;
    }

    /**
     * Get Dynamic Payload Size
     *
     * For dynamic payloads, this pulls the size of the payload off
     * the chip
     *
     * Corrupt packets are now detected and flushed per the manufacturer.
     * {@code
     * if(radio.available()){
     *   if(radio.getDynamicPayloadSize() < 1){
     *     // Corrupt payload has been flushed
     *     return;
     *   }
     *   radio.read(&data,sizeof(data));
     * }
     * }
     *
     * @return Payload length of last-received dynamic payload
     * @throws IOException when write / read on spi doesn't work
     */
    public int getDynamicPayloadSize() throws IOException {
        byte result;

        spi_txbuff[0] = (byte)nRF24L01.R_RX_PL_WID;
        spi_rxbuff[1] = (byte)0xff;
        beginTransaction();
        transfer( spi_txbuff, spi_rxbuff, 2);
        result = spi_rxbuff[1];
        endTransaction();

        if(result > 32) { flush_rx(); delay(2); return 0; }

        return result;
    }

    /**
     * Enable custom payloads on the acknowledge packets
     *
     * Ack payloads are a handy way to return data back to senders without
     * manually changing the radio modes on both units.
     *
     * Ack payloads are dynamic payloads. This only works on pipes 0&1 by default. Call
     * enableDynamicPayloads() to enable on all pipes.
     * @throws IOException if write / read on spi doesn't work
     */
    public void enableAckPayload() throws IOException {
        //
        // enable ack payload and dynamic payload features
        //

        //toggle_features();
        write_register(nRF24L01.FEATURE, read_register(nRF24L01.FEATURE) | _BV(nRF24L01.EN_ACK_PAY) | _BV(nRF24L01.EN_DPL));

        //IF_SERIAL_DEBUG(printf_P("FEATURE=%i\r\n",read_register(nRF24L01.FEATURE)));

        //
        // Enable dynamic payload on pipes 0 & 1
        //
        write_register(nRF24L01.DYNPD, read_register(nRF24L01.DYNPD) | _BV(nRF24L01.DPL_P1) | _BV(nRF24L01.DPL_P0));
        dynamic_payloads_enabled = true;
    }

    /**
     * Enable dynamically-sized payloads
     *
     * This way you don't always have to send large packets just to send them
     * once in a while.  This enables dynamic payloads on ALL pipes.
     *
     */
    public void enableDynamicPayloads() throws IOException {
        // Enable dynamic payload throughout the system

        //toggle_features();
        write_register(nRF24L01.FEATURE, read_register(nRF24L01.FEATURE) | _BV(nRF24L01.EN_DPL));

        // Enable dynamic payload on all pipes
        //
        // Not sure the use case of only having dynamic payload on certain
        // pipes, so the library does not support it.
        write_register(nRF24L01.DYNPD, read_register(nRF24L01.DYNPD) | _BV(nRF24L01.DPL_P5) | _BV(nRF24L01.DPL_P4) | _BV(nRF24L01.DPL_P3) | _BV(nRF24L01.DPL_P2) | _BV(nRF24L01.DPL_P1) | _BV(nRF24L01.DPL_P0));

        dynamic_payloads_enabled = true;
    }


    /**
     *
     * Disable dynamically-sized payloads
     * This disables dynamic payloads on ALL pipes. Since Ack Payloads
     * requires Dynamic Payloads, Ack Payloads are also disabled.
     * If dynamic payloads are later re-enabled and ack payloads are desired
     * then {@link #enableAckPayload()} must be called again as well.
     *
     */
    public void disableDynamicPayloads() throws IOException {
        // Disables dynamic payload throughout the system.  Also disables Ack Payloads

        //toggle_features();
        //write_register(nRF24L01.FEATURE, read_register(nRF24L01.FEATURE) & ~(_BV(nRF24L01.EN_DPL) | _BV(nRF24L01.EN_ACK_PAY)));
        write_register(nRF24L01.FEATURE, 0);
        //if(debug) Log.i(TAG, "FEATURE=%i\r\n", read_register(nRF24L01.FEATURE));

        // Disable dynamic payload on all pipes
        // Not sure the use case of only having dynamic payload on certain
        // pipes, so the library does not support it.
        //write_register(nRF24L01.DYNPD, read_register(nRF24L01.DYNPD) & ~(_BV(nRF24L01.DPL_P5) | _BV(nRF24L01.DPL_P4) | _BV(nRF24L01.DPL_P3) | _BV(nRF24L01.DPL_P2) | _BV(nRF24L01.DPL_P1) | _BV(nRF24L01.DPL_P0)));
        write_register(nRF24L01.DYNPD, 0);
        dynamic_payloads_enabled = false;
    }
    
    /**
     * Enable dynamic ACKs (single write multicast or unicast) for chosen messages
     *
     * To enable full multicast or per-pipe multicast, use setAutoAck()
     *
     * This MUST be called prior to attempting single write NOACK calls
     * {@code
     * radio.enableDynamicAck();
     * radio.write(data,32,1);  // Sends a payload with no acknowledgement requested
     * radio.write(data,32,0);  // Sends a payload using auto-retry/autoACK
     * }
     */
    public void enableDynamicAck()  throws IOException {
        //
        // enable dynamic ack features
        //
        //toggle_features();
        write_register(nRF24L01.FEATURE, read_register(nRF24L01.FEATURE) | _BV(nRF24L01.EN_DYN_ACK));

        //IF_SERIAL_DEBUG(printf_P("FEATURE=%i\r\n",read_register(nRF24L01.FEATURE)));
    }

    /**
     * Determine whether the hardware is an nRF24L01+ or not.
     *
     * @return true if the hardware is nRF24L01+ (or compatible) and false
     * if its not.
     */
    public boolean isPVariant() {
        return p_variant;
    }

    /**
     * Enable or disable auto-acknowlede packets
     *
     * This is enabled by default, so it's only needed if you want to turn
     * it off for some reason.
     *
     * @param enable Whether to enable (true) or disable (false) auto-acks
     */
    public void setAutoAck(boolean enable)  throws IOException {
        if (enable)
            write_register(nRF24L01.EN_AA, 0x3F);
        else
            write_register(nRF24L01.EN_AA, 0);
    }


    /**
     * Enable or disable auto-acknowlede packets on a per pipeline basis.
     *
     * AA is enabled by default, so it's only needed if you want to turn
     * it off/on for some reason on a per pipeline basis.
     *
     * @param pipe Which pipeline to modify
     * @param enable Whether to enable (true) or disable (false) auto-acks
     */
    public void setAutoAck( int pipe, boolean enable )throws IOException {

        if ( pipe <= 6 )
        {
            byte en_aa = read_register( nRF24L01.EN_AA ) ;
            if( enable )
            {
                en_aa |= _BV(pipe) ;
            }
            else
            {
                en_aa &= ~_BV(pipe) ;
            }
            write_register( nRF24L01.EN_AA, en_aa ) ;
        }
    }

    /**
     * Set Power Amplifier (PA) level to one of four levels:
     * RF24_PA_MIN, RF24_PA_LOW, RF24_PA_HIGH and RF24_PA_MAX
     *
     * The power levels correspond to the following output levels respectively:
     * NRF24L01: -18dBm, -12dBm,-6dBM, and 0dBm
     *
     * SI24R1: -6dBm, 0dBm, 3dBM, and 7dBm.
     *
     * @param level Desired PA level.
     */
    public void setPALevel(int level) throws IOException {
        byte setup = (byte) (read_register(nRF24L01.RF_SETUP) & 0xF8);

        if (level > 3) {                        // If invalid level, go to max PA
            //level = (nRF24L01.RF24_PA_MAX << 1) + 1;		// +1 to support the SI24R1 chip extra bit
        } else {
            level = (level << 1) + 1;            // Else set level as requested
        }

        write_register(nRF24L01.RF_SETUP, setup |= level);    // Write it to the chip
    }

    /**
     * Fetches the current PA level.
     *
     * NRF24L01: -18dBm, -12dBm, -6dBm and 0dBm
     * SI24R1:   -6dBm, 0dBm, 3dBm, 7dBm
     *
     * @return Returns values 0 to 3 representing the PA Level.
     */
    public int getPALevel() throws IOException {
        return (read_register(nRF24L01.RF_SETUP) & (_BV(nRF24L01.RF_PWR_LOW) | _BV(nRF24L01.RF_PWR_HIGH))) >> 1;
    }

    /**
     * Set the transmission data rate
     *
     * Warning:  setting RF24_250KBPS will fail for non-plus units
     *
     * @param speed RF24_250KBPS for 250kbs, RF24_1MBPS for 1Mbps, or RF24_2MBPS for 2Mbps
     * @return true if the change was successful,
     * @throws IOException when write / read on spi doesn't work
     */
    public boolean setDataRate(rf24_datarate_e speed) throws IOException {
        boolean result = false;
        byte setup = read_register(nRF24L01.RF_SETUP);

        // HIGH and LOW '00' is 1Mbs - our default
        setup &= ~(_BV(nRF24L01.RF_DR_LOW) | _BV(nRF24L01.RF_DR_HIGH));

        txDelay = 250;
        switch (speed) {
            case RF24_250KBPS:
                // Must set the RF_DR_LOW to 1; RF_DR_HIGH (used to be RF_DR) is already 0
                // Making it '10'.
                setup |= _BV(nRF24L01.RF_DR_LOW);
                txDelay = 450;
                break;

            case RF24_2MBPS:
                setup |= _BV(nRF24L01.RF_DR_HIGH);
                txDelay = 190;
                break;
        }

        write_register(nRF24L01.RF_SETUP, setup);

        // Verify our result
        if (read_register(nRF24L01.RF_SETUP) == setup) {
            result = true;
        }
        return result;
    }

    /**
     * Fetches the transmission data rate
     *
     * @return Returns the hardware's currently configured datarate. The value
     * is one of 250kbs, RF24_1MBPS for 1Mbps, or RF24_2MBPS, as defined in the
     * rf24_datarate_e enum.
     * @throws IOException when write / read on spi doesn't work
     */
    private rf24_datarate_e getDataRate() throws IOException {
        rf24_datarate_e result;
        short dr = (byte)(read_register(nRF24L01.RF_SETUP) & (_BV(nRF24L01.RF_DR_LOW) | _BV(nRF24L01.RF_DR_HIGH)));

        // switch uses RAM (evil!)
        // Order matters in our case below
        if (dr == _BV(nRF24L01.RF_DR_LOW)) {
            // '10' = 250KBPS
            result = rf24_datarate_e.RF24_250KBPS;
        } else if (dr == _BV(nRF24L01.RF_DR_HIGH)) {
            // '01' = 2MBPS
            result = rf24_datarate_e.RF24_2MBPS;
        } else {
            // '00' = 1MBPS
            result = rf24_datarate_e.RF24_1MBPS;
        }
        return result;
    }

    /**
     * Get the CRC length
     * CRC checking cannot be disabled if auto-ack is enabled
     * @return RF24_CRC_DISABLED if disabled or RF24_CRC_8 for 8-bit or RF24_CRC_16 for 16-bit
     * @throws IOException when write / read on spi doesn't work
     */

    private rf24_crclength_e getCRCLength() throws IOException {
        rf24_crclength_e result = rf24_crclength_e.RF24_CRC_DISABLED;

        int config = read_register(nRF24L01.NRF_CONFIG) & ( _BV(nRF24L01.CRCO) | _BV(nRF24L01.EN_CRC)) ;
        int AA = read_register(nRF24L01.EN_AA);

        if (((config & _BV(nRF24L01.EN_CRC ))== _BV(nRF24L01.EN_CRC ) || AA == 1) )
        {
            if ( (config & _BV(nRF24L01.CRCO)) == _BV(nRF24L01.CRCO) )
                result = rf24_crclength_e.RF24_CRC_16;
            else
                result = rf24_crclength_e.RF24_CRC_8;
        }

        return result;
    }

    /**
     * Set the CRC length
     * CRC checking cannot be disabled if auto-ack is enabled
     * @param length RF24_CRC_8 for 8-bit or RF24_CRC_16 for 16-bit
     * @throws IOException when write / read on spi doesn't work
     */
    private void setCRCLength(rf24_crclength_e length) throws IOException {
        int config = read_register(nRF24L01.NRF_CONFIG) & ~(_BV(nRF24L01.CRCO) | _BV(nRF24L01.EN_CRC));

        // switch uses RAM (evil!) ???
        switch(length)
        {
            case RF24_CRC_DISABLED:
                break;
            case RF24_CRC_8:
                config |= _BV(nRF24L01.EN_CRC);
                break;
            default:
                config |= _BV(nRF24L01.EN_CRC);
                config |= _BV(nRF24L01.CRCO);
                break;
        }
        write_register(nRF24L01.NRF_CONFIG, config);
    }

    /**
     * Disable CRC validation
     *
     * Warning:  CRC cannot be disabled if auto-ack/ESB is enabled.
     */
    private void disableCRC() throws IOException {
        byte disable = read_register(((nRF24L01.NRF_CONFIG) & ~_BV(nRF24L01.EN_CRC)));
        write_register(nRF24L01.NRF_CONFIG, disable);
    }

    /**
     * The radio will generate interrupt signals when a transmission is complete,
     * a transmission fails, or a payload is received. This allows users to mask
     * those interrupts to prevent them from generating a signal on the interrupt
     * pin. Interrupts are enabled on the radio chip by default.
     *
     * {@code
     * 	Mask all interrupts except the receive interrupt:
     *
     *		radio.maskIRQ(1,1,0);
     * }
     *
     * @param tx    Mask transmission complete interrupts
     * @param fail  Mask transmit failure interrupts
     * @param rx    Mask payload received interrupts
     * @throws IOException when write / read on spi doesn't work
     */
    private void maskIRQ(boolean tx, boolean fail, boolean rx) throws IOException {
        int itx = 0, ifail = 0, irx = 0;
        if (tx) itx = 1;
        if (fail) ifail = 1;
        if (rx) irx = 1;

        byte config = read_register(nRF24L01.NRF_CONFIG);
	/* clear the interrupt flags */
        config &= ~(1 << nRF24L01.MASK_MAX_RT | 1 << nRF24L01.MASK_TX_DS | 1 << nRF24L01.MASK_RX_DR);
	/* set the specified interrupt flags */
        config |= ifail << nRF24L01.MASK_MAX_RT | itx << nRF24L01.MASK_TX_DS | irx << nRF24L01.MASK_RX_DR;
        write_register(nRF24L01.NRF_CONFIG, config);
    }


    /*@}*/
    /*
     * @name Deprecated
     *
     *  Methods provided for backwards compabibility.
     */
    /*@{*/

    /**
     * @deprecated Open a pipe for reading
     * For compatibility with old code only, see new function: {@link #openReadingPipe(int, int[])}}
     *
     *
     * Warning:  Pipes 1-5 should share the first 32 bits.
     * Only the least significant byte should be unique, e.g.
     * {@code
     *   openReadingPipe(1,0xF0F0F0F0AA);
     *   openReadingPipe(2,0xF0F0F0F066);
     * }
     *
     * Warning:  Pipe 0 is also used by the writing pipe.  So if you open
     * pipe 0 for reading, and then {@link #startListening()}, it will overwrite the
     * writing pipe.  Ergo, do an openWritingPipe() again before {@link #write(int[], int)}}.
     *
     * @param child Which pipe# to open, 0-5.
     * @param address64 The 40-bit address of the pipe to open.
     * @throws IOException when write / read on spi doesn't work
     */
    public void openReadingPipe(int child, long address64) throws IOException {

        // If this is pipe 0, cache the address.  This is needed because
        // openWritingPipe() will overwrite the pipe 0 address, so
        // startListening() will have to restore it.
        int[] address = new int[5];
        for (int idx=0; idx < 5; idx++) {
            address[idx] = (byte)(0x00000000000000FF & address64);
            address64 = address64 >> 8;
        }

        openReadingPipe(child, address);
    }

    /**
     * @deprecated Open a pipe for writing
     * For compatibility with old code only, see new function {@link #openWritingPipe(int[])}
     *
     * Addresses are 40-bit hex values, e.g.:
     *
     * {@code
     *   openWritingPipe(0xF0F0F0F0F0);
     * }
     *
     * @param address64 The 40-bit address of the pipe to open.
     * @throws IOException when write / read on spi doesn't work
     */
    public void openWritingPipe(long address64) throws IOException {
        // Note that AVR 8-bit uC's store this LSB first, and the NRF24L01(+)
        // expects it LSB first too, so we're good.

        int[] address = new int[5];
        for (int idx=0; idx < 5; idx++) {
            address[idx] = (byte)(0x00000000000000FF & address64);
            address64 = address64 >> 8;
        }

        openWritingPipe(address);
    }


    /*
     * @name Low-level internal interface.
     *
     *  Protected methods that address the chip directly.  Regular users cannot
     *  ever call these.  They are documented for completeness and for developers who
     *  may want to extend this class.
     */
    /*@{*/

    /**
     * Set chip select pin
     *
     * Running SPI bus at PI_CLOCK_DIV2 so we don't waste time transferring data
     * and best of all, we make use of the radio's FIFO buffers. A lower speed
     * means we're less likely to effectively leverage our FIFOs and pay a higher
     * AVR runtime cost as toll.
     *
     * @param mode HIGH to take this unit off the SPI bus, LOW to put it on
     */
    private void csn(boolean mode)
    {
        //if(!mode)
        //   _SPI.chipSelect(csn_pin);
    }

    /**
     * Set chip enable
     *
     * @param level HIGH to actively begin transmission or LOW to put in standby.  Please see data sheet
     * for a much more detailed description of this pin.
     */
    private void ce(boolean level)
    {
        if (mCEpin == null) {
            return;
        }
        try {
            // Toggle the GPIO state
            mCEpin.setValue(level);

        } catch (IOException e) {
            Log.e(TAG, "Error on PeripheralIO API", e);
        }
    }

    /**
     * Read a chunk of data in from a register
     *
     * @param reg Which register. Use constants from nRF24L01.h
     * @param buf Where to put the data
     * @param len How many bytes of data to transfer
     * @return Current value of status register
     * @throws IOException when write / read on spi doesn't work
     */
    private byte read_register(int reg, int[] buf, int len) throws IOException
    {
        byte status;

        beginTransaction(); //configures the spi settings for RPi, locks mutex and setting csn low
        byte[] prx = spi_rxbuff;
        byte[] ptx = spi_txbuff;
        int size = len + 1; // Add register value to transmit buffer

        ptx[0] = (byte) ( nRF24L01.R_REGISTER | ( nRF24L01.REGISTER_MASK & reg ) );

        byte idx = 0;
        while ( len-- > 0)
            ptx[++idx] = (byte)(0x000000FF & nRF24L01.NOP); // Dummy operation, just for reading


        transfer (spi_txbuff, spi_rxbuff, size);

        status = prx[0]; // status is 1st byte of receive buffer

        // decrement before to skip status byte
        idx = 0;
        while ( --size > 0 ){ buf[idx] = prx[++idx]; }
        endTransaction(); //unlocks mutex and setting csn high

        return status;
    }

    /**
     * Read single byte from a register
     *
     * @param reg Which register. Use constants from nRF24L01.h
     * @return Current value of register @p reg
     * @throws IOException when write / read on spi doesn't work
     */
    private byte read_register(int reg) throws IOException {
        byte result;

        beginTransaction();

        byte[] prx = spi_rxbuff;
        byte[] ptx = spi_txbuff;
        ptx[0] = (byte) (0x000000FF & (nRF24L01.R_REGISTER | (nRF24L01.REGISTER_MASK & reg)));
        ptx[1] = (byte) (0x000000FF & nRF24L01.NOP); // Dummy operation, just for reading

        transfer(spi_txbuff, spi_rxbuff, 2);

        result = prx[1];   // result is 2nd byte of receive buffer

        endTransaction();

        return result;
    }

    /**
     * Write a chunk of data to a register
     *
     * @param reg Which register. Use constants from nRF24L01.h
     * @param buf Where to get the data
     * @param len How many bytes of data to transfer
     * @return Current value of status register
     * @throws IOException when write / read on spi doesn't work
     */
    private byte write_register(int reg, int[] buf, short len) throws IOException {
        byte status;
        beginTransaction();
        byte[] prx = spi_rxbuff;
        byte[] ptx = spi_txbuff;
        int size = (len + 1); // Add register value to transmit buffer

        ptx[0] = (byte) (nRF24L01.W_REGISTER | (nRF24L01.REGISTER_MASK & reg));

        byte idx = 0;
        while (len-- > 0)
            ptx[idx + 1] = (byte) (0x000000FF & buf[idx++]);

        transfer(spi_txbuff, spi_rxbuff, size);
        status = prx[0]; // status is 1st byte of receive buffer
        endTransaction();

        return status;
    }

    /**
     * Write a single byte to a register
     *
     * @param reg Which register. Use constants from nRF24L01.h
     * @param value The new value to write
     * @return Current value of status register
     * @throws IOException when write / read on spi doesn't work
     */
    private byte write_register(int reg, int value) throws IOException
    {
        byte status;

        beginTransaction();
        byte[] prx = spi_rxbuff;
        byte[] ptx = spi_txbuff;
        ptx[0] = (byte) (byte)(0x000000FF & ( nRF24L01.W_REGISTER | ( nRF24L01.REGISTER_MASK &  reg) ));
        ptx[1] = (byte)(0x000000FF & value);

        transfer (spi_txbuff, spi_rxbuff, 2);

        status = prx[0]; // status is 1st byte of receive buffer
        endTransaction();

        return status;
    }

    /**
     * Write the transmit payload
     *
     * The size of data written is the fixed payload size, see getPayloadSize()
     *
     * @param buf Where to get the data
     * @param data_len Number of bytes to be sent
     * @return Current value of status register
     * @throws IOException when write / read on spi doesn't work
     */
    private int write_payload(int buf[], int data_len, int writeType) throws IOException {
        byte status;
        //const uint8_t* current = reinterpret_cast<const uint8_t*>(buf);

        data_len = rf24_min(data_len, payload_size);
        int blank_len = dynamic_payloads_enabled ? 0 : payload_size - data_len;

        //printf("[Writing %u bytes %u blanks]",data_len,blank_len);
        //IF_SERIAL_DEBUG( printf_P("[Writing %u bytes %u blanks]\n",data_len,blank_len); );

        beginTransaction();
        byte[] prx = spi_rxbuff;
        byte[] ptx = spi_txbuff;
        int size;
        size = data_len + blank_len + 1 ; // Add register value to transmit buffer

        ptx[0] =  (byte) (0x000000FF & writeType);
        int idx = 0;
        while ( data_len-- > 0)
            ptx[idx + 1] = (byte)(0x000000FF & buf[idx++]);

        idx = 0;
        while ( blank_len-- > 0)
            ptx[idx++] =  0;

        transfer(spi_txbuff, spi_rxbuff, size);
        status = prx[0]; // status is 1st byte of receive buffer

        endTransaction();

        return status;
    }

    /**
     * Read the receive payload
     *
     * The size of data read is the fixed payload size, see getPayloadSize()
     *
     * @param buf Where to put the data
     * @param data_len Maximum number of bytes to read
     * @return Current value of status register
     * @throws IOException when write / read on spi doesn't work
     */
    private byte read_payload(int buf[], int data_len) throws IOException {
        byte status;
        //uint8_t* current = reinterpret_cast<uint8_t*>(buf);

        if (data_len > payload_size) data_len = payload_size;
        int blank_len = dynamic_payloads_enabled ? 0 : payload_size - data_len;

        //printf("[Reading %u bytes %u blanks]",data_len,blank_len);
        //IF_SERIAL_DEBUG( printf_P("[Reading %u bytes %u blanks]\n",data_len,blank_len); );

        beginTransaction();
        byte[] prx = spi_rxbuff;
        byte[] ptx = spi_txbuff;
        int size;
        size = data_len + blank_len + 1; // Add register value to transmit buffer

        ptx[0] = (byte) (nRF24L01.R_RX_PAYLOAD);
        int idx = 0;
        while (--size > 0)
            ptx[++idx] = (byte) (0x000000FF & nRF24L01.NOP);

        size = data_len + blank_len + 1; // Size has been lost during while, re affect

        transfer(spi_txbuff, spi_rxbuff, size);

        status = prx[0]; // 1st byte is status

        idx = 0;
        if (data_len > 0) {
            while (--data_len >= 0) // Decrement before to skip 1st status byte
                buf[idx] = prx[++idx];

            //buf[idx] = prx[idx + 1];

        }
        endTransaction();

        return status;
    }


    /**
     * Empty the receive buffer
     *
     * @return Current value of status register
     * @throws IOException when write / read on spi doesn't work
     */
    public byte flush_rx() throws IOException {
        return spiTrans((byte) nRF24L01.FLUSH_RX);
    }


    /**
     * Retrieve the current status of the chip
     *
     * @return Current value of status register
     * @throws IOException when write / read on spi doesn't work
     */
    private byte get_status() throws IOException {
        return spiTrans((byte) nRF24L01.NOP);
    }


    /**
     * Decode and print the given status to stdout
     *
     * @param status Status value to print
     *
     * Warning:  Does nothing if stdout is not defined.  See fdevopen in stdio.h
     */
    private void print_status(int status)
    {
        Log.i(TAG,"STATUS = " + Integer.toHexString(status));
        if((status & _BV(nRF24L01.RX_DR))== _BV(nRF24L01.RX_DR))
            Log.i(TAG,"RX_DR = 1");
        else
            Log.i(TAG,"RX_DR = 0");

        if((status & _BV(nRF24L01.TX_DS))== _BV(nRF24L01.TX_DS))
            Log.i(TAG,"TX_DS = 1");
        else
            Log.i(TAG,"TX_DS = 0");

        if((status & _BV(nRF24L01.MAX_RT))==_BV(nRF24L01.MAX_RT))
            Log.i(TAG,"MAX_RT = 1");
        else
            Log.i(TAG,"MAX_RT = 0");

        Log.i(TAG,"RX_P_NO = " + Integer.toHexString(((status >> nRF24L01.RX_P_NO) & 0x07)));

        if((status & _BV(nRF24L01.TX_FULL))==_BV(nRF24L01.TX_FULL))
            Log.i(TAG,"TX_FULL = 1");
        else
            Log.i(TAG,"TX_FULL = 0");

    }

    /**
     * Decode and print the given 'observe_tx' value to stdout
     *
     * @param value The observe_tx value to print
     *
     * Warning:  Does nothing if stdout is not defined.  See fdevopen in stdio.h
     */
    private void print_observe_tx(int value)
    {
        Log.i(TAG,"OBSERVE_TX="+Integer.toHexString(value)+": POLS_CNT="+Integer.toHexString((value >> nRF24L01.PLOS_CNT) & 0x0F)+" ARC_CNT="+Integer.toHexString((value >> nRF24L01.ARC_CNT) & 0x0F));
    }

    /**
     * Print the name and value of an 8-bit register to stdout
     *
     * Optionally it can print some quantity of successive
     * registers on the same line.  This is useful for printing a group
     * of related registers on one line.
     *
     * @param name Name of the register
     * @param reg Which register. Use constants from nRF24L01.h
     * @param qty How many successive registers to print
     * @throws IOException
     */
    private void print_byte_register(String name, int reg, int qty) throws IOException {
        //char extra_tab = strlen_P(name) < 8 ? '\t' : 0;
        //printf_P(PSTR(PRIPSTR"\t%c ="),name,extra_tab);
        String tmp = name;

        while (qty-- > 0)
            tmp += " " + Integer.toString(read_register(reg++));

        Log.i(TAG, tmp);
    }

    /**
     * Print the name and value of a 40-bit address register to stdout
     *
     * Optionally it can print some quantity of successive
     * registers on the same line.  This is useful for printing a group
     * of related registers on one line.
     *
     * @param name Name of the register
     * @param reg Which register. Use constants from nRF24L01.h
     * @param qty How many successive registers to print
     * @throws IOException when write / read on spi doesn't work
     */
    private void print_address_register(String name, int reg, int qty) throws IOException {
        String tmp = name;

        while (qty-- > 0)
        {
            int[] buffer = new int[addr_width];
            read_register(reg++, buffer, buffer.length);

            tmp += " " + Arrays.toString(buffer);
        }
        Log.i(TAG, tmp);
    }

    /**
     * Turn on or off the special features of the chip
     *
     * The chip has certain 'features' which are only available when the 'features'
     * are enabled.  See the datasheet for details.
     * @throws IOException when write / read on spi doesn't work
     */
    private void toggle_features() throws IOException {
        beginTransaction();
        transfer((byte) nRF24L01.ACTIVATE);
        transfer((byte) 0x73);
        endTransaction();
    }

    /**
     * Built in spi transfer function to simplify repeating code repeating code
     */

    private byte spiTrans(byte cmd) throws IOException {
        byte status;
        beginTransaction();
        status = transfer(cmd);
        endTransaction();
        return status;
    }



    private void errNotify(){
        Log.e(TAG, "RF24 HARDWARE FAIL: Radio not responding, verify pin connections, wiring, etc.");
        failureDetected = true;
    }


    /*
     * @name Arduino compatibility functions
     */
    /*@{*/

    /**
     * Stands for Bit Value where you pass it a bit and it gives you the byte value with that bit set. (avr function)
     * @param x input integer to make bit value
     * @return bit value for x
     */
    private int _BV(int x) {
        int y = (int) (1 << (x));
        return y;
    }

    /**
     * Function determines which value is greater
     * @param a Integer to compare with b
     * @param b Integer to compare with a
     * @return a if a > b, otherwise b
     */
    private int rf24_min(int a, int b) {
        if (a < b)
            return a;
        else
            return b;
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

    /*
     * Common code for SPI transactions including CSN toggle
     */
    protected void beginTransaction() {
        csn(LOW);
    }
    protected void endTransaction() {
        csn(HIGH);
    }


    /*
     * @name SPI functions
     */
    /*@{*/

    /**
     * Transfer 1 byte over SPI and return the 1 byte readed over SPI
     * @param buffer byte to be sent
     * @return received byte
     * @throws IOException when write / read on spi doesn't work
     */
    private byte transfer(byte buffer) throws IOException {
        // Shift data out to slave
        byte[] send = new byte[1];
        send[0] = buffer;

        // Shift data out to slave
        mDevice.write(send, 1);

        // Read the response
        byte[] tmp = new byte[1];
        mDevice.read(tmp, 1);

        return tmp[0];
    }

    /**
     * Transfer and receive data over SPI (received data is discarded)
     * @param buffer byte array of data to be send
     * @param size buffer size
     * @throws IOException when write / read on spi doesn't work
     */
    private void transfer(byte[] buffer, int size) throws IOException {
        // Shift data out to slave
        mDevice.write(buffer, size);

        // Read the response
        byte[] tmp = new byte[32];
        mDevice.read(tmp, tmp.length);

    }

    //

    /**
     * Full-duplex data transfer over SPI
     * @param buffer byte array of data to be send
     * @param response byte array received
     * @param size buffer size
     * @throws IOException when write/read on spi bus doesn't work
     */
    private void transfer(byte[] buffer, byte[] response, int size) throws IOException {
        //byte[] tmp = new byte[buffer.length];
        mDevice.transfer(buffer, response, size);
    }


    /****************************************************************************/
    // Closing devices
    @Override
    public void close() throws IOException {
        // closing spi
        if (mDevice != null) {
            try {
                mDevice.close();
            } finally {
                mDevice = null;
            }
        }

        // closing mCEpin
        if (mCEpin != null) {
            try {
                mCEpin.close();
            } finally {
                mCEpin = null;
            }
        }
    }

    /**
     Copyright [2017] [Mauro Riva <lemariva@mail.com> <lemariva.com>]

     Licensed under the Apache License, Version 2.0 (the "License");
     you may not use this file except in compliance with the License.
     You may obtain a copy of the License at

     http://www.apache.org/licenses/LICENSE-2.0

     Unless required by applicable law or agreed to in writing, software
     distributed under the License is distributed on an "AS IS" BASIS,
     WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
     See the License for the specific language governing permissions and
     limitations under the License.

     Originally (C++/C) from:
     * Copyright (c) 2007 Stefan Engelke <mbox@stefanengelke.de>
     * Portions Copyright (C) 2011 Greg Copeland

     Permission is hereby granted, free of charge, to any person
     obtaining a copy of this software and associated documentation
     files (the "Software"), to deal in the Software without
     restriction, including without limitation the rights to use, copy,
     modify, merge, publish, distribute, sublicense, and/or sell copies
     of the Software, and to permit persons to whom the Software is
     furnished to do so, subject to the following conditions:

     The above copyright notice and this permission notice shall be
     included in all copies or substantial portions of the Software.

     THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND,
     EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF
     MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND
     NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT
     HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY,
     WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
     OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER
     DEALINGS IN THE SOFTWARE.
     */

    private class nRF24L01 {
        /* Memory Map */
        static final int NRF_CONFIG  = 0x00;
        static final int EN_AA = 0x01;
        static final int EN_RXADDR = 0x02;
        static final int SETUP_AW = 0x03;
        static final int SETUP_RETR = 0x04;
        static final int RF_CH = 0x05;
        static final int RF_SETUP = 0x06;
        static final int NRF_STATUS = 0x07;
        static final int OBSERVE_TX = 0x08;
        static final int CD = 0x09;
        static final int RX_ADDR_P0 = 0x0A;
        static final int RX_ADDR_P1 = 0x0B;
        static final int RX_ADDR_P2 = 0x0C;
        static final int RX_ADDR_P3 = 0x0D;
        static final int RX_ADDR_P4 = 0x0E;
        static final int RX_ADDR_P5 = 0x0F;
        static final int TX_ADDR = 0x10;
        static final int RX_PW_P0 = 0x11;
        static final int RX_PW_P1 = 0x12;
        static final int RX_PW_P2 = 0x13;
        static final int RX_PW_P3 = 0x14;
        static final int RX_PW_P4 = 0x15;
        static final int RX_PW_P5 = 0x16;
        static final int FIFO_STATUS = 0x17;
        static final int DYNPD = 0x1C;
        static final int FEATURE	= 0x1D;

        /* Bit Mnemonics */
        static final int MASK_RX_DR = 6;
        static final int MASK_TX_DS = 5;
        static final int MASK_MAX_RT = 4;
        static final int EN_CRC = 3;
        static final int CRCO = 2;
        static final int PWR_UP = 1;
        static final int PRIM_RX = 0;
        static final int ENAA_P5 = 5;
        static final int ENAA_P4 = 4;
        static final int ENAA_P3 = 3;
        static final int ENAA_P2 = 2;
        static final int ENAA_P1 = 1;
        static final int ENAA_P0 = 0;
        static final int ERX_P5 = 5;
        static final int ERX_P4 = 4;
        static final int ERX_P3 = 3;
        static final int ERX_P2 = 2;
        static final int ERX_P1 = 1;
        static final int ERX_P0 = 0;
        static final int AW = 0;
        static final int ARD = 4;
        static final int ARC = 0;
        static final int PLL_LOCK = 4;
        static final int RF_DR =  3;
        static final int RF_PWR = 6;
        static final int RX_DR = 6;
        static final int TX_DS = 5;
        static final int MAX_RT = 4;
        static final int RX_P_NO = 1;
        static final int TX_FULL  = 0;
        static final int PLOS_CNT = 4;
        static final int ARC_CNT  = 0;
        static final int TX_REUSE  = 6;
        static final int FIFO_FULL =  5;
        static final int TX_EMPTY  =  4;
        static final int RX_FULL = 1;
        static final int RX_EMPTY  = 0;
        static final int DPL_P5	=  5;
        static final int DPL_P4	=  4;
        static final int DPL_P3	=  3;
        static final int DPL_P2	=  2;
        static final int DPL_P1	=  1;
        static final int DPL_P0	=  0;
        static final int EN_DPL	=  2;
        static final int EN_ACK_PAY = 1;
        static final int EN_DYN_ACK = 0;

        /* Instruction Mnemonics */
        static final int R_REGISTER  =  0x00;
        static final int W_REGISTER   = 0x20;
        static final int REGISTER_MASK = 0x1F;
        static final int ACTIVATE    = 0x50;
        static final int R_RX_PL_WID   = 0x60;
        static final int R_RX_PAYLOAD  = 0x61;
        static final int W_TX_PAYLOAD  = 0xA0;
        static final int W_ACK_PAYLOAD = 0xA8;
        static final int FLUSH_TX    = 0xE1;
        static final int FLUSH_RX    = 0xE2;
        static final int REUSE_TX_PL   = 0xE3;
        static final int NOP   =0xFF;

        /* Non-P omissions */
        static final int LNA_HCURR  = 0;

        /* P model memory Map */
        static final int RPD = 0x09;
        static final int W_TX_PAYLOAD_NO_ACK   = 0xB0;

        /* P model bit Mnemonics */
        static final int RF_DR_LOW  = 5;
        static final int RF_DR_HIGH = 3;
        static final int RF_PWR_LOW = 1;
        static final int RF_PWR_HIGH = 2;

    }

}


