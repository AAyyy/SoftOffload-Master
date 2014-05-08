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

import java.util.ArrayList;
import java.io.IOException;
import java.net.UnknownHostException;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import net.floodlightcontroller.core.IFloodlightProviderService;
import net.floodlightcontroller.core.IOFSwitch;
import net.floodlightcontroller.packet.Ethernet;

import org.openflow.protocol.OFFlowMod;
import org.openflow.protocol.OFMatch;
import org.openflow.protocol.OFPacketOut;
import org.openflow.protocol.OFPort;
import org.openflow.protocol.OFStatisticsRequest;
import org.openflow.protocol.OFType;
import org.openflow.protocol.Wildcards;
// import org.openflow.protocol.Wildcards.Flag;
import org.openflow.protocol.action.OFAction;
import org.openflow.protocol.statistics.OFFlowStatisticsReply;
import org.openflow.protocol.statistics.OFFlowStatisticsRequest;
import org.openflow.protocol.statistics.OFPortStatisticsReply;
import org.openflow.protocol.statistics.OFPortStatisticsRequest;
import org.openflow.protocol.statistics.OFStatistics;
import org.openflow.protocol.statistics.OFStatisticsType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Class designed for monitoring switch's OpenFlow table
 *
 * The flow table info can be used for later usage
 *
 * @author Yanhe Liu <yanhe.liu@cs.helsinki.fi>
 *
 */

public class OFMonitor implements Runnable {

    protected static Logger log = LoggerFactory.getLogger(OFMonitor.class);

    private IFloodlightProviderService floodlightProvider;
    private Master master;

    // private List<OFFlowStatisticsReply> statsReply;
    private Timer timer;
    private float interval;
    private List<SwitchOutQueue> swQueueList;

    // default max rate threshold
    static private final float RATE_THRESHOLD = 1000000;
    private double QUEUE_THRESHOLD = 0.8; // 80% * bandwidth

    // monitoring info is gathered by using a timer
    private class OFMonitorTask extends TimerTask {
        public void run() {
            // flowStatistics();
            portStatistics();
        }
    }

    public OFMonitor(IFloodlightProviderService fProvider, Master m,
            float printInterval, List<SwitchOutQueue> swList) {
        this.floodlightProvider = fProvider;
        this.master = m;

        this.timer = new Timer();
        this.interval = printInterval;
        this.swQueueList = swList;
    }


    @Override
    public void run() {
        // start the timer with our task
        timer.schedule(new OFMonitorTask(), (long)5000, (long)this.interval*1000);
    }

    private void portStatistics() {
        List<OFStatistics> values = null;
        Future<List<OFStatistics>> future;
        OFPortStatisticsReply reply;

        for (SwitchOutQueue swQueue: swQueueList) {
            IOFSwitch sw = floodlightProvider.getSwitch(swQueue.getSwId());

            OFStatisticsRequest req = new OFStatisticsRequest();
            req.setStatisticType(OFStatisticsType.PORT);
            int requestLength = req.getLengthU();
            OFPortStatisticsRequest specificReq = new OFPortStatisticsRequest();
            specificReq.setPortNumber((short)swQueue.getOutPort());
            req.setStatistics(Collections.singletonList((OFStatistics)specificReq));
            requestLength += specificReq.getLength();
            req.setLengthU(requestLength);



            try {
                // make the query
                future = sw.queryStatistics(req);
                values = future.get(5, TimeUnit.SECONDS);
            } catch (Exception e) {
                log.error("Failure retrieving port statistics from switch " + sw, e);
            }

            if (values != null) {
                for (OFStatistics stat: values) {
                    reply = (OFPortStatisticsReply) stat;

                    long receiveBytes = reply.getReceiveBytes();
                    long transmitBytes = reply.getTransmitBytes();

                    if (swQueue.isBytesUpdated) {
                        float downrate = (receiveBytes - swQueue.getReceiveBytes()) / (this.interval);
                        // float uprate = (transmitBytes - swQueue.getTransmitBytes()) / (this.interval);

                        if (downrate*8 > (QUEUE_THRESHOLD * swQueue.getBandwidth() * 1000000)) {
                           int num = swQueue.getDownThroughputOverNum();
                           swQueue.setDownThroughputOverNum(++num);
                        } else {
                            swQueue.setDownThroughputOverNum(0);
                        }

                        if (swQueue.getDownThroughputOverNum() >= 10) {
                            System.out.println("reach port download threshold!!!");
                            master.switchQueueManagement(sw, swQueue);
                            swQueue.setDownThroughputOverNum(0);
                        }

                    } else {
                        swQueue.isBytesUpdated = true;
                    }

                    swQueue.setReceiveBytes(receiveBytes);
                    swQueue.settransmitBytes(transmitBytes);

                }
            }
        }
    }


    /**
     * Get flow statistics from switch by using OFFlowStatisticsRequest
     *
     */

    // FIXME: currently the OFFlowStatisticsRequest can only use IN_PORT,
    // DL_SRC, DL_DST and DL_VLAN_PCP to match flows, using other fields can
    // only get an empty stats reply. In addition, the match in the reply
    // only contains those four tuples
    // This might be a bug of Floodlight???

    private void flowStatistics() {
        // statsReply = new ArrayList<OFFlowStatisticsReply>();
        List<OFStatistics> values = null;
        Future<List<OFStatistics>> future;
        OFFlowStatisticsReply reply;
        OFMatch match;
        float rate;
        Ethernet mac;

        // get switch
        Map<Long,IOFSwitch> swMap = floodlightProvider.getAllSwitchMap();


        for (IOFSwitch sw: swMap.values()) {
            try {
                OFStatisticsRequest req = new OFStatisticsRequest();
                req.setStatisticType(OFStatisticsType.FLOW);
                int requestLength = req.getLengthU();
                OFFlowStatisticsRequest specificReq = new OFFlowStatisticsRequest();
                specificReq.setMatch(new OFMatch().setWildcards(Wildcards.FULL));
                specificReq.setTableId((byte)0xff);

                // FIXME match bug
                // for example, this can not work here
                // match.setWildcards(Wildcards.FULL.matchOn(Flag.DL_TYPE)

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
                log.error("Failure retrieving flow statistics from switch " + sw, e);
            }
        }
    }

    /**
     * Modify the action of open flow entries to drop
     *
     * @param match match info obtained from OFFlowStatisticsReply
     * @param sw corresponding OpenFlow switch
     */

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

}
