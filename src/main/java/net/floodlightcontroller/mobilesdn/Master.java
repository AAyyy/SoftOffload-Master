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


import java.io.BufferedReader;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedList;
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
// import net.floodlightcontroller.packet.Ethernet;
// import net.floodlightcontroller.packet.IPv4;
// import net.floodlightcontroller.restserver.IRestApiService;
import net.floodlightcontroller.mobilesdn.ClickManageServer;
import net.floodlightcontroller.threadpool.IThreadPoolService;
import net.floodlightcontroller.util.MACAddress;


/**
 * This is an implementation of sdn wireless and mobile controller
 *
 * @author Yanhe Liu <yanhe.liu@cs.helsinki.fi>
 *
 **/

public class Master implements IFloodlightModule, IFloodlightService, IOFSwitchListener, IOFMessageListener{
    protected static Logger log = LoggerFactory.getLogger(Master.class);
    // protected IRestApiService restApi;

    private IFloodlightProviderService floodlightProvider;
    private ScheduledExecutorService executor;

    // private NetworkManager networkManager;
    private Map<String, APAgent> apAgentMap = new ConcurrentHashMap<String, APAgent>();
    private Map<String, Client> clientMap = new ConcurrentHashMap<String, Client>();
    private List<SwitchOutQueue> swQueueList = new LinkedList<SwitchOutQueue>();

    public class SwitchNetworkConfig implements Comparable<Object> {
        private String swIPAddr;
        private int outPort;
        private List<String> apList;

        public SwitchNetworkConfig(String ip, int port, List<String> ap) {
            swIPAddr = ip;
            outPort = port;
            apList = ap;
        }

        public String getSwIPAddr() {
            return swIPAddr;
        }

        public int getOutPort() {
            return outPort;
        }

        public List<String> getAPList() {
            return apList;
        }

        @Override
        public boolean equals(Object obj) {
            if (!(obj instanceof SwitchNetworkConfig))
                return false;

            if (obj == this)
                return true;

            SwitchNetworkConfig that = (SwitchNetworkConfig) obj;

            return (this.swIPAddr.toLowerCase().equals(that.getSwIPAddr().toLowerCase())
                    && this.outPort == that.getOutPort());
        }

        @Override
        public int compareTo(Object arg0) {
            assert (arg0 instanceof SwitchNetworkConfig);

            if (this.swIPAddr.toLowerCase() == ((SwitchNetworkConfig)arg0).getSwIPAddr().toLowerCase()) {
                if (this.outPort == ((SwitchNetworkConfig)arg0).getOutPort()) {
                    return 0;
                } else if (this.outPort > ((SwitchNetworkConfig)arg0).getOutPort()) {
                    return 1;
                }
            }

            return -1;
        }
    }

    private List<SwitchNetworkConfig> networkTopoConfig = new LinkedList<SwitchNetworkConfig>();

    // private IOFSwitch ofSwitch;

    // some defaults
    private final int DEFAULT_PORT = 2819;
    private final String DEFAULT_TOPOLOGY_FILE = "networkFile";
    private final int OF_MONITOR_INTERVAL = 5;

    public Master(){
        // networkManager = new NetworkManager();
    }

    /**
     * Add an agent to the Master tracker
     *
     * @param ipv4Address Client's IPv4 address
     */
    public void addUnrecordedAPAgent(final InetAddress ipv4Address) {
        String ipAddr = ipv4Address.getHostAddress();

        APAgent agent = new APAgent(ipv4Address, clientMap);
        apAgentMap.put(ipAddr, agent);
    }

    /**
     * check whether an agent is in the HashMap
     *
     * @param addr Agent's IPv4 address
     */
    public boolean isAPAgentTracked(InetAddress addr) {
        if (apAgentMap.containsKey(addr.getHostAddress())) {
            return true;
        }

        return false;
    }

    /**
     * Handle a ClientInfo message from an agent.
     *
     * @param agentAddr
     * @param clientEthAddr
     * @param clientIpAddr
     */
    void receiveClientInfo(final InetAddress agentAddr,
            final String clientEthAddr, final String clientIpAddr) {

        log.info("Client message from " + agentAddr.getHostAddress() + ": " +
               clientEthAddr + " - " + clientIpAddr);

        if (!isAPAgentTracked(agentAddr)) {
            log.warn("Found unrecorded agent ap");
            addUnrecordedAPAgent(agentAddr);
        }

        apAgentMap.get(agentAddr.getHostAddress()).receiveClientInfo(clientEthAddr,
                clientIpAddr);
    }

    /**
     * Handle ClientDisconnect message from an agent.
     *
     * @param AgentAddr
     */
    void clientDisconnect(final InetAddress agentAddr,
            final String clientEthAddr) {

        log.info("Client " + clientEthAddr + " disconnected from agent "
                + agentAddr.getHostAddress());

        if (!isAPAgentTracked(agentAddr)) {
            log.warn("Found unrecorded agent ap, ignore it!");
            return;
        }

        apAgentMap.get(agentAddr.getHostAddress()).removeClient(clientEthAddr);
    }

    /**
     * Handle a ClientInfo message from an agent.
     *
     * @param AgentAddr
     */
    void receiveAgentRate(final InetAddress agentAddr, final String upRate, final String downRate) {
        log.info("Agent rate message from " + agentAddr.getHostAddress() +
                 ": " + upRate + " " + downRate);

        if (!isAPAgentTracked(agentAddr)) {
            log.warn("Found unrecorded agent ap");
            addUnrecordedAPAgent(agentAddr);
        }

        float r1 = Float.parseFloat(upRate);
        float r2 = Float.parseFloat(downRate);
        apAgentMap.get(agentAddr.getHostAddress()).updateUpRate(r1);
        apAgentMap.get(agentAddr.getHostAddress()).updateDownRate(r2);
        // System.out.println(apAgentMap.get(agentAddr.getHostAddress()).toString());
    }

