
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
			
			for(int i = 0; i < arrLength; i++) {
				System.out.println("linkcost[" + i + "] = " + linkcost[i] );
			}
			
			// find the neighbours
			neighbours = new boolean[arrLength];
			for (int i = 0; i < arrLength; i++) 
			{
				if (i != this.id && linkcost[i] != DvrPacket.INFINITY) {
					neighbours[i] = true;
				}
				System.out.println("neigbhours[" + i + "] = " + neighbours[i] );
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
				//System.out.println("nexthop[" + i + "] = " + nexthop[i] );
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
			// print to see outcome
			for (int i = 0; i < linkcost.length; i++) {
				for (int j = 0 ; j < linkcost.length; j++) {
					System.out.println("mincost[" + i + "][" + j + "] = " + mincost[i][j]);
				}
			}
			
			// update table
			table = new RtnTable(mincost[this.id], nexthop);
			
			// start Timer
			timer = new Timer();
			timer.scheduleAtFixedRate(new TimeoutHandler(this), 0, (long) this.updateInterval);
			System.out.println("timer set");
			
			while(!quit)
			{			
				try
				{
					DvrPacket pkt1 = (DvrPacket)in.readObject();
					// process HELLO
					if (pkt1.type == DvrPacket.HELLO)
						throw new Exception("HELLO not expected");
					// process QUIT
					else if (pkt1.type == DvrPacket.QUIT) {
						timer.cancel();
						quit = true;	
						System.out.println("Quitting");
					}
					// else process ROUTE and run routing algorithm
					else if (pkt1.type == DvrPacket.ROUTE) {
						System.out.println("processing pkt1");
						processDvr(pkt1);
					}
					else
						processDvr(pkt1);
						
				}catch(Exception e){  e.printStackTrace();  }	
				System.out.println("looping");
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
		boolean change = false;
		
		// packet from server then update linkcost and mincost
		if (dvr.sourceid == DvrPacket.SERVER) {
			// update linkcost vector
			linkcost = dvr.getMinCost();
			for(int i = 0; i < linkcost.length; i++) {
				System.out.println("process linkcost[" + i + "] = " + linkcost[i] );
			}
			
			// update mincost vector
			int[] mincost_old = Arrays.copyOf(mincost[this.id], mincost[this.id].length);
			mincost[id] = dvr.getMinCost();
			
		}
		// packet from another router, update mincost of source router
		else {
			mincost[dvr.sourceid] = dvr.getMinCost();
		}
		System.out.println("updating mincost");
		
		// update mincost
		int distance;
		int cost;
		
		for (int i = 0 ; i < nexthop.length; i++) {
			// current router
			if (i == this.id) {
				distance = 0;
			}
			// not direct neighbour 
			else if (nexthop[i] == this.id) {
				distance = DvrPacket.INFINITY;
			}
			// direct neighbour 
			else {
				distance = mincost[this.id][i];
			}
			
			// routing algorithm
			for (int j = 0; j < nexthop.length; j++) {
				cost = mincost[j][i] + linkcost[j];
				
				if ((cost < distance)) {
					distance = cost;
					nexthop[i] = j;
					mincost[this.id][i] = distance;
					change = true;
				}
			}
		}
		
		for(int i = 0; i < nexthop.length; i++) {
			System.out.println("mincost[" + i + "] = " + mincost[this.id][i]);
			System.out.println("nexthop[" + i + "] = " + nexthop[i]);
		}
		
		table = new RtnTable(mincost[this.id], nexthop);
	}
	
	// send mincost vector to all directly connected neighbours
	void updateNeighbours() {
		try 
		{
			
			// print to see outcome
			for (int i = 0; i < linkcost.length; i++) {
				for (int j = 0 ; j < linkcost.length; j++) {
					System.out.println("mincost[" + i + "][" + j + "] = " + mincost[i][j]);
				}
			}
			
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
