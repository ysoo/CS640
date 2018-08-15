import java.io.*;


public class TCPend {
	private static final int DEFAULT_PORT = 8888;

	public static void main(String[] args) {
		String filename = null;
		String remoteIP = null;
		int remotePort = DEFAULT_PORT;
		int port = DEFAULT_PORT;
		int MTU = 0;
		int sws = 0;
		if(args.length == 12) {
			for(int i = 0; i < args.length; i++) {
				String arg = args[i];
				if(arg.equals("-p")) {
					port = Integer.parseInt(args[++i]);
				} else if (arg.equals("-s")) {
					remoteIP = args[++i];
				} else if (arg.equals("-a")) {
					remotePort = Integer.parseInt(args[++i]);
				} else if (arg.equals("-f")) {
					filename = args[++i];
				} else if (arg.equals("-m")) {
					MTU = Integer.parseInt(args[++i]);
				} else if (arg.equals("-c")) {
					sws = Integer.parseInt(args[++i]);
				} else {
					usage();
					return;
				}
			}
			Sender send = new Sender(port, remoteIP, remotePort, filename, MTU, sws);
		} else if (args.length == 6) {
			for(int i = 0; i < args.length; i++) {
				String arg = args[i];
				if(arg.equals("-p")) {
					port = Integer.parseInt(args[++i]);
				} else if(arg.equals("-m")) {
					MTU = Integer.parseInt(args[++i]);
				} else if(arg.equals("-c")) {
					sws = Integer.parseInt(args[++i]);
				} else {
					usage();
					return;
				}
			}
			Receiver receive = new Receiver(port, MTU, sws);
		} else {
			usage();
			return;
		}
	}


	static void usage() {
		System.out.println("Tranmission Control Protocol (TCP)");
		System.out.println("Transfer Initiator");
		System.out.println("TCPend [-p port] [-s remote IP] [-a remote port] [-f filename] [-m mtu] [-c sws]");
		System.out.println("Remote Process");
		System.out.println("TCPend [-p port] [-m mtu] [-c sws]");


	}
}


