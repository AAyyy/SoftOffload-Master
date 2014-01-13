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

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.util.Collection;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.floodlightcontroller.util.MACAddress;

/**
 * OffloadingAgent class is designed for recording and manage local agent (AP) info:
 * 1) ap's rate and ip address info
 * 2) connecting client map
 *
 * @author Yanhe Liu <yanhe.liu@cs.helsinki.fi>
 *
 */
public class OffloadingAgent {
    protected static Logger log = LoggerFactory.getLogger(OffloadingAgent.class);

    private InetAddress ipAddress;
    private float upRate;
    private float downRate;
    private long swDpid;                        // 0 means it is not the real value
    private short swInPort = (short)-1;        // -1 means it is not the real value
    private DatagramSocket agentSocket = null;

    private Map<String, OffloadingClient> clientMap
        = new ConcurrentHashMap<String, OffloadingClient> ();

    // defaults
    private final int AGENT_PORT = 6777;

    public OffloadingAgent(InetAddress ipAddr, long dpid) {
        this.ipAddress = ipAddr;
        this.swDpid = dpid;
        try {
            this.agentSocket = new DatagramSocket();
        } catch (SocketException e) {
            e.printStackTrace();
            System.exit(1);
        }
    }

    public OffloadingAgent(String ipAddr, long dpid) {
        this.swDpid = dpid;
        try {
            this.ipAddress = InetAddress.getByName(ipAddr);
            this.agentSocket = new DatagramSocket();
        } catch (SocketException e) {
            e.printStackTrace();
            System.exit(1);
        } catch (UnknownHostException e1) {
            e1.printStackTrace();
            System.exit(1);
        }
    }

    public InetAddress getIpAddress() {
        return this.ipAddress;
    }

    public float getUpRate() {
        return this.upRate;
    }

    public void updateUpRate(float r) {
        this.upRate = r;
    }

    public float getDownRate() {
        return this.downRate;
    }

    public void updateDownRate(float r) {
        this.downRate = r;
    }

    public long getSwitchDpid() {
        // (short)0 means not fully initialzed yet
        return this.swDpid;
    }

    public void setSwitchDpid(long dpid) {
        this.swDpid = dpid;
    }

    public short getSwitchInPort() {
        // (short)-1 means not fully initialzed yet
        return this.swInPort;
    }

    public void setSwitchInPort(short port) {
        this.swInPort = port;
    }

    /**
     * Add a client to the agent tracker
     *
     * @param hwAddress Client's hw address
     * @param ipv4Address Client's IPv4 address
     */
    public void addClient(final MACAddress clientHwAddress, final InetAddress ipv4Address) {
        String mac = clientHwAddress.toString().toLowerCase();

        if (!clientMap.containsKey(mac)) {
            clientMap.put(mac, new OffloadingClient(clientHwAddress, ipv4Address));
        }
    }

    /**
     * Add a client to the agent tracker
     *
     * @param initailized client instance
     */
    public void addClient(OffloadingClient client) {
        String clientMac = client.getMacAddress().toString().toLowerCase();

        if (!clientMap.containsKey(clientMac)) {
            clientMap.put(clientMac, client);
        }
    }

    /**
     * get the corresponding client instance
     *
     * @param macAddress MAC address string
     */
    public OffloadingClient getClient(String macAddress) {
        // assert clientMap.containsKey(macAddress);

        return clientMap.get(macAddress.toLowerCase());
    }

    /**
     * get the corresponding client instance
     *
     * @param macAddress MAC address
     */
    public OffloadingClient getClient(MACAddress macAddress) {
        String clientMac = macAddress.toString().toLowerCase();
        // assert clientMap.containsKey(clientMac);

        return clientMap.get(clientMac);
    }

    /**
     * get all corresponding client instances
     *
     */
    public Collection<OffloadingClient> getAllClients() {

        return clientMap.values();
    }

    /**
     * Remove a client from the agent tracker
     *
     * @param client - initailized client instance
     */
    public void removeClient(OffloadingClient client) {
        String clientMac = client.getMacAddress().toString().toLowerCase();

        if (clientMap.containsKey(clientMac)) {
            clientMap.remove(clientMac);
        }
    }

    /**
     * Remove a client from the agent tracker
     *
     * @param mac address string
     */
    public void removeClient(String clientMac) {
        if (!clientMap.containsKey(clientMac.toLowerCase())) {
            clientMap.remove(clientMac.toLowerCase());
        }
    }

    /**
     * Remove all clients from the agent tracker
     *
     */
    public void removeAllClients() {
        clientMap.clear();
    }

    /**
     * get client number on this agent
     *
     * @param macAddress MAC address
     */
    public int getClientNum() {
        return clientMap.size();
    }

    @Override
    public String toString() {
        StringBuilder builder = new StringBuilder();

        builder.append("Agent " + ipAddress.getHostAddress() + ", uprate="
                + Float.toString(upRate) + ", downrate=" + Float.toString(downRate)
                + ", dpid=" + Long.toString(swDpid) + ", inport="
                + Short.toString(swInPort) + ", clientNum="
                + Integer.toString(getClientNum()));

        return builder.toString();
    }

    /**
     * response to the client-info messages from agent
     * 1) initialize client instance
     * 2) update client ip address info
     *
     * @param clientEthAddr, client MAC address in the messages
     * @param clientIpAddr, client IPv4 address
     */
    void receiveClientInfo(final String clientEthAddr, final String clientIpAddr) {

        // update client info
        try {
            if (clientMap.containsKey(clientEthAddr)) {
                OffloadingClient client = clientMap.get(clientEthAddr);
                if (client.getIpAddress().getHostAddress() != clientIpAddr) {
                    client.setIpAddress(clientIpAddr);
                }
            } else {
                OffloadingClient client = new OffloadingClient(clientEthAddr, clientIpAddr, this.swDpid);
                clientMap.put(clientEthAddr.toLowerCase(), client);

                log.info("Discoveried client {} -- {}, initializing it...", clientEthAddr, clientIpAddr);

                // send ack message to agent ap
                byte[] buf = new byte[128];
                buf = "ack clientinfo \n".getBytes();
                DatagramPacket packet = new DatagramPacket(buf, buf.length,
                                                this.ipAddress, AGENT_PORT);
                this.agentSocket.send(packet);
            }
        } catch (UnknownHostException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /**
     * response to the client-rate messages from agent
     *
     * @param clientEthAddr, client mac address
     * @param rate, client's byte rate of ip flows
     */
    void receiveClientRate(final String clientEthAddr, final float uprate,
                            final float downrate) {

        if (clientMap.containsKey(clientEthAddr)) {
            getClient(clientEthAddr).updateUpRate(uprate);
            getClient(clientEthAddr).updateDownRate(downrate);
            System.out.println(getClient(clientEthAddr).toString());
        } else {
            log.warn("Received uninilized Client rate info, discard it!");
        }
    }

}
