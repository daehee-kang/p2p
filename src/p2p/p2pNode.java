package p2p;

import java.io.*;
import java.net.*;
import java.nio.file.Paths;

public class p2pNode {

	private static final String registered = "REGISTERED";
	private static final String retrieve = "RETRIEVE";
	private static final String unregister = "UNREGISTER";
	//Index server's IP
	private InetAddress _serverIP;
	//Socket to be used for accepting connection
    private Socket _node;
    //Socket to be used for instantiating listener
    private ServerSocket _listener;
    //Socket to be used for communication with index server
    private DatagramSocket _broadcaster;
    //Current system's IP
    private InetAddress _ipAddress;
    //Communication Port
    private final int _comPort = 57264;
    //File Transfer Port
    private final int _ftPort = 57265;
    //Thread to be used for broadcasting communication
    private DHT _bcThread = null;
    //Thread to be used for file transfer
    private Thread _dataThread;
    //Current system's folder to be synchronized
    private String _workingDir;
    //Files stored in _workingDir
    private File[] _listFiles;

    //Constructor
    public p2pNode() throws UnknownHostException, IOException {
    	_ipAddress = InetAddress.getLocalHost();
        _node = new Socket();
        _workingDir = Paths.get("").toAbsolutePath().toString() + "\\syncFolder";
        updateList();
    }
    //getters and setters
    public int getComPort() {
        return _comPort;
    }
    public int getFTPort() {
    	return _ftPort;
    }
    public InetAddress getAddress() {
        return _ipAddress;
    }
    public String getWorkingDir() {
        return _workingDir;
    }
    public void setWorkingDir(String dir) {
        _workingDir = dir;
        updateList();
    }
    public File[] getListFiles() {
        return _listFiles;
    }
    //helper method: make member variable _listFiles up to date
    private void updateList() {
    	//go to working directory
    	File dir = new File(_workingDir);
    	//if working directory is not exist, create new one
        if (!dir.exists())
            dir.mkdir();
        //update _listFiles
        _listFiles = dir.listFiles();
    }
    
    /*
     * Purpose: try register to the index server so that other system can discover current system
     * Precondition: p2pNode is constructed (means at least 4 member variables are initialized in ctor)
     * Postcondition:
     * 		1. register this system's IP address to the index server
     * 		2. registered IP contains string value of name of files concatenated by '@'
     * 		3. if no index server exist, create new server and put (ip, name of files)
     */
    public void register() throws IOException {
    	//temporarily turn off broadcaster in index server if any
    	if(_bcThread != null) {
    		_bcThread.switchOff();
    	}
    	//initialize broadcasting socket
    	_broadcaster = new DatagramSocket(_comPort);
    	_broadcaster.setBroadcast(true);
    	//concatenate name of files into single string
    	StringBuilder list = new StringBuilder();
    	for(File f : _listFiles)
    		list.append(f.getName()+"@");
    	//transfer string (which contains name of files) into byte array format to be sent to index server
    	byte[] sendList = list.toString().getBytes();
    	//copy current system's IP address and convert to broadcast channel
    	byte[] ip = _ipAddress.getAddress();
    	ip[3] = (byte)255;
    	//convert byte formatted IP address to InetAddress type
    	InetAddress addr = InetAddress.getByAddress(ip);
    	//create packet to be sent over broadcasting channel
    	//packet contains information of name of files
    	DatagramPacket outGoing = new DatagramPacket(sendList, sendList.length, addr, _comPort);
		//broadcast
    	_broadcaster.send(outGoing);
		_broadcaster.close();
		//response will come as TCP connection, thus wait for connection
		ServerSocket ss = new ServerSocket(_comPort);
		Socket s = new Socket();
		try {
			//set Timeout in case of no existence of index server
			ss.setSoTimeout(2000);
			//listen for connection for specified time
			s = ss.accept();
			//if response reached, 
			BufferedReader input = new BufferedReader(new InputStreamReader(s.getInputStream()));
			//print output to notify user that index server exist
			System.out.println(input.readLine());
			//set server IP as address where response was sent
			_serverIP = ((InetSocketAddress) s.getRemoteSocketAddress()).getAddress();
		} catch (SocketTimeoutException e) {
			//if Socket Timeout Exception is thrown, refer as no index server exist
			System.out.println("\t" + e.getMessage() + ": Creating Index Server...");
			_serverIP = InetAddress.getByAddress(_ipAddress.getAddress());
		}finally {
			//create index server in a different thread
			if(_bcThread == null) {
				_bcThread = new DHT();
				_bcThread.put(InetAddress.getByAddress(_ipAddress.getAddress()), list.toString());
				_bcThread.start();
			}
			//close socket used for response
	    	try {
				s.close();
				ss.close();
			}catch(Exception e) {
				System.out.println("\t" + e.getMessage() + ": Register Socket Close");
			}
		}
		//turn back on broadcaster in index server
        _bcThread.switchOn();
		//start listener thread
        listen();
        retrieve();
    }
    
