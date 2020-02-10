// Sender
// Written by Yubai Liu for Comp9331 Assignment

import java.io.*;
import java.net.*;
import java.util.*;
import java.nio.*;
import java.util.AbstractMap.SimpleEntry;


public class Receiver {
	//InetAddress receiverIp;
	//int receiverPort;
	//File sendFile;
	//byte[] fileInBytes;
	//long MWS;
	//long MSS;
	public static int seq;
	public static int ack;
	public static InetAddress senderIp;
	public static int senderPort;
	public static int extraData;
	public static int totalSegRcv;
	public static int numOfCorrRcv;
	public static int numOfDupRcv;
	public static int numOfDupAck;
	public static FileOutputStream logOutput;
	
	public static void main(String[] args) throws Exception {
		/*
		if (args.length != 1){
			System.out.println("Number of given arguments are not correct");
         	return;
		}
		*/
		// set des port
		int receiverPort = Integer.parseInt(args[0]);
		// set file to hold received data
		File receivedFile = new File(args[1]);
		FileOutputStream fileOutput = new FileOutputStream(receivedFile);
		File Receiver_log = new File("Receiver_log.txt");
		logOutput = new FileOutputStream(Receiver_log);
		// set start time
		long startTime = System.currentTimeMillis();
		// init seq_number
		seq = 0;
		// init ack_number
		ack = 0;
		//
		extraData = 0;
		totalSegRcv = 0;
		numOfCorrRcv = 0;
		numOfDupRcv = 0;
		numOfDupAck = 0;
		DatagramSocket receiverSocket = new DatagramSocket(receiverPort);
		//int TimeoutInterval = 1000;
		//receiverSocket.setSoTimeout(TimeoutInterval);
		connectionEstablishment(receiverSocket, startTime);
		ArrayList<SimpleEntry<Integer, byte[]>> packetArray = dataReceiving(receiverSocket, startTime);
		connectionTermination(receiverSocket, startTime);
		receiverSocket.close();
		// sort the outOfOrderPackect
		Collections.sort(packetArray, keyComparator);
		byte[] dataR = linkAllDataPortion(packetArray);
		fileOutput.write(dataR);
		int dataLength = dataR.length;
		fileOutput.flush();
		fileOutput.close();
		summaryPrinter(dataLength);
		logOutput.close();
	}

	private static void summaryPrinter(int dataLength)throws Exception{
		String summary = "=============================================================" + "\n"
					   + "Amount of data received (bytes)            \t\t" + (dataLength + extraData) + "\n"
					   + "Total Segments Received                    \t\t" + totalSegRcv + "\n"
					   + "Data segments received                     \t\t" + (totalSegRcv - 4) + "\n"
					   + "Data segments with Bit Errors              \t\t" + numOfCorrRcv + "\n"
					   + "Duplicate data segments received           \t\t" + numOfDupRcv + "\n"
					   + "Duplicate ACKs sent                        \t\t" + numOfDupAck + "\n"
					   + "=============================================================";
		//System.out.println(summary);
		byte[] summaryInBytes = summary.getBytes();
		logOutput.write(summaryInBytes);
		logOutput.flush();
	}

