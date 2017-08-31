package routing;

import java.util.HashMap;
import java.util.Map;

import core.Connection;
import core.DTNHost;
import core.Message;
import core.Settings;
import core.SimClock;

public class SprayAndFocusDurRouter implements RoutingDecisionEngine {
	/** SprayAndFocus router's settings name space ({@value} ) */
	public static final String SPRAYANDFOCUS_NS = "SprayAndFocusFreqRouter";

	/** identifier for the initial number of copies setting ({@value} ) */
	public static final String NROF_COPIES_S = "nrofCopies";
	/**
	 * identifier for the difference in timer values needed to forward on a
	 * message copy
	 */
	public static final String TIMER_THRESHOLD_S = "transitivityTimerThreshold";
	/** Message property key for the remaining available copies of a message */
	public static final String MSG_COUNT_PROP = "SprayAndFocusFreqRouter.copies";
	/**
	 * Message property key for summary vector messages exchanged between direct
	 * peers
	 */
	public static final String SUMMARY_XCHG_PROP = "SprayAndFocusFreqRouter.protoXchg";

	protected static final String SUMMARY_XCHG_IDPREFIX = "summary";
	protected static final double defaultTransitivityThreshold = 60.0;
	protected static int protocolMsgIdx = 0;

	protected int initialNrofCopies;
	protected double transitivityTimerThreshold;

	/** Stores information about nodes with which this host has come in contact */
	protected Map<DTNHost, EncounterInfoDuration> DurEncounters;
	private String fase="";
	
	public SprayAndFocusDurRouter(Settings s) {
		Settings snf = new Settings(SPRAYANDFOCUS_NS);
		initialNrofCopies = snf.getInt(NROF_COPIES_S);

		if (snf.contains(TIMER_THRESHOLD_S))
			transitivityTimerThreshold = snf.getDouble(TIMER_THRESHOLD_S);
		else
			transitivityTimerThreshold = defaultTransitivityThreshold;

		DurEncounters = new HashMap<DTNHost, EncounterInfoDuration>();
	}

	public SprayAndFocusDurRouter(SprayAndFocusDurRouter r) {
		this.initialNrofCopies = r.initialNrofCopies;
		DurEncounters = new HashMap<DTNHost, EncounterInfoDuration>();
	}

	@Override
	public void connectionUp(DTNHost thisHost, DTNHost peer) {
		if (this.DurEncounters.containsKey(peer)) {
			EncounterInfoDuration info = this.DurEncounters.get(peer);
			info.updateEncounterTime(SimClock.getTime(), "start");
	
			this.DurEncounters.put(peer, info);
//			System.out.println("node " + thisHost+ " peer " + peer.getAddress() + " freq " + info.getDurationTime());

		} else {
			EncounterInfoDuration baru = new EncounterInfoDuration();
			baru.updateEncounterTime(SimClock.getTime(), "start");
	
			this.DurEncounters.put(peer, baru);
//			System.out.println("node " + thisHost + " peer " + peer.getAddress() + " freq " + baru.getDurationTime());
		}
	}

	@Override
	public void connectionDown(DTNHost thisHost, DTNHost peer) {
		if (this.DurEncounters.containsKey(peer)) {
			EncounterInfoDuration info = this.DurEncounters.get(peer);
			info.updateEncounterTime(SimClock.getTime(), "end");
	
			this.DurEncounters.put(peer, info);
//			System.out.println("node " + thisHost+ " peer " + peer.getAddress() + " freq " + info.getDurationTime());

		} else {
			EncounterInfoDuration baru = new EncounterInfoDuration();
			baru.updateEncounterTime(SimClock.getTime(), "end");
	
			this.DurEncounters.put(peer, baru);
//			System.out.println("node " + thisHost + " peer " + peer.getAddress() + " freq " + baru.getDurationTime());
		}
	}

	@Override
	public void doExchangeForNewConnection(Connection con, DTNHost peer) {
		DTNHost thisHost = con.getOtherNode(peer);
		int msgSize = DurEncounters.size() * 64
				+ thisHost.getMessageCollection().size() * 8;
		Message newMsg = new Message(thisHost, peer, SUMMARY_XCHG_IDPREFIX
				+ protocolMsgIdx++, msgSize);
		// newMsg.addProperty(SUMMARY_XCHG_PROP,DurEncounters);
		newMessage(newMsg);
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
		// System.out.println(thisHost.getMessageCollection());
		Integer nrofCopies = (Integer) m.getProperty(MSG_COUNT_PROP);
		nrofCopies = (int) Math.ceil(nrofCopies / 2.0);
		m.updateProperty(MSG_COUNT_PROP, nrofCopies);
		return !m.getTo().equals(thisHost);
	}

	@Override
	public boolean shouldSendMessageToHost(Message m, DTNHost otherHost) {
		DTNHost dest = m.getTo();
		SprayAndFocusDurRouter de = getOtherDecisionEngine(otherHost);
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
			double maxPeerFreq = 0.0;
			double thisLastDur = this.getDurEncounter(dest);
//			MessageRouter otherRouter = otherHost.getRouter();
			DTNHost thisHost=null;
			
			for (Connection c : otherHost.getConnections()) {
				DTNHost cekHost = c.getOtherNode(otherHost);
				SprayAndFocusDurRouter cek=this.getOtherDecisionEngine(cekHost);
				if(cek.equals(this)){
					thisHost=cekHost;
					break;
				}
			}
//			System.out.println(thisHost+" other : "+otherHost);
			DTNHost maxpeer=null;
			SprayAndFocusDurRouter max = null;
			for (Connection c : thisHost.getConnections()) {
				DTNHost peer = c.getOtherNode(thisHost);
				double peerLastFreq = 0;
				SprayAndFocusDurRouter de1 = getOtherDecisionEngine(peer);
				if (de1.DurEncounters.containsKey(dest)) {
					peerLastFreq = de.getDurEncounter(dest);
				}
				if (peerLastFreq > maxPeerFreq) {
					max = de1;
					maxpeer=peer;
					maxPeerFreq = peerLastFreq;
				}
			}
			if (max != null) {
				if (max.getDurEncounter(dest) > thisLastDur) {
					if(max.equals(de)){
//						System.out.println("focus");
						return true;
					}else{
						fase="focus";
//						System.out.println("focus oper");
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
		return false;
	}

	@Override
	public RoutingDecisionEngine replicate() {
		return new SprayAndFocusDurRouter(this);
	}

	private SprayAndFocusDurRouter getOtherDecisionEngine(DTNHost h) {
		MessageRouter otherRouter = h.getRouter();
		assert otherRouter instanceof DecisionEngineRouter : "This router only works with other routers of same type";

		return (SprayAndFocusDurRouter) ((DecisionEngineRouter) otherRouter)
				.getDecisionEngine();
	}

	protected double getDurEncounter(DTNHost host) {
		if (DurEncounters.containsKey(host)){
			return DurEncounters.get(host).getDurationTime();
		}else{
			return 0.0;
		}
	}

}
