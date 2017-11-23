
import java.io.IOException;
import java.io.ObjectInputStream;

import java.io.ObjectOutputStream;
import java.net.Socket;
import java.net.UnknownHostException;
import java.util.*;
import cpsc441.a4.shared.*;

/**
 * Router Class
 * 
 * This class implements the functionality of a router
 * when running the distance vector routing algorithm.
 * 
 * The operation of the router is as follows:
 * 1. send/receive HELLO message
 * 2. while (!QUIT)
 *      receive ROUTE messages
 *      update mincost/nexthop/etc
 * 3. Cleanup and return
 * 
 *      
 * @author 	Majid Ghaderi
 * @version	3.0
 *
 */
public class Router {
	
	private String serverName;
	private int serverPort;
	private int id;
	private int updateInterval;
	RtnTable table;
	int[] linkcost; 	// linkcost [ i ] is the cost of link to router i 
	int[] nexthop;		// nexthop[ i ] is the next hop node to reach router i 
	int[] mincost;		// mincost[ i ] is the mincost vector of router i
	boolean[] neighbours;
	boolean status;
	ObjectInputStream in;
	ObjectOutputStream out;
	
	
    /**
     * Constructor to initialize the router instance 
     * 
     * @param routerId			Unique ID of the router starting at 0
     * @param serverName		Name of the host running the network server
     * @param serverPort		TCP port number of the network server
     * @param updateInterval	Time interval for sending routing updates to neighboring routers (in milli-seconds)
     */
	public Router(int routerId, String serverName, int serverPort, int updateInterval) {
		this.id = routerId;
		this.serverName = serverName;
		this.serverPort = serverPort;
		this.updateInterval = updateInterval;		
	}

    /**
     * starts the router 
     * 
     * @return The forwarding table of the router
     */
	public RtnTable start() {
	
		try
		{
			// open TCP connection
			Socket socket = new Socket(serverName, serverPort);
			// open input and output streams
			in = new ObjectInputStream(socket.getInputStream());
			out = new ObjectOutputStream(socket.getOutputStream());
			// send, recieve and process HELLO pkt
			DvrPacket pkt = new DvrPacket(this.id, DvrPacket.SERVER, DvrPacket.HELLO);
			out.writeObject(pkt);
			out.flush();
			System.out.println("send " + pkt.toString());
			
			DvrPacket response = (DvrPacket)in.readObject();
			try
			{
				if (response.type != DvrPacket.HELLO){
					socket.close();
					//System.out.println("Incorrect packet. HELLO pkt not recieved");
				}
				
				else if (response.sourceid != DvrPacket.SERVER) {
					socket.close();
					//System.out.println("Incorrect packet. Packet not from SERVER");
				}
			} catch (Exception e) {  System.out.println("Incorrect reciveve: " + e.getMessage()); }
			
			
			linkcost = response.getMinCost();
			
			// initialize neighbours
			neighbours = new boolean[linkcost.length];
			for (int i = 0; i < linkcost.length; i++) 
			{
				if (i != this.id && linkcost[i] != DvrPacket.INFINITY) {
					neighbours[i] = true;
				}
			}
			
			// get mincost
			mincost = new int[linkcost.length];
			mincost = linkcost;	// min cost of current router is the linkcost
			// mincost = INFINITY
			for (int i = 0; i < linkcost.length; i++) 
			{
				if (i != this.id) {
					mincost[i] = DvrPacket.INFINITY;	
				}
			}
			
			nexthop = new int[linkcost.length];
			for (int i = 0; i < nexthop.length; i++) 
			{
				if (neighbours[i]) {		// neighbours is a boolean[]
					nexthop[i] = i;
				}
				else {
					nexthop[i] = this.id;
				}
			}
			
			// update table
			table = new RtnTable(mincost, nexthop);
			
			// start Timer
			Timer timer = new Timer();
			timer.scheduleAtFixedRate(new TimeoutHandler(this), 0, (long) this.updateInterval);
			
			while(status || in.available() > 0)
			{
				try
				{
					DvrPacket pkt1 = (DvrPacket) in.readObject();
					if (pkt1.type == DvrPacket.HELLO)
						throw new Exception("Received HELLO when not expected");
					//process ROUTE
					else if (pkt1.type == DvrPacket.ROUTE)
						//table = processDvr(pkt1);
						System.out.println("need to process pkt");
					//else if QUIT then calncel the timer and we are not active anymore
					else if (pkt1.type == DvrPacket.QUIT) {
						status = false;
						timer.cancel();
					}
				}catch(Exception e){
					e.printStackTrace();
				}	
			}
			socket.close();
			
		}catch (UnknownHostException e) {
			e.printStackTrace();
		}catch (IOException e) {
			e.printStackTrace();
		}catch (ClassNotFoundException e) {
			e.printStackTrace();
		}
		
		return new RtnTable(mincost, nexthop);
	}

	public void resend() {
		try 
		{
			for (int i = 0; i < neighbours.length; i++) 
			{
				if (neighbours[i]) {
					out.writeObject(new DvrPacket(this.id, i, DvrPacket.ROUTE, mincost));
				}
			}
		} catch (IOException e) {  e.printStackTrace();  }
	}
	
    /**
     * A simple test driver
     * 
     */
	public static void main(String[] args) {
		// default parameters
		int routerId = 0;
		String serverName = "localhost";
		int serverPort = 2227;
		int updateInterval = 1000; //milli-seconds
		
		if (args.length == 4) {
			routerId = Integer.parseInt(args[0]);
			serverName = args[1];
			serverPort = Integer.parseInt(args[2]);
			updateInterval = Integer.parseInt(args[3]);
		} else {
			System.out.println("incorrect usage, try again.");
			System.exit(0);
		}
			
		// print the parameters
		System.out.printf("starting Router #%d with parameters:\n", routerId);
		System.out.printf("Relay server host name: %s\n", serverName);
		System.out.printf("Relay server port number: %d\n", serverPort);
		System.out.printf("Routing update intwerval: %d (milli-seconds)\n", updateInterval);
		
		// start the router
		// the start() method blocks until the router receives a QUIT message
		Router router = new Router(routerId, serverName, serverPort, updateInterval);
		RtnTable rtn = router.start();
		System.out.println("Router terminated normally");
		
		// print the computed routing table
		System.out.println();
		System.out.println("Routing Table at Router #" + routerId);
		System.out.print(rtn.toString());
	}

}
