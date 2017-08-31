package routing;

import java.util.HashMap;
import java.util.Map;

import core.Connection;
import core.DTNHost;
import core.Message;
import core.Settings;

public class SprayAndFocusFreqRouter implements RoutingDecisionEngine {
	/** SprayAndFocus router's settings name space ({@value} ) */
	public static final String SPRAYANDFOCUS_NS = "SprayAndFocusFreqRouter";

	/** identifier for the initial number of copies setting ({@value} ) */
	public static final String NROF_COPIES_S = "nrofCopies";

	/** Message property key for the remaining available copies of a message */
	public static final String MSG_COUNT_PROP = "SprayAndFocusFreqRouter.copies";
	/**
	 * Message property key for summary vector messages exchanged between direct
	 * peers
	 */
	public static final String SUMMARY_XCHG_PROP = "SprayAndFocusFreqRouter.protoXchg";

	protected static final String SUMMARY_XCHG_IDPREFIX = "summary";

	protected static int protocolMsgIdx = 0;

	protected int initialNrofCopies;

	/** Stores information about nodes with which this host has come in contact */
	protected Map<DTNHost, Integer> FreqEncounters;

	private String fase = "";

	public SprayAndFocusFreqRouter(Settings s) {

		Settings snf = new Settings(SPRAYANDFOCUS_NS);

		initialNrofCopies = snf.getInt(NROF_COPIES_S);

		FreqEncounters = new HashMap<DTNHost, Integer>();

	}

	public SprayAndFocusFreqRouter(SprayAndFocusFreqRouter r) {

		this.initialNrofCopies = r.initialNrofCopies;

		FreqEncounters = new HashMap<DTNHost, Integer>();

	}

	@Override
	public void connectionUp(DTNHost thisHost, DTNHost peer) {
	}

	@Override
	public void connectionDown(DTNHost thisHost, DTNHost peer) {
		if (FreqEncounters.containsKey(peer)) {

			Integer info = FreqEncounters.get(peer);

			FreqEncounters.put(peer, ++info);

			System.out.println("Freq " + thisHost + " to " + peer + " freq : "
					+ this.getFreqEncounterForHost(peer));
		}

		else {

			FreqEncounters.put(peer, 1);

			System.out.println("Freq " + thisHost + " to " + peer + " freq : "
					+ this.getFreqEncounterForHost(peer));
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

		return m.getTo() == (aHost);
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
		SprayAndFocusFreqRouter de = getOtherDecisionEngine(otherHost);
		
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
		} 
		
		else {
			
			int maxFreqPeer = 0;
			int FreqThisHost = this.getFreqEncounterForHost(dest);
			DTNHost thisHost=null;

			//cek myHost
			for (Connection c : otherHost.getConnections()) {
				DTNHost cekHost = c.getOtherNode(otherHost);
				SprayAndFocusFreqRouter cek=this.getOtherDecisionEngine(cekHost);
				if(cek.equals(this)){
					thisHost=cekHost;
					break;
				}
			}

			DTNHost maxpeer=null;
			SprayAndFocusFreqRouter max = null;
			//ambil peer dengan freq(dest) tertinggi
			
			for (Connection c : thisHost.getConnections()) {
				DTNHost peer = c.getOtherNode(thisHost);
				
				int LastFreqPeer = 0;
				SprayAndFocusFreqRouter de1 = getOtherDecisionEngine(peer);
				
				if (de1.FreqEncounters.containsKey(dest)) {
					LastFreqPeer = de.getFreqEncounterForHost(dest);
				}
				if (LastFreqPeer > maxFreqPeer) {
					max = de1;
					maxpeer=peer;
					maxFreqPeer = LastFreqPeer;
				}
			}
			
			//kirim
			if (max != null) {
//				double margin = (double)FreqThisHost*0.5;
					if (max.getFreqEncounterForHost(dest) > FreqThisHost) {
//						System.out.println("freq Peer "+max.getFreqEncounterForHost(dest)+" > "+"freq aku : "+FreqThisHost+" margin = "+margin );

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
		return true;
	}

	@Override
	public RoutingDecisionEngine replicate() {
		return new SprayAndFocusFreqRouter(this);
	}

	private SprayAndFocusFreqRouter getOtherDecisionEngine(DTNHost h) {
		MessageRouter otherRouter = h.getRouter();
		assert otherRouter instanceof DecisionEngineRouter : "This router only works with other routers of same type";

		return (SprayAndFocusFreqRouter) ((DecisionEngineRouter) otherRouter)
				.getDecisionEngine();
	}

	protected int getFreqEncounterForHost(DTNHost host) {
		if (FreqEncounters.containsKey(host)) {
			return FreqEncounters.get(host);
		} else {
			return 0;
		}
	}

}
