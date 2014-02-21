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
import java.util.concurrent.ExecutorService;

// import net.floodlightcontroller.util.MACAddress;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * This is an implementation of sdn wireless controllers
 *
 * @author Yanhe Liu <yanhe.liu@cs.helsinki.fi>
 *
 **/

class ClickManageServer implements Runnable {

    protected static Logger log = LoggerFactory.getLogger(ClickManageServer.class);

    // Message types
    private final String MSG_CLIENT_INFO = "client";
    private final String MSG_AGENT_RATE = "agentrate";
    private final String MSG_CLIENT_RATE = "clientrate";
    private final String MSG_CLIENT_DISCONNECT = "clientdisconnect";

    private final int SERVER_PORT;

    private DatagramSocket controllerSocket;
    private final ExecutorService executor;
    private final Master master;

    public ClickManageServer (Master m, int port, ExecutorService executor) {
        this.master = m;
        this.SERVER_PORT = port;
        this.executor = executor;
    }

    @Override
    public void run() {

        try {
            controllerSocket = new DatagramSocket(SERVER_PORT);
        } catch (IOException e) {
            e.printStackTrace();
        }

        while(true)	{

            try {
                final byte[] receiveData = new byte[1024]; // We can probably live with less
                final DatagramPacket receivedPacket = new DatagramPacket(receiveData, receiveData.length);
                controllerSocket.receive(receivedPacket);

                executor.execute(new ConnectionHandler(receivedPacket));
            }
            catch (IOException e) {
                log.error("controllerSocket.accept() failed: " + SERVER_PORT);
                e.printStackTrace();
                System.exit(-1);
            }
        }
    }

    /** Protocol handlers **/

    private void receiveClientInfo(final InetAddress agentAddr,
            final String clientEthAddr, final String clientIpAddr) {
        master.receiveClientInfo(agentAddr, clientEthAddr, clientIpAddr);
    }

    private void receiveAgentRate(final InetAddress agentAddr,
            final String upRate, final String downRate) {
        master.receiveAgentRate(agentAddr, upRate, downRate);
    }

    private void receiveClientRate(final InetAddress agentAddr,
            final String clientEthAddr, final String clientIpAddr,
            final String upRate, final String downRate) {
        master.receiveClientRate(agentAddr, clientEthAddr,
                clientIpAddr, upRate, downRate);
    }

    private void clientDisconnect(final InetAddress agentAddr,
            final String clientEthAddr) {
        master.clientDisconnect(agentAddr, clientEthAddr);
    }

    private class ConnectionHandler implements Runnable {
        final DatagramPacket receivedPacket;

        public ConnectionHandler(final DatagramPacket dp) {
            receivedPacket = dp;
        }

        // AP Agent message handler
        public void run() {
            final String msg = new String(receivedPacket.getData()).trim().toLowerCase();
            final String[] fields = msg.split(" ");
            final String msg_type = fields[0];
            final InetAddress agentAddr = receivedPacket.getAddress();

            if (msg_type.equals(MSG_CLIENT_INFO)) {
                final String clientEthAddr = fields[1];
                final String clientIpAddr = fields[2];

                receiveClientInfo(agentAddr, clientEthAddr, clientIpAddr);

            } else if (msg_type.equals(MSG_AGENT_RATE)) {
                final String agentUpRate = fields[1];
                final String agentDownRate = fields[2];

                receiveAgentRate(agentAddr, agentUpRate, agentDownRate);
            } else if (msg_type.equals(MSG_CLIENT_RATE)) {
                final String clientEthAddr = fields[1];
                final String clientIpAddr = fields[2];
                final String clientUpRate = fields[3];
                final String clientDownRate = fields[4];

                receiveClientRate(agentAddr, clientEthAddr, clientIpAddr, clientUpRate, clientDownRate);
            } else if (msg_type.equals(MSG_CLIENT_DISCONNECT)) {
                final String clientEthAddr = fields[1];

                clientDisconnect(agentAddr, clientEthAddr);
            }

        }
    }

}
