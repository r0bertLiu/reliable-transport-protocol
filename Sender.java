// Sender
// Written by Yubai Liu for Comp9331 Assignment

import java.io.*;
import java.net.*;
import java.util.*;
import java.nio.*;
import java.util.AbstractMap.SimpleEntry;

public class Sender {
	// arguments
	public static InetAddress receiverIp;
	public static int receiverPort;
	public static File sendFile;
	public static byte[] data;
	public static int mws;
	public static int mss;
	public static int gama;
	public static float pDrop;
	public static float pDup;
	public static float pCorrupt;
	public static float pOrder;
	public static int maxOrder;
	public static float pDelay;
	public static int maxDelay;
	
	// other variables
	public static DatagramSocket socket;
	public static Random random;
	public static long startTime;
	public static int seq;
	public static int ack;
	public static long estimatedRTT;
	public static long devRTT;
	public static long sampleRTT;
	public static long TimeoutInterval;
	public static int haveReorder;
	public static int orderCounter;
	public static DatagramPacket reorderPacket;
	public static int[] reorderHeadInInts;
	public static int haveDelay;
	public static int dataLength;

	// variables used by multithreads
	public static Vector<int[]> window; 
	public static int sendBase;
	public static int dupAckCounter;
	public static int onTracking;
	public static long trackingStartTime;

	// variables used by summary 
	public static int totalPacketSend;
	public static int numOfDrop;
	public static int numOfCorr;
	public static int numOfReord;
	public static int numOfDup;
	public static int numOfDelay;
	public static int numOfTimeout;
	public static int numOfFRXT;
	public static int numOfDupAck;
	public static FileOutputStream logOutput;


	public static void main(String[] args) throws Exception {
		if (args.length != 14){
			System.out.println("Number of given arguments are not correct");
         	return;
		}
		// set given arguments
		// program start, set start time
		startTime = System.currentTimeMillis();
		// set socket with a port number
		socket = new DatagramSocket(666);
		// set des Ip
		receiverIp = InetAddress.getByName(args[0]);
		// set des port
		receiverPort = Integer.parseInt(args[1]);
		// set file need to be send
		sendFile = new File(args[2]);
		// read sending file into byte[]
		data = new byte[(int) sendFile.length()];
		dataLength = data.length;
		FileInputStream fileInput = new FileInputStream(sendFile);
		fileInput.read(data);
		fileInput.close();
		// set MWS
		mws = Integer.parseInt(args[3]);
		// set MSS
		mss = Integer.parseInt(args[4]);
		// set gama
		gama = Integer.parseInt(args[5]);
		// set pDrop
		pDrop = Float.parseFloat(args[6]);
		// set pDup
		pDup = Float.parseFloat(args[7]);
		// set pCorrupt
		pCorrupt = Float.parseFloat(args[8]);
		// set pOrder and maxOrder
		pOrder = Float.parseFloat(args[9]);
		maxOrder = Integer.parseInt(args[10]);
		// set pDelay and maxDelay
		pDelay = Float.parseFloat(args[11]);
		maxDelay = Integer.parseInt(args[12]);
		// set seed
		int seed = Integer.parseInt(args[13]);
		// set random object
		random = new Random(seed);
		// init seq_number
		seq = 0;
		// init ack_number
		ack = 0;
		// set a arrylist to store segments in window
		window = new Vector<int[]>();
		// set initial value of estimatedRTT
		estimatedRTT = 500;
		// set initial value of devRTT
		devRTT = 250; 
		// comoute initial RTO
		TimeoutInterval = estimatedRTT + gama * devRTT;
		// there is no ontracking packet, reorder packet and delay packet at first
		onTracking = 0;
		haveReorder = 0;
		haveDelay = 0;
		// order & dupack counter should be 0
		dupAckCounter = 0;
		orderCounter = 0;

		// summary variable initial
		totalPacketSend = 0;
		numOfDrop = 0;
		numOfCorr = 0;
		numOfReord = 0;
		numOfDup = 0;
		numOfDelay = 0;
		numOfTimeout = 0;
		numOfFRXT = 0;
		numOfDupAck = 0;


		File Sender_log = new File("Sender_log.txt");
		logOutput = new FileOutputStream(Sender_log);

		// STP start
		connectionEstablishment();
		dataSending();
		connectionTermination();
		socket.close();
		summaryPrinter();
		logOutput.close();

	}

