import java.net.*;
import java.io.*;
import java.text.DecimalFormat;

public class Server {

//	private int portNo = 0;
	private ServerSocket server;
	private InputStream in;
	private long data;
	private Socket clientSocket;
	
	public Server(int port) throws IOException{
	//	this.portNo = port;
		server = new ServerSocket(port);
	}
	
	
	public void listen() throws IOException{
			clientSocket = server.accept();
			in = clientSocket.getInputStream();
			
			int accepted = 0;
			double totalTime = 0;
			
			byte[] data = new byte[1000];
                        long start, end;
			int readed = 0;			
			while(true) {
			//	in.read(data);
				start = System.nanoTime();
				readed = in.read(data, 0, data.length);
				end = System.nanoTime();
				totalTime += end - start;
				
				if(readed == -1) break;
				accepted += readed;
			}
		//	long curr = System.currentTimeMillis();
		//	long total = curr - start;
	     //		totalTime /= 1000;
			in.close();
			clientSocket.close();
			
		//	long rate = ((accepted*8/1000)/totalTime);
			
			totalTime = totalTime *Math.pow(10, -9); //seconds
			double rate = (accepted*Math.pow(10, -3)*8/totalTime); //Kbps
			DecimalFormat df = new DecimalFormat("#.000");
			System.out.println("received=" + accepted/1000 + " KB rate=" + df.format((rate/1000.0)) + " Mbps");
			
			
	}
}
