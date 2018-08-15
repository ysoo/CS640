import java.io.*;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.nio.ByteBuffer;
import java.util.*;
import java.util.concurrent.*;
import java.net.InetAddress;
import java.net.SocketException;

public class Sender {
	private DatagramSocket serverSk;
	private int myPort;
	private InetAddress otherIP; // deletable
	private int destPort;
	private String fileName;
	private int mtu;
	private int sws;
	private List<byte[]> data; // deletable?

	private String destAddr;
	private Semaphore s;

	private int seqNum;
	private int nextSeqNum;

	private int nextAck; // maybe needed to keep track of previous ack
	private int prevAck;

	private HashMap<Integer, byte[]> packetsMap;

	// Timer Task coupled with Threading Class
	private Timer timer;
	private boolean finishSending;
	private boolean finalPacket;

	// mtu
	private int payLoadSize;

	// sws
	private int sWindowSize;

	// Timeout Computation 2.2
	private int timeOutVal;

	// connection
	private boolean connected;

	private static final int SYN = 2;
	private static final int FIN = 1;
	private static final int ACK = 0;






	public Sender(int myPort, String destAddr, int destPort, String fileName, int mtu, int sws) {
		this.myPort = myPort;
		this.destAddr = destAddr;
		this.destPort = destPort;
		this.fileName = fileName;
		this.mtu = mtu;
		this.sws = sws;
		this.seqNum = 0;
		this.nextAck = 0;
		this.prevAck = -1;
		this.nextSeqNum = 0;
		this.packetsMap = new HashMap<>(sws);
		this.finishSending = false;
		this.connected = false;
		this.finalPacket = false;
		// Header of packet = 4 + 4 + 8 + 4 + 2 + 2 = 24 bytes
		this.payLoadSize = mtu - 4 - 4 - 8 - 4 - 2 - 2; // max transmission Unite - seqNum - ackNum - timestamp - lengthWFlags - zeroes - checksum
		this.sWindowSize = sws;
		this.timeOutVal = 30000; // Calculate this fucking shit;

		this.s = new Semaphore(1);

		System.out.println("Sender: myPort = " + myPort + " destPort = " + destPort);



		try {
			this.serverSk = new DatagramSocket(myPort);
			// Add Threads to run Datagram Socket;
			System.out.println("Sender: Server socket bind succesful to port = " + myPort);

		} catch (Exception e) {
			e.printStackTrace();
			System.exit(-1);
		}

		try {
			InThread inTh = new InThread();
			OutThread outTh = new OutThread();
			inTh.start();
			outTh.start();
			// Start listening to Any incoming packet
			System.out.println("Sender: Started Listening at Server with port = " + myPort);
		} catch (Exception e) {
			e.printStackTrace();
			System.exit(-1);
		}

		try {
			// Initialize ThreeWay HandShake
			s.acquire();
			ArrayList<Integer> flagBits = new ArrayList<>();
			flagBits.add(SYN);
			byte[] dataToSend = generatePacket(nextSeqNum, new byte[0], flagBits);
			DatagramPacket packetToSend = new DatagramPacket(dataToSend, dataToSend.length, InetAddress.getByName(destAddr), destPort);
			serverSk.send(packetToSend);
			packetsMap.put(nextSeqNum, dataToSend);
			printPacket(dataToSend, false);
			sndUpdate(dataToSend);
			s.release();

		} catch (Exception e) {

			e.printStackTrace();
			System.exit(-1);
		}
	}

	public boolean isFlagOrData(byte[] packet) {
		int lengthWFlags = getLengthWFlags(packet);
		int length = getLength(lengthWFlags);
		int sFlag = getBit(lengthWFlags, SYN);
		int fFlag = getBit(lengthWFlags, FIN);
		int aFlag = getBit(lengthWFlags, ACK);

		return (sFlag == 1 || fFlag == 1 || length > 0);
	}

	public void sndUpdate(byte[] packet) {
		int lengthWFlags = getLengthWFlags(packet);
		int length = getLength(lengthWFlags);
		if(isFlagOrData(packet)) {
			seqNum = nextSeqNum;
			if(length > 0) {
				nextSeqNum = seqNum + length;
			} else {
				nextSeqNum = seqNum + 1;
			}
		}

	}

