package edu.ucsb.nceas.mdqengine.dispatch;

import java.util.Map;
import java.util.Map.Entry;

import javax.script.ScriptEngine;
import javax.script.ScriptEngineManager;
import javax.script.ScriptException;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

public class Dispatcher {
	
	protected Log log = LogFactory.getLog(this.getClass());
	
	protected ScriptEngine engine = null;
	
	// create a script engine manager:
    protected ScriptEngineManager manager = new ScriptEngineManager();

	public String dispatch(Map<String, Object> names, String code) throws ScriptException {
		for (Entry<String, Object> entry: names.entrySet()) {
			log.debug("Setting variable: " + entry.getKey() + "=" + entry.getValue());
			engine.put(entry.getKey(), entry.getValue());
		}
		log.debug("Evaluating code: " + code);
		Object res = engine.eval(code);
		log.debug("Result: " + res);
		return res.toString();
		
	}
	
	private Dispatcher() {}
		
	private Dispatcher(String engineName) {
	
		// create a the engine:
	    engine = manager.getEngineByName(engineName);
	    // check if the engine has loaded correctly:
	    if (engine == null) {
	        throw new RuntimeException(engineName + " Engine not found on the classpath.");
	    }
	    
	}
	
	public static Dispatcher getDispatcher(String env) {
	    
		String engineName = null;
		if (env.equalsIgnoreCase("r")) {
			engineName = "Renjin";
		} else if (env.equalsIgnoreCase("python")) {
			engineName = "python";
		} else if (env.equalsIgnoreCase("JavaScript")) {
			engineName = "JavaScript";
		}
		
		Dispatcher instance = new Dispatcher(engineName);
		
		return instance;
	    
	}
}
