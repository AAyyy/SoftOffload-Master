/**
 *
 */
package net.floodlightcontroller.mobilesdn;

import java.util.List;

/**
 * @author Yanhe Liu <yanhe.liu@cs.helsinki.fi>
 *
 */
public class SwitchOutQueue implements Comparable<Object> {
    public boolean isBytesUpdated = false;  // used by OFMonitor

    private long swId;
    private int outPort;
    private int bandwidth;
    private long receiveBytes = 0;    // init value
    private long transmitBytes = 0;   // init value
    private int downThroughputOver = 0;

    private List<APAgent> apList;

    public SwitchOutQueue(long id, int port, int w, List<APAgent> ap) {
        swId = id;
        outPort = port;
        bandwidth = w;
        apList = ap;
    }

    public long getSwId() {
        return swId;
    }

    public int getOutPort() {
        return outPort;
    }

    public int getBandwidth() {
        return bandwidth;
    }

    public int getDownThroughputOverNum() {
        return downThroughputOver;
    }

    public List<APAgent> getAPList() {
        return apList;
    }

    public void setSwId(long id) {
        swId = id;
    }

    public void setOutPort(int port) {
        outPort = port;
    }

    public void setBandwidth(int w) {
        bandwidth = w;
    }

    public void setAPList(List<APAgent> ap) {
        apList = ap;
    }

    public long getReceiveBytes() {
        return receiveBytes;
    }

    public long getTransmitBytes() {
        return transmitBytes;
    }

    public void setReceiveBytes(long bytes) {
        receiveBytes = bytes;
    }

    public void settransmitBytes(long bytes) {
        transmitBytes = bytes;
    }

    public void setDownThroughputOverNum(int num) {
        downThroughputOver = num;
    }

    @Override
    public boolean equals(Object obj) {
        if (!(obj instanceof SwitchOutQueue))
            return false;

        if (obj == this)
            return true;

        SwitchOutQueue that = (SwitchOutQueue) obj;

        return (this.swId == that.getSwId() && this.outPort == that.getOutPort());
    }

    @Override
    public int compareTo(Object arg0) {
        assert (arg0 instanceof SwitchOutQueue);

        if (this.swId == ((SwitchOutQueue)arg0).getSwId()) {
            if (this.outPort == ((SwitchOutQueue)arg0).getOutPort()) {
                return 0;
            } else if (this.outPort > ((SwitchOutQueue)arg0).getOutPort()) {
                return 1;
            }
        }

        return -1;
    }
}
