package edu.wisc.cs.sdn.vnet.rt;

import edu.wisc.cs.sdn.vnet.Device;
import edu.wisc.cs.sdn.vnet.DumpFile;
import edu.wisc.cs.sdn.vnet.Iface;

import net.floodlightcontroller.packet.Ethernet;
import net.floodlightcontroller.packet.IPv4;
import net.floodlightcontroller.packet.MACAddress;

/**
 * @author Aaron Gember-Jacobson and Anubhavnidhi Abhashkumar
 */
public class Router extends Device
{	
	/** Routing table for the router */
	private RouteTable routeTable;
	
	/** ARP cache for the router */
	private ArpCache arpCache;
	
	/**
	 * Creates a router for a specific host.
	 * @param host hostname for the router
	 */
	public Router(String host, DumpFile logfile)
	{
		super(host,logfile);
		this.routeTable = new RouteTable();
		this.arpCache = new ArpCache();
	}
	
	/**
	 * @return routing table for the router
	 */
	public RouteTable getRouteTable()
	{ return this.routeTable; }
	
	/**
	 * Load a new routing table from a file.
	 * @param routeTableFile the name of the file containing the routing table
	 */
	public void loadRouteTable(String routeTableFile)
	{
		if (!routeTable.load(routeTableFile, this))
		{
			System.err.println("Error setting up routing table from file "
					+ routeTableFile);
			System.exit(1);
		}
		
		System.out.println("Loaded static route table");
		System.out.println("-------------------------------------------------");
		System.out.print(this.routeTable.toString());
		System.out.println("-------------------------------------------------");
	}
	
	/**
	 * Load a new ARP cache from a file.
	 * @param arpCacheFile the name of the file containing the ARP cache
	 */
	public void loadArpCache(String arpCacheFile)
	{
		if (!arpCache.load(arpCacheFile))
		{
			System.err.println("Error setting up ARP cache from file "
					+ arpCacheFile);
			System.exit(1);
		}
		
		System.out.println("Loaded static ARP cache");
		System.out.println("----------------------------------");
		System.out.print(this.arpCache.toString());
		System.out.println("----------------------------------");
	}

	/**
	 * Handle an Ethernet packet received on a specific interface.
	 * @param etherPacket the Ethernet packet that was received
	 * @param inIface the interface on which the packet was received
	 */
	public void handlePacket(Ethernet etherPacket, Iface inIface)
	{
		System.out.println("*** -> Received packet: " +
                etherPacket.toString().replace("\n", "\n\t"));
		
		/********************************************************************/
		/* TODO: Handle packets                                             */
		/* Send a received packet out the appropriate interface of the router */
		
		if (this.checkPacket(etherPacket, inIface)) {
			this.forward(etherPacket, inIface);
		} else {
			// Do nothing since I want to drop the packet
		}
					
		/********************************************************************/
	}
	
	/**
	* Checks whether the packet is okay or nah	
	*/
	public boolean checkPacket(Ethernet etherPacket, Iface inIface) 
	{
		// Check if it is IPv4
		if (etherPacket.getEtherType()!= Ethernet.TYPE_IPv4) {
			return false;
		}
		// Get the IPv4 header, need to cast 
		IPv4 header = (IPv4)etherPacket.getPayload();

		// Check the checksum
		short checksum = header.getChecksum();
		// Serialize computes and set fields, will need to set checksum to zero to use this
		header.resetChecksum();
		byte[] data = header.serialize();
		// Deserializing?
		header.deserialize(data, 0, data.length);
		short newCS = header.getChecksum();
//		System.out.println("New CS: " + newCS + "Old CS:" + checksum);
		if(checksum != newCS) {	
			return false;
		}	
	
		// Check the TTL and decrease it
		if(header.getTtl() == 0) {
			return false;
		} else {
			header.setTtl((byte)(header.getTtl() - 1));
//			System.out.println("Time to live: " + header.getTtl());
		}
		
		header.resetChecksum();
		// Check if router interface matches packet dest interface
		if(inIface.getIpAddress() == header.getDestinationAddress()) {
			return false;
		}
		// Need to check all interfaces on the router
//		System.out.println("header's destination Address: " + header.getDestinationAddress());
		for(Iface iface: interfaces.values()) {
			if(iface.getIpAddress() == header.getDestinationAddress()) {
				return false;
			}
		}
//		System.out.println("Managed to finish checkPacket");
		return true;
	}	
	

	/**
	* Modify Ethernet packet header and foward it 
	*/
	public void forward(Ethernet etherPacket, Iface inIface)
	{
//		System.out.println("Start of forwarding");

		// Use lookup method in RouteTable class to obtain RouteEntry
		IPv4 header = (IPv4)etherPacket.getPayload();

		RouteEntry found = routeTable.lookup(header.getDestinationAddress());
//		System.out.println("found RouteEntry within routeTable: " + found.toString());
		if(found == null) return;
		
		if(found.getInterface() == inIface) return;

		// Determine next-hop IP address and lookup MAC address in ArpCache class
		int nextHopIP = found.getGatewayAddress();
//		System.out.println("nextHopIP: " + nextHopIP);
		if(nextHopIP == 0) {
			// Means nextHop is destination YAY
			nextHopIP = header.getDestinationAddress();
		}
		ArpEntry nextHopMAC = arpCache.lookup(nextHopIP);
//		System.out.println("nextHopMAC: " + nextHopMAC.toString());
		if(nextHopMAC == null) return;



		// Change ethernet packet frame, can be string or byte array
		etherPacket.setSourceMACAddress(found.getInterface().getMacAddress().toBytes());
		etherPacket.setDestinationMACAddress(nextHopMAC.getMac().toBytes());

		// Send packets from Device class	
//		System.out.println("Managed to get to forwarding packet");
		boolean sent = sendPacket(etherPacket, found.getInterface());
		if(!sent) System.out.println("Failed to send");
	}	
}
