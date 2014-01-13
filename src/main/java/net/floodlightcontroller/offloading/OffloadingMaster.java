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


import java.io.BufferedReader;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledExecutorService;

import org.openflow.protocol.OFMatch;
import org.openflow.protocol.OFMessage;
import org.openflow.protocol.OFPacketIn;
import org.openflow.protocol.OFType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.floodlightcontroller.core.FloodlightContext;
import net.floodlightcontroller.core.IFloodlightProviderService;
import net.floodlightcontroller.core.IOFMessageListener;
import net.floodlightcontroller.core.IOFSwitch;
import net.floodlightcontroller.core.IOFSwitch.PortChangeType;
import net.floodlightcontroller.core.IOFSwitchListener;
import net.floodlightcontroller.core.ImmutablePort;
import net.floodlightcontroller.core.module.FloodlightModuleContext;
import net.floodlightcontroller.core.module.FloodlightModuleException;
import net.floodlightcontroller.core.module.IFloodlightModule;
import net.floodlightcontroller.core.module.IFloodlightService;
import net.floodlightcontroller.offloading.OffloadingProtocolServer;
// import net.floodlightcontroller.packet.Ethernet;
// import net.floodlightcontroller.packet.IPv4;
// import net.floodlightcontroller.restserver.IRestApiService;
import net.floodlightcontroller.threadpool.IThreadPoolService;


/**
 * This is an implementation of sdn wireless controllers
 *
 * @author Yanhe Liu <yanhe.liu@cs.helsinki.fi>
 *
 **/

public class OffloadingMaster implements IFloodlightModule, IFloodlightService, IOFSwitchListener, IOFMessageListener{
    protected static Logger log = LoggerFactory.getLogger(OffloadingMaster.class);
    // protected IRestApiService restApi;

    private IFloodlightProviderService floodlightProvider;
    private ScheduledExecutorService executor;

    private NetworkManager networkManager;
    private Map<String, List<String>> networkTopoConfig = new HashMap<String, List<String>>();
    private Map<String, OffloadingAgent> agentMap = new ConcurrentHashMap<String, OffloadingAgent> ();

    // private IOFSwitch ofSwitch;

    // some defaults
    // private final int AGENT_PORT = 6777;
    static private final int DEFAULT_PORT = 2819;
    static private final String DEFAULT_TOPOLOGY_FILE = "networkFile";

    public OffloadingMaster(){
        networkManager = new NetworkManager();
    }

    /**
     * Add an agent to the Master tracker
     *
     * @param ipv4Address Client's IPv4 address
     */
    public void addUnrecordedAgent(final InetAddress ipv4Address) {
        String ipAddr = ipv4Address.getHostAddress();

        OffloadingAgent agent = new OffloadingAgent(ipv4Address, (long)0);
        agentMap.put(ipAddr, agent);
    }

    /**
     * check whether an agent is in the hashmap
     *
     * @param addr Agent's IPv4 address
     */
    public boolean isAgentTracked(InetAddress addr) {
        if (agentMap.containsKey(addr.getHostAddress())) {
            return true;
        }

        return false;
    }

    /**
     * Handle a ClientInfo message from an agent.
     *
     * @param AgentAddr
     */
    void receiveClientInfo(final InetAddress agentAddr,
            final String clientEthAddr, final String clientIpAddr) {

        log.info("Client message from " + agentAddr.getHostAddress() + ": " +
               clientEthAddr + " - " + clientIpAddr);

        if (!isAgentTracked(agentAddr)) {
            log.warn("Found unrecorded agent ap");
            addUnrecordedAgent(agentAddr);
        }

        agentMap.get(agentAddr.getHostAddress()).receiveClientInfo(clientEthAddr,
                clientIpAddr);
    }

