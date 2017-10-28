/*===============================================================*
 *  Submitted by:                                                *
 *  															 *
 *  Derrick Peh Jia Hao U1621219F								 *
 *  Lim Dong Li, Tony   U1620970K								 *
 *  															 *
 *  Course:														 *
 *  CZ3006														 *
 *  															 *
 *  Lab Group:													 *
 *  TS1															 *
 *===============================================================*/


/*===============================================================*
 *  File: SWP.java                                               *
 *                                                               *
 *  This class implements the sliding window protocol            *
 *  Used by VMach class					         *
 *  Uses the following classes: SWE, Packet, PFrame, PEvent,     *
 *                                                               *
 *  Author: Professor SUN Chengzheng                             *
 *          School of Computer Engineering                       *
 *          Nanyang Technological University                     *
 *          Singapore 639798                                     *
 *===============================================================*/

import java.util.Timer;
import java.util.TimerTask;

public class SWP {

/*========================================================================*
 the following are provided, do not change them!!
 *========================================================================*/
   //the following are protocol constants.
   public static final int MAX_SEQ = 7; 
   public static final int NR_BUFS = (MAX_SEQ + 1)/2;

   // the following are protocol variables
   private int oldest_frame = 0;
   private PEvent event = new PEvent();  
   private Packet out_buf[] = new Packet[NR_BUFS];
   private Packet in_buf[] = new Packet[NR_BUFS];

   //the following are used for simulation purpose only
   private SWE swe = null;
   private String sid = null;  

   //Constructor
   public SWP(SWE sw, String s){
      swe = sw;
      sid = s;
   }

   //the following methods are all protocol related
   private void init(){
      for (int i = 0; i < NR_BUFS; i++){
	   out_buf[i] = new Packet();
	   in_buf[i] = new Packet();
      }
   }

   private void wait_for_event(PEvent e){
      swe.wait_for_event(e); //may be blocked
      oldest_frame = e.seq;  //set timeout frame seq
   }

   private void enable_network_layer(int nr_of_bufs) {
   //network layer is permitted to send if credit is available
	swe.grant_credit(nr_of_bufs);
   }

   private void from_network_layer(Packet p) {
      swe.from_network_layer(p);
   }

   private void to_network_layer(Packet packet) {
	swe.to_network_layer(packet);
   }

   private void to_physical_layer(PFrame fm)  {
      System.out.println("SWP: Sending frame: seq = " + fm.seq + 
			    " ack = " + fm.ack + " kind = " + 
			    PFrame.KIND[fm.kind] + " info = " + fm.info.data );
      System.out.flush();
      swe.to_physical_layer(fm);
   }

   private void from_physical_layer(PFrame fm) {
      PFrame fm1 = swe.from_physical_layer(); 
	fm.kind = fm1.kind;
	fm.seq = fm1.seq; 
	fm.ack = fm1.ack;
	fm.info = fm1.info;
   }


/*===========================================================================*
 	implement your Protocol Variables and Methods below: 
 *==========================================================================*/
   
   // no nak has been sent yet
   private boolean no_nak = true;
   
   // This method increments an integer while making sure it falls within the range of the max sequence number
   public static int increment(int n) {
       int temp1 = n + 1;
       int temp2 = MAX_SEQ + 1;
	   n = (temp1 % temp2);
       return n;
   }
 
   /*===========================================================================*
   This method ensures that the frame received is within the sliding window for any circular conditions
   x = lower edge of sliding window
   y = sequence number of frame received
   z = upper edge of sliding window
   1st condition example  x = Frame No. 0, y = Frame No. 2,  z = Frame No. 3
   2nd condition example  x = Frame No. 6, y = Frame No. 7,  z = Frame No. 1
   3rd condition example  x = Frame No. 7, y = Frame No. 0,  z = Frame No. 2
    *==========================================================================*/
   public static boolean between(int x, int y, int z) {
       return ((x <= y) && (y < z)) || ((z < x) && (x <= y)) || ((y < z) && (z < x));
   }

