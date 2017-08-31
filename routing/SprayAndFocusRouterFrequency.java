package routing;

import java.util.*;

import core.*;

/**
 * An implementation of Spray and Focus DTN routing as described in 
 * <em>Spray and Focus: Efficient Mobility-Assisted Routing for Heterogeneous
 * and Correlated Mobility</em> by Thrasyvoulos Spyropoulos et al.
 * 
 * @author PJ Dillon, University of Pittsburgh
 * Modified by Raymond A.D.P., Sanata Dharma University of Yogyakarta, Indonesia
 */
public class SprayAndFocusRouterFrequency extends ActiveRouter 
{
	/** SprayAndFocus router's settings name space ({@value})*/ 
	public static final String SPRAYANDFOCUS_NS = "SprayAndFocusRouterFrequency";
	/** identifier for the initial number of copies setting ({@value})*/ 
	public static final String NROF_COPIES_S = "nrofCopies";
	/** identifier for the difference in timer values needed to forward on a message copy  = menentukan perbedaan nilai waktu yang dibutuhkan untuk meneruskan salinan pesan*/
	public static final String TIMER_THRESHOLD_S = "transitivityTimerThreshold";
	
	/** Message property key for the remaining available copies of a message */
	public static final String MSG_COUNT_PROP = "SprayAndFocusRouterFrequency.copies";
	/** Message property key for summary vector messages exchanged between direct peers */
	public static final String SUMMARY_XCHG_PROP = "SprayAndFocusRouterFrequency.protoXchg";
	
	protected static final String SUMMARY_XCHG_IDPREFIX = "summary";
	protected static final double defaultTransitivityThreshold = 60.0;
	protected static int protocolMsgIdx = 0;
	
	protected int initialNrofCopies;
	protected double transitivityTimerThreshold;
	
	/** Stores information about nodes with which this host has come in contact */
	protected Map<DTNHost, EncounterInfo> FreqEncounters;
	
	protected Map<DTNHost, Map<DTNHost, EncounterInfo>> neighborEncounters;
	
	
	protected Map<DTNHost, Double> startTimestamps;
	//protected Map<DTNHost, List<Duration>> connHistory;
	
	
	public SprayAndFocusRouterFrequency(Settings s)
	{
		super(s);
		Settings snf = new Settings(SPRAYANDFOCUS_NS);
		initialNrofCopies = snf.getInt(NROF_COPIES_S);
		
		if(snf.contains(TIMER_THRESHOLD_S))
			transitivityTimerThreshold = snf.getDouble(TIMER_THRESHOLD_S);
		else
			transitivityTimerThreshold = defaultTransitivityThreshold;
		
		FreqEncounters = new HashMap<DTNHost, EncounterInfo>();
		neighborEncounters = new HashMap<DTNHost, Map<DTNHost, EncounterInfo>>();
		startTimestamps = new HashMap<DTNHost, Double>();
		
	}
	
	/**
	 * Copy Constructor.
	 * 
	 * @param r The router from which settings should be copied
	 */
	public SprayAndFocusRouterFrequency(SprayAndFocusRouterFrequency r)
	{
		super(r);
		this.initialNrofCopies = r.initialNrofCopies;
		
		FreqEncounters = new HashMap<DTNHost, EncounterInfo>();
		neighborEncounters = new HashMap<DTNHost, Map<DTNHost, EncounterInfo>>();
		startTimestamps = new HashMap<DTNHost, Double>();
		//connHistory = new HashMap<DTNHost, List<Duration>>();
	}
	
	@Override
	public MessageRouter replicate() 
	{
		return new SprayAndFocusRouterFrequency(this);
	}

