package edu.ucsb.nceas.mdqengine.dispatch;

import java.util.Map;
import java.util.Map.Entry;

import javax.script.ScriptEngine;
import javax.script.ScriptEngineManager;
import javax.script.ScriptException;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

public abstract class Dispatcher {
	
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
}