	public void rcvUpdate(byte[] packet) {
		int lengthWFlags = getLengthWFlags(packet);
		int length = getLength(lengthWFlags);
		int aFlag = getBit(lengthWFlags, ACK);
		int sFlag = getBit(lengthWFlags, SYN);
		int fFlag = getBit(lengthWFlags, FIN);
		int inSeqNum = getSeqNum(packet);
		if(isFlagOrData(packet)) {
			if(length > 0) {
				nextAck = inSeqNum + length;
			} else {
				nextAck = inSeqNum + 1;
			}
		}
		if(aFlag == 1) {
			prevAck = getAckNum(packet) - 1;
			setTimer(false);
		}

		if(sFlag == 1) {
			connected = true;
		}

		if(fFlag == 1) {
			connected = false;
			finishSending = true;
		}

	}

	// function to set timer
	public void setTimer(boolean newTimer) {
		if(timer != null) timer.cancel();
		if(newTimer) {
			timer = new Timer();
			timer.schedule(new Timeout(), timeOutVal);
		}
	}

	// class for timer if timeout
	public class Timeout extends TimerTask{
		public void run(){
			try{
				// acquire lock?
				s.acquire();
				nextSeqNum = prevAck + 1;
				// release lock
				s.release();

			} catch(Exception e) {
				e.printStackTrace();
			}
		}
	}

	// function to copy temporary buffer into new buffer with adjusted length
	public byte[] copyOfRange(byte[] srcArr, int start, int end) {
		int length = (end > srcArr.length) ? srcArr.length - start : end - start;
		byte[] newArr = new byte[length];
		System.arraycopy(srcArr, start, newArr, 0, length);
		return newArr;
	}

	// function to generate packet with sequNum, ackNum, timestamp, lengthWflags, zeroes (16 bits), checksum, currentByteBuffer
	// header bytes = 24 bytes
	// seqNum = 0 to 4
	// ackNum = 4 to 8
	// timstamp = 8 to 16
	// lengthWflags = 16 to 20
	// zeroes = 20 to 22
	// chekcsum = 22 to 24
	public byte[] generatePacket(int seqNum, byte[] dataBytes, ArrayList<Integer> flagBits) {
		// seqNum
		byte[] seqNumBytes = ByteBuffer.allocate(4).putInt(seqNum).array();
		// acknowledgement : next byte expected in reverse direction
		byte[] ackNumBytes = ByteBuffer.allocate(4).putInt(nextAck).array();
		// timestamp
		long currTimeStamp = System.nanoTime();
		byte[] timeStampBytes = ByteBuffer.allocate(8).putLong(currTimeStamp).array();
		// length, SYN, ACK, FIN, SYN bit = 2, FIN bit = 1, ACK bit = 0
		int length = dataBytes.length;
		int lengthWFlags = length << 3;
		for(int flagBit: flagBits) {
			int mask = 1 << flagBit;
			lengthWFlags = lengthWFlags | mask;
		}
		byte[] lengthWFlagsBytes = ByteBuffer.allocate(4).putInt(lengthWFlags).array();
		// zeroes
		byte[] zeroes = new byte[2];
		// calculate checksum ???
		short checkSum = 0; // use checksum func here
		byte[] checkSumBytes = ByteBuffer.allocate(2).putShort(checkSum).array();
		// currByteBuffer
		ByteBuffer packetBuff = ByteBuffer.allocate(4 + 4 + 8 + 4 + 2 + 2 + dataBytes.length);

		// seqNum
		packetBuff.put(seqNumBytes);
		// ackNum
		packetBuff.put(ackNumBytes);
		// timeStamp
		packetBuff.put(timeStampBytes);
		// lengthWFlags
		packetBuff.put(lengthWFlagsBytes);
		// zeroes
		packetBuff.put(zeroes);
		// checkSum
		packetBuff.put(checkSumBytes);
		// dataBytes
		packetBuff.put(dataBytes);
		return packetBuff.array();

	}

	public void printPacket(byte[] packet, boolean receive) {
		StringBuilder sb = new StringBuilder();
		if(receive) {
			sb.append("rcv");
		} else {
			sb.append("snd");
		}

		sb.append(" " + System.nanoTime());

		int lengthWFlags = getLengthWFlags(packet);
		int length = getLength(lengthWFlags);
		int sFlag = getBit(lengthWFlags, SYN);
		int fFlag = getBit(lengthWFlags, FIN);
		int aFlag = getBit(lengthWFlags, ACK);
		sb.append(" ");
		if(sFlag == 1) {
			sb.append("S");
		} else {
			sb.append("-");
		}
		sb.append(" ");
		if(aFlag == 1) {
			sb.append("A");
		} else {
			sb.append("-");
		}
		sb.append(" ");
		if(fFlag == 1) {
			sb.append("F");
		} else {
			sb.append("-");
		}
		sb.append(" ");
		if(length > 0) {
			sb.append("D");
		} else {
			sb.append("-");
		}


		int seqNum = getSeqNum(packet);
		sb.append(" " + seqNum);

		sb.append(" " + length);

		int ackNum = getAckNum(packet);
		sb.append(" " + ackNum);


		System.out.println(sb.toString());
	}

