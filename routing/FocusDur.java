package routing;

//import java.util.Collection;
//import java.util.Collection;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

//import java.util.List;

import routing.EncounterInfoDuration;
//import routing.community.DistributedBubbleRap;
import core.Connection;
import core.DTNHost;
import core.Message;
import core.Settings;
import core.SimClock;
import core.Tuple;

public class FocusDur implements RoutingDecisionEngine {
	/** SprayAndFocus router's settings name space ({@value} ) */
	public static final String SPRAYANDFOCUS_NS = "FocusDur";
	/** identifier for the initial number of copies setting ({@value} ) */
	public static final String NROF_COPIES_S = "nrofCopies";
	/**
	 * identifier for the difference in timer values needed to forward on a
	 * message copy = menentukan selisih nilai waktu yang dibutuhkan untuk
	 * meneruskan salinan pesan
	 */
	public static final String TIMER_THRESHOLD_S = "transitivityTimerThreshold";

	/** Message property key for the remaining available copies of a message */
	public static final String MSG_COUNT_PROP = "FocusDur.copies";
	/**
	 * Message property key for summary vector messages exchanged between direct
	 * peers
	 */
	public static final String SUMMARY_XCHG_PROP = "FocusDur.protoXchg";

	protected static final String SUMMARY_XCHG_IDPREFIX = "summary";
	protected static final double defaultTransitivityThreshold = 60.0;
	protected static int protocolMsgIdx = 0;

	protected int initialNrofCopies;
	protected double transitivityTimerThreshold;

	/** Stores information about nodes with which this host has come in contact */
	// protected Map<DTNHost, EncounterInfoDuration> recentEncounters;

	protected Map<DTNHost, EncounterInfoDuration> DurEncounters;

	public FocusDur(Settings s) {
		Settings snf = new Settings(SPRAYANDFOCUS_NS);
		initialNrofCopies = snf.getInt(NROF_COPIES_S);

		if (snf.contains(TIMER_THRESHOLD_S)) {
			transitivityTimerThreshold = snf.getDouble(TIMER_THRESHOLD_S);
		} else {
			transitivityTimerThreshold = defaultTransitivityThreshold;
		}
		DurEncounters = new HashMap<DTNHost, EncounterInfoDuration>();

	}

	public FocusDur(FocusDur r) {
		// super(r);
		this.initialNrofCopies = r.initialNrofCopies;

		DurEncounters = new HashMap<DTNHost, EncounterInfoDuration>();
	}

	@Override
	public void connectionUp(DTNHost thisHost, DTNHost peer) {

		double waktu = SimClock.getTime();
		if (this.DurEncounters.containsKey(peer)) {
			EncounterInfoDuration info = this.DurEncounters.get(peer);
			info.updateEncounterTime(waktu, "start");
			this.DurEncounters.put(peer, info);
		} else {
			EncounterInfoDuration baru = new EncounterInfoDuration();
			baru.updateEncounterTime(waktu, "start");
			this.DurEncounters.put(peer, baru);
		}
	}

	@Override
	public void connectionDown(DTNHost thisHost, DTNHost peer) {
		double waktu = SimClock.getTime();
		if (this.DurEncounters.containsKey(peer)) {
			EncounterInfoDuration info = this.DurEncounters.get(peer);
			info.updateEncounterTime(waktu, "end");
			this.DurEncounters.put(peer, info);
			// System.out.println("node " + thisHost + " peer "
			// + peer.getAddress() + " dur " + info.getDurationTime());
		} else {
			EncounterInfoDuration baru = new EncounterInfoDuration();
			baru.updateEncounterTime(waktu, "end");
			this.DurEncounters.put(peer, baru);
			// System.out.println("node " + thisHost + " peer "
			// + peer.getAddress() + " dur " + baru.getDurationTime());
		}
	}

	@Override
	public void doExchangeForNewConnection(Connection con, DTNHost peer) {

		DTNHost thisHost = con.getOtherNode(peer);
		FocusDur de = this.getOtherDecisionEngine(peer);
		Collection<Message> m = thisHost.getMessageCollection();
		Collection<Message> m1 = peer.getMessageCollection();
		for (Message ms : m) {

			boolean ada = false;
			for (Message ms1 : m1) {
				if (ms1.equals(ms)) {
					ada = true;
				}
			}
			if (ada == false) {
				DTNHost dest = ms.getTo();
				Integer nrofCopies = (Integer) ms.getProperty(MSG_COUNT_PROP);
				if (ms.getTo() == peer) {
					thisHost.sendMessage(ms.getId(), peer);
				} else if (nrofCopies > 1) {
					nrofCopies = (int) Math.ceil(nrofCopies / 2.0);
					ms.updateProperty(MSG_COUNT_PROP, nrofCopies);
					thisHost.sendMessage(ms.getId(), peer);
				} else if (nrofCopies == 1) {
					if (de.getDurEncounterForHost(dest) > this
							.getDurEncounterForHost(dest)) {
						thisHost.sendMessage(ms.getId(), peer);
					}
				}
			}
		}
	}

