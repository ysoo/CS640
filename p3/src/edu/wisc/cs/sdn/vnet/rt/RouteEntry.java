package edu.wisc.cs.sdn.vnet.rt;

import net.floodlightcontroller.packet.IPv4;
import edu.wisc.cs.sdn.vnet.Iface;

import java.util.Timer;
import java.util.TimerTask;
/**
 * An entry in a route table.
 * @author Aaron Gember-Jacobson and Anubhavnidhi Abhashkumar
 */
public class RouteEntry 
{
	/** Destination IP address */
	private int destinationAddress;
	
	/** Gateway IP address */
	private int gatewayAddress;
	
	/** Subnet mask */
	private int maskAddress;
	
	/** Router interface out which packets should be sent to reach
	 * the destination or gateway */
	private Iface iface;
	/**Total cost of getting data gram from this router to destination*/
	private int metric;
	/**Flag to determine that the route changed recently, true of the entry is valid*/
	private boolean flag;
	/**Timer to se to determine timeout*/
	private Timer timer;
	private RouteTable routeTable;
	
	/**
	 * Create a new route table entry.
	 * @param destinationAddress destination IP address
	 * @param gatewayAddress gateway IP address
	 * @param maskAddress subnet mask
	 * @param iface the router interface out which packets should 
	 *        be sent to reach the destination or gateway
	 */
	public RouteEntry(int destinationAddress, int gatewayAddress, 
			int maskAddress, Iface iface)
	{
		this.destinationAddress = destinationAddress;
		this.gatewayAddress = gatewayAddress;
		this.maskAddress = maskAddress;
		this.iface = iface;
	}
	
	/**
	 * @return destination IP address
	 */
	public int getDestinationAddress()
	{ return this.destinationAddress; }
	
	/**
	 * @return gateway IP address
	 */
	public int getGatewayAddress()
	{ return this.gatewayAddress; }

    public void setGatewayAddress(int gatewayAddress)
    { this.gatewayAddress = gatewayAddress; }
	
	/**
	 * @return subnet mask 
	 */
	public int getMaskAddress()
	{ return this.maskAddress; }
	
	/**
	 * @return the router interface out which packets should be sent to 
	 *         reach the destination or gateway
	 */
	public Iface getInterface()
	{ return this.iface; }

    public void setInterface(Iface iface)
    { this.iface = iface; }
	
	public boolean getFlag() {
		return this.flag;
	}

	public RouteTable getTable() {
		return this.routeTable;
	}
	public void setRouteTable(RouteTable routeTable) {
		this.routeTable = routeTable;
	}
	public void startTimer() {
		this.timer = new Timer();
		this.timer.schedule(new invalid(), 30000);
	}

	public void restart() {
		this.timer.cancel();
		this.timer.purge();
		this.flag = true; // Valid entry
		this.timer = new Timer();
		this.timer.schedule(new invalid(), 30000);
	}

	class invalid extends TimerTask {
		public void run() {
			removeEntry();
		}
	}
	public void removeEntry() {
		this.routeTable.remove(this.getDestinationAddress(), this.getMaskAddress());
	}
	public String toString()
	{
		return String.format("%s \t%s \t%s \t%s",
				IPv4.fromIPv4Address(this.destinationAddress),
				IPv4.fromIPv4Address(this.gatewayAddress),
				IPv4.fromIPv4Address(this.maskAddress),
				this.iface.getName());
	}
    public void setMetric(int metric) 
    { this.metric = metric; }

    public int getMetric()
    { return this.metric; }
	
}
