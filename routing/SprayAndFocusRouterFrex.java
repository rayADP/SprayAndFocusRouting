package routing;

import java.util.*;

import Encounter.EncounterInfo;
import routing.MessageRouter;
import core.*;

/**
 * An implementation of Spray and Focus DTN routing as described in 
 * <em>Spray and Focus: Efficient Mobility-Assisted Routing for Heterogeneous
 * and Correlated Mobility</em> by Thrasyvoulos Spyropoulos et al.
 * 
 * @author PJ Dillon, University of Pittsburgh
 * Modified by Raymond A.D.P., Sanata Dharma University of Yogyakarta, Indonesia
 */
public class SprayAndFocusRouterFrex implements RoutingDecisionEngine {
	/** SprayAndFocus router's settings name space ({@value})*/ 
	public static final String SPRAYANDFOCUS_NS = "SprayAndFocusRouterFrex";
	/** identifier for the initial number of copies setting ({@value})*/ 
	public static final String NROF_COPIES_S = "nrofCopies";
	/** identifier for the difference in timer values needed to forward on a message copy  = menentukan perbedaan nilai waktu yang dibutuhkan untuk meneruskan salinan pesan*/
	public static final String TIMER_THRESHOLD_S = "transitivityTimerThreshold";

	/** Message property key for the remaining available copies of a message */
	public static final String MSG_COUNT_PROP = "SprayAndFocusRouterFrex.copies";
	/** Message property key for summary vector messages exchanged between direct peers */
	public static final String SUMMARY_XCHG_PROP = "SprayAndFocusRouterFrex.protoXchg";

	protected static final String SUMMARY_XCHG_IDPREFIX = "summary";
	protected static final double defaultTransitivityThreshold = 60.0;
	protected static int protocolMsgIdx = 0;

	protected int initialNrofCopies;
	protected double transitivityTimerThreshold;

	/** Stores information about nodes with which this host has come in contact */
	protected Map<DTNHost, EncounterInfo> FreqEncounters;

	protected Map<DTNHost, Map<DTNHost, EncounterInfo>> neighborEncounters;	


	private List <Message> sprayList;
	private List <Tuple<Message,Connection>> focusList;


	public SprayAndFocusRouterFrex(Settings s) {
		//		super(s);
		Settings snf = new Settings(SPRAYANDFOCUS_NS);
		initialNrofCopies = snf.getInt(NROF_COPIES_S);

		if(snf.contains(TIMER_THRESHOLD_S))
			transitivityTimerThreshold = snf.getDouble(TIMER_THRESHOLD_S);
		else
			transitivityTimerThreshold = defaultTransitivityThreshold;

		FreqEncounters = new HashMap<DTNHost, EncounterInfo>();
		neighborEncounters = new HashMap<DTNHost, Map<DTNHost, EncounterInfo>>();
		
	}

	/**
	 * Copy Constructor.
	 * 
	 * @param r The router from which settings should be copied
	 */
	public SprayAndFocusRouterFrex(SprayAndFocusRouterFrex r) {
		//		super(r);		
		this.sprayList = new ArrayList<>();
		this.focusList = new ArrayList<>();
		this.initialNrofCopies = r.initialNrofCopies;

		FreqEncounters = new HashMap<DTNHost, EncounterInfo>();
		neighborEncounters = new HashMap<DTNHost, Map<DTNHost, EncounterInfo>>();		
		
	}

	@Override
	public SprayAndFocusRouterFrex replicate() {
		return new SprayAndFocusRouterFrex(this);
	}

	//==========================================//

	@Override
	public void connectionDown(DTNHost thisHost, DTNHost peer) {
		// TODO Auto-generated method stub
		
		neighborEncounters.remove(peer);
		return;
	}