    /**
     * Handle a ClientInfo message from an agent.
     *
     * @param AgentAddr
     */
    void receiveAgentRate(final InetAddress agentAddr, final String upRate, final String downRate) {
        log.info("Agent rate message from " + agentAddr.getHostAddress() +
                 ": " + upRate + " " + downRate);

        if (!isAgentTracked(agentAddr)) {
            addUnrecordedAgent(agentAddr);
        }

        float r1 = Float.parseFloat(upRate);
        float r2 = Float.parseFloat(upRate);
        agentMap.get(agentAddr.getHostAddress()).updateUpRate(r1);
        agentMap.get(agentAddr.getHostAddress()).updateDownRate(r2);
    }

    void receiveClientRate(final InetAddress agentAddr, final String clientEthAddr,
            final String clientIpAddr, final String upRate, final String downRate) {

        log.info("Client rate message from " + agentAddr.getHostAddress() +
                ": " + clientEthAddr + " -- " + clientIpAddr + " -- " +
                upRate + " " + downRate);

        if (!isAgentTracked(agentAddr)) {
            addUnrecordedAgent(agentAddr);
        }

        float r1 = Float.parseFloat(upRate);
        float r2 = Float.parseFloat(downRate);
        agentMap.get(agentAddr.getHostAddress()).receiveClientRate(clientEthAddr, r1, r2);
        System.out.println(agentMap.get(agentAddr.getHostAddress()).toString());
    }



    //********* from IFloodlightModule **********//

    @Override
    public Collection<Class<? extends IFloodlightService>> getModuleServices() {
        return null;
    }

    @Override
    public Map<Class<? extends IFloodlightService>, IFloodlightService> getServiceImpls() {
        Map<Class<? extends IFloodlightService>,
        IFloodlightService> m =
        new HashMap<Class<? extends IFloodlightService>,
        IFloodlightService>();
        m.put(OffloadingMaster.class, this);
        return m;
    }

    @Override
    public Collection<Class<? extends IFloodlightService>> getModuleDependencies() {
        Collection<Class<? extends IFloodlightService>> l =
            new ArrayList<Class<? extends IFloodlightService>>();
        l.add(IFloodlightProviderService.class);
        // l.add(IRestApiService.class);
        return l;
    }

    @Override
    public void init(FloodlightModuleContext context)
            throws FloodlightModuleException {
        floodlightProvider = context.getServiceImpl(IFloodlightProviderService.class);
        // restApi = context.getServiceImpl(IRestApiService.class);
        IThreadPoolService tp = context.getServiceImpl(IThreadPoolService.class);
        executor = tp.getScheduledExecutor();
    }

    @Override
    public void startUp(FloodlightModuleContext context)
            throws FloodlightModuleException {

        floodlightProvider.addOFSwitchListener(this);
        floodlightProvider.addOFMessageListener(OFType.PACKET_IN, this);
        // restApi.addRestletRoutable(new OdinMasterWebRoutable());

        // read configure options
        Map<String, String> configOptions = context.getConfigParams(this);

        // master port config
        int port = DEFAULT_PORT;
        String portNum = configOptions.get("masterPort");
        if (portNum != null) {
            port = Integer.parseInt(portNum);
        }

        // network topology config
        String networkTopoFile = DEFAULT_TOPOLOGY_FILE;
        String networkTopoFileConfig = configOptions.get("networkFile");

        if (networkTopoFileConfig != null) {
            networkTopoFile = networkTopoFileConfig;
        }

        try {
            BufferedReader br = new BufferedReader (new FileReader(networkTopoFile));

            String strLine;

            /* Each line has the following format:
             *
             * Key value1 value2...
             */
            while ((strLine = br.readLine()) != null) {
                if (strLine.startsWith("#")) // comment
                    continue;

                if (strLine.length() == 0) // blank line
                    continue;

                // Openflow Switch IP Address
                String [] fields = strLine.split(" ");
                if (!fields[0].equals("OFSwitchIP")) {
                    log.error("Missing OFSwitchIP field " + fields[0]);
                    log.error("Offending line: " + strLine);
                    System.exit(1);
                }
                if (fields.length != 2) {
                    log.error("A OFSwitch field should specify a single string as IP address");
                    log.error("Offending line: " + strLine);
                    System.exit(1);
                }
                String swIP = fields[1];

                // APs
                strLine = br.readLine();
                if (strLine == null) {
                    log.error("Unexpected EOF after OFSwitchIP field: ");
                    System.exit(1);
                }
                fields = strLine.split(" ");
                if (!fields[0].equals("AP")){
                    log.error("A OFSwitchIP field should be followed by a AP field");
                    log.error("Offending line: " + strLine);
                    System.exit(1);
                }
                if (fields.length == 1) {
                    log.error("An AP field must have at least one ap defined for it");
                    log.error("Offending line: " + strLine);
                    System.exit(1);
                }
                if (networkTopoConfig.containsKey(swIP)) {
                    log.warn("Redundent switch, ignore it");
                    continue;
                }
                networkTopoConfig.put(swIP, new ArrayList<String>());
                for (int i = 1; i < fields.length; i++) {
                    networkTopoConfig.get(swIP).add(fields[i]);
                }
            }

            br.close();

        } catch (FileNotFoundException e1) {
            log.error("Agent authentication list (config option poolFile) not supplied. Terminating.");
            System.exit(1);
        } catch (IOException e) {
            e.printStackTrace();
            System.exit(1);
        }

        IThreadPoolService tp = context.getServiceImpl(IThreadPoolService.class);
        executor = tp.getScheduledExecutor();
        // Spawn threads for different services
        executor.execute(new OffloadingProtocolServer(this, port, executor));

        // Statistics
        // executor.execute(new SwitchFlowStatistics(this.floodlightProvider, executor, 6));
    }



