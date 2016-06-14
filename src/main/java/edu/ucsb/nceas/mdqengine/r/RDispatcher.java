package edu.ucsb.nceas.mdqengine.r;

import java.util.Map;
import java.util.Map.Entry;

import javax.script.ScriptEngine;
import javax.script.ScriptEngineManager;
import javax.script.ScriptException;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.renjin.sexp.SEXP;

public class RDispatcher {

	private static Log log = LogFactory.getLog(RDispatcher.class);
	
	private ScriptEngine engine = null;
	
	public RDispatcher() {
		// create a script engine manager:
	    ScriptEngineManager manager = new ScriptEngineManager();
	    // create a Renjin engine:
	    engine = manager.getEngineByName("Renjin");
	    // check if the engine has loaded correctly:
	    if (engine == null) {
	        throw new RuntimeException("Renjin Script Engine not found on the classpath.");
	    }
	}
	

	public String dispatch(Map<String, Object> names, String code) throws ScriptException {
		for (Entry<String, Object> entry: names.entrySet()) {
			log.debug("Setting variable: " + entry.getKey() + "=" + entry.getValue());
			engine.put(entry.getKey(), entry.getValue());
		}
		log.debug("Evaluating code: " + code);
		SEXP res = (SEXP) engine.eval(code);
		log.debug("Result: " + res);
		return res.toString();
		
	}
	
	private void test() throws ScriptException {
		engine.put("x", 2);
		engine.put("y", 2);
		SEXP res = (SEXP)engine.eval("x+y");
		System.out.println("Result= " + res.asReal());
		
	}
	
	public static void main(String arg[]) {
		RDispatcher dispatcher = new RDispatcher();
		try {
			dispatcher.test();
		} catch (ScriptException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}
}