	// header bytes = 24 bytes
	// seqNum = 0 to 4
	public int getSeqNum(byte[] pkt) {
		byte[] receivedSeqNumBytes = copyOfRange(pkt, 0, 4);
		return ByteBuffer.wrap(receivedSeqNumBytes).getInt();
	}

	// ackNum = 4 to 8
	public int getAckNum(byte[] pkt) {
		byte[] receivedAckNumBytes = copyOfRange(pkt, 4, 8);
		return ByteBuffer.wrap(receivedAckNumBytes).getInt();
	}

	// timstamp = 8 to 16
	public long getTimeStamp(byte[] pkt) {
		byte[] receivedTimeStampBytes = copyOfRange(pkt, 8, 16);
		return ByteBuffer.wrap(receivedTimeStampBytes).getLong();
	}

	// lengthWflags = 16 to 20
	public int getLengthWFlags(byte[] pkt) {
		byte[] receivedLengthWFlagsBytes = copyOfRange(pkt, 16, 20);
		return ByteBuffer.wrap(receivedLengthWFlagsBytes).getInt();
	}

	// zeroes = 20 to 22
	public short getZeroes(byte[] pkt) {
		byte[] receivedZeroesBytes = copyOfRange(pkt, 20, 22);
		return ByteBuffer.wrap(receivedZeroesBytes).getShort();
	}

	// checksum = 22 to 24
	public short getCheckSumNum(byte[] pkt) {
		byte[] receivedCheckSumBytes = copyOfRange(pkt, 22, 24);
		return ByteBuffer.wrap(receivedCheckSumBytes).getShort();
	}

	// function use to get flag bits
	public int getBit(int n, int k) {
		return (n >> k) & 1;
	}

	// function to get length
	public int getLength(int n) {
		return n >> 3;
	}



	public class InThread extends Thread {

		public InThread() {
			// empty construct?
		}

		// return -1 if failed (duplicate), else return ackNum
		public int ackChecker(byte[] packet) {
			int ackNum = getAckNum(packet);
			if(ackNum != nextSeqNum) {
				return -1;
			}

			if(isFlagOrData(packet)) {
				int seqNum = getSeqNum(packet);
				if(seqNum != nextAck) {
					return -1;
				}
			}

			return ackNum;

		}

		public void run() {
			try {
				byte[] incomingData = new byte[24]; // fixed calculations* only stores header
				DatagramPacket incomingPacket = new DatagramPacket(incomingData, incomingData.length);

				try {
					// while not finish sending
					while(!finishSending) {
						serverSk.receive(incomingPacket);
						// checksum check
						int ackCheckNum = ackChecker(incomingData);
						if(ackCheckNum != -1) {
							// print packet
							printPacket(incomingData, true);
							int inSeqNum = getSeqNum(incomingData);
							if(isFlagOrData(incomingData)) {
								// update next ack
							  rcvUpdate(incomingData);
								// reply
								ArrayList<Integer> flagBits = new ArrayList<>();
								flagBits.add(ACK);
								byte[] ackPkt = generatePacket(seqNum, new byte[0], flagBits);
								serverSk.send(new DatagramPacket(ackPkt, ackPkt.length, InetAddress.getByName(destAddr), destPort));
								printPacket(ackPkt, false);
							}

							// ack only
							else {
								// updating prev ack
								prevAck = ackCheckNum - 1;
								setTimer(false);
								if(finalPacket) {
									try {
										// Finalize ThreeWay HandShake
										ArrayList<Integer> flagBits = new ArrayList<>();
										flagBits.add(FIN);
										byte[] dataToSend = generatePacket(nextSeqNum, new byte[0], flagBits);
										DatagramPacket packetToSend = new DatagramPacket(dataToSend, dataToSend.length, InetAddress.getByName(destAddr), destPort);
										serverSk.send(packetToSend);
										packetsMap.put(nextSeqNum, dataToSend);
										printPacket(dataToSend, false);
										sndUpdate(dataToSend);

									} catch (Exception e) {

										e.printStackTrace();
										System.exit(-1);
									}
								}
							}
						}
						// receive dup ack uhoh
						else {
							printPacket(incomingData, true);
							int inAckNum = getAckNum(incomingData);
							if(inAckNum < nextSeqNum ) {
								nextSeqNum = inAckNum;
							}
						}
					}

				} catch (Exception e) {
					e.printStackTrace();
				} finally {
				}
			} catch (Exception e) {
				e.printStackTrace();
				System.exit(-1);
			}
		}
	}

