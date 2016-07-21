package edu.ucsb.nceas.mdqengine.dispatch;

import java.util.Map;
import java.util.Map.Entry;

import javax.script.Invocable;
import javax.script.ScriptEngine;
import javax.script.ScriptEngineManager;
import javax.script.ScriptException;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import edu.ucsb.nceas.mdqengine.model.Result;
import edu.ucsb.nceas.mdqengine.model.Status;

public class Dispatcher {
	
	protected Log log = LogFactory.getLog(this.getClass());
	
	protected ScriptEngine engine = null;
	
	// create a script engine manager:
    protected ScriptEngineManager manager = new ScriptEngineManager();

    /**
     * Dispatches the code and variables to the script engine.
     * There are many options for the code and some depend on the engine being used:
     * 1) script code that uses given global variables
     * 2) function definition for call() that uses given global variables
     * 3) fully-qualified Java bean name that implements Callable<Result> and has properties that 
     * match the given variable names
     * @param variables the variable name/values that will be made available to the script
     * @param code the code, function definition, or classname
     * @return
     * @throws ScriptException
     */
	public Result dispatch(Map<String, Object> variables, String code) throws ScriptException {
		Result dr = new Result();
		
		for (Entry<String, Object> entry: variables.entrySet()) {
			log.trace("Setting variable: " + entry.getKey() + "=" + entry.getValue());
			engine.put(entry.getKey(), entry.getValue());
		}
		log.debug("Evaluating code: " + code);
		
		Object res = null;
		try {
			res = engine.eval(code);
			log.debug("Result: " + res);
			log.debug("Result class: " + res.getClass().getName());

		} catch (Exception e) {
			// let's report this
			dr.setStatus(Status.ERROR);
			dr.setMessage(e.getMessage());
			log.warn("Error encountered evaluating code: " + e.getMessage());
			return dr;
		}
		
		// defining functions can result in different results depending on the engine
		if (
				res == null // python
				|| res.toString().equals("function()") // r
				|| res.toString().contains("sun.org.mozilla.javascript.internal.InterpretedFunction") // js
				) {
			Invocable invoc = (Invocable) engine;
			try {
				res = invoc.invokeFunction("call");
				log.debug("Invocation result: " + res);
			} catch (NoSuchMethodException e) {
				dr.setStatus(Status.ERROR);
				dr.setMessage(e.getMessage());
				log.warn("Error encountered invoking function: " + e.getMessage());
				return dr;
			}
		}
		dr.setValue(res.toString());
		
		// try to find other result items
		Object var = engine.get("message");
		if (var != null && !var.toString().equals("<unbound>")) {
			dr.setMessage(var.toString());
		}
		var = engine.get("status");
		if (var != null && !var.toString().equals("<unbound>")) {
			dr.setStatus(Status.valueOf(var.toString()));
		}
		return dr;
		
	}
	
	public boolean isEnvSupported(String env) {
		String currentEnv = engine.getFactory().getLanguageName();
		log.debug("currentEnv=" + currentEnv);
		return env.equalsIgnoreCase(currentEnv);
	}
	
	protected Dispatcher() {}
		
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
		Dispatcher instance = null;
		if (env.equalsIgnoreCase("r")) {
			engineName = "Renjin";
		} else if (env.equalsIgnoreCase("python")) {
			engineName = "python";
		} else if (env.equalsIgnoreCase("JavaScript")) {
			engineName = "JavaScript";
		}
	
		if (env.equalsIgnoreCase("Java")) {
			instance = new JavaDispatcher();
		} else {
			instance = new Dispatcher(engineName);
		}
		
		return instance;
	    
	}
}
