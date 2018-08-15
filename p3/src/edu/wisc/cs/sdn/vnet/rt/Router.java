package edu.wisc.cs.sdn.vnet.rt;

import edu.wisc.cs.sdn.vnet.Device;
import edu.wisc.cs.sdn.vnet.DumpFile;
import edu.wisc.cs.sdn.vnet.Iface;

import net.floodlightcontroller.packet.Ethernet;
import net.floodlightcontroller.packet.IPv4;
import net.floodlightcontroller.packet.UDP;
import net.floodlightcontroller.packet.RIPv2;
import net.floodlightcontroller.packet.RIPv2Entry;
import net.floodlightcontroller.packet.MACAddress;

import java.util.*;

/**
 * @author Aaron Gember-Jacobson and Anubhavnidhi Abhashkumar
 */
public class Router extends Device
{	
	/** Routing table for the router */
	private RouteTable routeTable;
	
	/** ARP cache for the router */
	private ArpCache arpCache;

	/** Timer for fucks */
	private Timer timer;
	
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
	 * Create a new routing table
	 * @param Tak ada, sohai
	 */
	public void createRouteTable()
	{
		for (Iface iface: this.interfaces.values()){
			routeTable.insert(iface.getIpAddress() & iface.getSubnetMask(), 0, iface.getSubnetMask(), iface);
			System.out.println(iface.getName());
		}

		for (Iface iface: this.interfaces.values()){
			this.sendRestInPeace(iface, true, true);
		}

                System.out.println("Creating static route table");
                System.out.println("-------------------------------------------------");
                System.out.print(this.routeTable.toString());
                System.out.println("-------------------------------------------------");

		
		timer = new Timer();
		timer.scheduleAtFixedRate( new updateRestInPeace(), 10000 ,10000);
		
	}

	public void response() {
		for(Iface iface: this.interfaces.values()){
			this.sendRestInPeace(iface, true, false);
		}
                System.out.println("After Response static route table");
                System.out.println("-------------------------------------------------");
                System.out.print(this.routeTable.toString());
                System.out.println("-------------------------------------------------");

		return;
	}

