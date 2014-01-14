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
import org.openflow.protocol.statistics.OFStatistics;
import org.openflow.protocol.statistics.OFStatisticsType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class OFMonitor implements Runnable {

    protected static Logger log = LoggerFactory.getLogger(OFMonitor.class);

    private IFloodlightProviderService floodlightProvider;
    // private final ExecutorService executor;

    // private List<OFFlowStatisticsReply> statsReply;
    private Timer timer;
    private long interval;

    // default max rate threshold
    static private final float RATE_THRESHOLD = 30000;

    private class PrintTask extends TimerTask {
        public void run() {
            printStatistics();
        }
    }

    public OFMonitor(IFloodlightProviderService fProvider,
            ExecutorService executor, int printInterval) {
        this.floodlightProvider = fProvider;
        // this.executor = executor;
        this.timer = new Timer();
        this.interval = (long)printInterval;
    }



    @Override
    public void run() {
        timer.schedule(new PrintTask(), (long)2000, this.interval*1000);
    }


    // FIXME: currently the OFFlowStatisticsRequest can only use IN_PORT,
    // DL_SRC, DL_DST and DL_VLAN_PCP to match flows, using other fields can
    // only get an empty stats reply. In addition, the match in the reply
    // only contains those four tuples
    // This might be a bug of Floodlight???

    private void printStatistics() {
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
                log.error("Failure retrieving statistics from switch " + sw, e);
            }
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

}
