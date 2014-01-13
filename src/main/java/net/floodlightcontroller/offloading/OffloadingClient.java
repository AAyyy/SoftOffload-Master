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



package net.floodlightcontroller.offloading;

import java.net.InetAddress;
import java.net.UnknownHostException;

import net.floodlightcontroller.util.MACAddress;

/**
 * Wireless client class
 * Used for recording and managing client info
 *
 * @author Yanhe Liu <yanhe.liu@cs.helsinki.fi>
 *
 */
public class OffloadingClient implements Comparable<Object> {
    private final MACAddress hwAddress;
    private InetAddress ipAddress;
    private float upRate;
    private float downRate;
    private long swDpid;    // associated openflow switch, 0 means not be fully initialized
    private short swInPort; // associated openflow port, -1 means not be fully initialized


    /**
     * construct a client instance
     *
     * @param hwAddress Client's hw address
     * @param ipv4Address Client's IPv4 address
     */
    public OffloadingClient(MACAddress hwAddress, InetAddress ipAddress) {
        this.hwAddress = hwAddress;
        this.ipAddress = ipAddress;
        this.swDpid = (long)0;
        this.swInPort = (short)-1;
    }

    /**
     * construct a client instance
     *
     * @param hwAddress Client's hw address
     * @param ipv4Address Client's IPv4 address
     */
    public OffloadingClient(String hwAddress, String ipAddress) throws UnknownHostException {
        this.hwAddress = MACAddress.valueOf(hwAddress);
        this.ipAddress = InetAddress.getByName(ipAddress);
        this.swDpid = (long)0;
        this.swInPort = (short)-1;
    }

    /**
     * construct a client instance
     *
     * @param hwAddress Client's hw address
     * @param ipv4Address Client's IPv4 address
     */
    public OffloadingClient(MACAddress hwAddress, InetAddress ipAddress, long dpid) {
        this.hwAddress = hwAddress;
        this.ipAddress = ipAddress;
        this.swDpid = dpid;
        this.swInPort = (short)-1;
    }

    /**
     * construct a client instance
     *
     * @param hwAddress Client's hw address
     * @param ipv4Address Client's IPv4 address
     */
    public OffloadingClient(String hwAddress, String ipAddress, long dpid) throws UnknownHostException {
        this.hwAddress = MACAddress.valueOf(hwAddress);
        this.ipAddress = InetAddress.getByName(ipAddress);
        this.swDpid = dpid;
        this.swInPort = (short)-1;
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
     * get client's corresponding openflow switch dataplane id
     * @return
     */
    public long getSwitchDpid() {
        return this.swDpid;
    }

    /**
     * Set the client's association openflow switch's dataplane id
     * this id can be used to get corresponding IOFSwitch instance
     * @param dpid
     */
    public void setSwitchDpid(long dpid) {
        this.swDpid = dpid;
    }

    /**
     * get client's corresponding openflow switch in port
     * @return
     */
    public short getSwitchInPort() {
        return this.swInPort;
    }

    /**
     * Set the client's association openflow switch's in port
     * @param port
     */
    public void setSwitchInPort(short port) {
        this.swInPort = port;
    }

    @Override
    public String toString() {
        StringBuilder builder = new StringBuilder();

        builder.append("Client " + hwAddress.toString() + ", ipAddr="
                + ipAddress.getHostAddress() + ", uprate="
                + Float.toString(upRate) + ", downrate=" + Float.toString(downRate)
                + ", dpid=" + Long.toString(swDpid) + ", inport="
                + Short.toString(swInPort));

        return builder.toString();
    }

    @Override
    public boolean equals(Object obj) {
        if (!(obj instanceof OffloadingClient))
            return false;

        if (obj == this)
            return true;

        OffloadingClient that = (OffloadingClient) obj;

        return (this.hwAddress.equals(that.hwAddress));
    }


    @Override
    public int compareTo(Object o) {
        assert (o instanceof OffloadingClient);

        if (this.hwAddress.toLong() == ((OffloadingClient)o).hwAddress.toLong())
            return 0;

        if (this.hwAddress.toLong() > ((OffloadingClient)o).hwAddress.toLong())
            return 1;

        return -1;
    }
}