	private static void summaryPrinter() throws Exception{
		String summary = "=============================================================" + "\n"
					   + "Size of the file (in Bytes)                \t\t" + dataLength + "\n"
					   + "Segments transmitted (including drop & RXT)\t\t" + totalPacketSend + "\n"
					   + "Number of Segments handled by PLD          \t\t" + (totalPacketSend - 4) + "\n"
					   + "Number of Segments dropped                 \t\t" + numOfDrop + "\n"
					   + "Number of Segments Corrupted               \t\t" + numOfCorr + "\n"
					   + "Number of Segments Re-ordered              \t\t" + numOfReord + "\n"
					   + "Number of Segments Duplicated              \t\t" + numOfDup + "\n"
					   + "Number of Segments Delayed                 \t\t" + numOfDelay + "\n"
					   + "Number of Retransmissions due to TIMEOUT   \t\t" + numOfTimeout + "\n"
					   + "Number of FAST RETRANSMISSION              \t\t" + numOfFRXT + "\n"
					   + "Number of DUP ACKS received                \t\t" + numOfDupAck + "\n"
					   + "=============================================================";
		//System.out.println(summary);
		byte[] summaryInBytes = summary.getBytes();
		logOutput.write(summaryInBytes);
		logOutput.flush();
	}

	private static void connectionEstablishment() throws Exception{
		// int array for received head 
		int[] headReceivedInInts = new int[10];
		// int array for sent head 
		int[] headSentInInts = new int[10];
		// build SYN segment
		// build int array for SYN segments head
		headSentInInts = headInIntsBuilder(0, seq, 0, ack, 0);
		// convert int array to byte array
		byte[] headSentInBytes = toByteArray(headSentInInts);
		// set packet in sender set
		DatagramPacket senderPacket = new DatagramPacket(headSentInBytes, headSentInBytes.length, receiverIp, receiverPort);
		// when SA from reciver not received continue to send syn
		// send SYN
		socket.send(senderPacket);
		totalPacketSend ++;
		logPrinter(headSentInInts, 0, startTime);
		while(true){
			// Create a datagram packet for packet from receiver.
         	DatagramPacket receiverPacket = new DatagramPacket(new byte[1024], 1024);
			// block until receiver packet from receiver or timeout
			socket.receive(receiverPacket);
			// check the content of receiving packet
			byte[] headReceivedInBytes = Arrays.copyOfRange(receiverPacket.getData(), 0, 20);
			// convert the byte array to int array
			headReceivedInInts = toIntArray(headReceivedInBytes);
			logPrinter(headReceivedInInts, 1, startTime);
			if(headReceivedInInts[0] == 1){ // type == 1 : SA
				// SA received
				break;
			}
		}

		// set new Seq and Ack
		seq = 1;
		ack = 1;
		// build ACK segement
		headSentInInts = headInIntsBuilder(2, seq, 0, ack, 0);
		// set new sendHeadString
		headSentInBytes = toByteArray(headSentInInts);
		// set new head into packet
		senderPacket.setData(headSentInBytes);
		// send packet
		socket.send(senderPacket);
		totalPacketSend ++;
		logPrinter(headSentInInts, 0, startTime);
	}

	private static void dataSending() throws Exception{
		senderThread.start();
		receiverThread.start();
		// waiting senderThread and receiverThread finished
		
		senderThread.join();
		receiverThread.join();

	}