	class updateRestInPeace extends TimerTask{
		public void run(){
			response();

		}
	}
	// Iface is interface where the packet is received
	public void sendRestInPeace(Iface iface, boolean bCast, boolean request){
		
		Ethernet ethernet = new Ethernet();
		IPv4 ip = new IPv4();
		UDP udpPac = new UDP();
		RIPv2 restInPeace2 = new RIPv2();
		
		// ethernet
		ethernet.setEtherType(Ethernet.TYPE_IPv4);
		ethernet.setSourceMACAddress("FF:FF:FF:FF:FF:FF");
		if(bCast) 
			ethernet.setDestinationMACAddress("FF:FF:FF:FF:FF:FF");
		else
			ethernet.setDestinationMACAddress(iface.getMacAddress().toBytes());
		
		// IP *** Might need to set IP header stuff
		ip.setTtl((byte)64);
		ip.setProtocol(IPv4.PROTOCOL_UDP);
		//ip.setSourceAddress(iface.getIpAddress());
		if(bCast) {
			ip.setDestinationAddress("224.0.0.9");
		} else {
			ip.setDestinationAddress(iface.getIpAddress());
		}
		// UDP
		udpPac.setSourcePort((short)520);
		udpPac.setDestinationPort((short)520);


		// Rest In Peace
		if(request) 
			restInPeace2.setCommand(RIPv2.COMMAND_REQUEST);
		else 
			restInPeace2.setCommand(RIPv2.COMMAND_RESPONSE);

		// RIP entry
		for (RouteEntry entry : this.routeTable.getAll()) {
			int address  = entry.getDestinationAddress(); 
			int subnetMask = entry.getMaskAddress();
			int nextHopAddr = iface.getIpAddress();
			int metric = entry.getMetric();

			RIPv2Entry restInPeaceEntry = new RIPv2Entry(address, subnetMask, metric);
			restInPeaceEntry.setNextHopAddress(nextHopAddr);
			restInPeace2.addEntry(restInPeaceEntry);
		}

		udpPac.setPayload(restInPeace2);
		ip.setPayload(udpPac);
		ethernet.setPayload(ip);
		ethernet.serialize();
		this.sendPacket(ethernet, iface);

		return;
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
	* Handle RIP packets
	* @param etherPacket eternet packet and 
	* @param inIface the interface the packet came in from
	*/
	public void handleRIP(Ethernet etherPacket, Iface inIface) {
		IPv4 ip = (IPv4)etherPacket.getPayload();
		UDP udp = (UDP)ip.getPayload();
		RIPv2 rip = (RIPv2)udp.getPayload();

		// Check checksum of UDP packet
		short origCksum = udp.getChecksum();
		udp.resetChecksum();
	        byte[] serialized = udp.serialize();
	        udp.deserialize(serialized, 0, serialized.length);
	        short calcCksum = udp.getChecksum();
	        if (origCksum != calcCksum)
		        { return; }

		// Check if response or request packet
		if(rip.getCommand() == RIPv2.COMMAND_RESPONSE) {
			System.out.println(" GOTTYYEN RESPONDING BOI BOI");
			// Check if it came from one of the router's own address
	//		for(Iface iface : this.interfaces.values()) {
	//			if (inIface.getIpAddress() == iface.getIpAddress()) return;
	//		}

			// If response packet just add into route table
			for(RIPv2Entry ripentry : rip.getEntries()) {
				// Check if metric valid
	        //	        if(ripentry.getMetric() > 16 || ripentry.getMetric() < 1) {return;}
				int metric = ripentry.getMetric() + 1;
				ripentry.setMetric(metric);
				// Find if this entry is already in the route table
				RouteEntry found = this.routeTable.lookup(ripentry.getAddress());
				System.out.println("found " + found);
				if(found == null || found.getMetric() > metric) {
						if(found != null) {System.out.println("found metric: " + found.getMetric() + "   new metric: " + metric);}
					if(found == null){
						System.out.println("inserting stupid shit: " + ripentry);
						this.routeTable.insert(ripentry.getAddress(), ripentry.getNextHopAddress(), ripentry.getSubnetMask(), inIface, metric);
					} else {
						System.out.println("updating SHIT INTO IT: " + ripentry);
						this.routeTable.update(ripentry.getAddress(), ripentry.getNextHopAddress(), ripentry.getSubnetMask(), inIface, metric);
					}
					for(Iface iface : this.interfaces.values()) {
						this.sendRestInPeace(inIface, false, false);
					}			
				}
			}
		}
		else if (rip.getCommand() == RIPv2.COMMAND_REQUEST) {
		// If request packet send to the sender a response packet
			System.out.println("Gotten REQUESTING BOI BOI"); 
			this.sendRestInPeace(inIface, true, false);
			return;
		}
	}

	/**
	 * Handle an Ethernet packet received on a specific interface.
	 * @param etherPacket the Ethernet packet that was received
	 * @param inIface the interface on which the packet was received
	 */
	public void handlePacket(Ethernet etherPacket, Iface inIface)
	{
	//	System.out.println("*** -> Received packet: " +
          //      etherPacket.toString().replace("\n", "\n\t"));
		
		/********************************************************************/
		/* TODO: Handle packets                                             */
		
		switch(etherPacket.getEtherType())
		{
		case Ethernet.TYPE_IPv4:
			this.handleIpPacket(etherPacket, inIface);
			break;
		// Ignore all other packet types, for now
		}
		
		/********************************************************************/
	}
	
	private void handleIpPacket(Ethernet etherPacket, Iface inIface)
	{
		// Make sure it's an IP packet
		if (etherPacket.getEtherType() != Ethernet.TYPE_IPv4)
		{ return; }
		
		// Get IP header
		IPv4 ipPacket = (IPv4)etherPacket.getPayload();

        // Verify checksum
        short origCksum = ipPacket.getChecksum();
        ipPacket.resetChecksum();
        byte[] serialized = ipPacket.serialize();
        ipPacket.deserialize(serialized, 0, serialized.length);
        short calcCksum = ipPacket.getChecksum();
        if (origCksum != calcCksum)
        { return; }
        
        // Check TTL
        ipPacket.setTtl((byte)(ipPacket.getTtl()-1));
        if (0 == ipPacket.getTtl())
        { return; }
        
	// Check if the IP packet has destination 224.0.0.9
	if(ipPacket.getDestinationAddress() == IPv4.toIPv4Address("224.0.0.9")) {
		System.out.println("desitnation addr 224 gotten");
		if(ipPacket.getProtocol() == IPv4.PROTOCOL_UDP) {
			System.out.println("UDP protocol good to go");
			UDP udphead = (UDP)ipPacket.getPayload();
			if(udphead.getDestinationPort() == UDP.RIP_PORT)
				System.out.println("Port is udp sending shit RIP");
				handleRIP(etherPacket, inIface);
		}
	}

        // Reset checksum now that TTL is decremented
        ipPacket.resetChecksum(); 

        
        // Check if packet is destined for one of router's interfaces
        for (Iface iface : this.interfaces.values())
        {
        	if (ipPacket.getDestinationAddress() == iface.getIpAddress())
       		{
			if(ipPacket.getProtocol() == IPv4.PROTOCOL_UDP) {
				UDP udphead = (UDP)ipPacket.getPayload();
				if(udphead.getDestinationPort() == UDP.RIP_PORT)
					handleRIP(etherPacket, inIface);
               		 } else {return;}
       	 	}
	 }
		
        // Do route lookup and forward
        this.forwardIpPacket(etherPacket, inIface);
	}

    private void forwardIpPacket(Ethernet etherPacket, Iface inIface)
    {
        // Make sure it's an IP packet
		if (etherPacket.getEtherType() != Ethernet.TYPE_IPv4)
		{ return; }
        System.out.println("Forward IP packet");
		
		// Get IP header
		IPv4 ipPacket = (IPv4)etherPacket.getPayload();
        int dstAddr = ipPacket.getDestinationAddress();

        // Find matching route table entry 
        RouteEntry bestMatch = this.routeTable.lookup(dstAddr);

        // If no entry matched, do nothing
        if (null == bestMatch)
        { return; }

        // Make sure we don't sent a packet back out the interface it came in
        Iface outIface = bestMatch.getInterface();
        if (outIface == inIface)
        { return; }

        // Set source MAC address in Ethernet header
        etherPacket.setSourceMACAddress(outIface.getMacAddress().toBytes());

        // If no gateway, then nextHop is IP destination
        int nextHop = bestMatch.getGatewayAddress();
        if (0 == nextHop)
        { nextHop = dstAddr; }

        // Set destination MAC address in Ethernet header
        ArpEntry arpEntry = this.arpCache.lookup(nextHop);
        if (null == arpEntry)
        { return; }
        etherPacket.setDestinationMACAddress(arpEntry.getMac().toBytes());
        
        this.sendPacket(etherPacket, outIface);
    }
}
