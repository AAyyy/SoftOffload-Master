/**
 *
 */
package net.floodlightcontroller.offloading;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import net.floodlightcontroller.util.MACAddress;

/**
 * @author Yanhe Liu <yanhe.liu@cs.helsinki.fi>
 *
 */
public class OffloadingAgent {

    private InetAddress ipAddress;
    private float rate;
    private DatagramSocket agentSocket = null;

    private Map<String, OffloadingClient> clientMap
        = new ConcurrentHashMap<String, OffloadingClient> ();

    // defaults
    private final int AGENT_PORT = 6777;

    public OffloadingAgent(InetAddress ipAddr) {
        this.ipAddress = ipAddr;
        try {
            this.agentSocket = new DatagramSocket();
        } catch (SocketException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
    }

    public InetAddress getIpAddress() {
        return this.ipAddress;
    }

    public float getRate() {
        return this.rate;
    }

    public void updateRate(float r) {
        this.rate = r;
    }

    /**
     * Add a client to the agent tracker
     *
     * @param hwAddress Client's hw address
     * @param ipv4Address Client's IPv4 address
     */
    public void addClient(final MACAddress clientHwAddress, final InetAddress ipv4Address) {
        String mac = clientHwAddress.toString();

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
        String clientMac = client.getMacAddress().toString();

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
        assert clientMap.containsKey(macAddress);

        return clientMap.get(macAddress);
    }

    /**
     * get the corresponding client instance
     *
     * @param macAddress MAC address
     */
    public OffloadingClient getClient(MACAddress macAddress) {
        String clientMac = macAddress.toString();
        assert clientMap.containsKey(clientMac);

        return clientMap.get(clientMac);
    }

    /**
     * Remove a client from the agent tracker
     *
     * @param client - initailized client instance
     */
    public void removeClient(OffloadingClient client) {
        String clientMac = client.getMacAddress().toString();

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
        if (!clientMap.containsKey(clientMac)) {
            clientMap.remove(clientMac);
        }
    }

    /**
     * response to the client-info messages from agent
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
                OffloadingClient client = new OffloadingClient(clientEthAddr, clientIpAddr);
                clientMap.put(clientEthAddr, client);

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
    void receiveClientRate(final String clientEthAddr, final float rate) {

        getClient(clientEthAddr).updateRate(rate);

    }

}