    void receiveClientRate(final InetAddress agentAddr, final String clientEthAddr,
            final String clientIpAddr, final String upRate, final String downRate) {

        log.info("Client rate message from " + agentAddr.getHostAddress() +
                ": " + clientEthAddr + " -- " + clientIpAddr + " -- " +
                upRate + " " + downRate);

        if (!isAPAgentTracked(agentAddr)) {
            log.warn("Found unrecorded agent ap");
            addUnrecordedAPAgent(agentAddr);
        }

        float r1 = Float.parseFloat(upRate);
        float r2 = Float.parseFloat(downRate);
        apAgentMap.get(agentAddr.getHostAddress()).receiveClientRate(clientEthAddr, r1, r2);
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
        m.put(Master.class, this);
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

            // TODO now the config parser is quite simple, and can only handle
            // the format which strictly follows our definition without any
            // error

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

                // outport
                strLine = br.readLine();
                if (strLine == null) {
                    log.error("Unexpected EOF after OFSwitchIP field: ");
                    System.exit(1);
                }
                fields = strLine.split(" ");
                if (!fields[0].equals("OUTPORT")){
                    log.error("A OFSwitchIP field should be followed by a OUTPORT field");
                    log.error("Offending line: " + strLine);
                    System.exit(1);
                }
                int outport = Integer.parseInt(fields[2]);

                // APs
                strLine = br.readLine();
                if (strLine == null) {
                    log.error("Unexpected EOF after OFSwitchIP field: ");
                    System.exit(1);
                }
                fields = strLine.split(" ");
                if (!fields[0].equals("AP")){
                    log.error("A OUTPORT field should be followed by a AP field");
                    log.error("Offending line: " + strLine);
                    System.exit(1);
                }
                if (fields.length == 1) {
                    log.error("An AP field must have at least one ap defined for it");
                    log.error("Offending line: " + strLine);
                    System.exit(1);
                }

                ArrayList<String> apList = new ArrayList<String>();
                for (int i = 1; i < fields.length; i++) {
                    apList.add(fields[i]);
                }

                SwitchNetworkConfig swconfig = new SwitchNetworkConfig(swIP, outport, apList);

                if (networkTopoConfig.contains(swconfig)) {
                    log.error("Found dupliated switch network");
                    System.exit(1);
                }

                networkTopoConfig.add(swconfig);
            }

            br.close();

        } catch (FileNotFoundException e1) {
            log.error("Network topology config is not found. Terminating.");
            System.exit(1);
        } catch (IOException e) {
            e.printStackTrace();
            System.exit(1);
        }

        IThreadPoolService tp = context.getServiceImpl(IThreadPoolService.class);
        executor = tp.getScheduledExecutor();
        // Spawn threads for different services
        executor.execute(new ClickManageServer(this, port, executor));

        // Statistics
        executor.execute(new OFMonitor(this.floodlightProvider, executor, OF_MONITOR_INTERVAL, swQueueList));
    }



    /** IOFSwitchListener and IOFMessageListener methods **/

    @Override
    public String getName() {
        return "Master";
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

        OFPacketIn pi = (OFPacketIn) msg;
        // System.out.println(pi.toString());

        OFMatch match = new OFMatch();
        match.loadFromPacket(pi.getPacketData(), (short) 0);
        MACAddress srcMacAddr = MACAddress.valueOf(match.getDataLayerSource());
        for (APAgent agent: apAgentMap.values()) {
            Client clt = agent.getClient(srcMacAddr.toString());

            if (clt != null) {
                if (clt.getSwitch() == null) {
                    clt.setSwitch(sw);
                } else if (clt.getSwitch().getId() != sw.getId()) {
                    log.warn("Client dpid might be different from associated AP!");
                    clt.setSwitch(sw);
                }
            }
        }

        return null;
    }

    @Override
    public void switchAdded(long switchId) {
        // TODO Auto-generated method stub

    }

    @Override
    public void switchRemoved(long switchId) {
        for (SwitchOutQueue swqueue: swQueueList) {
            if (swqueue.getSwId() == switchId) {
                swQueueList.remove(swqueue);
            }
        }
    }

    @Override
    public void switchActivated(long switchId) {
        IOFSwitch sw = floodlightProvider.getSwitch(switchId);

        InetSocketAddress swInetAddr = (InetSocketAddress) sw.getInetAddress();
        String swInetAddrStr = swInetAddr.getAddress().getHostAddress();

        boolean isSwitchInConfig = false;
        for (SwitchNetworkConfig sc: networkTopoConfig) {
            if (sc.getSwIPAddr().toLowerCase().equals(swInetAddrStr.toLowerCase())) {
                isSwitchInConfig = true;

                List<APAgent> agentList = new LinkedList<APAgent>();
                for (String agentInetAddr: sc.getAPList()) {
                    APAgent agent = new APAgent(agentInetAddr, clientMap, sw);
                    apAgentMap.put(agentInetAddr, agent);
                    agentList.add(agent);
                }

                swQueueList.add(new SwitchOutQueue(switchId, sc.getOutPort(), agentList));
            }
        }

        if (!isSwitchInConfig) {
            log.warn("Unrecording switch is connected and activated, ignore it!");
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