	public static Thread senderThread = new Thread (){
		public void run (){
			try{
				// 
				int firstsegment = 1;
				// set sendBase = init seq
			 	sendBase = seq;
				// int array for sent head 
				int[] headSentInInts = new int[10];
				// while last packet not received continue send segments
				while(sendBase != dataLength + 1){
					Thread.sleep(1); // make program run faster, because sendBase will not always occupied by this thread

					if(seq - sendBase < mws && seq != data.length + 1){	
						// if there is space in window, and there are new segments send a new segment
						// EVENT 1: SEND NEW SEGMENT

						// set start and end index for data portion
						int startIndex = seq - 1;
						int numOfBytes = mss;
						if(data.length - startIndex < 150){
							numOfBytes = data.length - startIndex;
						}		
						int endIndex = startIndex + numOfBytes;
						// get data portion
						byte[] dataPortion = Arrays.copyOfRange(data, startIndex, endIndex);
						// compute checksum of dataPortion
						int checksum = computeCheckSum(dataPortion);
						// build int array for SYN segments head
						headSentInInts = headInIntsBuilder(3, seq, numOfBytes, ack, checksum);
						// convert int array to byte array
						byte[] headSentInBytes = toByteArray(headSentInInts);
						// segement should be sent = head + dataPortion
						byte[] segment = connectBytes(headSentInBytes, dataPortion);
						// set packet in sender set
						DatagramPacket senderPacket = new DatagramPacket(segment, segment.length, receiverIp, receiverPort);

						// add segment head into window, it already build at last loop
						// if before add this head, window should be empty and, socket.received will not run, so timer is not running
						// after we add a head into window, socket.received will run, and timer will start

						window.add(headSentInInts);
						// run PLD
						pld(senderPacket, headSentInInts, dataPortion, 0);

						// TIMER
						if(firstsegment == 1){
							// first time to start timer
							timerThread.start();
							firstsegment = 0;
						}

						// RTT MEASURE
						if(onTracking == 0){
							// no segment is on tracking, track this segments for sampleRTT
							// tracking seq = curr seq
							onTracking = seq;
							trackingStartTime = System.currentTimeMillis();
						}
						seq = seq + numOfBytes;
						// build next segment
						// set start and end index for data portion
						startIndex = seq - 1;
					}				
					// check, is there a waiting reorder packet need to be send
					if(orderCounter > maxOrder && haveReorder == 1){
						socket.send(reorderPacket);
						totalPacketSend ++;
						// event: snd/rord
						logPrinter(reorderHeadInInts, 5, startTime);
						numOfReord++;
						// reorder packet sent, there is no reorder packet currenly 
						haveReorder = 0;
						// set order counter back to 0
						orderCounter = 0;
					}
				}
				// all segments sent, restart timer and it will finished
				timerThread.interrupt();
			}catch(RuntimeException e){
				throw e;
			}catch(Exception e){
				throw new RuntimeException("socket error", e);
			}
		}
	};

	public static Thread receiverThread = new Thread (){
		public void run (){
			try{
				// the ack number in received packet
				int lastAckReceived = 1;
				// int array for received head 
				int[] headReceivedInInts = new int[10];
				// int array for sent head 
				int[] headSentInInts = new int[10];
				// set DatagramPacket 
				DatagramPacket receiverPacket = new DatagramPacket(new byte[1024], 1024);
				while(sendBase != dataLength + 1){
					// ack will continue incomming
					// block until receiver packet from receiver
					socket.receive(receiverPacket);

					// EVENT2: ACK RECEIVED

					// check the content of receiving packet
					byte[] headReceivedInBytes = Arrays.copyOfRange(receiverPacket.getData(), 0, 20);
					headReceivedInInts = toIntArray(headReceivedInBytes);
					if(headReceivedInInts[3] > sendBase){
						// EVENT2.1: NEW ACK RECEIVED

						// event: rcv
						logPrinter(headReceivedInInts, 1, startTime);

						// set new sendBase
						sendBase = headReceivedInInts[3];

						// use curr ack received to update window
						while(window.get(0)[1] < headReceivedInInts[3]){
							window.remove(0);
							if(window.isEmpty()){
								// no any segmetns in window
								break;
							}
						}
						if(headReceivedInInts[3] > onTracking && onTracking != 0){
							// onTracking Segment is received normally
							sampleRTT = System.currentTimeMillis() - trackingStartTime;
							// update RTO
							updateRTO();
							// tracking of this segments is finished
							onTracking = 0;
						}
						//if(!window.isEmpty()){
						// there are not-yet-ack segments in window
						// restart timer
						timerThread.interrupt();
						//Thread.sleep(10);
						//}
					}else{// last ack received, curr ack received must equal to sendBase
						
						dupAckCounter ++;
						numOfDupAck++;
						// event: rcv/DA
						logPrinter(headReceivedInInts, 7, startTime);
						// tracking segment failed
						onTracking = 0;


						// EVENT2.2: 3DUP ACK RECEIVED 
						if(dupAckCounter == 3){
							// timeout retransmit not-yet-ack segments with smallest seq in window
							// in window the order of segment is depends on the seq, so retransmit window[0]
							// int array for sent head 
							
							
							while(window.isEmpty()){
								Thread.sleep(2);
							}
							

							headSentInInts = window.get(0);
							// set start and end index for data portion
							int startIndex = headSentInInts[1] - 1;
							int endIndex = startIndex + headSentInInts[2];
							// get data portion
							byte[] dataPortion = Arrays.copyOfRange(data, startIndex, endIndex);
							byte[] headSentInBytes = toByteArray(headSentInInts);
							// segement should be sent = head + dataPortion
							byte[] segment = connectBytes(headSentInBytes, dataPortion);
							// set packet in sender set
							DatagramPacket senderPacket = new DatagramPacket(segment, segment.length, receiverIp, receiverPort);
							pld(senderPacket, headSentInInts, dataPortion, 1);
							numOfFRXT++;
							// recount dupAck
							dupAckCounter = 0;
							// retransmission success
							if(!window.isEmpty()){
								// there are not-yet-ack segments in window
								// restart timer
								timerThread.interrupt();
							}
						}
					}
				}
			}catch(RuntimeException e){
				System.out.println("debug");
				throw e;
			}catch(Exception e){
				throw new RuntimeException("socket error", e);
			}
		}
	};

