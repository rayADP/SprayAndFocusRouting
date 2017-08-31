package routing;

//import java.util.Collection;
//import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

//import java.util.List;

import routing.EncounterInfoFreq;
//import routing.community.DistributedBubbleRap;
import core.Connection;
import core.DTNHost;
import core.Message;
import core.Settings;
import core.Tuple;

public class FocusFreq implements RoutingDecisionEngine {
	/** SprayAndFocus router's settings name space ({@value} ) */
	public static final String SPRAYANDFOCUS_NS = "FocusFreq";
	/** identifier for the initial number of copies setting ({@value} ) */
	public static final String NROF_COPIES_S = "nrofCopies";
	/**
	 * identifier for the difference in timer values needed to forward on a
	 * message copy = menentukan selisih nilai waktu yang dibutuhkan untuk
	 * meneruskan salinan pesan
	 */
	public static final String TIMER_THRESHOLD_S = "transitivityTimerThreshold";

	/** Message property key for the remaining available copies of a message */
	public static final String MSG_COUNT_PROP = "FocusFreq.copies";
	/**
	 * Message property key for summary vector messages exchanged between direct
	 * peers
	 */
	public static final String SUMMARY_XCHG_PROP = "FocusFreq.protoXchg";

	protected static final String SUMMARY_XCHG_IDPREFIX = "summary";
	protected static final double defaultTransitivityThreshold = 60.0;
	protected static int protocolMsgIdx = 0;

	protected int initialNrofCopies;
	protected double transitivityTimerThreshold;

	/** Stores information about nodes with which this host has come in contact */
	// protected Map<DTNHost, EncounterInfoFreq> recentEncounters;

	protected Map<DTNHost, EncounterInfoFreq> FreqEncounters;

	public FocusFreq(Settings s) {
		Settings snf = new Settings(SPRAYANDFOCUS_NS);
		initialNrofCopies = snf.getInt(NROF_COPIES_S);

		if (snf.contains(TIMER_THRESHOLD_S)) {
			transitivityTimerThreshold = snf.getDouble(TIMER_THRESHOLD_S);
		} else {
			transitivityTimerThreshold = defaultTransitivityThreshold;
		}
		FreqEncounters = new HashMap<DTNHost, EncounterInfoFreq>();

	}

	public FocusFreq(FocusFreq r) {
		// super(r);
		this.initialNrofCopies = r.initialNrofCopies;

		FreqEncounters = new HashMap<DTNHost, EncounterInfoFreq>();
	}

	@Override
	public void connectionUp(DTNHost thisHost, DTNHost peer) {
		// DTNHost thisHost = con.getOtherNode(peer);
		// FocusFreq de = this.getOtherDecisionEngine(peer);
		if (this.FreqEncounters.containsKey(peer)) {
			EncounterInfoFreq info = this.FreqEncounters.get(peer);
			// EncounterInfoFreq info2 = de.FreqEncounters.get(peer);
			info.updateFreq();
			// System.out.println("node " + thisHost + " peer "
			// + peer.getAddress() + " freq " + info.getFreq());
			// System.out.println();
			this.FreqEncounters.put(peer, info);
			// de.FreqEncounters.put(thisHost, info);
		} else {
			EncounterInfoFreq baru = new EncounterInfoFreq();
			baru.updateFreq();
			// System.out.println("node " + thisHost + " peer "
			// + peer.getAddress() + " freq " + baru.getFreq());
			this.FreqEncounters.put(peer, baru);
			// de.FreqEncounters.put(thisHost, baru);
		}
	}

	@Override
	public void connectionDown(DTNHost thisHost, DTNHost peer) {

	}

	@Override
	public void doExchangeForNewConnection(Connection con, DTNHost peer) {

		 DTNHost thisHost = con.getOtherNode(peer);
		 FocusFreq de = this.getOtherDecisionEngine(peer);
		 
		 de.getFreqEncounterForHost(thisHost);
		 this.getFreqEncounterForHost(peer);
		 
		

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

		DTNHost dest = m.getTo();
		FocusFreq de = this.getOtherDecisionEngine(otherHost);
		Integer nrofCopies = (Integer) m.getProperty(MSG_COUNT_PROP);
		if (m.getTo() == otherHost) {
			return true;
		}

		else if (nrofCopies > 1) {
			// System.out.println("spray TTL : "+m.getTtl()+" ID : "+m.getId());
			// System.out.println(" belum kirim "+nrofCopies);

			nrofCopies = (int) Math.ceil(nrofCopies / 2.0);

			m.updateProperty(MSG_COUNT_PROP, nrofCopies);
			// System.out.println("kirim "+nrofCopies);
			return true;

		} else if (nrofCopies == 1) {
			// System.out.println(" harus = 1 >>> "+nrofCopies);

			// double margin = this.getFreqEncounterForHost(dest) * 0.3;

			if (de.getFreqEncounterForHost(dest) > this
					.getFreqEncounterForHost(dest)) {
				// System.out.println("focus TTL : "+m.getTtl()+" ID : "+m.getId());
				// System.out.println(" fre3q peer "+de.getFreqEncounterForHost(dest)+" > Freq aku "+this.getFreqEncounterForHost(dest)+" freq+margin gue "+(double)(this.getFreqEncounterForHost(dest)+margin));

				return true;

			}
		}
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
		return new FocusFreq(this);
	}

	protected int getFreqEncounterForHost(DTNHost host) {// done
		if (this.FreqEncounters.containsKey(host)) {
			return this.FreqEncounters.get(host).getFreq();
		} else {
			return 0;
		}
	}

	private FocusFreq getOtherDecisionEngine(DTNHost h) {
		MessageRouter otherRouter = h.getRouter();
		assert otherRouter instanceof DecisionEngineRouter : "This router only works with other routers of same type";

		return (FocusFreq) ((DecisionEngineRouter) otherRouter)
				.getDecisionEngine();
	}
}
