package routing;

import java.util.HashMap;
import java.util.Map;

import core.Connection;
import core.DTNHost;
import core.Message;
import core.Settings;
import core.SimClock;

public class Focus implements RoutingDecisionEngine {
	/** SprayAndFocus router's settings name space ({@value} ) */
	public static final String SPRAYANDFOCUS_NS = "Focus";

	/** identifier for the initial number of copies setting ({@value} ) */
	public static final String NROF_COPIES_S = "nrofCopies";
	
	/** Message property key for the remaining available copies of a message */
	public static final String MSG_COUNT_PROP = "Focus.copies";
	/**
	 * Message property key for summary vector messages exchanged between direct
	 * peers
	 */
	public static final String SUMMARY_XCHG_PROP = "Focus.protoXchg";

	protected static final String SUMMARY_XCHG_IDPREFIX = "summary";
	
	protected static int protocolMsgIdx = 0;

	protected int initialNrofCopies;
	

	/** Stores information about nodes with which this host has come in contact */
	protected Map<DTNHost, Double> RecentEncounters;
	
	private String fase="";
	
	public Focus(Settings s) {
		Settings snf = new Settings(SPRAYANDFOCUS_NS);
		initialNrofCopies = snf.getInt(NROF_COPIES_S);

		
		RecentEncounters = new HashMap<DTNHost, Double>();
		
	}

	public Focus(Focus r) {
		this.initialNrofCopies = r.initialNrofCopies;
		RecentEncounters = new HashMap<DTNHost, Double>();
		
	}

	@Override
	public void connectionUp(DTNHost thisHost, DTNHost peer) {	}

	@Override
	public void connectionDown(DTNHost thisHost, DTNHost peer) {
		if (RecentEncounters.containsKey(peer)) {
			
			RecentEncounters.put(peer, new Double(SimClock.getTime()));
			 
		} 
		
		else {
			
			RecentEncounters.put(peer,new Double(SimClock.getTime()));
			 
		}
	}

	@Override
	public void doExchangeForNewConnection(Connection con, DTNHost peer) {
		
	}

	@Override
	public boolean newMessage(Message m) {
		m.addProperty(MSG_COUNT_PROP, new Integer(initialNrofCopies));
		return true;
	}

	@Override
	public boolean isFinalDest(Message m, DTNHost aHost) {
		return m.getTo().equals(aHost);
	}

	@Override
	public boolean shouldSaveReceivedMessage(Message m, DTNHost thisHost) {

		Integer nrofCopies = (Integer) m.getProperty(MSG_COUNT_PROP);
		
		nrofCopies = (int) Math.ceil(nrofCopies / 2.0);
		
		m.updateProperty(MSG_COUNT_PROP, nrofCopies);
		
		return !m.getTo().equals(thisHost);
		
	}

	@Override
	public boolean shouldSendMessageToHost(Message m, DTNHost otherHost) {
		
		DTNHost dest = m.getTo();
		
		Focus de = getOtherDecisionEngine(otherHost);
		
		if (dest == otherHost){
		
			return true;
		}
		if (m.getProperty(SUMMARY_XCHG_PROP) != null){
			return false;
		}
		Integer nrofCopies = (Integer) m.getProperty(MSG_COUNT_PROP);
		assert nrofCopies != null : "SnF message " + m
				+ " didn't have nrof copies property!";
		if(fase.equals("focus")){
			fase="";
			return true;
		}
		if (nrofCopies > 1) {
			fase="spray";
			return true;
		} else {
			double maxRecentPeer= 0;
			double RecentaTimeThisHost = this.getRecentEncounterForHost(dest);
//			MessageRouter otherRouter = otherHost.getRouter();
			DTNHost thisHost=null;
			
			for (Connection c : otherHost.getConnections()) {
				DTNHost cekHost = c.getOtherNode(otherHost);
				Focus cek=this.getOtherDecisionEngine(cekHost);
				if(cek.equals(this)){
					thisHost=cekHost;
					break;
				}
			}
//			System.out.println(thisHost+" other : "+otherHost);
			DTNHost maxpeer=null;
			Focus max = null;
			for (Connection c : thisHost.getConnections()) {
				DTNHost peer = c.getOtherNode(thisHost);
				double peerLastSeen = 0;
				Focus de1 = getOtherDecisionEngine(peer);
				if (de1.RecentEncounters.containsKey(dest)) {
					peerLastSeen = de.getRecentEncounterForHost(dest);
				}
				if (peerLastSeen > maxRecentPeer) {
					max = de1;
					maxpeer=peer;
					maxRecentPeer= peerLastSeen;
				}
			}
			if (max != null) {
//				double margin = RecentaTimeThisHost*0.5;
				if (max.getRecentEncounterForHost(dest) > RecentaTimeThisHost) {
					if(max.equals(de)){

						return true;
					}else{
						fase="focus";

						shouldSendMessageToHost(m, maxpeer);
					}
				}
			}
		}
		return false;
	}

	@Override
	public boolean shouldDeleteSentMessage(Message m, DTNHost otherHost) {
		Integer nrofCopies;

		if (m == null) { // message has been dropped from the buffer after..
			return false; // ..start of transfer -> no need to reduce amount of
							// copies
		}

		if (m.getProperty(SUMMARY_XCHG_PROP) != null) {
			return true;
		}

		/*
		 * reduce the amount of copies left. If the number of copies was at 1
		 * and we apparently just transferred the msg (focus phase), then we
		 * should delete it.
		 */
		nrofCopies = (Integer) m.getProperty(MSG_COUNT_PROP);
		if (nrofCopies > 1)
			nrofCopies /= 2;
		else
			return true;

		m.updateProperty(MSG_COUNT_PROP, nrofCopies);

		return false;
	}

	@Override
	public boolean shouldDeleteOldMessage(Message m, DTNHost hostReportingOld) {
		return true;
	}

	@Override
	public RoutingDecisionEngine replicate() {
		return new Focus(this);
	}

	private Focus getOtherDecisionEngine(DTNHost h) {
		MessageRouter otherRouter = h.getRouter();
		assert otherRouter instanceof DecisionEngineRouter : "This router only works with other routers of same type";

		return (Focus) ((DecisionEngineRouter) otherRouter)
				.getDecisionEngine();
	}

	protected double getRecentEncounterForHost(DTNHost host) {
		if (RecentEncounters.containsKey(host)){
			return RecentEncounters.get(host);
		}else{
			return 0;
		}
	}
	}

	
	
	

