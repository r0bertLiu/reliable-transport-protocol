# A reliable transport protocol
> This is a UNSW MIT project for course 9331.

## Introduction
This project have implemented a simple transport protocol(STP) based on UDP socket write in JAVA and most of features in TCP are realized. There is a PLD module as well which can simulate the real internet environment.

## Implementation
### STP Sender
To realized the features of TCP, STP sender use three threads:
#### Thread1: SendingThread(EVENT1 Send packet)
Sending thread is use to keep create and send new packets if there is enough space in	window. __Window__ is implemented by an Vector object(array). If the current seq 	__number(lastbytessend)__ minus __sendBase(LastByteAcked)__ is lower than MWS, then add this segment in window and sent it to PLD. After that we update the seq number by seq += mss(except last packet). If the segments send this time is the only one segment in window 	currently, start/restart Timer.

#### Thread2: ReceivingThread(EVENT2 ACK received)
Receiving thread will continue collect the upcoming packets, it will block until a new 	packet received. When a new packet received, try to do one of below:
1. If the ack of received segment is larger than __sendBase__ which means a new ack received, set new __sendBase__ and use curr ack received to update window(delete segment with seq lower than sendBase). If __Window__ is not empty, there are not-yet-ack segments in window, restart timer.		
3. If ack is not larger than __sendBase__, last ack received (curr ack received must equal to sendBase). We increase the __dupCounter__ by plus 1. If __dupCounter__ equal to 3, do fast retransmission by resent segments with smallest seq number which is segments in Window[0] currently as window is append in correct order. After retransmission restart 	timer.

#### Thread3: TimerThread(EVENT3 Timout)
There is a while loop in Timer thread, so when we start this thread it will keep looping 	until we received last segments. Timer thread will first sleep for a RTO time, if it is 	interrupt(restart) 	by other thread during sleep, it continue the while and sleep again for a RTO time, if it not interrupt by other and finish sleep which mean timout, it will retransmits 	smaller segments like fast retransmission mentioned before.

### PLD module
To simulate real internet environment, The PLD module will take the following events:
1. Drop packets   
2. Duplicate packets 
3. Create bit errors within packets (a single bit error) 
4. Transmits out of order packets 
5. Delays packets 
...__Drop__ and __Dup__ are just straight line. __Corrupt__ is implemented by change the first byte of dataPortion. If this byte is from 0 ~ 127 convert it to -128 ~ -1 which will change first bit(first bit of byte in java is 	represent positive or negative). Otherwise -128 ~ -1 byte convert it to 0 ~ 127. __Reorder__ realized by first set there is a currently reoder packets. And count when other packest send. __Delay__ is implemented by thread, this thread will first sleep for a random time(less than maxdelay), then send the delay packet.

### STP Receiver
The data receiving function is implemented by one thread and it can receive segments 	which are out of order and then send an Ack immediately for each received data segments. 	It is implemented by:
1. A SET for the seq number that received
2. An receivingbuffer(based on arrayList) to store the packets which are out of order. 
3. Elements of arrayList is pairPacket<key, value>, with key = seq number of segments, value 	= payload of segments. Which make the arrayList can be sorted by seq
	
When a new segments received: do one of the following:
1. If the received seq is contains in SET which means this segments is received before, discard it and send last ack segments again.
2. If the seq not in SET, a new segments received, check it type value(in header), if it type is F(not D for data), which means data transmission is finished, set ack = ack + 1 then go to connection termination.
3. If seq not in SET, Type of segment is Data, get it payload and compute it checksum, if the 	checksum is not as same as the checksum in header. Packet corrupt, discard it.
4. If seq not in SET, Type of segment is Data, Checksum is correct which a valid new segment received. Add it seq number in SET, add it into buffer. If the seq of received packet equal to 	current ack, update ack number by information in header. As the segments are come with 	wrong order, ack should also be update by check the received seq in SET. Otherwise, we 	received a segment with larger seq, no need to update ack, just send last ack segments again.



## Usage
### Sender.javaU
The Sender should accept the following fourteen (14) arguments   
1. receiver_host_ip: The IP address of the host machine on which the Receiver is running.  
2. receiver_port: The port number on which Receiver is expecting to receive packets from the sender.  
3. file.pdf: The name of the pdf file that has to be transferred from sender to receiver using your STP.  
4. MWS: The maximum window size used by your STP protocol in bytes.
5. MSS: Maximum Segment Size which is the maximum amount of data (in bytes) carried in each STP segment.
6. gamma: This value is used for calculation of timeout value. of the specification for details. 
7. pDrop: The probability that a STP data segment which is ready to be transmitted will be dropped. This value must be between 0 and 1. For example if pDrop = 0.5, it means that 50% of the transmitted segments are dropped by the PLD. 
8. pDuplicate: The probability that a data segment which is not dropped will be duplicated. This value must also be between 0 and 1. 
9. pCorrupt: The probability that a data segment which is not dropped/duplicated will be corrupted. This value must also be between 0 and 1. 
10. pOrder: The probability that a data segment which is not dropped, duplicated and corrupted will be re-ordered. This value must also be between 0 and 1. 
11. maxOrder: The maximum number of packets a particular packet is held back for re-ordering purpose. This value must be between 1 and 6. 
12. pDelay: The probability that a data segment which is not dropped, duplicated, corrupted or re-ordered will be delayed. This value must also be between 0 and 1. 
13. maxDelay: The maximum delay (in milliseconds) experienced by those data segments that are delayed.  
14. seed: The seed for your random number generator. The use of seed will be explained in Section 4.5.2 of the specification.  

Then run it as:
```
java Sender receiver_host_ip receiver_port file.pdf MWS MSS gamma pDrop pDuplicate pCorrupt pOrder maxOrder pDelay maxDelay seed
```

### receiver.java
The Receiver should accept the following two arguments:  
1. receiver_port: the port number on which the Receiver will open a UDP socket for receiving datagrams from the Sender.  
2. file_r.pdf: the name of the pdf file into which the data sent by the sender should be stored (this is a copy of the file that is being transferred from sender to receiver). 
```
java Receiver receiver_port file_r.pdf  
```