	@Override
	public boolean newMessage(Message m) {
		m.addProperty(MSG_COUNT_PROP, new Integer(initialNrofCopies));
		// System.out.println("Baru ID pesan : "+m.getId()+" From : "+m.getFrom()+" To : "+m.getTo());
		return true;
	}

	@Override
	public boolean isFinalDest(Message m, DTNHost aHost) {
		if (m.getTo() == aHost) {
			// System.out.println("final ID pesan : "+m.getId()+" from : "+m.getFrom()+" To : "+m.getTo());
		}
		return m.getTo() == aHost;
	}

	@Override
	public boolean shouldSaveReceivedMessage(Message m, DTNHost thisHost) {
		// System.out.println("penerima : "+thisHost+" coll :"+thisHost.getMessageCollection());
		// System.out.println("pesan yang diterima : "+m.getId());
		// System.out.println("hapus TTL : "+m.getTtl()+" ID : "+m.getId()+" final");
		return true;
	}

	@Override
	public boolean shouldSendMessageToHost(Message m, DTNHost otherHost) {

//		DTNHost dest = m.getTo();
//		FocusDur de = this.getOtherDecisionEngine(otherHost);
//		Integer nrofCopies = (Integer) m.getProperty(MSG_COUNT_PROP);
//		if (m.getTo() == otherHost) {
//			return true;
//		}
//
//		else if (nrofCopies > 1) {
//			// System.out.println("spray TTL : "+m.getTtl()+" ID : "+m.getId());
//			// System.out.println(" belum kirim "+nrofCopies);
//
//			nrofCopies = (int) Math.ceil(nrofCopies / 2.0);
//
//			m.updateProperty(MSG_COUNT_PROP, nrofCopies);
//			// System.out.println("kirim "+nrofCopies);
//			return true;
//
//		} else if (nrofCopies == 1) {
//			// System.out.println(" harus = 1 >>> "+nrofCopies);
//
//			// double margin = this.getFreqEncounterForHost(dest) * 0.3;
//
//			if (de.getDurEncounterForHost(dest) > this
//					.getDurEncounterForHost(dest)) {
//				// System.out.println("focus TTL : "+m.getTtl()+" ID : "+m.getId());
//				// System.out.println(" fre3q peer "+de.getFreqEncounterForHost(dest)+" > Freq aku "+this.getFreqEncounterForHost(dest)+" freq+margin gue "+(double)(this.getFreqEncounterForHost(dest)+margin));
//
//				return true;
//
//			}
//		}
		return true;
	}

	@Override
	public boolean shouldDeleteSentMessage(Message m, DTNHost otherHost) {
		Integer nrofCopies = (Integer) m.getProperty(MSG_COUNT_PROP);

		if (m.getTo() == otherHost) {
			// System.out.println("other : "+otherHost+" coll :"+otherHost.getMessageCollection());
			// System.out.println("hapus TTL : "+m.getTtl()+" ID : "+m.getId()+" final");
			return true;
		} else if (nrofCopies > 1) {
			return false;
		} else if (nrofCopies == 1) {
			// System.out.println("other : "+otherHost+" coll :"+otherHost.getMessageCollection());
			// System.out.println("hapus TTL : "+m.getTtl()+" ID : "+m.getId()+" L==1");
			return true;
		}
		return false;
	}

	@Override
	public boolean shouldDeleteOldMessage(Message m, DTNHost hostReportingOld) {
		return false;
	}

	@Override
	public RoutingDecisionEngine replicate() {
		// TODO Auto-generated method stub
		return new FocusDur(this);
	}

	protected double getDurEncounterForHost(DTNHost host) {// done
		if (this.DurEncounters.containsKey(host)) {
			return this.DurEncounters.get(host).getDurationTime();
		} else {
			return 0;
		}
	}

	private FocusDur getOtherDecisionEngine(DTNHost h) {
		MessageRouter otherRouter = h.getRouter();
		assert otherRouter instanceof DecisionEngineRouter : "This router only works with other routers of same type";

		return (FocusDur) ((DecisionEngineRouter) otherRouter)
				.getDecisionEngine();
	}
}
