package net.floodlightcontroller.offloading;

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

import org.openflow.protocol.OFMatch;
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

    private List<OFFlowStatisticsReply> statsReply;
    private Timer timer;
    private int interval;

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
        this.interval = printInterval;
    }



    @Override
    public void run() {
        timer.schedule(new PrintTask(), this.interval*1000);
    }



    private void printStatistics() {
        statsReply = new ArrayList<OFFlowStatisticsReply>();
        List<OFStatistics> values = null;
        Future<List<OFStatistics>> future;

        // Statistics request object for getting flows
        OFStatisticsRequest req = new OFStatisticsRequest();
        req.setStatisticType(OFStatisticsType.FLOW);
        int requestLength = req.getLengthU();
        OFFlowStatisticsRequest specificReq = new OFFlowStatisticsRequest();
        specificReq.setMatch(new OFMatch().setWildcards(0xffffffff));
        // specificReq.setOutPort(outPort);
        specificReq.setTableId((byte) 0xff);
        req.setStatistics(Collections.singletonList((OFStatistics) specificReq));
        requestLength += specificReq.getLength();
        req.setLengthU(requestLength);

        Map<Long,IOFSwitch> swMap = floodlightProvider.getAllSwitchMap();

        log.info("here");

        for (IOFSwitch sw: swMap.values()) {
            try {
                // System.out.println(sw.getStatistics(req));
                future = sw.queryStatistics(req);
                values = future.get(10, TimeUnit.SECONDS);
                if (values != null) {

                    log.info("Statistics Info");


                    for (OFStatistics stat : values) {
                        statsReply.add((OFFlowStatisticsReply) stat);
                    }

                    for (OFFlowStatisticsReply flow: statsReply) {
                        log.info(flow.toString());
                    }
                }
            } catch (Exception e) {
                log.error("Failure retrieving statistics from switch " + sw, e);
            }
        }
    }

}
