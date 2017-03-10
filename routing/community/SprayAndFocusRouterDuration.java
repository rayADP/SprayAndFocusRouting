package routing.community;

import java.util.*;

import core.*;
import routing.*;
//import routing.SprayAndFocusRouter.EncounterInfo;

/**
 * An implementation of Spray and Focus DTN routing as described in 
 * <em>Spray and Focus: Efficient Mobility-Assisted Routing for Heterogeneous
 * and Correlated Mobility</em> by Thrasyvoulos Spyropoulos et al.
 * 
 * @author PJ Dillon, University of Pittsburgh
 * stressed by Raymond A.D.P., Sanata Dharma University of Yogyakarta, Indonesia
 */
public class SprayAndFocusRouterDuration extends ActiveRouter  
{
	/** SprayAndFocus router's settings name space ({@value})*/ 
	public static final String SPRAYANDFOCUS_NS = "SprayAndFocusRouter";
	/** identifier for the initial number of copies setting ({@value})*/ 
	public static final String NROF_COPIES_S = "nrofCopies";
	/** identifier for the difference in timer values needed to forward on a message copy  = menentukan perbedaan nilai waktu yang dibutuhkan untuk maju pada salinan pesan*/
	public static final String TIMER_THRESHOLD_S = "transitivityTimerThreshold";
	
	/** Message property key for the remaining available copies of a message */
	public static final String MSG_COUNT_PROP = "SprayAndFocus.copies";
	/** Message property key for summary vector messages exchanged between direct peers */
	public static final String SUMMARY_XCHG_PROP = "SprayAndFocus.protoXchg";
	
	protected static final String SUMMARY_XCHG_IDPREFIX = "summary";
	protected static final double defaultTransitivityThreshold = 60.0;
	protected static int protocolMsgIdx = 0;
	
	protected int initialNrofCopies;
	protected double transitivityTimerThreshold;
	
	// variabel from LDE
	public static final String COMMUNITY_ALG_SETTING = "communityDetectAlg";
	protected Map<DTNHost, DTNHost> durationEncounters;
	protected Map<DTNHost, EncounterInfo> recentDuration;
	protected CommunityDetection community;
	
	/** 
	 * Records the times at which each open connection started. Used to compute
	 * the duration of each connection (needed by the community detection algs).
	 */
	protected Map<DTNHost, Double> startTimestamps;
	
	/**
	 * A record of the entire connection history of this node for the whole 
	 * simulation. As each connection goes down, a new entry is added into the 
	 * list for the peer that just disconnected. 
	 */
	protected Map<DTNHost, List<Duration>> connHistory;
	
	/** Stores information about nodes with which this host has come in contact */
	protected Map<DTNHost, EncounterInfo> recentEncounters;
	protected Map<DTNHost, Map<DTNHost, EncounterInfo>> neighborEncounters;
	
	
	public SprayAndFocusRouterDuration(Settings s)
	{
		super(s);
		Settings snf = new Settings(SPRAYANDFOCUS_NS);
		initialNrofCopies = snf.getInt(NROF_COPIES_S);
		
		if(snf.contains(TIMER_THRESHOLD_S))
			transitivityTimerThreshold = snf.getDouble(TIMER_THRESHOLD_S);
		else
			transitivityTimerThreshold = defaultTransitivityThreshold;
		recentDuration = new HashMap<DTNHost, EncounterInfo>();
		neighborEncounters = new HashMap<DTNHost, Map<DTNHost, EncounterInfo>>();
	}
	
	/**
	 * Copy Constructor.
	 * 
	 * @param r The router from which settings should be copied
	 */
	public SprayAndFocusRouterDuration(SprayAndFocusRouterDuration  r)
	{
		super(r);
		this.initialNrofCopies = r.initialNrofCopies;
		
		recentDuration = new HashMap<DTNHost, EncounterInfo>();
		neighborEncounters = new HashMap<DTNHost, Map<DTNHost, EncounterInfo>>();
	}
	
	@Override
	public MessageRouter replicate() 
	{
		return new SprayAndFocusRouterDuration (this);
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
		if(recentDuration.containsKey(peer))
		{ 	
			EncounterInfo info = recentDuration.get(peer);
			info.updateEncounterTime(SimClock.getTime());
		}
		else
		{
			recentDuration.put(peer, new EncounterInfo(SimClock.getTime()));
		}
		
		if(!con.isUp())
		{			
			neighborEncounters.remove(peer);
			this.connectionDown(thisHost, peer);//call to count time
			
			return;
		}else {
			startTimestamps.put(thisHost, SimClock.getTime());
		}
	/*
		 * For this simulator, we just need a way to give the other node in this connection
		 * access to the peers we recently encountered; so we duplicate the recentEncounters
		 * Map and attach it to a message.se
		 */
		int msgSize = recentDuration.size() * 64 + getMessageCollection().size() * 8;
		Message newMsg = new Message(thisHost, peer, SUMMARY_XCHG_IDPREFIX + protocolMsgIdx++, msgSize);
		newMsg.addProperty(SUMMARY_XCHG_PROP, /*new HashMap<DTNHost, EncounterInfo>(*/recentDuration);
		
		createNewMessage(newMsg);
	}

	public void connectionUp(DTNHost thisHost, DTNHost peer){}

