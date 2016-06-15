package edu.ucsb.nceas.mdqengine.dispatch;

public class RDispatcher extends Dispatcher {
	
	public RDispatcher() {
		
	    // create a Renjin engine:
	    engine = manager.getEngineByName("Renjin");
	    // check if the engine has loaded correctly:
	    if (engine == null) {
	        throw new RuntimeException("Renjin Script Engine not found on the classpath.");
	    }
	}
}