    /*
     * Purpose: runs thread responsible for the file transfer connection attempts
     * Precondition: none
     * Postcondition: separate thread runs for listening file transfer attempts
     */
    public void listen() {
    	_dataThread = new Thread() {
            @Override
            public void run() {
            	try {
            		//server may already be opened
					_listener = new ServerSocket(_ftPort);
				} catch (IOException e) {
					System.out.println(e.getMessage() + ": Listener");
				}
                System.out.println("\tSocket started listening for connection attempt...");
                while(true) {
	            	try {
	            		//one file per connection
	                    for(File f : _listFiles) {
	                    	//stay listening for connection
	                    	_node = _listener.accept();
	                    	//once connection is made, send files
	                    	System.out.println("Sending files to " + ((InetSocketAddress) _node.getRemoteSocketAddress()).getAddress());
	                    	byte[] bytes = new byte[(int)f.length()];
	                    	FileInputStream fis = new FileInputStream(f);
	                    	OutputStream os = _node.getOutputStream();
	                    	fis.read(bytes);
	                    	os.write(bytes, 0, bytes.length);
	                    	os.flush();
	                    	_node.close();
	                    }
	                    
	                } catch (IOException e) {
	                    System.out.println("\t" + e.getMessage() + ": Listener");
	                    break;
					}
                }
            }
        };
        _dataThread.start();
    }
    
    /*
     * Purpose: obtain address and file information from index server
     * Precondition: system is registered to index server
     * Postcondition: prints out the ip address registered to index server 
     * 					and files corresponds to address
     */
    public void retrieve() throws IOException {
    	//if this system runs index server, simply obtain without connection
    	if(_serverIP != null && _serverIP.equals(_ipAddress)) {
    		_bcThread.print();
    		return;
    	}
    	//if not, temporarily stop server thread
    	_bcThread.switchOff();
    	//broadcast string "RETRIEVE"
    	_broadcaster = new DatagramSocket(_comPort);
    	byte[] send = retrieve.getBytes();
    	DatagramPacket outGoing = new DatagramPacket(send, send.length, _serverIP, _comPort);
    	_broadcaster.send(outGoing);
    	_broadcaster.close();
    	//listen for response
    	try {
    		Socket s = new Socket();
    		s.connect(new InetSocketAddress(_serverIP, _comPort), 5000);
    		//response comes in as ip address: file name@file name@ ... separated by line
    		BufferedReader input = new BufferedReader(new InputStreamReader(s.getInputStream()));
			String str;
    		while((str = input.readLine()) != null)
    			System.out.println("\t" + str);
			input.close();
			s.close();
		} catch (SocketTimeoutException e) {
			//if the server is not responsive, create new server by re-registering
			System.out.println("\tCannot access index server");
			_serverIP = InetAddress.getByAddress(_ipAddress.getAddress());
			register();
		} catch (IOException e) {
			System.out.println("\t" + e.getMessage() + ": Retrieve");
		} finally {
			_bcThread.switchOn();
		}
    }
    