	private static void connectionEstablishment(DatagramSocket socket, long startTime) throws Exception{
		// array string for received head string
		int[] headReceivedInInts = new int[10];
		// array string for received head string
		int[] headSentInInts = new int[10];
		// Create a datagram packet waiting for packet from sender
		DatagramPacket senderPacket = new DatagramPacket(new byte[1024], 1024);
		// loop for waiting SYN
		while(true){
			// Block until the host receives a packet from sender
			socket.receive(senderPacket);
			totalSegRcv++;
			senderIp = senderPacket.getAddress();
			senderPort = senderPacket.getPort();
			byte[] headReceivedInBytes = Arrays.copyOfRange(senderPacket.getData(), 0, 20);
			// convert the byte array to int array
			headReceivedInInts = toIntArray(headReceivedInBytes);
			logPrinter(headReceivedInInts, 1, startTime);
			if(headReceivedInInts[0] == 0){ // type == 0 : SYN 
				// SYN segment received stop waiting for SYN
				// set ack for next segment SA
				ack = 1;
				break;
			}

		}
		// start send SA to sender
		// build SA segment
		headSentInInts = headInIntsBuilder(1, seq, 0, ack, 0);
		byte[] headSentInBytes = toByteArray(headSentInInts); 
		// set the packet need to be send in receiver side 
		DatagramPacket receiverPacket = new DatagramPacket(headSentInBytes, headSentInBytes.length, senderIp, senderPort);
		// send SA
		// when A from reciver not received continue to send SA
		socket.send(receiverPacket);
		logPrinter(headSentInInts, 0, startTime);
		while(true){
			// block until receive packet from sender or timeout
			socket.receive(senderPacket);
			totalSegRcv++;
			byte[] headReceivedInBytes = Arrays.copyOfRange(senderPacket.getData(), 0, 20);
			// convert the byte array to int array
			headReceivedInInts = toIntArray(headReceivedInBytes);
			logPrinter(headReceivedInInts, 1, startTime);
			if(headReceivedInInts[0] == 2){ //type == 2: ACK
				// A received
				break;
			}
			else{// send SA
				socket.send(receiverPacket);
				logPrinter(headSentInInts, 0, startTime);
			}
		}
		seq = 1;
	}

	private static ArrayList<SimpleEntry<Integer, byte[]>> dataReceiving(DatagramSocket socket, long startTime)throws Exception{
		// create an arraylist to store the packets which are out of order
		ArrayList<SimpleEntry<Integer, byte[]>> outOfOrderPackect = new ArrayList<SimpleEntry<Integer, byte[]>>();
		// create a set for seq received
		Set<Integer> setOfSeqReceievd =  new HashSet<Integer>();  
		// int array for received head 
		int[] headReceivedInInts = new int[10];
		// int array for sent head 
		int[] headSentInInts = new int[10];
		// create a packet for hold segments from sender
		DatagramPacket senderPacket = new DatagramPacket(new byte[1024], 1024);
		// set a segment type for received packet
		int segmentType = 3;
		//
		int newMss = 0;
		//
		int lastSeq = 0;
		headSentInInts = headInIntsBuilder(2, seq, 0, ack, 0);
		byte[] headSentInBytes = toByteArray(headSentInInts); 
		// create a packet used by receiver for send segments to sender
		DatagramPacket receiverPacket = new DatagramPacket(headSentInBytes, headSentInBytes.length, senderIp, senderPort);
		// if segmentType is not F continue processing
		while(true){
			// block until receive packet from sender
			socket.receive(senderPacket);
			totalSegRcv++;
			byte[] headReceivedInBytes = Arrays.copyOfRange(senderPacket.getData(), 0, 20);
			// convert the byte array to int array
			headReceivedInInts = toIntArray(headReceivedInBytes);
			segmentType = headReceivedInInts[0];
			if(!setOfSeqReceievd.contains(headReceivedInInts[1])){ // seq is not received before
				if(segmentType == 3){
					// segment type is data
					// get data portion in segement
					byte[] dataPortion = Arrays.copyOfRange(senderPacket.getData(), 20, 20 + headReceivedInInts[2]);
					int checksum = computeCheckSum(dataPortion);
					if(headReceivedInInts[4] == checksum){// checksum in head is equal to checksum just compute, payload no corrupt
						// event: rcv
						logPrinter(headReceivedInInts, 1, startTime);
						// add seq into setOfSeqReceievd, only the segment not dup, type is data, data not curr will add it seq
						setOfSeqReceievd.add(headReceivedInInts[1]);
						// when mss change, record this new Mss
						// only the first segment and last segment will get into this codition.
						if(headReceivedInInts[2] != newMss && headReceivedInInts[1] > lastSeq){
							lastSeq = headReceivedInInts[1];
							newMss = headReceivedInInts[2];
						}
						// get data in segment and asking to next segment
						// set a pairPacke <seq, dataPortion>, this sturcture can easy be sort
						SimpleEntry<Integer, byte[]> pairPacket = new SimpleEntry<Integer, byte[]>(headReceivedInInts[1], dataPortion);
						outOfOrderPackect.add(pairPacket);
						if(headReceivedInInts[1] == ack){ // asking packect received, try to update ack
							// set new ack, new ack = ack + numOfBytes in segment, seq in receiver set do not change during data transmission
							ack = ack + headReceivedInInts[2];
							// last ack could be already received check the setOfSeqReceievd to update ack
							while(setOfSeqReceievd.contains(ack)){
								if(ack == lastSeq){
									// the length of segment with last seq is diff with other
									ack = ack + newMss;
								}else{
									// 
									ack = ack + headReceivedInInts[2];
								}
							}
							// build next ACK segment in int array, only ack need to be changed
							headSentInInts[3] = ack;
							headSentInBytes = toByteArray(headSentInInts);
							receiverPacket.setData(headSentInBytes);
							socket.send(receiverPacket);
							// event: snd
							logPrinter(headSentInInts, 0, startTime);
						}else{// a packet is not receiver asking for  
							// send last ack again
							numOfDupAck++;
							socket.send(receiverPacket);
							// event: snd/DA
							logPrinter(headSentInInts, 2, startTime);
						}
					}else{
						// event: rcv/corr
						extraData += headReceivedInInts[2];
						numOfCorrRcv++;
						logPrinter(headReceivedInInts, 3, startTime);
					}
				}else{
					// event: rcv
					logPrinter(headReceivedInInts, 1, startTime);
					// segment type is F, dataReceiving finished
					// set new ack by ack + 1
					ack = ack + 1;
					break;
				}
			}else{// a packet that already received before
				// event: rcv
				extraData += headReceivedInInts[2];
				numOfDupRcv++;
				logPrinter(headReceivedInInts, 1, startTime);
				// send last ack again
				numOfDupAck++;
				socket.send(receiverPacket);
				// event: send/DA
				logPrinter(headSentInInts, 2, startTime);
			}
		}
		return outOfOrderPackect;
	}