	/**
	 * Called whenever a connection goes up or comes down.
	 */
	@Override
	public void changedConnection(Connection con)
	{
		super.changedConnection(con);
		
		/*
		 * The paper for this router describes Message summary vectors 
		 * (from the original Epidemic paper), which
		 * are exchanged between hosts when a connection is established. This
		 * functionality is already handled by the simulator in the protocol
		 * implemented in startTransfer() and receiveMessage().
		 * 
		 * Below we need to implement sending the corresponding message.
		 */
		DTNHost thisHost = getHost();
		DTNHost peer = con.getOtherNode(thisHost);
		
		//do this when con is up and goes down (might have been up for awhile)
		if(FreqEncounters.containsKey(peer)){ 
			EncounterInfo info = FreqEncounters.get(peer);
			if(con.isUp()){
//				info.updateEncounterFreq(info.getFreq());
				info.updateFreq();
				System.out.println("node "+thisHost+" peer "+peer.getAddress()+" freq "+info.getFreq() );	
			}
			FreqEncounters.put(peer, info);
			
		}else{
			if(con.isUp()){
				EncounterInfo baru=new EncounterInfo();
				baru.updateFreq();
				System.out.println("node "+thisHost+" peer "+peer.getAddress()+" freq "+baru.getFreq() );
				FreqEncounters.put(peer, baru);
			}
//			FreqEncounters.put(peer, new EncounterInfo(SimClock.getTime()));
		}
		
		if(!con.isUp()){
			neighborEncounters.remove(peer);
			return;
		}
		
		/*
		 * For this simulator, we just need a way to give the other node in this connection
		 * access to the peers we recently encountered; so we duplicate the FreqEncounters
		 * Map and attach it to a message.se
		 */
		int msgSize = FreqEncounters.size() * 64 + getMessageCollection().size() * 8;
		Message newMsg = new Message(thisHost, peer, SUMMARY_XCHG_IDPREFIX + protocolMsgIdx++, msgSize);
		newMsg.addProperty(SUMMARY_XCHG_PROP, /*new HashMap<DTNHost, EncounterInfo>(*/FreqEncounters);
		
		createNewMessage(newMsg);
	}

	@Override
	public boolean createNewMessage(Message m)
	{
		
		makeRoomForNewMessage(m.getSize());

		m.addProperty(MSG_COUNT_PROP, new Integer(initialNrofCopies));
		addToMessages(m, true);
		return true;
	}

	@Override
	public Message messageTransferred(String id, DTNHost from)
	{
		Message m = super.messageTransferred(id, from);
		
		/*
		 * Here we update our last encounter times based on the information sent
		 * from our peer. 
		 */
		Map<DTNHost, EncounterInfo> peerEncounters = (Map<DTNHost, EncounterInfo>)m.getProperty(SUMMARY_XCHG_PROP);
		if(isDeliveredMessage(m) && peerEncounters != null)
		{
			double distTo = getHost().getLocation().distance(from.getLocation());
			double speed = from.getPath() == null ? 0 : from.getPath().getSpeed();
			
			if(speed == 0.0) return m;
			
			
//			double timediff = distTo/speed;
//			System.out.println("weeeeeeek");
			/*
			 * We save the peer info for the utility based forwarding decisions, which are
			 * implemented in update()
			 */
			neighborEncounters.put(from, peerEncounters); 
			
			for(Map.Entry<DTNHost, EncounterInfo> entry : peerEncounters.entrySet())
			{
				DTNHost h = entry.getKey();
				if(h == getHost()) continue;
				
				EncounterInfo peerEncounter = entry.getValue();
				EncounterInfo info = FreqEncounters.get(h);
				
				/*
				 * We set our timestamp for some node, h, with whom our peer has come in contact
				 * if our peer has a newer timestamp beyond some threshold.
				 * 
				 * The paper describes timers that count up from the time of contact. We use
				 * fixed timestamps here to accomplish the same effect, but the computations
				 * here are consequently a little different from the paper. 
				 * 
				 * 
				 */
//				if(!FreqEncounters.containsKey(h)){
//					info = new EncounterInfo();
//					FreqEncounters.put(h, info);
//					continue;
//				}
				
				
//				if(info.getFreq() < peerEncounter.getFreq())
//				{
//					FreqEncounters.get(h).updateEncounterFreq(peerEncounter.getFreq());
//				}
			}
			return m;
		}
		
		//Normal message beyond here
		
		Integer nrofCopies = (Integer)m.getProperty(MSG_COUNT_PROP);
		
		nrofCopies = (int)Math.ceil(nrofCopies/2.0);
		
		m.updateProperty(MSG_COUNT_PROP, nrofCopies);
		
		return m;
	}