	public class OutThread extends Thread {
		private InetAddress dstAddr;

		public OutThread() {
		}

		public void run() {

			try {
				this.dstAddr = InetAddress.getByName(destAddr);
				FileInputStream fileIS = new FileInputStream(new File(fileName));

				try {
					// if all packets are sent
					while(!finishSending){
						byte[] dataToSend = new byte[sWindowSize];
						// establish connection
						if(connected && !finalPacket) {
							// when window is not yet full
							if(nextSeqNum + payLoadSize < prevAck + 1 + sWindowSize) {
								s.acquire();

								// need locks?
								if(prevAck + 1 == nextSeqNum) setTimer(true);;

								// check if packet already exists in packetsMap??
								if(nextSeqNum < packetsMap.size()) {
									dataToSend = packetsMap.get(nextSeqNum);
								}

								// if first packet
								if (nextSeqNum == 1) {
									// fileName in literal bytes
									byte[] fileNameBytes = fileName.getBytes();
									// Integer value for length of fileName
									byte[] fileNameLengthBytes = ByteBuffer.allocate(4).putInt(fileNameBytes.length).array();
									// temporary buffer, size = payloadsize
									byte[] dataBuffer = new byte[payLoadSize]; // mtu for payload size

									// read file from 0 to actual bytes to read and store in databuffer, return length of data
									// datafile that can read = payloadsize - int value of fileName length - fileName bytes literal size
									int dataLength = fileIS.read(dataBuffer,  0, payLoadSize - 4 - fileNameBytes.length);

									// transfer tmp databuffer to new buffer with actual adjusted size = payload size - int - filenamesize
									byte[] dataBytes = copyOfRange(dataBuffer, 0, dataLength);

									// Bybtebuffer with payloadsize = 4 + sizeoffilename + databyteslength
									ByteBuffer BB = ByteBuffer.allocate(4 + fileNameBytes.length + dataBytes.length);
									// put int from 0 to 4
									BB.put(fileNameLengthBytes);
									// put filename bytes literal from 4 to  (4 + filenamesize)
									BB.put(fileNameBytes);
									// put databytes from (4 + fileanmesize) to (4 + filenamesize + datasize)
									BB.put(dataBytes);

									// generate the packet with sequNum, acknowledgement, timestamp, acknoledgetimestamp, length, SYN, FIN, ACK, zeroes (16 bits),
									// checksum, currentByteBuffer
									ArrayList<Integer> flagBits = new ArrayList<>();
									dataToSend = generatePacket(nextSeqNum, BB.array(), flagBits);
								}

								// other packets
								else {

									// temporary buffer
									byte[] dataBuffer = new byte[payLoadSize];

									// store data in temporary buffer
									int dataLength = fileIS.read(dataBuffer, 0, payLoadSize);

									// check if there is still data to send
									if(dataLength == -1) {
										ArrayList<Integer> flagBits = new ArrayList<>();
										dataToSend = generatePacket(seqNum, new byte[0], flagBits);
										finalPacket = true;
									}
									// valid data
									else {
										// copy to new buffer with adjusted length, for edge case last few bytes < payloadsize
										byte[] dataBytes = copyOfRange(dataBuffer, 0, dataLength);
										ArrayList<Integer> flagBits = new ArrayList<>();
										if(dataBytes.length < payLoadSize) finalPacket = true;
										dataToSend = generatePacket(nextSeqNum, dataBytes, flagBits);
									}
								}

								// send packet
								serverSk.send(new DatagramPacket(dataToSend, dataToSend.length, dstAddr, destPort));
								printPacket(dataToSend, false);
								packetsMap.put(nextSeqNum, dataToSend);
								sndUpdate(dataToSend);

								// close ThreadsS?????
								s.release();
							}
							// Sleep thread if window id full????

						}
					}
				} catch (Exception e) {
					e.printStackTrace();
				} finally {
					setTimer(false);
				}

			} catch (Exception e) {
				e.printStackTrace();
				System.exit(-1);
			}
		}
	} // END CLASS OUT THREAD

	public int onesComp(int n) {
		int bitnum = (int)(Math.floor(Math.log(n) / Math.log(2))) + 1;
		return ((1 << bitnum) -1) ^ n;
	}

}