	private static void connectionTermination(DatagramSocket socket, long startTime) throws Exception{
		socket.setSoTimeout(1000);
		// array string for received head string
		int[] headReceivedInInts = new int[10];
		// array string for received head string
		int[] headSentInInts = new int[10];
		// start send A to sender
		// build A segment
		headSentInInts = headInIntsBuilder(2, seq, 0, ack, 0);
		byte[] headSentInBytes = toByteArray(headSentInInts); 
		// set the packet need to be send in receiver side 
		DatagramPacket receiverPacket = new DatagramPacket(headSentInBytes, headSentInBytes.length, senderIp, senderPort);
		// send A
		socket.send(receiverPacket);
		logPrinter(headSentInInts, 0, startTime);
		// build F segment, only need to change type
		headSentInInts[0] = 4;
		headSentInBytes = toByteArray(headSentInInts);
		receiverPacket.setData(headSentInBytes);
		// send F
		socket.send(receiverPacket);
		logPrinter(headSentInInts, 0, startTime);

		//loop for waiting for A segment from Sender
		// create a packet for hold segments from sender
		DatagramPacket senderPacket = new DatagramPacket(new byte[1024], 1024);
		while(true){
			// block until receive packet from sender 
			socket.receive(senderPacket);
			totalSegRcv++;
			byte[] headReceivedInBytes = Arrays.copyOfRange(senderPacket.getData(), 0, 20);
			// convert the byte array to int array
			headReceivedInInts = toIntArray(headReceivedInBytes);
			logPrinter(headReceivedInInts, 1, startTime);
			if(headReceivedInInts[0] == 2){ //type == 2: ACK
				// A received
				break;
			}
		}
	}