	/**
	 * Records the duration of the lost connection and informs the Community
	 * Detection object that the connection was last.
	 */
	public void connectionDown(DTNHost thisHost, DTNHost peer)
	{
		double time = startTimestamps.get(peer);
		double etime = SimClock.getTime();
		
		// Find or create the connection history list
		List<Duration> history;
		if(!connHistory.containsKey(peer))
		{
			history = new LinkedList<Duration>();
			connHistory.put(peer, history);
		}
		else
			history = connHistory.get(peer);
		
		// add the new connection to the history
		if(etime - time > 0)
			history.add(new Duration(time, etime));
		
		// Inform the community detection object
		CommunityDetection peerCD = this.getOtherDecisionEngine(peer).community;
		community.connectionLost(thisHost, peer, peerCD, history);
		
		startTimestamps.remove(peer);
	}

	/**
	 * Starts timing the duration of the new connection and informs the community
	 * detection object of the new connection.
	 */
	public void doExchangeForNewConnection(Connection con, DTNHost peer)
	{
		DTNHost myHost = con.getOtherNode(peer);
		LABELDecisionEngine de = this.getOtherDecisionEngine(peer);
		
		this.community.newConnection(myHost, peer, de.community);

		this.startTimestamps.put(peer, SimClock.getTime());
		de.startTimestamps.put(myHost, SimClock.getTime());
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
			
			double timediff = distTo/speed;
			
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
				EncounterInfo info = recentDuration.get(h);
				
				/*
				 * We set our timestamp for some node, h, with whom our peer has come in contact
				 * if our peer has a newer timestamp beyond some threshold.
				 * 
				 * The paper describes timers that count up from the time of contact. We use
				 * fixed timestamps here to accomplish the same effect, but the computations
				 * here are consequently a little different from the paper. 
				 */
				if(!recentDuration.containsKey(h))
				{
					info = new EncounterInfo(peerEncounter.getDurashi() - timediff);
					recentDuration.put(h, info);
					continue;
				}
				
				
				if(info.getDurashi() + timediff < peerEncounter.getDurashi())
				{
					recentDuration.get(h).updateEncounterTime(peerEncounter.getDurashi() - 
							timediff);
				}
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
	protected void transferDone(Connection con) 
	
	{
		Integer nrofCopies;
		String msgId = con.getMessage().getId();
		/* get this router's copy of the message */
		Message msg = getMessage(msgId);

		if (msg == null) { // message has been dropped from the buffer after..
			return; // ..start of transfer -> no need to reduce amount of copies
		}
		
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
				double maxPeerLastSeen = 0.0; //beginning of time (simulation time)
				double maxDuration = 0.0; //beginning of time (simulation time)
				//Get the timestamp of the last time this Host saw the destination
				//double thisLastSeen = getLastEncounterTimeForHost(dest);
				double thisDuration = getDurationTimeForHost(dest);
				
				for(Connection c : getConnections())
				{
					DTNHost peer = c.getOtherNode(getHost());
					Map<DTNHost, EncounterInfo> peerEncounters = neighborEncounters.get(peer);
					double peerDurasi = 0.0;
					
					if(peerEncounters != null && peerEncounters.containsKey(dest))
						peerDurasi= neighborEncounters.get(peer).get(dest).getDurashi();
					
					/*
					 * We need to pick only one peer to send the copy on to; so lets find the
					 * one with the newest encounter time.
					 */
					
						if(peerDurasi > maxDuration)
						{
							toSend = c;
							maxDuration= peerDurasi;
						}
			
				}
				if (toSend != null && maxDuration > thisDuration + transitivityTimerThreshold)
				{
					focuslist.add(new Tuple<Message, Connection>(m, toSend));
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

	protected double getLastEncounterTimeForHost(DTNHost host)
	{
		if(recentEncounters.containsKey(host))
			return ((EncounterInfo) recentEncounters.get(host)).getLastSeenTime();
		else
			return 0.0;
	}
	
	protected double getDurationTimeForHost(DTNHost host)
	{
		if(recentDuration.containsKey(host))
			return ((EncounterInfo) recentDuration.get(host)).getDurashi();
		else
			return 0.0;
	}
	
	private LABELDecisionEngine getOtherDecisionEngine(DTNHost h)
	{
		MessageRouter otherRouter = h.getRouter();
		assert otherRouter instanceof DecisionEngineRouter : 
			"This router only works with other routers of same type";
		
		return (LABELDecisionEngine) ((DecisionEngineRouter)otherRouter)
						.getDecisionEngine();
	}

	
	/**
	 * Stores all necessary info about encounters made by this host to some other host.
	 * At the moment, all that's needed is the timestamp of the last time these two hosts
	 * met.
	 * 
	 * @author PJ Dillon, University of Pittsburgh
	 */
	protected class EncounterInfo
	{
		protected double seenAtTime;
		protected double Durashi;
		
		public EncounterInfo(double atTime)
		{
			this.seenAtTime = atTime;
			this.Durashi=atTime;
		}		
		public void updateEncounterTime(double atTime)
		{
			this.seenAtTime = atTime;
			this.Durashi = atTime;
		}
		
		
		
		public double getLastSeenTime()
		{
			return seenAtTime;
		}		
		public void updateLastSeenTime(double atTime)
		{
			this.seenAtTime = atTime;
		}
		
		
		public double getDurashi()
		{
			return Durashi;
		}		
		public void updateDurashi(double atTime)
		{
			this.Durashi= atTime;
		}
	}
}