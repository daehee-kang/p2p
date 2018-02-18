package p2p;

import java.io.IOException;
import java.net.UnknownHostException;
import java.util.Scanner;
import java.util.StringTokenizer;

public class mainConsole {

	public static void main(String[] args) {
		System.out.println("****************************************************************************************************");
		System.out.println("Welcome - this program simulate simple p2p network");
		System.out.println("followings are list of commands:");
		System.out.println("\trg: broadcast my ip addresss to register");
		System.out.println("\trt: obtain list of registered ip and files");
		System.out.println("\tip: check my local ip addresses");
		System.out.println("\tsync [ip address]: sync chosen directory with other specific user's directory");
		System.out.println("\texit: make my directory invisible to others and finish program");
		System.out.println("****************************************************************************************************");

		boolean exit = false;
		try {
			p2pNode node = new p2pNode();
			while(!exit) {
				System.out.println("Enter your command: ");
				Scanner input = new Scanner(System.in);
				StringTokenizer token = new StringTokenizer(input.nextLine());
				String command = "";
				if(token.hasMoreTokens())
					command = token.nextToken();
				String parameter = "";
				if(token.hasMoreTokens())
					parameter = token.nextToken();
				switch(command) {
				case "rg":
					node.register();
					break;
				case "rt":
					node.retrieve();
					break;
				case "ip":
					System.out.println("This system's local ip address: " + node.getAddress());
					break;
				case "sync":
					//call method with param
					if(parameter == null)
					{
						System.out.println("sync operation requires ip address as parameter.");
						System.out.println("Please follow format of [sync ipAddress]");
						break;
					}
					node.sync(parameter);
					break;
				case "exit":
					//finish program
					exit = true;
					node.exit();
					break;
				default:
					System.out.println("Invalid input. Check your command.");
				}
			}
			System.out.println("Program Terminates.");

		} catch (UnknownHostException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (InterruptedException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		
	}

}
