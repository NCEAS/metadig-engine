package edu.ucsb.nceas.mdqengine.dispatch;

public class PythonDispatcher extends Dispatcher {
	
	public PythonDispatcher() {
	    
	    // create a the jython engine:
	    engine = manager.getEngineByName("python");
	    // check if the engine has loaded correctly:
	    if (engine == null) {
	        throw new RuntimeException("Python Script Engine not found on the classpath.");
	    }
	}
}