	@Override
	protected void transferDone(Connection con){
		Integer nrofCopies;
		String msgId = con.getMessage().getId();
		/* get this router's copy of the message */
		Message msg = getMessage(msgId);

		if (msg == null) { // message has been dropped from the buffer after..
			return; // ..start of transfer -> no need to reduce amount of copies
		}
		
		// terkirim ke final destination (kasus #2)
		if(msg.getProperty(SUMMARY_XCHG_PROP) != null)
		{
			deleteMessage(msgId, false);
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
			deleteMessage(msgId, false);
		
		msg.updateProperty(MSG_COUNT_PROP, nrofCopies);
	}
	
	

	@Override
	public void update()
	{
		super.update();
		if (!canStartTransfer() || isTransferring()) {
			return; // nothing to transfer or is currently transferring 
		}

		/* try messages that could be delivered to final recipient */
		if (exchangeDeliverableMessages() != null) {
			return;
		}
		
		List<Message> spraylist = new ArrayList<Message>();
		List<Tuple<Message,Connection>> focuslist = new LinkedList<Tuple<Message,Connection>>();

		for (Message m : getMessageCollection())
		{
			if(m.getProperty(SUMMARY_XCHG_PROP) != null) continue;
			
			Integer nrofCopies = (Integer)m.getProperty(MSG_COUNT_PROP);
			assert nrofCopies != null : "SnF message " + m + " didn't have " + 
				"nrof copies property!";
			if (nrofCopies > 1)
			{
				spraylist.add(m);
			}
			else
			{
				/*
				 * Here we implement the single copy utility-based forwarding scheme.
				 * The utility function is the last encounter time of the msg's 
				 * destination node. If our peer has a newer time (beyond the threshold),
				 * we forward the msg on to it. 
				 */
				DTNHost dest = m.getTo();
				Connection toSend = null;
				int maxPeerLastFreq = 0; //beginning of time (simulation time)
				
				//Get the timestamp of the last time this Host saw the destination
				int thisFreq = getFreqEncounterForHost(dest);
				double margin = thisFreq*0.3;
				for(Connection c : getConnections()){
					DTNHost peer = c.getOtherNode(getHost());
					Map<DTNHost, EncounterInfo> peerEncounters = neighborEncounters.get(peer);
					int peerLastFreq = 0;
					
					if(peerEncounters != null && peerEncounters.containsKey(dest))
						peerLastFreq = neighborEncounters.get(peer).get(dest).getFreq();
					
					/*
					 * We need to pick only one peer to send the copy on to; so lets find the
					 * one with the newest encounter time.
					 */
					
						if(peerLastFreq > maxPeerLastFreq)
						{
							toSend = c;
							maxPeerLastFreq = peerLastFreq;
//							System.out.println(maxpeerLastFreq);
						}
							
				}
				//if (toSend != null && maxpeerLastFreq > thisFreq + margin)
				if (toSend != null && maxPeerLastFreq > thisFreq + margin){
//					System.out.println("max :"+maxpeerLastFreq+" this : "+thisFreq);
					focuslist.add(new Tuple<Message, Connection>(m, toSend));
//					System.out.println("focus");
				}
			}
		}
		
		//arbitrarily favor spraying
		if(tryMessagesToConnections(spraylist, getConnections()) == null)
		{
			if(tryMessagesForConnected(focuslist) != null)
			{
				
			}
		}
	}

	protected int getFreqEncounterForHost(DTNHost host)
	{
		if(FreqEncounters.containsKey(host))
			return FreqEncounters.get(host).getFreq();
		else
			return 0;
	}
	
	//tambah untuk hitung durasi
	
	public void connectionUp(DTNHost thisHost, DTNHost peer){}

	/**
	 * Starts timing the duration of this new connection and informs the community
	 * detection object that a new connection was formed.
	 * 
	 * @see routing.RoutingDecisionEngine#doExchangeForNewConnection(core.Connection, core.DTNHost)
	 */

	/**
	 * Stores all necessary info about encounters made by this host to some other host.
	 * At the moment, all that's needed is the timestamp of the last time these two hosts
	 * met.
	 * 
	 * @author PJ Dillon, University of Pittsburgh
	 */
	protected class EncounterInfo
	{

		protected int FreqTime;

		public EncounterInfo(){
			this.FreqTime= 0;
		}
		
		public void updateEncounterFreq(){
			updateFreq();
		}
		//tambah
		public int getFreq(){
			return FreqTime;
		}		
		public void updateFreq(){
			this.FreqTime++;
		}
		
	}
}