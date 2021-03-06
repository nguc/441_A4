
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
	
	TimeoutHandler handler;
	private String serverName;
	private int serverPort;
	private int id;
	int updateInterval;
	Socket socket;
	Timer timer;
	RtnTable table;
	int[] linkcost; 	// linkcost [ i ] is the cost of link to router i 
	int[] nexthop;		// nexthop[ i ] is the next hop node to reach router i 
	int[][] mincost;		// mincost[ i ] is the mincost vector of router i
	boolean[] neighbours;
	boolean quit;
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
		this.quit = false;
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
			socket = new Socket(serverName, serverPort);
			
			// open input and output streams
			in = new ObjectInputStream(socket.getInputStream());
			out = new ObjectOutputStream(socket.getOutputStream());
			
			// send, recieve and process HELLO
			DvrPacket pkt = new DvrPacket(this.id, DvrPacket.SERVER, DvrPacket.HELLO);
			out.writeObject(pkt);
			out.flush();
			System.out.println("send " + pkt.toString());
			
			// read response from server
			DvrPacket response = (DvrPacket)in.readObject();
			try
			{
				if (response.type != DvrPacket.HELLO){
					socket.close();
					//System.out.println("Incorrect packet. HELLO pkt not recieved");
				}
				else if (response.sourceid != DvrPacket.SERVER) {
					socket.close();
					System.out.println("Incorrect packet. Packet not from SERVER");
				}
			} catch (Exception e) {  System.out.println("Incorrect reciveve: " + e.getMessage()); }
			
			// get linkcost array
			linkcost = response.getMinCost();
			int arrLength = linkcost.length;
			
			
			// find the neighbours
			neighbours = new boolean[arrLength];
			for (int i = 0; i < arrLength; i++) 
			{
				if (i != this.id && linkcost[i] != DvrPacket.INFINITY) {
					neighbours[i] = true;
				}
				//System.out.println("neigbhours[" + i + "] = " + neighbours[i] );
			}
			
			// determine the next hop node to reach router i
			nexthop = new int[arrLength];
			for (int i = 0; i < arrLength; i++) 
			{
				if (neighbours[i]) {
					nexthop[i] = i;
				}
				else {
					nexthop[i] = this.id;
				}
			}
			
			// initialize min cost matrix
			mincost = new int[arrLength][arrLength];
			mincost[this.id] = Arrays.copyOf(linkcost, arrLength);	// min cost array of current router is equal to linkcost		
			// set mincost for all other router to infinity
			for (int i = 0; i < arrLength; i++) 
			{
				if (i != this.id) {
					for (int j = 0; j < linkcost.length; j++)
					mincost[i][j] = DvrPacket.INFINITY;	
				}
			}
			
			// start Timer
			timer = new Timer();
			handler = new TimeoutHandler(this);
			timer.schedule(handler , this.updateInterval);
			
			while(!quit)
			{			
				try
				{
					DvrPacket pkt1 = (DvrPacket)in.readObject();
					
					// process HELLO
					if (pkt1.type == DvrPacket.HELLO){
						throw new Exception("HELLO not expected");
					}
					
					// process QUIT
					else if (pkt1.type == DvrPacket.QUIT) {
						System.out.println("Quitting");
						timer.cancel();
						quit = true;	
					}
					
					// process ROUTE and run routing algorithm
					else if (pkt1.type == DvrPacket.ROUTE) {
						processDvr(pkt1);
					}
					updateMincost();
						
				}catch(Exception e){  e.printStackTrace();  }	
			}
			cleanUp();
			
		}catch (UnknownHostException e) {
			e.printStackTrace();
		}catch (IOException e) {
			e.printStackTrace();
		}catch (ClassNotFoundException e) {
			e.printStackTrace();
		}
		
		return new RtnTable(mincost[this.id], nexthop);
	}

	
	void processDvr (DvrPacket dvr) {
		// packet from server then update linkcost and mincost
		if (dvr.sourceid == DvrPacket.SERVER) {
			// update linkcost vector
			linkcost = dvr.getMinCost();
			
			// update nexthop
			nexthop = new int[linkcost.length];
			for (int i = 0; i < linkcost.length; i++) 
			{
				if (neighbours[i]) {
					nexthop[i] = i;
				}
				else {
					nexthop[i] = this.id;
				}
			}
			
		}
		// packet from another router, update mincost of source router
		else {
			mincost[dvr.sourceid] = dvr.getMinCost();
		}
		updateMincost();
	}
		
	void updateMincost(){
		// update mincost
		boolean change = false;
		int distance;
		int cost;
		
		for (int i = 0 ; i < nexthop.length; i++) {
			
			// current router
			if (i == this.id) {
				distance = 0;
			}
			// not direct neighbour 
			else if (this.nexthop[i] == this.id) {
				distance = DvrPacket.INFINITY;
			}
			// direct neighbour 
			else {
				distance = this.mincost[this.id][i];
			}
			
			// routing algorithm
			for (int j = 0; j < nexthop.length; j++) {
				cost = this.mincost[j][i] + this.linkcost[j];
				
				if ((cost < distance)) {
					distance = cost;
					this.nexthop[i] = j;
					this.mincost[this.id][i] = distance;
					change = true; // local min cost changed
				}
			}
			
		}
		// if local mincost changes then update neighbours
		if (change){
			updateNeighbours();
			this.handler.restart();
			
		}
	}
	
	// send mincost vector to all directly connected neighbours and restarts timer
	void updateNeighbours() {
		try 
		{			
			for (int i = 0; i < neighbours.length; i++) 
			{
				if (neighbours[i]) {
					DvrPacket pkt = new DvrPacket(this.id, i, DvrPacket.ROUTE, mincost[this.id]);
					out.writeObject(pkt);
					out.flush();
				}
			}	
		} catch (IOException e) {  e.printStackTrace();  }
	}
	
	
	// close all object writers and the socket
	void cleanUp() {
		try 
		{
			this.in.close();
			this.out.close();
			this.socket.close();
		} catch (Exception e) {  e.printStackTrace();  }
		
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