	@Override
	public void doExchangeForNewConnection(Connection con, DTNHost peer) {
		// TODO Auto-generated method stub

		DTNHost thisHost = con.getOtherNode(peer);

		if(FreqEncounters.containsKey(peer)){ 
			EncounterInfo info = FreqEncounters.get(peer);
			info.updateFreq();
			System.out.println("node "+thisHost+" peer "+peer.getAddress()+" info freq "+info.getFreq() );	
			FreqEncounters.put(peer, info);
		} else{
			EncounterInfo baru=new EncounterInfo();
			baru.updateFreq();
			System.out.println("node "+thisHost+" peer "+peer.getAddress()+" baru freq "+baru.getFreq() );
			FreqEncounters.put(peer, baru);	
		}
		
		// buat pesan baru (tanpa ada hubungan dengan koneksi sekarang)
		// menambahkan informasi FreqEncounter kedalam summary xchg pesan
		int msgSize = FreqEncounters.size() * 64 + thisHost.getMessageCollection().size() * 8;
		Message newMsg = new Message(thisHost, peer, SUMMARY_XCHG_IDPREFIX + protocolMsgIdx++, msgSize);
		newMsg.addProperty(SUMMARY_XCHG_PROP, /*new HashMap<DTNHost, EncounterInfo>(*/FreqEncounters);		
		this.newMessage(newMsg);	// buat pesan baru dengan penambahan summary exchange
		
		Integer nrofCopies;
		String msgId = con.getMessage().getId();
		/* get this router's copy of the message */
		Message msg = con.getMessage();

		if (msg == null) { // message has been dropped from the buffer after..
			return; // ..start of transfer -> no need to reduce amount of copies
		}
		
		if(msg.getProperty(SUMMARY_XCHG_PROP) == (Integer)1) {
			thisHost.deleteMessage(msgId, false);
			return;
		}
		
		/* 
		 * reduce the amount of copies left. If the number of copies was at 1 and
		 * we apparently just transferred the msg (focus phase), then we should
		 * delete it. 
		 */
		
		nrofCopies = (Integer)msg.getProperty(MSG_COUNT_PROP);
		
		if(nrofCopies > 1)
			nrofCopies /= 2;
		else
			thisHost.deleteMessage(msgId, false);
		
		msg.updateProperty(MSG_COUNT_PROP, nrofCopies);
	}

	
	
	
	@Override
	public boolean newMessage(Message m) {
//		makeRoomForNewMessage(m.getSize());
		m.addProperty(MSG_COUNT_PROP, new Integer(initialNrofCopies));
		shouldSendMessageToHost(m, m.getTo());
		return true;
	}

	@Override
	public void connectionUp(DTNHost thisHost, DTNHost peer) {

		Collection <Message> messageCollection = thisHost.getMessageCollection(); 
		List <Connection> getConnection = thisHost.getConnections();

		for (Message m : messageCollection) {
			
			if (m.getProperty(SUMMARY_XCHG_PROP) != null) continue;
			
			Integer nrofCopies = (Integer)m.getProperty(MSG_COUNT_PROP);
			assert nrofCopies != null : "SnF message " + m + " didn't have nrof copies property!";
			if (nrofCopies > 1) { this.sprayList.add(m); } 
			
			if (nrofCopies == 1){

				int maxFreqOfPeer = 0;
				DTNHost dest = m.getTo();
				Connection toSend = null; 

				int myFreq = getFreqEncounterForHost(dest);
				double margin = myFreq * 0.3;

				for(Connection connection : getConnection){

					int FreqOfPeer = 0;
					Map<DTNHost, EncounterInfo> peerEncounters = neighborEncounters.get(peer);

					if(peerEncounters != null && peerEncounters.containsKey(dest)) {
						FreqOfPeer= neighborEncounters.get(peer).get(dest).getFreq();
					}

					if (FreqOfPeer > maxFreqOfPeer) {
						toSend = connection;
						maxFreqOfPeer = FreqOfPeer;
						System.out.println(maxFreqOfPeer);
					}

				}
				if (toSend != null && maxFreqOfPeer > myFreq + margin){
					focusList.add(new Tuple<Message, Connection>(m, toSend));
					System.out.println("focus");
				}			
			}
			
			
			
		}
	}

	

	private int getFreqEncounterForHost (DTNHost host) {
		if(FreqEncounters.containsKey(host)){
			return FreqEncounters.get(host).getFreq();
		} return 0;
	}

  
	
	@Override
	public boolean shouldSendMessageToHost(Message m, DTNHost otherHost) {
		// TODO Auto-generated method stub
		return false;
	}
	@Override
	public boolean isFinalDest(Message m, DTNHost aHost) {
		// TODO Auto-generated method stub
		return false;
	}

	@Override
	public boolean shouldSaveReceivedMessage(Message m, DTNHost thisHost) {
		// TODO Auto-generated method stub
		return false;
	}

	

	@Override
	public boolean shouldDeleteSentMessage(Message m, DTNHost otherHost) {
		// TODO Auto-generated method stub
		
		return false;
	}

	@Override
	public boolean shouldDeleteOldMessage(Message m, DTNHost hostReportingOld) {
		// TODO Auto-generated method stub
		return false;
	}

}

