/**
 *
 */
package net.floodlightcontroller.mobilesdn;

// import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * record switch and agent mapping here;
 * this class is just a wrapper for corresponding HashMap
 *
 * @author Yanhe Liu <yanhe.liu@cs.helsinki.fi>
 *
 */
public class NetworkManager {
    private Map<Long, List<APAgent>> swToApAgentMap = new ConcurrentHashMap<Long, List<APAgent>>();

    public NetworkManager() {
        // initializing
    }

    public boolean containsSwitch(long swId) {
        return swToApAgentMap.containsKey(swId);
    }

    public void putSwitch(long swId, List<APAgent> agentList) {
        swToApAgentMap.put(swId, agentList);
    }

    public List<APAgent> getAssociatedAgent(long swId) {
        return swToApAgentMap.get(swId);
    }

    public int getAgentNum(long swId) {
        return swToApAgentMap.size();
    }

    public void removeSwitch(long swId) {
        swToApAgentMap.remove(swId);
    }

    public void removeAllSwitches() {
        swToApAgentMap.clear();
    }

    public boolean isSwitchMapEmpty() {
        return swToApAgentMap.isEmpty();
    }
}
