
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.io.IOException;
import java.net.UnknownHostException;

public class Iperfer {

	public static void main(String[] args) {
		// TODO Auto-generated method stub
		String hostname = "localhost";
		int portNum = 0;
		double time = 0;
		String type = "";
		
		if(args.length == 0) {
			System.out.println("Error: missing or additional arguments");
		}
		
		List<String> arG = Arrays.asList(args);
		
		type = args[0];
		
		// Parsing param 
		
		for(int i = 1; i < args.length; i ++) {
			if(args[i].equals("-h")) {
				hostname = args[i+1];
				i++;
			} else if (args[i].equals("-p")) {
				portNum = Integer.parseInt(args[i+1]);
				i ++;
				if(portNum < 1024 || portNum > 65535) {
					System.out.println("Error: port number must be in the range 1024 to 65535");
					return;
				}
			} else if (args[i].equals("-t")) {
				time = Double.parseDouble(args[i+1]);
				i++;
				if(time < 0) {
					System.out.println("Error: Time should be >= 0");
					return;
				}
			} else {
				System.out.println("Error: Invalid Option " + args[i]);
				return;
			}
		}
		
		// Modes
		
		try {
		
			if(type.equals("-s") && args.length == 3 && args[1].equals("-p")) {
				Server server = new Server(portNum);
				server.listen();
			} else if (type.equals("-c") && args.length == 7 && arG.contains("-h") && arG.contains("-p") && arG.contains("-t")) {
				Client client = new Client(hostname, portNum);
				client.send(time);
			} else {
				System.out.println("Error: missing or additional arguments");
				return;
			}
			
		} catch (IOException e) {
			e.printStackTrace();
		}
			

	}

}
