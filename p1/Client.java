import java.io.*;
import java.net.*;
import java.text.DecimalFormat;

public class Client {
		Socket cSock;
		OutputStream os;
		int dataSent;
		
		public Client(String hostname, int portNum) throws UnknownHostException, IOException {
			cSock = new Socket(hostname, portNum);
			os = cSock.getOutputStream();
		}
		
		public void send(double time) throws IOException{
			long maxTime = (long) time *1000000000; //ms
			boolean done = false;
			byte[] clientBuff = new byte[1000];
			long startTime, lastTime;
			double timeLeft = 0;
			
			int bytesSent = 0;
			
			while(!done) {
				startTime = System.nanoTime();
				os.write(clientBuff, 0, clientBuff.length);
				os.flush();
				lastTime = System.nanoTime();
				
				timeLeft += lastTime - startTime;
				
				done = (timeLeft >= maxTime);
				bytesSent+= clientBuff.length;
			}
			
	//		timeLeft /= 1000;
			os.close();
			cSock.close();
			timeLeft = timeLeft * Math.pow(10, -9);
			double rate = (bytesSent*Math.pow(10, -3)*8)/timeLeft;
			DecimalFormat df = new DecimalFormat("#.000");
			System.out.println("sent=" + bytesSent/1000 + " KB rate=" + df.format(rate/1000.0) + " Mbps");
			
		}
}