	public static Thread timerThread = new Thread (){
		public void run (){
			try{
				while(sendBase != dataLength + 1){
					// timer can only be stop be outer method
					try{
						// if window is null, do not restart timer
						while(window.isEmpty()){
							Thread.sleep(2);
						}
						// delay xxx time, during the delay if the timer is not restart, timeout
						Thread.sleep(TimeoutInterval);

						// EVENT3: TIMEOUT
						// timeout retransmit not-yet-ack segments with smallest seq in window
						// in window the order of segment is depends on the seq, so retransmit window[0]
						// int array for sent head 

						int[] headSentInInts = window.get(0);
						// set start and end index for data portion
						int startIndex = headSentInInts[1] - 1;
						int endIndex = startIndex + headSentInInts[2];
						// get data portion
						byte[] dataPortion = Arrays.copyOfRange(data, startIndex, endIndex);
						byte[] headSentInBytes = toByteArray(headSentInInts);
						// segement should be sent = head + dataPortion
						byte[] segment = connectBytes(headSentInBytes, dataPortion);
						// set packet in sender set
						DatagramPacket senderPacket = new DatagramPacket(segment, segment.length, receiverIp, receiverPort);
						pld(senderPacket, headSentInInts, dataPortion, 1);
						numOfTimeout++;
						// recount dupAck
						dupAckCounter = 0;
						// tracking segment failed
						onTracking = 0;

						//after timeout, restart timer
					}catch(InterruptedException e){
						// timer is restrat by other thread
						continue;
					}	
				}
			}catch(RuntimeException e){
				System.out.println("debug");
				throw e;
			}catch(Exception e){
				throw new RuntimeException("socket error", e);
			}
		}
	};
	

