package edu.ucsb.nceas.mdqengine.dispatch;

import java.util.Map;
import java.util.Map.Entry;

import javax.script.Invocable;
import javax.script.ScriptContext;
import javax.script.ScriptEngine;
import javax.script.ScriptEngineManager;
import javax.script.ScriptException;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import edu.ucsb.nceas.mdqengine.model.Output;
import edu.ucsb.nceas.mdqengine.model.Result;
import edu.ucsb.nceas.mdqengine.model.Status;

public class Dispatcher {
	
	protected Log log = LogFactory.getLog(this.getClass());
	
	protected ScriptEngine engine = null;
	
	protected Map<String, Object> bindings = null;
	
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
			log.trace("Result: " + res);

		} catch (Exception e) {
			// let's report this
			dr.setStatus(Status.ERROR);
			dr.setOutput(new Output(e.getMessage()));
			log.warn("Error encountered evaluating code: " + e.getMessage());
			return dr;
		}
		
		// defining functions can result in different results depending on the engine
		if (
				res == null // python
				|| res.toString().equals("function()") // r
				|| res.getClass().getName().equals("sun.org.mozilla.javascript.internal.InterpretedFunction") // js, java 7 (rhino)
				|| res.getClass().getName().equals("jdk.nashorn.api.scripting.ScriptObjectMirror") // js, java 8 (nashorn)
				) {
			Invocable invoc = (Invocable) engine;
			try {
				res = invoc.invokeFunction("call");
				log.trace("Invocation result: " + res);
			} catch (NoSuchMethodException e) {
				dr.setStatus(Status.ERROR);
				dr.setOutput(new Output(e.getMessage()));
				log.warn("Error encountered invoking function: " + e.getMessage());
				return dr;
			}
		}
		
		if (res instanceof Result) {
			dr = (Result) res;
		} else {
		
			// do we have a result object?
			Object var = engine.get("mdq_result");
			if (var != null && !var.toString().equals("<unbound>")) {
				log.debug("result is: " + var);
				log.debug("result is class: " + var.getClass());
	
				dr = (Result) var;
			} else {
				
				dr.setOutput(new Output(res.toString()));
				
				// try to find other result items
				var = engine.get("output");
				if (var != null && !var.toString().equals("<unbound>")) {
					dr.setOutput(new Output(var.toString()));
				}
				var = engine.get("status");
				if (var != null && !var.toString().equals("<unbound>")) {
					dr.setStatus(Status.valueOf(var.toString()));
				}
			}
		}
		
		// harvest all other vars for downstream dispatchers
		bindings = engine.getBindings(ScriptContext.ENGINE_SCOPE);
		
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
		} else if (env.equalsIgnoreCase("rscript")) {
			instance = new RDispatcher();
		} else {
			instance = new Dispatcher(engineName);
		}
		
		return instance;
	    
	}

	public Map<String, Object> getBindings() {
		return bindings;
	}

	public void setBindings(Map<String, Object> bindings) {
		this.bindings = bindings;
	}
}
