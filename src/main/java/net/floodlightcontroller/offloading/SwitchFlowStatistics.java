package net.floodlightcontroller.offloading;

// import java.util.ArrayList;
import java.io.IOException;
import java.math.BigInteger;
import java.net.InetAddress;
import java.util.ArrayList;
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

import org.openflow.protocol.OFFlowMod;
import org.openflow.protocol.OFMatch;
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

public class SwitchFlowStatistics implements Runnable {

    protected static Logger log = LoggerFactory.getLogger(OffloadingProtocolServer.class);

    private IFloodlightProviderService floodlightProvider;
    // private final ExecutorService executor;

    // private List<OFFlowStatisticsReply> statsReply;
    private Timer timer;
    private long interval;
    private List<OFMatch> suspiciousMatches;

    // default max rate threshold
    static private final float RATE_THRESHOLD = 5000;

    private class PrintTask extends TimerTask {
        public void run() {
            printStatistics();
        }
    }

    public SwitchFlowStatistics(IFloodlightProviderService fProvider,
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



    private void printStatistics() {
        // statsReply = new ArrayList<OFFlowStatisticsReply>();
        List<OFStatistics> values = null;
        Future<List<OFStatistics>> future;
        OFFlowStatisticsReply reply;
        float rate;
        byte[] bytes;
        InetAddress srcAddr, dstAddr;


        // get switch
        Map<Long,IOFSwitch> swMap = floodlightProvider.getAllSwitchMap();

        for (IOFSwitch sw: swMap.values()) {
            try {
                OFStatisticsRequest req = new OFStatisticsRequest();
                req.setStatisticType(OFStatisticsType.FLOW);
                int requestLength = req.getLengthU();
                OFFlowStatisticsRequest specificReq = new OFFlowStatisticsRequest();
                specificReq.setMatch(new OFMatch().setWildcards(0xffffffff));
                specificReq.setTableId((byte) 0xff);

                // using OFPort.OFPP_NONE(0xffff) as the outport
                specificReq.setOutPort((short)0xffff);
                req.setStatistics(Collections.singletonList((OFStatistics) specificReq));
                requestLength += specificReq.getLength();
                req.setLengthU(requestLength);

                // make the query
                future = sw.queryStatistics(req);
                values = future.get(3, TimeUnit.SECONDS);
                if (values != null) {
                    for (OFStatistics stat : values) {
                        // statsReply.add((OFFlowStatisticsReply) stat);
                        reply = (OFFlowStatisticsReply) stat;
                        rate = (float) reply.getByteCount()
                                  / ((float) reply.getDurationSeconds()
                                  + ((float) reply.getDurationNanoseconds() / 1000000000));
                        if (rate >= RATE_THRESHOLD && !suspiciousMatches.contains(reply.getMatch())) {
                            // log.info(reply.toString());

                            bytes = BigInteger.valueOf(reply.getMatch().getNetworkSource()).toByteArray();
                            srcAddr = InetAddress.getByAddress(bytes);
                            bytes = BigInteger.valueOf(reply.getMatch().getNetworkDestination()).toByteArray();
                            dstAddr = InetAddress.getByAddress(bytes);
                            log.info(srcAddr.getHostAddress() + " -- " + dstAddr.getHostAddress());

                            log.info("FlowRate=" + Float.toString(rate)
                                    + " -- suspicious flow, drop matched pkts");

                            // modify flow action to drop
                            suspiciousMatches.add(reply.getMatch());
                            setOFFlowActionToDrop(reply.getMatch(), sw);
                        }
                    }
                }


            } catch (Exception e) {
                log.error("Failure retrieving statistics from switch " + sw, e);
            }
        }
    }

    private void setOFFlowActionToDrop(OFMatch match, IOFSwitch sw) {

        OFFlowMod flowMod = (OFFlowMod) floodlightProvider.getOFMessageFactory().getMessage(OFType.FLOW_MOD);
        // set no action to drop
        List<OFAction> actions = new ArrayList<OFAction>();

        // set flow_mod
        flowMod.setOutPort(OFPort.OFPP_NONE);
        flowMod.setMatch(match);
        flowMod.setHardTimeout((short) 0);
        flowMod.setIdleTimeout((short) 20);
        flowMod.setActions(actions);
        flowMod.setCommand(OFFlowMod.OFPFC_MODIFY);

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
        }
    }

}