	private static void connectionTermination() throws Exception{
		// int array for received head 
		int[] headReceivedInInts = new int[10];
		// int array for sent head 
		int[] headSentInInts = new int[10];
		// build F segment
		// build int array for F segments head
		headSentInInts = headInIntsBuilder(4, seq, 0, ack, 0);
		// convert int array to byte array
		byte[] headSentInBytes = toByteArray(headSentInInts);
		// set packet in sender set
		DatagramPacket senderPacket = new DatagramPacket(headSentInBytes, headSentInBytes.length, receiverIp, receiverPort);
		// send F
		socket.send(senderPacket);
		logPrinter(headSentInInts, 0, startTime);
		//waiting A From receiver when A from reciver not received continue to send F
		while(true){
			// Create a datagram packet for packet from receiver.
			DatagramPacket receiverPacket = new DatagramPacket(new byte[1024], 1024);
			try{
				// block until receiver packet from receiver or timeout
				socket.receive(receiverPacket);
				// check the content of receiving packet
				byte[] headReceivedInBytes = Arrays.copyOfRange(receiverPacket.getData(), 0, 20);
				// convert the byte array to int array
				headReceivedInInts = toIntArray(headReceivedInBytes);
				logPrinter(headReceivedInInts, 1, startTime);
				if(headReceivedInInts[0] == 2){ // type == 2 : A
					// A received
					// set seq and ack for last segement
					seq = seq + 1;
					ack = ack + 1;
					break;
				}else{
					// send F again
					socket.send(senderPacket);
					logPrinter(headSentInInts, 0, startTime);
				}
			} catch(IOException e){
				// timeout send F again
				socket.send(senderPacket);
				logPrinter(headSentInInts, 0, startTime);
			} 
		}
		//waiting F From receiver when F from reciver not received continue to send A
		while(true){
			// Create a datagram packet for packet from receiver.
			DatagramPacket receiverPacket = new DatagramPacket(new byte[1024], 1024);
			// block until receiver packet from receiver or timeout
			socket.receive(receiverPacket);
			// check the content of receiving packet
			byte[] headReceivedInBytes = Arrays.copyOfRange(receiverPacket.getData(), 0, 20);
			// convert the byte array to int array
			headReceivedInInts = toIntArray(headReceivedInBytes);
			logPrinter(headReceivedInInts, 1, startTime);
			if(headReceivedInInts[0] == 4){ // type == 4 : F
				// A received
				// set seq and ack for last segement
				break;
			}
 
		}
		// create a segment with type = 2 : ack segment
		headSentInInts = headInIntsBuilder(2, seq, 0, ack, 0);
		// convert int array to byte array
	 	headSentInBytes = toByteArray(headSentInInts);
		senderPacket.setData(headSentInBytes);
		socket.send(senderPacket);
		logPrinter(headSentInInts, 0, startTime);
	}

