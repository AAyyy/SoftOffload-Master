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
public class APAgent implements Comparable<Object> {
    protected static Logger log = LoggerFactory.getLogger(APAgent.class);

    private InetAddress ipAddress;
    private String ssid;
    private String bssid;
    private String auth;
    private short ofPort;
    private double downlinkBW;

    private double upRate;
    private double downRate;
    private Map<String, Client> clientMap = new ConcurrentHashMap<String, Client>();
    private IOFSwitch ofSwitch = null;          // not initialized
    private DatagramSocket agentSocket = null;
    // private boolean offloadingFlag = false;   // OFMonitor may change this to true
    
    // used for new OFMonitor statistics
    private long ofDownBytes = 0;
    private long ofUpBytes = 0;
    private double ofDownRate;
    private int downRateOverNum = 0;
    private int pendingNum = 0;


    // defaults
    private final int AGENT_PORT = 6777;
    static private final float RATE_THRESHOLD = 500000;
    static private final int MAX_LEN = 512;


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

    public APAgent(InetAddress ipAddr, IOFSwitch sw, String s, String b, String auth, short port, double bw) {
        this.ipAddress = ipAddr;
        this.ofSwitch = sw;
        this.ssid = s;
        this.bssid = b;
        this.auth = auth;
        this.ofPort = port;
        this.downlinkBW = bw;

        try {
            this.agentSocket = new DatagramSocket();
        } catch (SocketException e) {
            e.printStackTrace();
            System.exit(1);
        }
    }