    /** IOFSwitchListener and IOFMessageListener methods **/

    @Override
    public String getName() {
        return "OffloadingMaster";
    }

    @Override
    public boolean isCallbackOrderingPrereq(OFType type, String name) {
        // TODO Auto-generated method stub
        return false;
    }

    @Override
    public boolean isCallbackOrderingPostreq(OFType type, String name) {
        // TODO Auto-generated method stub
        return false;
    }

    @Override
    public net.floodlightcontroller.core.IListener.Command receive(
            IOFSwitch sw, OFMessage msg, FloodlightContext cntx) {
        // TODO Auto-generated method stub
        // log.info("Received OpenFlow Message\n");

        OFPacketIn pi = (OFPacketIn) msg;
        // System.out.println(pi.toString());

        OFMatch match = new OFMatch();
        match.loadFromPacket(pi.getPacketData(), (short) 0);

        // log.info(match.toString());
        // log.info(IPv4.fromIPv4Address(match.getNetworkDestination()));


        return null;
    }

    @Override
    public void switchAdded(long switchId) {
        // TODO Auto-generated method stub

    }

    @Override
    public void switchRemoved(long switchId) {
        if (networkManager.containsSwitch(switchId)) {
            networkManager.removeSwitch(switchId);
        } else {
            log.warn("Unrecording switch is removed");
        }
    }

    @Override
    public void switchActivated(long switchId) {
        IOFSwitch sw = floodlightProvider.getSwitch(switchId);

        InetSocketAddress swInetAddr = (InetSocketAddress) sw.getInetAddress();
        String swInetAddrStr = swInetAddr.getAddress().getHostAddress();

        if (networkTopoConfig.containsKey(swInetAddrStr)) {
            // first time
            if (!networkManager.containsSwitch(switchId)) {
                List<OffloadingAgent> agentList = new ArrayList<OffloadingAgent>();
                for (String agentInetAddr: networkTopoConfig.get(swInetAddrStr)) {
                    OffloadingAgent agent = new OffloadingAgent(agentInetAddr, switchId);
                    agentMap.put(agentInetAddr, agent);
                    agentList.add(agent);
                }
                networkManager.putSwitch(switchId, agentList);
            }

        } else {
            log.warn("Unrecording switch is connected and activated");
            networkManager.putSwitch(switchId, new ArrayList<OffloadingAgent>());
        }
    }

    @Override
    public void switchPortChanged(long switchId, ImmutablePort port,
            PortChangeType type) {
        // TODO Auto-generated method stub

    }

    @Override
    public void switchChanged(long switchId) {
        // TODO Auto-generated method stub

    }



}