	private static int[] headInIntsBuilder(int type, int seq, int numOfBytes, int ack, int checksum){
		/*  
			head is consist by int[4] with {type, seq, numOfBytes, ack, checksum}
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

	public static float getCurrTime(long startTime){
		return (float) (System.currentTimeMillis() - startTime) / 1000;
	}

	private static int computeCheckSum(byte[] dataPortion){
		int checksum = 0;
		for(int i = 0; i < dataPortion.length; i++){
			checksum = checksum + dataPortion[i];
		}
		return checksum;
	}

	public static void logPrinter(int[] headInInts, int event, long startTime)throws Exception{
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
				log = "drop\t\t"; 
				//System.out.print("drop ");
				break;
			case 3:
				log = "snd/dup\t\t"; 
				//System.out.print("snd/dup ");
				break;
			case 4:
				log = "snd/corr\t"; 
				//System.out.print("snd/corr ");
				break;
			case 5:
				log = "snd/rord\t"; 
				//System.out.print("snd/rord ");
				break;
			case 6:
				log = "snd/dely\t"; 
				//System.out.print("snd/dely ");
				break;
			case 7:
				log = "rcv/DA\t\t"; 
				//System.out.print("rcv/DA ");
				break;
			case 8:
				log = "snd/RXT\t\t"; 
				//System.out.print("snd/RXT ");
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

		System.out.print(log);
		byte[] logInBytes = log.getBytes();
		logOutput.write(logInBytes);
		logOutput.flush();
	}

	private static void pld(DatagramPacket senderPacket, int[] headInInts, byte[] dataPortion, int isRXT) throws Exception{
		if(random.nextFloat() < pDrop){// drop event
			// packet droped, do not send but write log
			// event: drop
			totalPacketSend ++;
			numOfDrop++;
			logPrinter(headInInts, 2, startTime);
		}else if (random.nextFloat() < pDup) {// dup event
			// packet dup, send it twice back-to-back
			socket.send(senderPacket);
			totalPacketSend ++;
			if(isRXT == 1){
				// event snd/RXT
				logPrinter(headInInts, 8, startTime);
			}else{
				// event snd
				logPrinter(headInInts, 0, startTime);
			}

			// check, if there is a reorder packet
			if(haveReorder == 1){
				orderCounter ++;
			}
			socket.send(senderPacket);
			totalPacketSend ++;
			numOfDup++;
			// event snd/dup
			logPrinter(headInInts, 3, startTime);

			// check, if there is a reorder packet
			if(haveReorder == 1){
				orderCounter ++;
			}
		}else if(random.nextFloat() < pCorrupt){ // corr event
			// packet corrupt, change 1 bit in first byte
			byte[] corrDataPortion = Arrays.copyOfRange(dataPortion, 0, dataPortion.length);
			if(dataPortion[0] >= 0){ // 0 ~ 127 byte convert it to -128 ~ -1 which will change only 1 bit
				corrDataPortion[0] = (byte) (dataPortion[0] - 128);
			}else{// -128 ~ -1 byte convert it to 0 ~ 127 which will change only 1 bit
				corrDataPortion[0] = (byte) (dataPortion[0] + 128);
			}
			// set corrupt data
			byte[] headInBytes = toByteArray(headInInts);
			senderPacket.setData(connectBytes(headInBytes, corrDataPortion));
			socket.send(senderPacket);
			totalPacketSend ++;
			numOfCorr++;
			// event snd/corr
			logPrinter(headInInts, 4, startTime);

			// check, if there is a reorder packet
			if(haveReorder == 1){
				orderCounter ++;
			}

		}else if(haveReorder != 1 && random.nextFloat() < pOrder){// possible have a new Reorder packet
			// Reorder event
			haveReorder = 1;
			reorderPacket = senderPacket;
			reorderHeadInInts = headInInts;

		}else if(haveDelay != 1 && random.nextFloat() < pDelay){
			// Delay event
			haveDelay = 1;
			Runnable delaySend = new DelaySend(senderPacket, headInInts);
			Thread delaySendThread = new Thread(delaySend);
			delaySendThread.start();

		}else{
			// no drop, no dup, no corr, no rord, no delay
			socket.send(senderPacket);
			totalPacketSend ++;

			// check, if there is a reorder packet
			if(haveReorder == 1){
				orderCounter ++;
			}

			if(isRXT == 1){
				// event snd/RXT
				logPrinter(headInInts, 8, startTime);
			}else{
				// event snd
				logPrinter(headInInts, 0, startTime);
			}
		}
	}

	private static void updateRTO(){
		estimatedRTT = (long) (0.875 * estimatedRTT) + (long) (0.125 * sampleRTT);
		long temp = estimatedRTT - sampleRTT;
		if(temp < 0){
				temp = -temp;
		}
		devRTT = (long) (0.75 * devRTT) + (long) (0.25 * temp);
		TimeoutInterval = estimatedRTT + gama * devRTT;
		if(TimeoutInterval > 60000){
			// set the up bound of RTO
			TimeoutInterval = 60000;
		}else if(TimeoutInterval < 200){
			TimeoutInterval = 200;
		}
	}


}

class DelaySend implements Runnable{
	private DatagramPacket senderPacket;
	private int[] headInInts;

	public DelaySend(DatagramPacket senderPacket, int[] headInInts){
		this.senderPacket = senderPacket;
		this.headInInts = headInInts;
	}

	public void run (){
		try{

			long tempDelay = (long) Sender.random.nextInt(Sender.maxDelay);

			Thread.sleep(tempDelay);

			Sender.socket.send(senderPacket);
			Sender.numOfDelay++;
			Sender.totalPacketSend ++;
			// event: snd/dealy
			Sender.logPrinter(headInInts, 6, Sender.startTime);
			// delay packet sent, there is no delay packet currently
			Sender.haveDelay = 0;
			// check, if there is a reorder packet
			if(Sender.haveReorder == 1){
				Sender.orderCounter ++;
			}

		}catch(RuntimeException e){
			System.out.println("debug");
			throw e;
		}catch(Exception e){
			throw new RuntimeException("socket error", e);
		}
	}
}