    public APAgent(String ipAddr, IOFSwitch sw, String s, String b, String auth, short port, double bw) {

        this.ofSwitch = sw;
        this.ssid = s;
        this.bssid = b;
        this.auth = auth;
        this.ofPort = port;
        this.downlinkBW = bw;

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
    public double getUpRate() {
        return this.upRate;
    }

    /**
     * Set the AP's up rate value
     * @param r
     */
    public void updateUpRate(double r) {
        this.upRate = r;
    }

    /**
     * get AP's total down rate value
     * @return downRate (float)
     */
    public double getDownRate() {
        return this.downRate;
    }

    /**
     * Set the AP's down rate value
     * @param r
     */
    public void updateDownRate(double r) {
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
     * get AP's max downlink bandwidth
     * @return
     */
    public double getDownlinkBW() {
        return this.downlinkBW;
    }

    /**
     * set AP's associating openflow switch instance
     * @return
     */
    public void setSwitch(IOFSwitch sw) {
        this.ofSwitch = sw;
    }

    public String getSSID() {
        return this.ssid;
    }

    public String getBSSID() {
        return this.bssid;
    }

    public String getAuth() {
        return this.auth;
    }

    public void setSSID(String s) {
        ssid = s;
    }

    public void setBSSID(String b) {
        bssid = b;
    }

    public void setAuth(String auth) {
        this.auth = auth;
    }
    
    public short getOFPort() {
        return this.ofPort;
    }

    public void setOFPort(short port) {
        ofPort = port;
    }
    
    public void setDownlinkBW(double bw) {
        downlinkBW = bw;
    }
    
    
    // these following funcs might be removed later
    public int getDownRateOverNum() {
        return downRateOverNum;
    }

    public int getPendingNum() {
        return pendingNum;
    }
    
    public long getOFDownBytes() {
        return ofDownBytes;
    }

    public long getOFUpBytes() {
        return ofUpBytes;
    }

    public void setOFDownBytes(long bytes) {
        ofDownBytes = bytes;
    }

    public void setOFUpBytes(long bytes) {
        ofUpBytes = bytes;
    }

    public void setDownRateOverNum(int num) {
        downRateOverNum = num;
    }

    public void setPendingNum(int num) {
        pendingNum = num;
    }
    
    public double getOFDownRate() {
        return ofDownRate;
    }

    public void setOFDownRate(double rate) {
        ofDownRate = rate;
    }
    


    /**
     * Add a client to the agent tracker
     *
     * @param hwAddress Client's hw address
     * @param ipv4Address Client's IPv4 address
     */
    // public void addClient(final MACAddress clientHwAddress, final InetAddress ipv4Address) {
        // // we use lower case keys for the clientMap
        // String mac = clientHwAddress.toString().toLowerCase();

        // if (ofSwitch != null) {
            // clientMap.put(mac, new Client(clientHwAddress, ipv4Address, ofSwitch, this));
        // } else {
            // clientMap.put(mac, new Client(clientHwAddress, ipv4Address, this));
        // }
    // }

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
            // clientMap.get(clientMac).cancelTask();
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

        if (clientMap.containsKey(mac)) {
            // clientMap.get(mac).cancelTask();
            clientMap.remove(mac);
        }
    }

    /**
     * Remove all clients from the agent tracker
     *
     */
    public void removeAllClients() {
        // for (Client i: clientMap.values()) {
        //     i.cancelTask();
        // }

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
    
    /**
     * set offloadingFlag, this flag is used to indicate whether offloading is 
     * needed now for this AP
     *
     * @param macAddress MAC address
     */
    /**
    public synchronized void setOffloadingFlag(boolean flag) {
        offloadingFlag = flag;
    }
    
    public boolean getOffloadingFlag() {
        return offloadingFlag;
    }
    */

    public void send(String message) {
        // send message to agent ap
        byte[] buf = new byte[MAX_LEN];
        buf = message.getBytes();
        DatagramPacket packet = new DatagramPacket(buf, buf.length,
                                        this.ipAddress, this.AGENT_PORT);
        try {
            this.agentSocket.send(packet);
        } catch (IOException e) {
            log.error("can not send udp message to agent: " + message);
            e.printStackTrace();
        }
    }

    public void send(byte[] message) {
        // send message to agent ap
        DatagramPacket packet = new DatagramPacket(message, message.length,
                                        this.ipAddress, this.AGENT_PORT);
        try {
            this.agentSocket.send(packet);
        } catch (IOException e) {
            log.error("can not send udp message to agent: " + message.toString());
            e.printStackTrace();
        }
    }

    @Override
    public String toString() {
        StringBuilder builder = new StringBuilder();

        builder.append("Agent " + ipAddress.getHostAddress() + ", uprate="
                + Double.toString(upRate) + ", downrate=" + Double.toString(downRate)
                + ", clientNum=" + Integer.toString(getClientNum()));

        if (this.ofSwitch != null) {
            builder.append(", dpid=" + Long.toString(ofSwitch.getId()));
        }

        return builder.toString();
    }

    /**
     * response to the client-info messages from agent
     * 1) initialize client instance
     * 2) update client ip address info
     *
     * @param clientEthAddr, client MAC address in the messages
     * @param clientIpAddr, client IPv4 address
     * @return client, client instance corresponding to this info
     */
    Client receiveClientInfo(final String clientEthAddr, final String clientIpAddr) {

        String mac = clientEthAddr.toLowerCase();

        try {
            if (clientMap.containsKey(mac)) { // update client info
                Client client = clientMap.get(mac);
                if (client.getIpAddress().getHostAddress() != clientIpAddr) {
                    client.setIpAddress(clientIpAddr);
                }
            } else { // new client
                Client client = new Client(clientEthAddr, clientIpAddr, this);
                if (ofSwitch != null) {
                    client.setSwitch(ofSwitch);
                }
                clientMap.put(mac, client);

                log.info("New client connected {} -- {}, initializing it...", 
                        clientEthAddr, clientIpAddr);
                
                return client;
            }
        } catch (UnknownHostException e) {
            e.printStackTrace();
        }

        return null;
    }

    /**
     * response to the client-rate messages from agent
     *
     * @param clientEthAddr, client mac address
     * @param rate, client's byte rate of ip flows
     */
    Client receiveClientRate(final String clientEthAddr, final double uprate,
                            final double downrate) {

        String mac = clientEthAddr.toLowerCase();

        if (clientMap.containsKey(mac)) {
            Client clt = clientMap.get(mac);
            clt.updateUpRate(uprate);
            clt.updateDownRate(downrate);
            // System.out.println(clt.toString());
            
            IOFSwitch sw = clt.getSwitch();
            // modify flow action to drop
            if (uprate >= RATE_THRESHOLD && sw != null) {

                log.info("FlowRate = {}bytes/s: suspicious flow, " +
                        "drop matched pkts", Double.toString(uprate));

                dropFlow(sw, clt, mac);
            }
            
            return clt;
        } else {
            log.warn("Received uninilized Client rate info, checking with agent...");
            
            MACAddress macAddr = MACAddress.valueOf(mac);
            byte[] m = macAddr.toBytes();
            byte[] signal = "ack".getBytes();
            byte[] message = new byte[m.length + signal.length];

            System.arraycopy(signal, 0, message, 0, signal.length);
            System.arraycopy(m, 0, message, signal.length, m.length);
            this.send(message);
            
            return null;
        }
    }
    
    public void dropFlow(IOFSwitch sw, Client clt, String mac) {
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
   


    @Override
    public boolean equals(Object obj) {
        if (!(obj instanceof APAgent))
            return false;

        if (obj == this)
            return true;

        APAgent that = (APAgent) obj;

        return (this.bssid.toLowerCase().equals(that.getBSSID().toLowerCase()));
    }


    @Override
    public int compareTo(Object o) {
        assert (o instanceof APAgent);

        if (this.bssid.toLowerCase().equals(((APAgent)o).getBSSID().toLowerCase()))
            return 0;

        if (this.downRate > ((APAgent)o).getDownRate())
            return 1;

        return -1;
    }
}