    /*
     * Purpose: unregister and hide from the connection
     * Precondition: system is registered to index server
     * Postcondition: system will request to remove its ip and file info from index server
     */
    public void unregister() throws IOException {
    	//if this system runs index server, simply remove node
    	if(_serverIP.equals(_ipAddress)) {
    		_bcThread.nodeExit(_ipAddress);
    		_bcThread.switchOff();
    		_node.close();
    		_listener.close();
    		return;
    	}
    	//else, compose "UNREGISTER" and send to server
    	_bcThread.switchOff();
    	_broadcaster = new DatagramSocket(_comPort);
    	byte[] send = unregister.getBytes();
    	DatagramPacket outGoing = new DatagramPacket(send, send.length, _serverIP, _comPort);
    	_broadcaster.send(outGoing);
    	_broadcaster.close();
    	try {
    		_node.close();
		} catch (IOException e) {
			e.printStackTrace();
		} finally {
			_listener.close();
	    	_bcThread.switchOn();
		}
    }   
    
    /*
     * Purpose: synchronize (download) files from the specified IP
     * Precondition: Parameter is passed as String data type in format of IP address
     * Postcondition: system requested synchronized should have files downloaded from other system
     */
    public void sync(String ipParam) throws UnknownHostException {
    	//str gets file names separated by '@'
    	String str;
    	//convert String ip to InetAddress data type
    	InetAddress reqIP = InetAddress.getByName(ipParam);
    	try {
    		//
    		if(_serverIP.equals(_ipAddress)) {
            	str = _bcThread.getFiles(reqIP);
            }
    		else {
	    		_broadcaster = new DatagramSocket(_comPort);
	    		DatagramPacket dp = new DatagramPacket(ipParam.getBytes(), ipParam.length(), _serverIP, _comPort);
	    		_broadcaster.send(dp);
	    		_broadcaster.close();
	    		Socket s = new Socket(_serverIP, _comPort);
	    		BufferedReader input = new BufferedReader(new InputStreamReader(s.getInputStream()));
	    		str = input.readLine();
				input.close();
				s.close();
    		}
    		String[] files = str.split("@");
        	for(String fName :files) {
            	Socket s = new Socket(reqIP, _ftPort);
        		System.out.println("\treceiving files from " + reqIP);
        		byte[] bytes = new byte[1024];
        		InputStream is = s.getInputStream();
        		FileOutputStream fos = new FileOutputStream(new File(_workingDir + "\\" + fName));
        		
        		int len;
        		while ((len = is.read(bytes)) > 0) {
        			fos.write(bytes, 0, len);
        		}
        		fos.flush();
        		fos.close();
        	}
        } catch (IOException e) {
        	System.out.println("\t" + e.getMessage() + ": Synchronization");
        }finally {
        	if(_serverIP.equals(_ipAddress)) {
        		_bcThread.nodeExit(_ipAddress);
        		updateList();
        		StringBuilder list = new StringBuilder();
            	for(File f : _listFiles)
            		list.append(f.getName()+"@");
        		_bcThread.put(InetAddress.getByAddress(_ipAddress.getAddress()), list.toString());
        	}
        	else {
        		try {
					_broadcaster = new DatagramSocket(_comPort);
					DatagramPacket dp = new DatagramPacket(unregister.getBytes(), unregister.length(), _serverIP, _comPort);
		    		_broadcaster.send(dp);
		    		updateList();
		    		StringBuilder list = new StringBuilder();
		        	for(File f : _listFiles)
		        		list.append(f.getName()+"@");
		        	byte[] sendList = list.toString().getBytes();
		    		dp = new DatagramPacket(sendList, sendList.length, _serverIP, _comPort);
		    		_broadcaster.send(dp);
		    		_broadcaster.close();
		    		ServerSocket ss = new ServerSocket(_comPort);
		    		ss.setSoTimeout(2000);
		    		Socket s = ss.accept();
	    			BufferedReader input = new BufferedReader(new InputStreamReader(s.getInputStream()));
	    			System.out.println(input.readLine());
	    			s.close();
	    			ss.close();
				} catch (IOException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
        	}
        }
    }
    
    public void exit() throws IOException, InterruptedException {
    	unregister();
		_bcThread.switchOff();
		_bcThread.join();
		_dataThread.join();
    }
}