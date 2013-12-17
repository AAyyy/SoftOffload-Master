/**
 * 
 */
package net.floodlightcontroller.offloading;

import java.net.InetAddress;
import java.net.UnknownHostException;

import net.floodlightcontroller.util.MACAddress;

/**
 * @author Yanhe Liu <yanhe.liu@cs.helsinki.fi>
 *
 */
public class OffloadingClient implements Comparable<Object> {
	private final MACAddress hwAddress;
	private InetAddress ipAddress;
	private float rate;
	

	/**
	 * construct a client instance
	 * 
	 * @param hwAddress Client's hw address
	 * @param ipv4Address Client's IPv4 address
	 */
	public OffloadingClient(MACAddress hwAddress, InetAddress ipAddress) {
		this.hwAddress = hwAddress;
		this.ipAddress = ipAddress;
	}
	
	/**
	 * construct a client instance
	 * 
	 * @param hwAddress Client's hw address
	 * @param ipv4Address Client's IPv4 address
	 */
	public OffloadingClient(String hwAddress, String ipAddress) throws UnknownHostException {
		this.hwAddress = MACAddress.valueOf(hwAddress);
		this.ipAddress = InetAddress.getByName(ipAddress);
	}
	
	/**
	 * Get the client's MAC address.
	 * @return
	 */
	public MACAddress getMacAddress() {
		return this.hwAddress;
	}

	/**
	 * Get the client's IP address.
	 * @return
	 */
	public InetAddress getIpAddress() {
		return ipAddress;
	}
	
	/**
	 * Set the client's IP address
	 * @param addr
	 */
	public void setIpAddress(InetAddress addr) {
		this.ipAddress = addr;
	}
	
	/**
	 * Set the client's IP address
	 * @param String addr
	 * @throws UnknownHostException 
	 */
	public void setIpAddress(String addr) throws UnknownHostException {
		this.ipAddress = InetAddress.getByName(addr);
	}
	
	/**
	 * get client's rate value
	 * @return
	 */
	public float getRate() {
		return this.rate;
	}
	
	/**
	 * Set the client's rate value
	 * @param r
	 */
	public void updateRate(float r) {
		this.rate = r;
	}
	
	@Override
	public boolean equals(Object obj) {
		if (!(obj instanceof OffloadingClient))
			return false;

		if (obj == this)
			return true;
		
		OffloadingClient that = (OffloadingClient) obj;
			
		return (this.hwAddress.equals(that.hwAddress));
	}

	
	@Override
	public int compareTo(Object o) {
		assert (o instanceof OffloadingClient);
		
		if (this.hwAddress.toLong() == ((OffloadingClient)o).hwAddress.toLong())
			return 0;
		
		if (this.hwAddress.toLong() > ((OffloadingClient)o).hwAddress.toLong())
			return 1;
		
		return -1;
	}
}
