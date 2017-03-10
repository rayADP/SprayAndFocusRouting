package report;
import core.DTNHost;
import core.Message;
import core.MessageListener;
import java.util.*;


public class MessageReceivedReport extends Report implements MessageListener {

protected Map<DTNHost, Integer> receivedCounts;

public MessageReceivedReport(){
	init();
}
	
public void messageTransferred (Message m, DTNHost from, DTNHost to, boolean firstfirstDelivery){
	
	List<DTNHost> hopNodes = m.getHops();
	
	if(hopNodes.get(hopNodes.size()-1) != m.getTo())
		//mau hitung relay trffic
	{
		//List<DTNHost> hopNodes = m.getHops();
		DTNHost receivedNode = hopNodes.get(hopNodes.size()-1);
		if(receivedCounts.containsKey(receivedNode))receivedCounts.put(receivedNode,receivedCounts.get(receivedNode)+1);
		else receivedCounts.put(receivedNode,1);
	}
}

@Override
protected void init(){
	super.init();
	receivedCounts=new HashMap<DTNHost, Integer>();
}

@Override
public void done(){
	for(Map.Entry<DTNHost, Integer> entry: receivedCounts.entrySet())
	{
		DTNHost a = entry.getKey();
		Integer b = a.getAddress();
		write("" + b + ' ' + entry.getValue());
	}
	super.done();
}
public void newMessage(Message m){}
public void messageTransferStarted(Message m, DTNHost from, DTNHost to) {}
public void messageDeleted(Message m, DTNHost where, boolean dropped) {}
public void messageTransferAborted(Message m, DTNHost from, DTNHost to){}

}
