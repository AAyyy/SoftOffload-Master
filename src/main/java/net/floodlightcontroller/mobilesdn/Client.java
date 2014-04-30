/**
*    Copyright 2013 University of Helsinki
*
*    Licensed under the Apache License, Version 2.0 (the "License"); you may
*    not use this file except in compliance with the License. You may obtain
*    a copy of the License at
*
*         http://www.apache.org/licenses/LICENSE-2.0
*
*    Unless required by applicable law or agreed to in writing, software
*    distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
*    WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
*    License for the specific language governing permissions and limitations
*    under the License.
**/



package net.floodlightcontroller.mobilesdn;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Timer;
import java.util.TimerTask;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.floodlightcontroller.core.IOFSwitch;
import net.floodlightcontroller.util.MACAddress;

/**
 * Class for Wireless client:
 * used for recording and managing client info
 *
 * @author Yanhe Liu <yanhe.liu@cs.helsinki.fi>
 *
 */
public class Client implements Comparable<Object> {
    protected static Logger log = LoggerFactory.getLogger(Client.class);

    private final MACAddress hwAddress;
    private InetAddress ipAddress;
    private float upRate;
    private float downRate;
    private String app = "trivial";

    private IOFSwitch ofSwitch = null;      // not initialized
    private APAgent agent;

    private Timer switchTimer;

    // defaults
    static private final long SECONDS = 3 * 60 * 1000;

    private void initializeClientTimer() {

        switchTimer = new Timer();    // set the timer

        switchTimer.schedule(new TimerTask() {
            @Override
            public void run() {
                // set up message data
                byte[] mac = hwAddress.toBytes();
                byte[] b1 = "c".getBytes();
                byte[] b2 = "switch|sdntest1|open|\n".getBytes();

                byte[] message = new byte[b1.length + b2.length + mac.length];

                System.arraycopy(b1, 0, message, 0, b1.length);
                System.arraycopy(mac, 0, message, b1.length, mac.length);
                System.arraycopy(b2, 0, message, b1.length + mac.length, b2.length);

                agent.send(message);
                log.info("Send message to agent for client switching");
            }
        }, SECONDS);
    }

    /**
     * construct a client instance
     *
     * @param hwAddress Client's hw address
     * @param ipv4Address Client's IPv4 address
     */
    public Client(MACAddress hwAddress, InetAddress ipAddress, APAgent agt) {
        this.hwAddress = hwAddress;
        this.ipAddress = ipAddress;
        this.agent = agt;

        // initializeClientTimer();
    }

    /**
     * construct a client instance
     *
     * @param hwAddress Client's hw address
     * @param ipv4Address Client's IPv4 address
     */
    public Client(String hwAddress, String ipAddress, APAgent agt) throws UnknownHostException {
        this.hwAddress = MACAddress.valueOf(hwAddress);
        this.ipAddress = InetAddress.getByName(ipAddress);
        this.agent = agt;

        // initializeClientTimer();
    }

    /**
     * construct a client instance
     *
     * @param hwAddress Client's hw address
     * @param ipv4Address Client's IPv4 address
     */
    public Client(MACAddress hwAddress, InetAddress ipAddress, IOFSwitch sw, APAgent agt) {
        this.hwAddress = hwAddress;
        this.ipAddress = ipAddress;
        this.ofSwitch = sw;
        this.agent = agt;

        // initializeClientTimer();
    }

    /**
     * construct a client instance
     *
     * @param hwAddress Client's hw address
     * @param ipv4Address Client's IPv4 address
     */
    public Client(String hwAddress, String ipAddress, IOFSwitch sw, APAgent agt) throws UnknownHostException {
        this.hwAddress = MACAddress.valueOf(hwAddress);
        this.ipAddress = InetAddress.getByName(ipAddress);
        this.ofSwitch = sw;
        this.agent = agt;

        // initializeClientTimer();
    }

    /**
     * Get the client's MAC address.
     * @return
     */
    public MACAddress getMacAddress() {
        return this.hwAddress;
    }

    /**
     * Get the client's IP address.
     * @return
     */
    public InetAddress getIpAddress() {
        return ipAddress;
    }

    /**
     * Set the client's IP address
     * @param addr
     */
    public void setIpAddress(InetAddress addr) {
        this.ipAddress = addr;
    }

    /**
     * Set the client's IP address
     * @param String addr
     * @throws UnknownHostException
     */
    public void setIpAddress(String addr) throws UnknownHostException {
        this.ipAddress = InetAddress.getByName(addr);
    }

    /**
     * get client's uprate value
     * @return
     */
    public float getUpRate() {
        return this.upRate;
    }

    /**
     * get client's downrate value
     * @return
     */
    public float getDownRate() {
        return this.downRate;
    }

    /**
     * Set the client's up rate value
     * @param r
     */
    public void updateUpRate(float r) {
        this.upRate = r;
    }

    /**
     * Set the client's down rate value
     * @param r
     */
    public void updateDownRate(float r) {
        this.downRate = r;
    }

    /**
     * get client's corresponding openflow switch instance
     * @return
     */
    public IOFSwitch getSwitch() {
        return this.ofSwitch;
    }

    /**
     * Set the client's association openflow switch instance
     *
     * @param sw
     */
    public void setSwitch(IOFSwitch sw) {
        this.ofSwitch = sw;
    }

    /**
     * Set the client's running application
     *
     * @param app
     */
    public void setApp(String app) {
        this.app = app;
    }

    /**
     * Get the client's running app
     * @return app
     */
    public String getApp() {
        return app;
    }

    /**
     * Get the client's corresponding AP Agent.
     * @return
     */
    public APAgent getAgent() {
        return agent;
    }

    /**
     * clear the task for the timer
     */
    public void cancelTask() {
        this.switchTimer.cancel();
        this.switchTimer.purge();
    }

    @Override
    public String toString() {
        StringBuilder builder = new StringBuilder();

        builder.append("Client " + hwAddress.toString() + ", ipAddr="
                + ipAddress.getHostAddress() + ", uprate="
                + Float.toString(upRate) + ", downrate=" + Float.toString(downRate)
                + ", dpid=" + Long.toString(ofSwitch.getId()));

        return builder.toString();
    }

    @Override
    public boolean equals(Object obj) {
        if (!(obj instanceof Client))
            return false;

        if (obj == this)
            return true;

        Client that = (Client) obj;

        return (this.hwAddress.equals(that.getMacAddress()));
    }


    @Override
    public int compareTo(Object o) {
        assert (o instanceof Client);

        if (this.hwAddress.toLong() == ((Client)o).getMacAddress().toLong())
            return 0;

        if (this.hwAddress.toLong() > ((Client)o).getMacAddress().toLong())
            return 1;

        return -1;
    }

}
