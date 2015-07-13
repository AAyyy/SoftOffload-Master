package net.floodlightcontroller.mobilesdn;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.openflow.protocol.OFMatch;
import org.openflow.protocol.OFPort;
import org.openflow.protocol.OFStatisticsRequest;
import org.openflow.protocol.Wildcards;
import org.openflow.protocol.statistics.OFFlowStatisticsReply;
import org.openflow.protocol.statistics.OFFlowStatisticsRequest;
import org.openflow.protocol.statistics.OFStatistics;
import org.openflow.protocol.statistics.OFStatisticsType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.floodlightcontroller.core.IFloodlightProviderService;
import net.floodlightcontroller.core.IOFSwitch;

public class OFRateStatistics implements Runnable {
	protected static Logger log = LoggerFactory.getLogger(OFMonitor.class);

    private IFloodlightProviderService floodlightProvider;
    private Master master;
    private Timer timer;
    private double interval;
	
    
    // monitoring info is gathered by using a timer
    private class OFRateStatisticsTask extends TimerTask {
        public void run() {
        	RateStatistics();
        }
    }
    
    public OFRateStatistics(IFloodlightProviderService fProvider, Master m,
            double detectInterval) {
        this.floodlightProvider = fProvider;
        this.master = m;
        this.timer = new Timer();
        this.interval = detectInterval;
    }
	
	@Override
	public void run() {
		timer.schedule(new OFRateStatisticsTask(), (long)5000, 
				(long)(this.interval*1000));
	}
	
	private void RateStatistics() {
		List<OFStatistics> values = null;
        Future<List<OFStatistics>> future;
        OFFlowStatisticsReply reply;

        for (APAgent agent: master.getAllAPAgents()) { // Terrible O(n³)
            IOFSwitch sw = agent.getSwitch();

            OFStatisticsRequest req = new OFStatisticsRequest();
            req.setStatisticType(OFStatisticsType.FLOW);
            int requestLength = req.getLengthU();
            OFFlowStatisticsRequest specificReq = new OFFlowStatisticsRequest();
            OFMatch mPattern = new OFMatch();
            mPattern.setWildcards(Wildcards.FULL);
            specificReq.setMatch(mPattern);
            specificReq.setTableId((byte)0xff);
            
            // using OFPort.OFPP_NONE(0xffff) as the outport
            specificReq.setOutPort(OFPort.OFPP_NONE.getValue());
            req.setStatistics(Collections.singletonList((OFStatistics) specificReq));
            requestLength += specificReq.getLength();
            req.setLengthU(requestLength);

            try {
                // make the query
                future = sw.queryStatistics(req);
                values = future.get(3, TimeUnit.SECONDS);
            } catch (Exception e) {
                log.error("[ClientRate] Failure retrieving flow statistics from switch " + sw, e);
            }

            if (values != null) {
            	double agentUpRateSum = 0;
                double agentDownRateSum = 0;
            	// calculate rate for each client
            	for (Client clt: agent.getAllClients()) {
            		byte[] cltMac = clt.getMacAddress().toBytes();
            		long cltUpByteSum = 0;
                    long cltDownByteSum = 0;
            		for (OFStatistics stat: values) {
                		reply = (OFFlowStatisticsReply) stat;
                        long byteCount = reply.getByteCount();
                        if (!reply.getActions().isEmpty() && byteCount > 0) {
                            OFMatch match = reply.getMatch();
                            if (Arrays.equals(cltMac, match.getDataLayerDestination())) {
                            	cltDownByteSum += byteCount;
                                continue;
                            } else if (Arrays.equals(cltMac, match.getDataLayerSource())) {
                            	cltUpByteSum += byteCount;
                            	continue;
                            }
                        }
            		}
            		
            		long upByteDiff = cltUpByteSum - clt.getOFUpBytes();
            		long downByteDiff = cltDownByteSum - clt.getOFDownBytes();
            		if (cltUpByteSum < clt.getOFUpBytes()) { // in case of overflow
            			upByteDiff = Long.MAX_VALUE - clt.getOFUpBytes() 
            					+ cltUpByteSum - Long.MIN_VALUE;
            			cltUpByteSum = cltUpByteSum - Long.MIN_VALUE;
            		}
            		if (cltDownByteSum < clt.getOFDownBytes()) {
            			downByteDiff = Long.MAX_VALUE - clt.getOFDownBytes()
            					+ cltDownByteSum - Long.MIN_VALUE;
            			cltDownByteSum = cltDownByteSum - Long.MIN_VALUE;
            		}
            		
            		double upRate = Math.abs(upByteDiff) * 8 / interval;
            		double downRate = Math.abs(downByteDiff) * 8 / interval;
            		
            		clt.updateUpRate(upRate);
            		clt.updateDownRate(downRate);
            		clt.updateOFUpBytes(cltUpByteSum);
            		clt.updateOFDownBytes(cltDownByteSum);
            		agentUpRateSum += upRate;
            		agentDownRateSum += downRate;
            		
            		log.info("clt rate debug: " + clt.getIpAddress().getHostAddress() 
            					+ " -- " + upRate + " - " + downRate);
            	}
            	agent.updateUpRate(agentUpRateSum);
            	agent.updateDownRate(agentDownRateSum);
            	// log.info("agent rate debug: " + agent.getSSID()
        		// 		+ " -- " + agentUpRateSum + " - " + agentDownRateSum);
            }
        }
	}
	

}
