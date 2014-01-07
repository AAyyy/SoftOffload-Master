package net.floodlightcontroller.offloading;

// import java.util.ArrayList;
import java.util.Collection;
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
import net.floodlightcontroller.core.ImmutablePort;

import org.openflow.protocol.OFMatch;
import org.openflow.protocol.OFPort;
import org.openflow.protocol.OFStatisticsRequest;
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


        // get switch
        Map<Long,IOFSwitch> swMap = floodlightProvider.getAllSwitchMap();

        for (IOFSwitch sw: swMap.values()) {
            try {
//                // iterate all the ports
//                Collection<ImmutablePort> ports = sw.getEnabledPorts();
//                for (ImmutablePort port: ports) {
//                    short outPort = port.getPortNumber();
//                    if (outPort > 0) { // exclude internal port
//                        // Statistics request object for getting flows
//                        OFStatisticsRequest req = new OFStatisticsRequest();
//                        req.setStatisticType(OFStatisticsType.FLOW);
//                        int requestLength = req.getLengthU();
//                        OFFlowStatisticsRequest specificReq = new OFFlowStatisticsRequest();
//                        specificReq.setMatch(new OFMatch().setWildcards(0xffffffff));
//                        specificReq.setTableId((byte) 0xff);
//
//                        specificReq.setOutPort(outPort);
//                        req.setStatistics(Collections.singletonList((OFStatistics) specificReq));
//                        requestLength += specificReq.getLength();
//                        req.setLengthU(requestLength);
//
//                        // make the query
//                        future = sw.queryStatistics(req);
//                        values = future.get(3, TimeUnit.SECONDS);
//                        if (values != null) {
//                            for (OFStatistics stat : values) {
//                                // statsReply.add((OFFlowStatisticsReply) stat);
//                                reply = (OFFlowStatisticsReply) stat;
//                                rate = (float) reply.getByteCount()
//                                            / ((float) reply.getDurationSeconds()
//                                            + ((float) reply.getDurationNanoseconds() / 1000000000));
//                                log.info(reply.toString());
//                                System.out.println(rate);
//                            }
//                        }
//                    }
//                }

                OFStatisticsRequest req = new OFStatisticsRequest();
                req.setStatisticType(OFStatisticsType.FLOW);
                int requestLength = req.getLengthU();
                OFFlowStatisticsRequest specificReq = new OFFlowStatisticsRequest();
                specificReq.setMatch(new OFMatch().setWildcards(0xffffffff));
                specificReq.setTableId((byte) 0xff);

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
                        log.info(reply.toString());
                        System.out.println(rate);
                    }
                }


            } catch (Exception e) {
                log.error("Failure retrieving statistics from switch " + sw, e);
            }
        }
    }

}
