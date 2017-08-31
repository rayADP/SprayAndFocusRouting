package routing;

import java.util.HashMap;
//import java.util.LinkedList;
//import java.util.List;
//import java.util.List<routing.community.Duration>;
import java.util.Map;

import routing.DecisionEngineRouter;
import routing.MessageRouter;
import routing.RoutingDecisionEngine;
//import routing.community.Duration;

import core.Connection;
import core.DTNHost;
import core.Message;
import core.Settings;
import core.SimClock;

public class FocusDuration1 implements RoutingDecisionEngine {
	/** SprayAndFocus router's settings name space ({@value} ) */
	public static final String SPRAYANDFOCUS_NS = "FocusDuration1";

	/** identifier for the initial number of copies setting ({@value} ) */
	public static final String NROF_COPIES_S = "nrofCopies";

	/** Message property key for the remaining available copies of a message */
	public static final String MSG_COUNT_PROP = "FocusDuration1.copies";
	/**
	 * Message property key for summary vector messages exchanged between direct
	 * peers
	 */
	public static final String SUMMARY_XCHG_PROP = "FocusDuration1.protoXchg";

	protected static final String SUMMARY_XCHG_IDPREFIX = "summary";

	protected static int protocolMsgIdx = 0;

	protected int initialNrofCopies;

	/** Stores information about nodes with which this host has come in contact */
	protected Map<DTNHost, Double> DurEncounters;
	private String fase = "";

	protected Map<DTNHost, Double> startTimestamps;

	public FocusDuration1(Settings s) {
		Settings snf = new Settings(SPRAYANDFOCUS_NS);
		initialNrofCopies = snf.getInt(NROF_COPIES_S);
		startTimestamps = new HashMap<DTNHost, Double>(); 
		DurEncounters = new HashMap<DTNHost, Double>();
	}

	public FocusDuration1(FocusDuration1 r) {
		this.initialNrofCopies = r.initialNrofCopies;
		DurEncounters = new HashMap<DTNHost, Double>();
		startTimestamps = new HashMap<DTNHost, Double>();
	}

	@Override
	public void connectionUp(DTNHost thisHost, DTNHost peer) {
	}

	@Override
	public void doExchangeForNewConnection(Connection con, DTNHost peer) {
		DTNHost myHost = con.getOtherNode(peer);
		FocusDuration1 de = this.getOtherDecisionEngine(peer);

		this.startTimestamps.put(peer, SimClock.getTime());
		de.startTimestamps.put(myHost, SimClock.getTime());
	}

	@Override
	public void connectionDown(DTNHost thisHost, DTNHost peer) {

		double time = startTimestamps.get(peer);

		double etime = SimClock.getTime();

		double durasi = etime - time;
		if (durasi > 0) {

			if (DurEncounters.containsKey(peer))

			{
				durasi = durasi + this.getDurEncounter(peer);

			}
			DurEncounters.put(peer, durasi);
			System.out.println("Node "+thisHost+" to "+peer+" duration : "+this.getDurEncounter(peer));
		}

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
		FocusDuration1 de = getOtherDecisionEngine(otherHost);
		if (dest == otherHost) {
			return true;
		}
		if (m.getProperty(SUMMARY_XCHG_PROP) != null) {
			return false;
		}
		Integer nrofCopies = (Integer) m.getProperty(MSG_COUNT_PROP);
		assert nrofCopies != null : "SnF message " + m
				+ " didn't have nrof copies property!";
		if (fase.equals("focus")) {
			fase = "";
			return true;
		}
		if (nrofCopies > 1) {
			fase = "spray";
			return true;
		} else {
			double maxDurationPeer= 0.0;
			double thisLastDur = this.getDurEncounter(dest);
			// MessageRouter otherRouter = otherHost.getRouter();
			DTNHost thisHost = null;

			for (Connection c : otherHost.getConnections()) {
				DTNHost cekHost = c.getOtherNode(otherHost);
				FocusDuration1 cek = this.getOtherDecisionEngine(cekHost);
				if (cek.equals(this)) {
					thisHost = cekHost;
					break;
				}
			}
			// System.out.println(thisHost+" other : "+otherHost);
			DTNHost maxpeer = null;
			FocusDuration1 max = null;
			for (Connection c : thisHost.getConnections()) {
				DTNHost peer = c.getOtherNode(thisHost);
				double peerLastDuration = 0.0;
				FocusDuration1 de1 = getOtherDecisionEngine(peer);
				if (de1.DurEncounters.containsKey(dest)) {
					peerLastDuration = de.getDurEncounter(dest);
				}
				if (peerLastDuration  > maxDurationPeer) {
					max = de1;
					maxpeer = peer;
					maxDurationPeer = peerLastDuration ;
				}
			}
			if (max != null) {
//				double margin = thisLastDur*0.5;
				if (max.getDurEncounter(dest) > thisLastDur) {
					if (max.equals(de)) {
						// System.out.println("focus");
						return true;
					} else {
						fase = "focus";
						// System.out.println("focus oper");
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
		return new FocusDuration1(this);
	}

	private FocusDuration1 getOtherDecisionEngine(DTNHost h) {
		MessageRouter otherRouter = h.getRouter();
		assert otherRouter instanceof DecisionEngineRouter : "This router only works with other routers of same type";

		return (FocusDuration1) ((DecisionEngineRouter) otherRouter)
				.getDecisionEngine();
	}

	protected double getDurEncounter(DTNHost host) {
		if (DurEncounters.containsKey(host)) {
			return DurEncounters.get(host);
		} else {
			return 0.0;

		}
	}

}