   // This method allows creating and sending of frames
   private void send_frame(int frame_type, int frame_nr, int frame_expected, Packet buffer[]) {
	   // Scratch variable
	   // Creates frame to be sent
       PFrame s = new PFrame();  

       // There are 3 type of frames - DATA, ACK, NAK
       // This line of code sets the frame type field in the new frame
       s.kind = frame_type; 
       
       // Sending DATA frame (sender will enter this condition)
       if (frame_type == PFrame.DATA) {
    	   
    	   // Using modulus function on the sequence number to determine which buffer slot to retrieve DATA to put into the new frame
           s.info = buffer[frame_nr % NR_BUFS];
       }
       
       // This line of code sets the sequence number field in the new frame (only meaningful for DATA frames)
       s.seq = frame_nr;  
       
       // This line of code sets the acknowledgement field in the new frame
       // to piggyback the acknowledgement of a previously received frame
       s.ack = (frame_expected + MAX_SEQ) % (MAX_SEQ + 1);
       
       // Sending NAK (receiver will enter this condition)
       // One NAK per frame 
       if (frame_type == PFrame.NAK) {
           no_nak = false;  
       }
    
       // Transmit the frame
       to_physical_layer(s); 
       
       // Timer is set and starts when sender sends out a DATA frame
       // so that the sender will know when the frame needs to be resent by using the timer
       if (frame_type == PFrame.DATA) {
           startTimer(frame_nr);
       }
       
       // No need for separate ACK frame
       stop_ack_timer();  
   }

   
   public void protocol6() {
        init();
        int ack_expected;          // lower edge of sender's window
        int next_frame_to_send;    // upper edge of sender's window + 1
        int frame_expected;        // lower edge of receiver's window
        int too_far;               // upper edge of receiver's window + 1

        // Size of the sliding Window 
        // Keeps track of the frames arrived
        boolean arrived[] = new boolean[NR_BUFS];
        
        PFrame t_frame = new PFrame();  // scratch variable
        
        // Initialize Network Layer:
        enable_network_layer(NR_BUFS);

        // Initialize Counter Variables:
        ack_expected = 0;
        next_frame_to_send = 0;
        frame_expected = 0;
        too_far = NR_BUFS;

        for(int i = 0; i < NR_BUFS; i++)
        	// Sets the sliding window to empty as no frames are received yet
            arrived[i] = false;
        
	while(true) {	
		// Wait for an event to occur
		wait_for_event(event);
		switch(event.type) {
	   		// Accept, save, and transmit a new frame
			case (PEvent.NETWORK_LAYER_READY):
				
				// Fetches the new packet from network layer
				// determined by using modulus function on the sequence number
				from_network_layer(out_buf[next_frame_to_send % NR_BUFS]);
			
				// Transmit the frame
				send_frame(PFrame.DATA, next_frame_to_send, frame_expected, out_buf);
				
				// Advance upper edge of window
				next_frame_to_send = increment(next_frame_to_send); 
				break;
				
			// A data or control frame has arrived
			case (PEvent.FRAME_ARRIVAL):
	    	  	// Fetch incoming frame from physical layer
	    	  	from_physical_layer(t_frame); 

          		if (t_frame.kind == PFrame.DATA) {
          			// An undamaged frame has arrived
          			if ((t_frame.seq != frame_expected) && no_nak) {
          				// If received frame is not the expected frame and no NAK is received, send a NAK to sender
          				send_frame(PFrame.NAK, 0, frame_expected, out_buf);
          			} else {
          				// Start ACK timer to retransmit ACK to sender when timer timeout
          				startAckTimer();
          			}

          			// Check if the frame received is within the sliding window range 
          			// & whether it has been previously received.
          			if (between(frame_expected, t_frame.seq, too_far) && arrived[t_frame.seq % NR_BUFS] == false) {
          				// Frames may be accepted in any order
          				// Mark buffer as full
          				arrived[t_frame.seq % NR_BUFS] = true;
            	  
          				// Insert data into buffer
          				in_buf[t_frame.seq % NR_BUFS] = t_frame.info;  

          				while (arrived[frame_expected % NR_BUFS]) {
          					// Pass frames and advance window
          					to_network_layer(in_buf[frame_expected % NR_BUFS]);
          					
          					// Correct frame received so no NAK is sent
          					no_nak = true;
                      
          					// Remove received frame from buffer
          					arrived[frame_expected % NR_BUFS] = false; 
                      
          					// Advance lower edge of receiver's window
          					frame_expected = increment(frame_expected); 
                      
          					// Advance upper edge of receiver's window
          					too_far = increment(too_far);
                      
          					// To see if a separate ACK is needed
          					startAckTimer(); 
                  }
              }
          }

          		// If a NAK frame arrives, check that the frame is within the sliding window range
          		if (t_frame.kind == PFrame.NAK && between(ack_expected, ((t_frame.ack + 1) % (MAX_SEQ + 1)), next_frame_to_send)) {
              		// Retransmit the data of the frame for which a NAK has arrived.
          			send_frame(PFrame.DATA, ((t_frame.ack + 1) % (MAX_SEQ + 1)), frame_expected, out_buf);
          		}

          		while (between(ack_expected, t_frame.ack, next_frame_to_send)) {
          			// Frame arrived intact
          			stopTimer(ack_expected % NR_BUFS);
          			
          			// Advance lower edge of sender’s window
          			ack_expected = increment(ack_expected);
          			
          			// Free up 1 buffer slot
          			enable_network_layer(1);               
          		}
          break;
          
          // Damaged frame with checksum error has arrived
	      case (PEvent.CKSUM_ERR):
              if (no_nak) {
                  // Damaged frame
            	  // Send NAK if not NAK is sent yet
                  send_frame(PFrame.NAK, 0, frame_expected, out_buf);
              }
              break;
          
          // Timer timeout
	      case (PEvent.TIMEOUT):
	    	  // We timed out
              // If the timer has expired for the frame, retransmit the frame
              send_frame(PFrame.DATA, oldest_frame, frame_expected, out_buf);
              break;
              
          // ACK Timer timeout
	      case (PEvent.ACK_TIMEOUT):
	    	  // Ack timer expired, send ACK
              // If the ACK timer has expired, send the ACK on its own without piggyback
              send_frame(PFrame.ACK, 0, frame_expected, out_buf);
              break;
 
	      default:
              System.out.println("SWP: undefined event type = " + event.type);
              System.out.flush();
              break;
	   }
      }      
   	}
   
   
   
