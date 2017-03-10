/* 
 * 
 * 
 */
package report;

/** 
 * Records the average buffer occupancy and its variance with format:
 * <p>
 * <Simulation time> <average buffer occupancy % [0..100]> <variance>
 * </p>
 * 
 * 
 */
import java.text.DecimalFormat;
import java.util.*;
//import java.util.List;
//import java.util.Map;

import core.DTNHost;
import core.Settings;
import core.SimClock;
import core.UpdateListener;

public class BufferOccupancyReport extends Report implements UpdateListener {

	/**
	 * Record occupancy every nth second -setting id ({@value}). 
	 * Defines the interval how often (seconds) a new snapshot of buffer
	 * occupancy is taken previous:5
	 */
	public static final String BUFFER_REPORT_INTERVAL = "occupancyInterval";
	/** Default value for the snapshot interval */
	public static final int DEFAULT_BUFFER_REPORT_INTERVAL = 3600;
	
	private double lastRecord = Double.MIN_VALUE;
	private int interval;
	
	private Map<DTNHost, Double> bufferCounts = new HashMap<DTNHost, Double>();
	private HashMap<DTNHost, ArrayList<Double>> time =new HashMap<>();
//	private HashMap<DTNHost, ArrayList<HashMap<Double, ArrayList<Double>>>> waktubuffer =new HashMap<>();
	private HashMap<DTNHost, ArrayList<HashMap<Double, Double>>> waktubuffer =new HashMap<>();
	private int updateCounter = 0;  //new added
	
	
	public BufferOccupancyReport() {
		super();
		
		Settings settings = getSettings();
		if (settings.contains(BUFFER_REPORT_INTERVAL)) {
			interval = settings.getInt(BUFFER_REPORT_INTERVAL);
		} else {
			interval = -1; /* not found; use default */
		}
		
		if (interval < 0) { /* not found or invalid value -> use default */
			interval = DEFAULT_BUFFER_REPORT_INTERVAL;
		}
	}
	
	public void updated(List<DTNHost> hosts) {
		if (isWarmup()) {
			return;
		}
		
		if (SimClock.getTime() - lastRecord >= interval) {
			lastRecord = SimClock.getTime();
			printLine(hosts,lastRecord);
			updateCounter++; // new added
			
		}
			
//			for (DTNHost ho : hosts ) {
//				double temp = ho.getBufferOccupancy();
//				temp = (temp<=100.0)?(temp):(100.0);
//				if (bufferCounts.containsKey(ho.getAddress())){
//					bufferCounts.put(ho, (bufferCounts.get(ho.getAddress()+temp))/2);	
//				}else{
//				bufferCounts.put(ho, temp);
//				}
//			}
			
		
	}
	
	/**
	 * Prints a snapshot of the average buffer occupancy
	 * @param hosts The list of hosts in the simulation
	 */
	 
	private void printLine(List<DTNHost> hosts,double waktu) {
		/**
		double bufferOccupancy = 0.0;
		double bo2 = 0.0;

		for (DTNHost h : hosts) {
			double tmp = h.getBufferOccupancy();
			tmp = (tmp<=100.0)?(tmp):(100.0);
			bufferOccupancy += tmp;
			bo2 += (tmp*tmp)/100.0;
		}
		
		double E_X = bufferOccupancy / hosts.size();
		double Var_X = bo2 / hosts.size() - (E_X*E_X)/100.0;
		
		String output = format(SimClock.getTime()) + " " + format(E_X) + " " +
			format(Var_X);
		write(output);
		*/
		for (DTNHost h : hosts ) {
			double temp = h.getBufferOccupancy();
			temp = (temp<=100.0)?(temp):(100.0);
			if (bufferCounts.containsKey(h)){
				//bufferCounts.put(h, (bufferCounts.get(h)+temp)/2); seems WRONG
				
				bufferCounts.put(h, bufferCounts.get(h)+temp);
				//write (""+ bufferCounts.get(h));
				ArrayList<Double> X=time.get(h);
				X.add(temp);
				time.put(h, X);
//				ArrayList<HashMap<Double, ArrayList<Double>>> Y=waktubuffer.get(h);
				
				ArrayList<HashMap<Double, Double>> Y=waktubuffer.get(h);
				HashMap<Double, Double> M=new HashMap<>();
				M.put(lastRecord, temp);
				Y.add(M);
				waktubuffer.put(h, Y);
			}
			else {
				ArrayList<Double> X=new ArrayList<>();
				X.add(temp);
				bufferCounts.put(h, temp);
				time.put(h,X);
				
				ArrayList<HashMap<Double, Double>> Y=new ArrayList<>();
				HashMap<Double, Double> M=new HashMap<>();
				M.put(lastRecord, temp);
				Y.add(M);
				waktubuffer.put(h, Y);
			//write (""+ bufferCounts.get(h));
			}
		}
		
		
	}
	
	
		
	@Override
	public void done()
	{
		String rata_rata="";
		for (Map.Entry<DTNHost, Double> entry : bufferCounts.entrySet()) {
			
			DTNHost a = entry.getKey();
			Integer b = a.getAddress();
			Double avgBuffer = entry.getValue()/updateCounter;
			rata_rata=rata_rata+b + "\t" + avgBuffer+"\n";
//			write(b + "\t" + avgBuffer);
			
			//write("" + b + ' ' + entry.getValue());
		}
		String A="";
		for(DTNHost key:time.keySet()){
			ArrayList<Double> X=time.get(key);
			String times="";
			DecimalFormat df = new DecimalFormat("#.000");
			for(int i=0;i<X.size();i++){
				times=times+" "+df.format(X.get(i));
			}
			A=A+key.getAddress()+" "+times+"\n";
		}
		String B="";
		for(DTNHost key:waktubuffer.keySet()){
			ArrayList<HashMap<Double, Double>> X=waktubuffer.get(key);
			String times="";
			DecimalFormat df = new DecimalFormat("#.000");
			for(int i=0;i<X.size();i++){
				HashMap<Double, Double> K=X.get(i);
				for(Double keys:K.keySet()){
					times=times+"  detik : "+keys.toString()+"  buffer : "+df.format(K.get(keys));
				}
//				times=times+"  "+df.format(X.get(i));
			}
			B=B+key.getAddress()+" "+times+"\n";
		}
		
		write(A);
		write("\n\navgBuffer : ");
		write("==================");
		write(rata_rata);
		write("\n\nwaktuBuffer : ");
		write("==================");
		write(B);
		super.done();
	}

	
}