package edu.ucsb.nceas.mdqengine.dispatch;

public class JavaScriptDispatcher extends Dispatcher {
	
	public JavaScriptDispatcher() {
	    
	    // create a the jython engine:
	    engine = manager.getEngineByName("JavaScript");
	    // check if the engine has loaded correctly:
	    if (engine == null) {
	        throw new RuntimeException("JavaScript Engine not found on the classpath.");
	    }
	}
}