   /* Note: when start_timer() and stop_timer() are called, 
      the "seq" parameter must be the sequence number, rather 
      than the index of the timer array, 
      of the frame associated with this timer, 
     */
   
   	 // Create a timer array to store the timers for DATA frames with the same size as the sliding window range
     Timer frameTimer[] = new Timer[NR_BUFS];
     
     // Create a timer for ACK frames
     Timer ackTimer;
     
     // This method is called when a DATA frame is sent out
     private void startTimer(int seq) {
    	 // Stop timer if it already exists for the sequence number
    	 stopTimer(seq);
    	 
    	 // Create a timer for the sequence number and store it into the timer array
         frameTimer[seq % NR_BUFS] = new Timer();
         
         // Creates a new retransmission task for the timer
         RTask RT = new RTask(swe, seq);
         
         // Schedule the task in the timer
         frameTimer[seq % NR_BUFS].schedule(RT, 200);
     }

     // This method is called to stop the timer when ACK is received
     private void stopTimer(int seq) {
         if (frameTimer[seq % NR_BUFS] != null) {
             frameTimer[seq % NR_BUFS].cancel();
         }
     }
     
     // This method is called to start the timer when the receiver sends out ACK frame to sender
     private void startAckTimer( ) {
    	 // Stop timer if it already exists
    	 stop_ack_timer();
    	 
    	 // Create a new timer object
         ackTimer = new Timer();
         
         // Creates a new ACK task for the timer
         ATask AT = new ATask(swe);
         
         // Schedule the task in the timer
         ackTimer.schedule(AT, 100);
     }

     // This method is called to stop the timer when the receiver sends out another ACK frame to sender
     private void stop_ack_timer() {
    	 // If the ACK timer already exists
         if (ackTimer != null) {
        	 // Stop the timer and remove the scheduled task
             ackTimer.cancel();
         }
     }
     
     // Timer for DATA retransmission
     class RTask extends TimerTask {
         private SWE swe = null;
         public int seqnr;

         // Create a DATA retransmission task object
         public RTask(SWE swe, int seqnr) {
        	 this.swe = swe;
        	 this.seqnr = seqnr;
         }
         
         public void run() {
        	 // Stop the timer
             stopTimer(seqnr);
             
             // Generate a timeout event on the SWE
             swe.generate_timeout_event(seqnr);
         }
     }

     // Timer for ACK retransmission
     class ATask extends TimerTask {
         private SWE swe = null;
         
         // Create a ACK retransmission task object
         public ATask(SWE swe) {
        	 this.swe = swe;
         }

         public void run() {
        	 // Stop the timer
             stop_ack_timer();
             
             // Generate a timeout event on the SWE
             swe.generate_acktimeout_event();
         }
     }
 }    
     
  //End of class

  /* Note: In class SWE, the following two public methods are available:
     . generate_acktimeout_event() and
     . generate_timeout_event(seqnr).

     To call these two methods (for implementing timers),
     the "swe" object should be referred as follows:
       swe.generate_acktimeout_event(), or
       swe.generate_timeout_event(seqnr).
  */




