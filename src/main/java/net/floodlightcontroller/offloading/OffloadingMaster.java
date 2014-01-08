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
import java.net.InetAddress;
import java.net.UnknownHostException;
// import java.net.Socket;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import org.openflow.protocol.OFFlowMod;
import org.openflow.protocol.OFMatch;
import org.openflow.protocol.OFMessage;
import org.openflow.protocol.OFPacketIn;
import org.openflow.protocol.OFPacketOut;
import org.openflow.protocol.OFPort;
import org.openflow.protocol.OFStatisticsRequest;
import org.openflow.protocol.OFType;
import org.openflow.protocol.action.OFAction;
import org.openflow.protocol.statistics.OFFlowStatisticsReply;
import org.openflow.protocol.statistics.OFFlowStatisticsRequest;
import org.openflow.protocol.statistics.OFStatistics;
import org.openflow.protocol.statistics.OFStatisticsType;
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
import net.floodlightcontroller.packet.Ethernet;
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

    //	private final AgentManager agentManager;
    private Map<String, OffloadingAgent> agentMap
        = new ConcurrentHashMap<String, OffloadingAgent> ();

    // private IOFSwitch ofSwitch;

    // some defaults
    // private final int AGENT_PORT = 6777;
    static private final int DEFAULT_PORT = 2819;
    static private final float RATE_THRESHOLD = 5000;

    public OffloadingMaster(){
        // clientManager = new ClientManager();
    }

    /**
     * Add an agent to the Master tracker
     *
     * @param ipv4Address Client's IPv4 address
     */
    public void addAgent(final InetAddress ipv4Address) {
        String ipAddr = ipv4Address.getHostAddress();

        if (!agentMap.containsKey(ipAddr)) {
            OffloadingAgent agent = new OffloadingAgent(ipv4Address);
            agentMap.put(ipAddr, agent);
        }
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
            addAgent(agentAddr);
        }

        agentMap.get(agentAddr.getHostAddress()).receiveClientInfo(clientEthAddr,
                clientIpAddr);
    }

    /**
     * Handle a ClientInfo message from an agent.
     *
     * @param AgentAddr
     */
    void receiveAgentRate(final InetAddress agentAddr, final String rate) {
        log.info("Agent rate message from " + agentAddr.getHostAddress() +
                 ": " + rate);

        if (!isAgentTracked(agentAddr)) {
            addAgent(agentAddr);
        }

        float r = Float.parseFloat(rate);
        agentMap.get(agentAddr.getHostAddress()).updateRate(r);
    }

    void receiveClientRate(final InetAddress agentAddr, final String clientEthAddr,
            final String clientIpAddr, final String clientRate) {

        log.info("Client rate message from " + agentAddr.getHostAddress() +
                ": " + clientEthAddr + " -- " + clientIpAddr + " -- " +
                clientRate);

        if (!isAgentTracked(agentAddr)) {
            addAgent(agentAddr);
        }

        float r = Float.parseFloat(clientRate);
        agentMap.get(agentAddr.getHostAddress()).receiveClientRate(clientEthAddr, r);
    }

    private void getFlowStatistics(OFMatch reqMatch, IOFSwitch sw) {
        List<OFStatistics> values = null;
        Future<List<OFStatistics>> future;
        OFFlowStatisticsReply reply;
        OFMatch match;
        float rate;
        Ethernet mac;

        try {
            OFStatisticsRequest req = new OFStatisticsRequest();
            req.setStatisticType(OFStatisticsType.FLOW);
            int requestLength = req.getLengthU();
            OFFlowStatisticsRequest specificReq = new OFFlowStatisticsRequest();
            specificReq.setMatch(reqMatch);
            specificReq.setTableId((byte)0xff);

            // using OFPort.OFPP_NONE(0xffff) as the outport
            specificReq.setOutPort(OFPort.OFPP_NONE.getValue());
            req.setStatistics(Collections.singletonList((OFStatistics) specificReq));
            requestLength += specificReq.getLength();
            req.setLengthU(requestLength);

            // make the query
            future = sw.queryStatistics(req);
            values = future.get(3, TimeUnit.SECONDS);
            if (values != null) {
                for (OFStatistics stat: values) {
                    // statsReply.add((OFFlowStatisticsReply) stat);

                    reply = (OFFlowStatisticsReply) stat;
                    rate = (float) reply.getByteCount()
                              / ((float) reply.getDurationSeconds()
                              + ((float) reply.getDurationNanoseconds() / 1000000000));
                    match = reply.getMatch();
                    // actions list is empty means the current flow action is to drop
                    if (rate >= RATE_THRESHOLD && !reply.getActions().isEmpty()) {
                        // log.info(reply.toString());

                        System.out.println(match.getNetworkDestination());
                        System.out.println(match.getNetworkSource());

                        mac = new Ethernet().setSourceMACAddress(match.getDataLayerSource())
                                               .setDestinationMACAddress(match.getDataLayerDestination());

                        log.info("Flow {} -> {}", mac.getSourceMAC().toString(),
                                                  mac.getDestinationMAC().toString());

                        log.info("FlowRate = {}bytes/s: suspicious flow, " +
                                "drop matched pkts", Float.toString(rate));

                        // modify flow action to drop
                        setOFFlowActionToDrop(match, sw);
                    }
                }
            }


        } catch (Exception e) {
            log.error("Failure retrieving statistics from switch " + sw, e);
        }
    }

    private void setOFFlowActionToDrop(OFMatch match, IOFSwitch sw) throws UnknownHostException {

        OFFlowMod flowMod = (OFFlowMod) floodlightProvider.getOFMessageFactory().getMessage(OFType.FLOW_MOD);
        // set no action to drop
        List<OFAction> actions = new ArrayList<OFAction>();

        // set flow_mod
        flowMod.setOutPort(OFPort.OFPP_NONE);
        flowMod.setMatch(match);
        // this buffer_id is needed for avoiding a BAD_REQUEST error
        flowMod.setBufferId(OFPacketOut.BUFFER_ID_NONE);
        flowMod.setHardTimeout((short) 0);
        flowMod.setIdleTimeout((short) 20);
        flowMod.setActions(actions);
        flowMod.setCommand(OFFlowMod.OFPFC_MODIFY_STRICT);

        // send flow_mod
        if (sw == null) {
            log.debug("Switch is not connected!");
            return;
        }
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

        int port = DEFAULT_PORT;
        String portNum = configOptions.get("masterPort");
        if (portNum != null) {
            port = Integer.parseInt(portNum);
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
        System.out.println(pi.toString());

        OFMatch match = new OFMatch();
        match.loadFromPacket(pi.getPacketData(), (short) 0);

        getFlowStatistics(match, sw);


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
        // TODO Auto-generated method stub

    }

    @Override
    public void switchActivated(long switchId) {
        // TODO Auto-generated method stub

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
