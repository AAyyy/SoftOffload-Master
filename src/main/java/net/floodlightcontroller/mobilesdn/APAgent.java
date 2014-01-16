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

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.openflow.protocol.OFFlowMod;
import org.openflow.protocol.OFMatch;
import org.openflow.protocol.OFPacketOut;
import org.openflow.protocol.OFPort;
import org.openflow.protocol.Wildcards;
import org.openflow.protocol.Wildcards.Flag;
import org.openflow.protocol.action.OFAction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.floodlightcontroller.core.IOFSwitch;
import net.floodlightcontroller.packet.IPv4;
import net.floodlightcontroller.util.MACAddress;

/**
 * APAgent class is designed for recording and manage AP(local agent) info:
 * 1) ap's rate and ip address info
 * 2) connecting client mapping
 *
 * @author Yanhe Liu <yanhe.liu@cs.helsinki.fi>
 *
 */
public class APAgent {
    protected static Logger log = LoggerFactory.getLogger(APAgent.class);

    private InetAddress ipAddress;
    private float upRate;
    private float downRate;
    private IOFSwitch ofSwitch = null;          // not initialized
    private DatagramSocket agentSocket = null;

    private Map<String, Client> clientMap
        = new ConcurrentHashMap<String, Client> ();

    // defaults
    private final int AGENT_PORT = 6777;
    static private final float RATE_THRESHOLD = 5000;


    public APAgent(InetAddress ipAddr) {
        this.ipAddress = ipAddr;
        try {
            this.agentSocket = new DatagramSocket();
        } catch (SocketException e) {
            e.printStackTrace();
            System.exit(1);
        }
    }

    public APAgent(String ipAddr) {
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

    public APAgent(InetAddress ipAddr, IOFSwitch sw) {
        this.ipAddress = ipAddr;
        this.ofSwitch = sw;
        try {
            this.agentSocket = new DatagramSocket();
        } catch (SocketException e) {
            e.printStackTrace();
            System.exit(1);
        }
    }

    public APAgent(String ipAddr, IOFSwitch sw) {

        this.ofSwitch = sw;

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

    /**
     * Get the AP's management IPv4 address.
     * @return
     */
    public InetAddress getIpAddress() {
        return this.ipAddress;
    }

    /**
     * get AP's total up rate value
     * @return
     */
    public float getUpRate() {
        return this.upRate;
    }

    /**
     * Set the AP's up rate value
     * @param r
     */
    public void updateUpRate(float r) {
        this.upRate = r;
    }

    /**
     * get AP's total down rate value
     * @return
     */
    public float getDownRate() {
        return this.downRate;
    }

    /**
     * Set the AP's down rate value
     * @param r
     */
    public void updateDownRate(float r) {
        this.downRate = r;
    }

    /**
     * get AP's corresponding openflow switch instance
     * @return
     */
    public IOFSwitch getSwitch() {
        return this.ofSwitch;
    }

    /**
     * set AP's associating openflow switch instance
     * @return
     */
    public void setSwitch(IOFSwitch sw) {
        this.ofSwitch = sw;
    }


    /**
     * Add a client to the agent tracker
     *
     * @param hwAddress Client's hw address
     * @param ipv4Address Client's IPv4 address
     */
    public void addClient(final MACAddress clientHwAddress, final InetAddress ipv4Address) {
        // we use lower case keys for the clientMap
        String mac = clientHwAddress.toString().toLowerCase();

        if (ofSwitch != null) {
            clientMap.put(mac, new Client(clientHwAddress, ipv4Address, ofSwitch));
        } else {
            clientMap.put(mac, new Client(clientHwAddress, ipv4Address));
        }
    }

    /**
     * Add a client to the agent tracker
     *
     * @param initailized client instance
     */
    public void addClient(Client client) {
        String clientMac = client.getMacAddress().toString().toLowerCase();

        clientMap.put(clientMac, client);
    }

    /**
     * get the corresponding client instance
     *
     * @param macAddress MAC address string
     */
    public Client getClient(String macAddress) {
        // assert clientMap.containsKey(macAddress);

        return clientMap.get(macAddress.toLowerCase());
    }

    /**
     * get the corresponding client instance
     *
     * @param macAddress MAC address
     */
    public Client getClient(MACAddress macAddress) {
        String clientMac = macAddress.toString().toLowerCase();
        // assert clientMap.containsKey(clientMac);

        return clientMap.get(clientMac);
    }

    /**
     * get all corresponding client instances
     *
     */
    public Collection<Client> getAllClients() {

        return clientMap.values();
    }

    /**
     * Remove a client from the agent tracker
     *
     * @param client - initailized client instance
     */
    public void removeClient(Client client) {
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
        String mac = clientMac.toLowerCase();

        if (!clientMap.containsKey(mac)) {
            clientMap.remove(mac);
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
                + ", dpid=" + Long.toString(ofSwitch.getId()) + ", clientNum="
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

        String mac = clientEthAddr.toLowerCase();

        try {
            if (clientMap.containsKey(mac)) { // update client info
                Client client = clientMap.get(mac);
                if (client.getIpAddress().getHostAddress() != clientIpAddr) {
                    client.setIpAddress(clientIpAddr);
                }
            } else {
                Client client = new Client(clientEthAddr, clientIpAddr);
                if (ofSwitch != null) {
                    client.setSwitch(ofSwitch);
                }
                clientMap.put(mac, client);

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

        String mac = clientEthAddr.toLowerCase();

        if (clientMap.containsKey(mac)) {
            Client clt = clientMap.get(mac);
            IOFSwitch sw = clt.getSwitch();

            // modify flow action to drop
            if (uprate >= RATE_THRESHOLD && sw != null) {

                log.info("Detected suspicious traffic, drop the flow!");

                OFMatch match = new OFMatch();
                match.setWildcards(Wildcards.FULL.matchOn(Flag.DL_SRC)
                                                 .matchOn(Flag.DL_TYPE)
                                                 .matchOn(Flag.NW_SRC)
                                                 .withNwSrcMask(32));
                match.setDataLayerSource(mac)
                     .setDataLayerType((short)0x0800)
                     .setNetworkSource(IPv4.toIPv4Address(clt.getIpAddress().getAddress()));

                OFFlowMod flowMod = new OFFlowMod();
                // set no action to drop
                List<OFAction> actions = new ArrayList<OFAction>();

                // set flow_mod
                flowMod.setCookie(67);   // some value chosen randomly
                flowMod.setPriority((short)200);
                flowMod.setOutPort(OFPort.OFPP_NONE);
                flowMod.setMatch(match);
                // this buffer_id is needed for avoiding a BAD_REQUEST error
                flowMod.setBufferId(OFPacketOut.BUFFER_ID_NONE);
                flowMod.setHardTimeout((short) 0);
                flowMod.setIdleTimeout((short) 20);
                flowMod.setActions(actions);
                flowMod.setCommand(OFFlowMod.OFPFC_MODIFY);

                // send flow_mod

                try {
                    sw.write(flowMod, null);
                    sw.flush();
                } catch (IOException e) {
                    log.error("tried to write flow_mod to {} but failed: {}",
                                sw.getId(), e.getMessage());
                } catch (Exception e) {
                    log.error("Failure to modify flow entries", e);
                }
            }

            clt.updateUpRate(uprate);
            clt.updateDownRate(downrate);
            System.out.println(clt.toString());
            log.info("FlowRate = {}bytes/s: suspicious flow, " +
                    "drop matched pkts", Float.toString(upRate));
        } else {
            log.warn("Received uninilized Client rate info, discard it!");
        }
    }

}