package edu.wisc.cs.sdn.vnet.sw;

import net.floodlightcontroller.packet.Ethernet;
import edu.wisc.cs.sdn.vnet.Device;
import edu.wisc.cs.sdn.vnet.DumpFile;
import edu.wisc.cs.sdn.vnet.Iface;
import java.util.*;

/**
 * @author Aaron Gember-Jacobson
 */
public class Switch extends Device
{
        class bridgeEntry {
                public Iface iface;
                public long time;

                public bridgeEntry(Iface iface, long time) {
                        this.iface = iface;
                        this.time = time;
                }
        }

        HashMap<Object, bridgeEntry> bridgeTable = new HashMap<Object, bridgeEntry>();

        /**
         * Creates a router for a specific host.
         * @param host hostname for the router
         */
        public Switch(String host, DumpFile logfile)
        {
                super(host,logfile);
        }

        /**
         * Handle an Ethernet packet received on a specific interface.
         * @param etherPacket the Ethernet packet that was received
         * @param inIface the interface on which the packet was received
         */

        // ETHERPACKAT.MACADDR == HOST
        // IFACE == PORTSSSS 

        public void handlePacket(Ethernet etherPacket, Iface inIface)
        {
                System.out.println("*** -> Received packet: " +
                etherPacket.toString().replace("\n", "\n\t"));

                /********************************************************************/
                /* TODO: Handle packets                                             */
                /********************************************************************/

                // adding 
                bridgeTable.put(etherPacket.getSourceMAC(), new bridgeEntry(inIface, System.currentTimeMillis()));
                bridgeEntry ent = bridgeTable.get(etherPacket.getDestinationMAC());
                boolean isValid = (ent == null || System.currentTimeMillis() - ent.time > 15000) ? false : true;

                if(isValid) {
                        sendPacket(etherPacket, ent.iface);
                } else {
                        for(Iface iface : interfaces.values()) {
                                if(!iface.equals(inIface)) {
                                        sendPacket(etherPacket, iface);
                                }
                        }
                }




                /* Send a received packet out the appropriate interface(s) of the switch
                *  use getSourceMAC() and getDestinationMAC() methods
                */
        }
}

