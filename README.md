#A reliable transport protocol
> This is a UNSW MIT project for course 9044/2041.

## introduction
This project have implemented a simple transport protocol(STP) based on UDP socket write in JAVA and most of features in TCP are realized. There is a PLD module as well which can simulate the real internet environment.

##

## user manual
### Sender.java
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
