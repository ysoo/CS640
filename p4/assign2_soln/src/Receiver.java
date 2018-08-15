import java.io.*;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.nio.ByteBuffer;
import java.util.*;
import java.util.concurrent.*;
import java.net.InetAddress;
import java.net.SocketException;

public class Receiver {
	private int port;
	private int mtu;
	private int sws;
	private DatagramSocket listenSk, sendSk;
	private int prevSeqNum;
	private int nextSeqNum;
	private int seqNum;
	private int nextAck;
	private int prevAck;
	private int nextBaseAck;
	private boolean finishReceiving;
	private boolean connected;
	private int payLoadSize;
	private Timer timer;
	private Semaphore s;
	private int timeOutVal;

	private static final int SYN = 2;
	private static final int FIN = 1;
	private static final int ACK = 0;

	private boolean running = true; // wtf is this for, deletable?

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
		nextBaseAck = nextAck;

	}

	public void rcvUpdate(byte[] packet) {
		int lengthWFlags = getLengthWFlags(packet);
		int length = getLength(lengthWFlags);
		int aFlag = getBit(lengthWFlags, ACK);
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
	}

	public Receiver(int port, int mtu, int sws) {
		this.port = port;
		this.mtu = mtu;
		this.sws = sws;
		System.out.println("Receiver: serverPort = " + port);

		this.prevSeqNum = -1;
		this.seqNum = 0;
		this.nextAck = 0;
		this.nextSeqNum = 0;
		this.prevAck = -1;
		this.nextBaseAck = 0;
		this.finishReceiving = false;
		this.timeOutVal = 30000;
		this.connected = false;
		this.payLoadSize = mtu - 24;
		this.s = new Semaphore(1);

		try {
			this.listenSk = new DatagramSocket(port);
			this.sendSk = new DatagramSocket();
			System.out.println("Receiver: Listening");
			try {
				byte[] incomingData = new byte[mtu];
				DatagramPacket incomingPacket = new DatagramPacket(incomingData, incomingData.length);

				// Make file shit

				while(!finishReceiving) {
					// receive packet
					listenSk.receive(incomingPacket);
					// checkSum check
					int ackCheckNum = ackChecker(incomingData);
					if(ackCheckNum != -1) {
						int destPort = incomingPacket.getPort();
						InetAddress destAddr = incomingPacket.getAddress();

						int lengthWFlags = getLengthWFlags(incomingData);
						int length = getLength(lengthWFlags);
						int sFlag = getBit(lengthWFlags, SYN);
						int fFlag = getBit(lengthWFlags, FIN);
						printPacket(incomingData, true);
						rcvUpdate(incomingData);
							// syn fin data
							if(isFlagOrData(incomingData)) {
									ArrayList<Integer> flagBits = new ArrayList<>();
									if(length > 0) {
										if(connected) {
											if(nextAck + payLoadSize < nextBaseAck + sws && length == payLoadSize) {
												 continue; //build file here
											} else {
												flagBits.add(ACK);
												byte[] ackPkt = generatePacket(seqNum, new byte[0], flagBits);
												printPacket(ackPkt, false);
												listenSk.send(new DatagramPacket(ackPkt, ackPkt.length, destAddr, destPort));
												sndUpdate(ackPkt);
											}
										}
									} else {
										// syn or fin
										if(sFlag == 1){
											flagBits.add(SYN);
										}
										if(fFlag == 1) {
											flagBits.add(FIN);
										}
										// mayday, cumlative data before sending back ack
										flagBits.add(ACK);
										byte[] ackPkt = generatePacket(nextSeqNum, new byte[0], flagBits);
										printPacket(ackPkt, false);
										listenSk.send(new DatagramPacket(ackPkt, ackPkt.length, destAddr, destPort));
										if(prevAck + 1 == nextSeqNum) setTimer(true);
										sndUpdate(ackPkt);
									}
							}
							// ack for FIN or SYN last ack reply for datapakcet
							else {
								if(nextSeqNum == 1) {
									connected = true;
								} else if(nextSeqNum == 2) {
									connected = false;
									finishReceiving = true;
								}

							}
					}
					// send dup ack, out of order
					else {
						int destPort = incomingPacket.getPort();
						InetAddress destAddr = incomingPacket.getAddress();
						ArrayList<Integer> flagBits = new ArrayList<>();
						flagBits.add(ACK);
						byte[] ackPkt = generatePacket(seqNum, new byte[0], flagBits);
						printPacket(ackPkt, false);
						listenSk.send(new DatagramPacket(ackPkt, ackPkt.length, destAddr, destPort));
						sndUpdate(ackPkt);
					}

				}
			} catch (Exception e) {
				e.printStackTrace();
				System.exit(-1);
			} finally {
			}
		} catch(Exception e) {
			e.printStackTrace();
			System.exit(1);
		}
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

}
