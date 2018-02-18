package p2p;

import java.io.IOException;
import java.io.PrintWriter;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.util.Hashtable;

/**
 * Distributed Hash Table (DHT) class for the p2p network term project.
 * This class creates a hash table (key, value) for the users and their files.
 * The thread interfaced is used here in order to create a hash table for each user.
 * @author kyle nakano & Daehee Kang
 *
 */
public class DHT extends Thread {
	private static final String registered = "REGISTERED";
	private static final String retrieve = "RETRIEVE";
	private static final String unregister = "UNREGISTER";
	// DHT for the IP address of peer and string (list of files)
	private Hashtable<InetAddress, String> dht;
	// Datagram socket to create port to send packets between peers
	private DatagramSocket ds;
	// Default port for sockets to establish connection
	private int port = 57264;
	// Boolean for the server thread
	private boolean on;
	
	/**
	 * Constructor to instantiate the hash table
	 */
	public DHT(){
		dht = new Hashtable<InetAddress, String>();
	}

	/**
	 * Getter for the hash table
	 * @return hash table
	 */
	public Hashtable<InetAddress, String> getDht() {
		return dht;
	}

	/**
	 * Get method to get the string of files for a peer
	 * @param ip Parameter for the IP address of the peer
	 * @return Files for specified peer
	 */
	public String getFiles(InetAddress ip) {
		return dht.get(ip);
	}
	
	/**
	 * Removes the hash table if a peer is to leave the network
	 * @param ip IP address of peer
	 */
	public void nodeExit(InetAddress ip) {
		dht.remove(ip);
	}
	
	/**
	 * Serves as the DHT set method to place a peer and files into the hash table
	 * @param ip IP address of peer
	 * @param files String of files located on the peer
	 */
	public void put(InetAddress ip, String files) {
		dht.put(ip, files);
	}
	
	/**
	 * Print method to out the IP address (key) in the hash table
	 */
	public void print() {
		for(InetAddress k : dht.keySet())
			System.out.println("\t" + k + " : " + dht.get(k));
	}
	
	/**
	 * Method to indicate a disconnect on the server thread (peer leaves network)
	 */
	public void switchOff() {
		ds.close();
		on = false;
	}
	
	/**
	 * Method to indicate a peer is creating a server thread (joining network)
	 */
	public void switchOn() {
		on = true;
	}

	/**
	 * Running thread method. Each peer will create its own thread in order to
	 * establish a connection to the network of peers.
	 * Also creates a datagram packet so that the peer's files maybe distributed 
	 * over the network.
	 */
	@Override
	public void run() {
		// TODO Auto-generated method stub
		try {
			ds = new DatagramSocket(port);
			ds.setBroadcast(true);
		} catch (SocketException e2) {
			// TODO Auto-generated catch block
			e2.printStackTrace();
		}
		on = true;
		while(on) {
			while(true) {
				try {
					byte[] recv = new byte[1000];
					DatagramPacket dp = new DatagramPacket(recv, recv.length);
					ds.receive(dp);
					
					// Check to ensure that the DHT does not already contain the IP address
					// Otherwise, it will register the peer onto the network and create a hash
					if(!dht.keySet().contains(dp.getAddress())) {
						dht.put(dp.getAddress(), new String(dp.getData()));
						ds.close();
						Socket s = new Socket(dp.getAddress(), port);
						PrintWriter output = new PrintWriter(s.getOutputStream());
						output.print(registered);
						output.close();
						s.close();
						ds = new DatagramSocket(port);
					}
					
					// Once the peer is on the network, the peer will establish a server socket
					else {
						if(new String(dp.getData(), 0, dp.getLength()).equals(retrieve)) {
							ds.close();
							ServerSocket ss = new ServerSocket(port);
							Socket s = ss.accept();
							PrintWriter output = new PrintWriter(s.getOutputStream());
							for(InetAddress k : dht.keySet())
								output.println(k + " : " + dht.get(k));
							output.flush();
							output.close();
							s.close();
							ss.close();
							ds = new DatagramSocket(port);
						}
						else if(new String(dp.getData(), 0, dp.getLength()).equals(unregister)) {
							dht.remove(dp.getAddress());
						}
						else if(dht.keySet().contains(InetAddress.getByName(new String(dp.getData(), 0, dp.getLength())))) {
							InetAddress reqIP = InetAddress.getByName(new String(dp.getData(), 0, dp.getLength()));
							ds.close();
							ServerSocket ss = new ServerSocket(port);
							Socket s = ss.accept();
							PrintWriter output = new PrintWriter(s.getOutputStream());
							output.print(this.getFiles(reqIP));
							output.flush();
							output.close();
							s.close();
							ss.close();
							ds = new DatagramSocket(port);
						}
					}
				} catch (SocketException e) {
					// TODO Auto-generated catch block
					System.out.println("\t" + e.getMessage() + ": Temporal Stop of Index Server");
					break;
				} catch (IOException e1) {
					// TODO Auto-generated catch block
					e1.printStackTrace();
				}
			}
		}
	}

}