	private static int[] headInIntsBuilder(int type, int seq, int numOfBytes, int ack, int checksum){
		/*  
			head is consist by int[5] with {type, seq, numOfBytes, ack, checksum}
			type = 0: SYN
			type = 1: SYNACK
			type = 2: ACK
			type = 3: Data
			type = 4: F
		*/
		int[] head = {type, seq, numOfBytes, ack, checksum};
		return head;
	}

	private static byte[] toByteArray(int[] headInInts){
		ByteBuffer bytebuf = ByteBuffer.allocate(headInInts.length * 4).order(ByteOrder.LITTLE_ENDIAN);
		bytebuf.asIntBuffer().put(headInInts);
		return bytebuf.array();
	}

	private static int[] toIntArray(byte[] headInBytes){
		ByteBuffer bytebuf = ByteBuffer.wrap(headInBytes).order(ByteOrder.LITTLE_ENDIAN);
		int [] headInInts = new int[headInBytes.length / 4];
		bytebuf.asIntBuffer().get(headInInts);
		return headInInts;
	}

	private static byte[] connectBytes(byte[] byte1, byte[] byte2){
		byte[] newBytes = new byte[byte1.length + byte2.length];
		System.arraycopy(byte1, 0, newBytes, 0, byte1.length);
        System.arraycopy(byte2, 0, newBytes, byte1.length, byte2.length);
        return newBytes;  
	}

	private static float getCurrTime(long startTime){
		return (float) (System.currentTimeMillis() - startTime) / 1000;
	}

	private static void logPrinter(int[] headInInts, int event, long startTime) throws Exception{
		// print event
		String log = " ";
		switch(event){
			case 0: 
				log = "snd\t\t";
				//System.out.print("snd ");
				break;
			case 1: 
				log = "rcv\t\t";
				//System.out.print("rcv ");
				break;
			case 2:
				log = "snd/DA\t\t"; 
				//System.out.print("rcv/DA ");
				break;
			case 3:
				log = "rcv/corr\t"; 
				//System.out.print("rcv/DA ");
				break;
		}
		// print time
		log = log + (getCurrTime(startTime) + "\t\t");

		switch(headInInts[0]){
			case 0:
				log = log +  "S\t";
				//System.out.print("S  ");
				break;
			case 1: 
				log = log +  "SA\t";
				//System.out.print("SA  ");
				break;
			case 2: 
				log = log +  "A\t";
				//System.out.print("A  ");
				break;
			case 3:
				log = log +  "D\t" ;
				//System.out.print("D  ");
				break;
			case 4:
				log = log +  "F\t"; 
				//System.out.print("F  ");
				break;

		}
		// print seq, numOfBytes, ack
		log = log + (headInInts[1] + "\t\t");
		log = log + (headInInts[2] + "\t");
		log = log + (headInInts[3] + "\n");

		//System.out.print(log);
		byte[] logInBytes = log.getBytes();
		logOutput.write(logInBytes);
		logOutput.flush();
	}

	private static int computeCheckSum(byte[] dataPortion){
		int checksum = 0;
		for(int i = 0; i < dataPortion.length; i++){
			checksum = checksum + dataPortion[i];
		}
		return checksum;
	}

	private static Comparator<SimpleEntry> keyComparator = new Comparator<SimpleEntry>(){
		@Override
		public int compare(SimpleEntry e1, SimpleEntry e2) {
			return (int) (Integer.parseInt(e1.getKey().toString()) - Integer.parseInt(e2.getKey().toString()));
		}
    };

    private static byte[] linkAllDataPortion(ArrayList<SimpleEntry<Integer, byte[]>> packetArray){
    	byte[] dataR = packetArray.get(0).getValue();
    	for(int i = 1; i < packetArray.size(); i++){
    		dataR = connectBytes(dataR, packetArray.get(i).getValue());
    	}
    	return dataR;
    }

}
