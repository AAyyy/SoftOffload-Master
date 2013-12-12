package net.floodlightcontroller.offloading;


import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
// import java.net.Socket;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ScheduledExecutorService;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.floodlightcontroller.core.IFloodlightProviderService;
import net.floodlightcontroller.core.module.FloodlightModuleContext;
import net.floodlightcontroller.core.module.FloodlightModuleException;
import net.floodlightcontroller.core.module.IFloodlightModule;
import net.floodlightcontroller.core.module.IFloodlightService;
import net.floodlightcontroller.offloading.OffloadingProtocolServer;
// import net.floodlightcontroller.restserver.IRestApiService;
import net.floodlightcontroller.threadpool.IThreadPoolService;

public class OffloadingMaster implements IFloodlightModule, IFloodlightService {
	protected static Logger log = LoggerFactory.getLogger(OffloadingMaster.class);
	// protected IRestApiService restApi;

	// private IFloodlightProviderService floodlightProvider;
	private ScheduledExecutorService executor;
	private DatagramSocket agentSocket = null;
	
	//	private final AgentManager agentManager;

	// some defaults
	private final int AGENT_PORT = 6777;
	static private final int DEFAULT_PORT = 2819;
	
	public OffloadingMaster(){
		// clientManager = new ClientManager();
	}
	
	/**
     * Handle a ping from an agent. 
     * 
     * @param AgentAddr
     */
	void receiveClientInfo(final InetAddress agentAddr, 
			final String clientEthAddr, final String clientIpAddr) {
		
		byte[] buf = new byte[128];
		log.info("Client message from " + agentAddr + ": " + clientEthAddr + 
			" - " + clientIpAddr);
		
		try {
			agentSocket = new DatagramSocket();
			buf = "ack\n".getBytes();
			DatagramPacket packet = new DatagramPacket(buf, buf.length, agentAddr, AGENT_PORT);
			agentSocket.send(packet);
			agentSocket.close();
		} catch (UnknownHostException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
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
		// floodlightProvider = context.getServiceImpl(IFloodlightProviderService.class);
		// restApi = context.getServiceImpl(IRestApiService.class);
		IThreadPoolService tp = context.getServiceImpl(IThreadPoolService.class);
		executor = tp.getScheduledExecutor();
	}

	@Override
	public void startUp(FloodlightModuleContext context)
			throws FloodlightModuleException {
			
		// restApi.addRestletRoutable(new OdinMasterWebRoutable());
		
		// read config options
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
	}

}
