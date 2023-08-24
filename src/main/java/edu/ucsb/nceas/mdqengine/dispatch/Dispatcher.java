package edu.ucsb.nceas.mdqengine.dispatch;

import edu.ucsb.nceas.mdqengine.MDQconfig;
import edu.ucsb.nceas.mdqengine.model.Output;
import edu.ucsb.nceas.mdqengine.model.Result;
import edu.ucsb.nceas.mdqengine.model.Status;

import org.apache.commons.configuration2.ex.ConfigurationException;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import jep.SharedInterpreter;
import jep.MainInterpreter;
import jep.JepException;

import java.nio.file.Files;
import java.nio.file.Path;
import java.io.IOException;
import java.io.Reader;

import javax.script.Bindings;
import javax.script.ScriptContext;
import javax.script.ScriptEngine;
import javax.script.ScriptEngineFactory;
import javax.script.ScriptException;
import javax.script.SimpleBindings;
import javax.script.ScriptEngineManager;
import javax.script.Invocable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Collections;
import java.util.List;

public class Dispatcher {

	protected Log log = LogFactory.getLog(this.getClass());

	protected ScriptEngine engine = null;
	protected String engineName = null;

	protected Map<String, Object> bindings = null;

	// create a script engine manager:
	// protected ScriptEngineManager manager = new ScriptEngineManager();
	protected JepScriptEngineFactory jepSEF;
	protected ScriptEngineManager manager = new ScriptEngineManager();

	private static Map<String, Dispatcher> instances = new HashMap<>();

	/**
	 * Dispatches the code and variables to the script engine.
	 * There are many options for the code and some depend on the engine being used:
	 * 1) script code that uses given global variables
	 * 2) function definition for call() that uses given global variables
	 * 3) fully-qualified Java bean name that implements Callable<Result> and has
	 * properties that
	 * match the given variable names
	 * 
	 * @param variables the variable name/values that will be made available to the
	 *                  script
	 * @param code      the code, function definition, or classname
	 * @return
	 * @throws ScriptException
	 */
	public Result dispatch(Map<String, Object> variables, String code) throws ScriptException {

		Result dr = new Result();

		for (Entry<String, Object> entry : variables.entrySet()) {
			log.trace("Setting variable: " + entry.getKey() + "=" + entry.getValue());
			engine.put(entry.getKey(), entry.getValue());
		}
		log.debug("Evaluating code: " + code);

		Object res = null;
		try {
			res = engine.eval(code);
			if (res != "_NA_") {
				log.trace("Result: " + res);
			}

		} catch (Exception e) {
			// let's report this
			dr.setStatus(Status.ERROR);
			dr.setOutput(new Output(e.getMessage()));
			log.warn("Error encountered evaluating code: " + e.getMessage());
			return dr;
		}

		// defining functions can result in different results depending on the engine
		// NOTE: return values from python must be retrieved by name
		if (res.toString().equals("function()") // r
				|| res.getClass().getName().equals("sun.org.mozilla.javascript.internal.InterpretedFunction") // js,
																												// java
																												// 7
																												// (rhino)
				|| res.getClass().getName().equals("jdk.nashorn.api.scripting.ScriptObjectMirror") // js, java 8
																									// (nashorn)
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
			Object var = null;
			try {
				var = engine.get("mdq_result");
			} catch (Exception e) {
				// catch this silently since we are just fishing
			}

			if (var != null && !var.toString().equals("<unbound>")) {
				log.trace("result is: " + var);
				log.debug("result is class: " + var.getClass());

				dr = (Result) var;
			} else {

				// if res has something in it, save it to the output
				if (res != null && res != "_NA_") {
					dr.setOutput(new Output(res.toString()));
					var = null;
				}

				// try to find other result items
				try {
					var = engine.get("call()");
				} catch (Exception e) {
					// catch this silently since we are just fishing
				}
				if (var != null && !var.toString().equals("<unbound>")) {
					dr.setOutput(new Output(var.toString()));
					var = null;
				}

				try {
					var = engine.get("output");
				} catch (Exception e) {
					// catch this silently since we are just fishing
				}
				if (var != null && !var.toString().equals("<unbound>")) {
					dr.setOutput(new Output(var.toString()));
					var = null;
				}

				try {
					var = engine.get("status");
				} catch (Exception e) {
					// catch this silently since we are just fishing
				}
				if (var != null && !var.toString().equals("<unbound>")) {
					dr.setStatus(Status.valueOf(var.toString()));
				} else {
					// assume a true result means that the test was successful
					if (dr.getOutput() != null) {
						dr.setStatus(Status.SUCCESS);
					} else {
						dr.setStatus(Status.FAILURE);
					}
				}
			}
		}

		// harvest all other vars for downstream dispatchers
		bindings = engine.getBindings(ScriptContext.ENGINE_SCOPE);

		return dr;

	}

	/**
	 * Checks if the environment is supported by the ScriptEngine.
	 *
	 * @param env The environment string to check for compatibility.
	 * @return {@code true} if the specified environment is supportedScriptEngine
	 *         {@code false} otherwise.
	 */
	public boolean isEnvSupported(String env) {
		String currentEnv = engine.getFactory().getLanguageName();
		log.debug("currentEnv=" + currentEnv);
		return env.equalsIgnoreCase(currentEnv);
	}

	protected Dispatcher() {
	}

	/**
	 * Register the Python scripting engine
	 *
	 * Otherwise the ScriptEngineFactory discovery mechanism will not find the
	 * python engine.
	 * 
	 * @param engineName The engine name.
	 */
	private Dispatcher(String engineName) {

		// Register the Python scripting engine,
		if (engineName.equalsIgnoreCase("python")) {
			JepScriptEngineFactory factory = new JepScriptEngineFactory();
			manager.registerEngineName("python", factory);
			// create a the engine:
			engine = factory.getScriptEngine();
		}

		// check if the engine has loaded correctly:
		if (engine == null) {
			throw new RuntimeException(engineName + " Engine not found on the classpath.");
		}
	}

	/**
	 * Get the dispatcher for a given environment
	 * 
	 * @param env The environment name.
	 */

	public static Dispatcher getDispatcher(String env) {

		String engineName = null;
		Dispatcher instance = null;
		if (env.equalsIgnoreCase("r") || env.equalsIgnoreCase("rscript")) {
			engineName = "r";
		} else if (env.equalsIgnoreCase("renjin")) {
			engineName = "Renjin";
		} else if (env.equalsIgnoreCase("python")) {
			engineName = "python";
		} else if (env.equalsIgnoreCase("JavaScript")) {
			engineName = "JavaScript";
		} else if (env.equalsIgnoreCase("Java")) {
			engineName = "Java";
		}

		if (!instances.containsKey(engineName)) {
			synchronized (Dispatcher.class) {
				if (!instances.containsKey(engineName)) {
					if (env.equalsIgnoreCase("Java")) {
						instance = new JavaDispatcher();
					} else if (env.equalsIgnoreCase("r") || env.equalsIgnoreCase("rscript")) {
						instance = new RDispatcher();
					} else {
						instance = new Dispatcher(engineName);
					}
					instance.engineName = engineName;
					instances.put(engineName, instance);
				}
			}
		} else {
			instance = instances.get(engineName);
		}

		return instance;

	}

	public Map<String, Object> getBindings() {
		return bindings;
	}

	public void setBindings(Map<String, Object> bindings) {
		this.bindings = bindings;
	}

	/**
	 * Implements {@link javax.script.ScriptEngine}
	 * 
	 */
	public class JepScriptEngine implements ScriptEngine {

		private SharedInterpreter jepInterpreter = null;
		private Bindings bindings = new SimpleBindings();
		private Bindings globalBindings = new SimpleBindings();
		private ScriptEngineFactory factory = null;

		/**
		 * Create a JepScriptEngine
		 * 
		 * The JepScriptEngine initalizes a jep SharedInterpreter, and relevant
		 * ScriptEngine methods are called on the interpreter. Only relevant
		 * ScriptEngine methods are implemented, the rest will error if they
		 * are called. The interpreter is configured to find the jep path listed
		 * in metadig.properties as `jep.path`.
		 * 
		 * @throws RuntimeException              for script or configuration errors
		 * @throws UnsupportedOperationException for methods not implemented
		 */
		public JepScriptEngine() {

			try {
				MDQconfig cfg = new MDQconfig();
				String pythonFolder = cfg.getString("jep.path");

				// define the jep library path
				String jepPath = pythonFolder + "/libjep.jnilib";

				if (!Files.exists(Path.of(jepPath))) {
					jepPath = pythonFolder + "/libjep.so";
				}
				// set path for jep executing python
				MainInterpreter.setJepLibraryPath(jepPath);

				// create the interpreter for python executing
				jepInterpreter = new SharedInterpreter();

			} catch (JepException e) {
				throw new RuntimeException("Error setting configurating Jep interpreter: " + e);
			} catch (ConfigurationException ce) {
				throw new RuntimeException("Error reading metadig configuration, ConfigurationException: " + ce);
			} catch (IOException io) {
				throw new RuntimeException("Error reading metadig configuration, IOException: " + io);
			}
		}

		/**
		 * Mapping of eval method for ScriptEngine to the jep SharedInterpreter
		 * 
		 * Instead of eval we actually implement exec, which allows us to run an
		 * arbitrary number of statments as opposed to just one. Note that exec returns
		 * void. To retrieve values from the python execution, `getValue` is required.
		 * 
		 * Since this method is designed to fit in with dispatch code running other
		 * engines that return values from eval, it cannot return null. Returning a
		 * boolean can make it look like code was executed and results available when
		 * they might not be, so a null-looking value is returned and handled in the
		 * dispatch code.
		 * 
		 * @param script The python script as a string
		 * 
		 * @throws RuntimeException If an error occurs during script execution.
		 * @see jep.Jep.SharedInterpreter#exec()
		 * 
		 */
		@Override
		public Object eval(String script) throws RuntimeException {
			// Implement the evaluation logic using the jepInterpreter
			try {
				jepInterpreter.exec(script);
				return "_NA_";
			} catch (JepException e) {
				throw new RuntimeException(e);
			}
		}

		/**
		 * Retrieves a value associated with the specified key
		 *
		 * @param key The key representing the value to retrieve.
		 * @return The value associated with the key, if found.
		 * @throws RuntimeException If an error occurs during value retrieval.
		 * @see jep.Jep.SharedInterpreter#getValue(String)
		 */
		@Override
		public Object get(String key) {
			try {
				Object object = jepInterpreter.getValue(key);
				return object;
			} catch (JepException e) {
				throw new RuntimeException(e);
			}
		}

		/**
		 * Set a value associated with the specified key
		 *
		 * @param key   The key to set.
		 * @param value The value to assign to the key.
		 * @throws RuntimeException If an error occurs.
		 * @see jep.Jep.SharedInterpreter#setValue(String)
		 */
		@Override
		public void put(String key, Object value) {
			try {
				jepInterpreter.set(key, value);
			} catch (JepException e) {
				throw new RuntimeException(e);
			}
		}

		/**
		 * Set the script engine factory
		 *
		 * @param fact The script engine factory to assign
		 */
		protected void setFactory(ScriptEngineFactory fact) {
			this.factory = fact;
		}

		/**
		 * Get the script engine factory
		 *
		 */
		@Override
		public ScriptEngineFactory getFactory() {
			if (this.factory == null)
				this.factory = new JepScriptEngineFactory();
			return this.factory;
		}

		/**
		 * Get bindings
		 * 
		 * Not sure exactly how/if this works, got this from an old Jep release
		 * 
		 * @param scope
		 * @return a Bindings value
		 */
		@Override
		public Bindings getBindings(int scope) {
			if (scope == ScriptContext.ENGINE_SCOPE) {
				return this.bindings;
			}

			return this.globalBindings;
		}

		@Override
		public void setBindings(Bindings bindings, int scope) {
			throw new UnsupportedOperationException(
					"setBindings(Bindings, int) not supported for the JepScriptEngine class");
		}

		@Override
		public Bindings createBindings() {
			throw new UnsupportedOperationException("createBindings() not supported for the JepScriptEngine class");
		}

		@Override
		public ScriptContext getContext() {
			throw new UnsupportedOperationException("getContext() not supported for the JepScriptEngine class");
		}

		@Override
		public void setContext(ScriptContext context) {
			throw new UnsupportedOperationException(
					"setContext(ScriptContext) is not implemented for the JepScriptEngine class");
		}

		public Object eval(Reader reader, Bindings bindings) throws ScriptException {
			throw new UnsupportedOperationException(
					"eval(Reader, Bindings) is not implemented for the JepScriptEngine class");
		}

		@Override
		public Object eval(String script, Bindings bindings) throws ScriptException {
			throw new UnsupportedOperationException(
					"eval(String, Bindings) is not implemented for the JepScriptEngine class");
		}

		@Override
		public Object eval(Reader reader) throws ScriptException {
			throw new UnsupportedOperationException("eval(Reader) is not implemented for the JepScriptEngine class");
		}

		@Override
		public Object eval(Reader reader, ScriptContext context) throws ScriptException {
			throw new UnsupportedOperationException(
					"eval(Reader, ScriptContext) is not implemented for the JepScriptEngine class");
		}

		@Override
		public Object eval(String script, ScriptContext context) throws ScriptException {
			throw new UnsupportedOperationException(
					"eval(String, ScriptContext) is not implemented for the JepScriptEngine class");
		}

	}

	public class JepScriptEngineFactory implements ScriptEngineFactory {

		private static List<String> names;

		private static List<String> extensions;

		private static List<String> mimeTypes;

		static {
			names = new ArrayList<>(1);
			names.add("jep");
			names = Collections.unmodifiableList(names);

			extensions = new ArrayList<>(1);
			extensions.add("py");
			extensions = Collections.unmodifiableList(extensions);

			mimeTypes = new ArrayList<>(0);
			mimeTypes = Collections.unmodifiableList(mimeTypes);
		}

		@Override
		public String getEngineName() {
			return "jep";
		}

		@Override
		public String getEngineVersion() {
			return "2.x";
		}

		@Override
		public List<String> getExtensions() {
			return extensions;
		}

		@Override
		public String getLanguageName() {
			throw new UnsupportedOperationException(
					"getLanguageName is not implemented for the JepScriptEngineFactory class");
		}

		@Override
		public String getLanguageVersion() {
			throw new UnsupportedOperationException(
					"getLanguageVersion is not implemented for the JepScriptEngineFactory class");
		}

		@Override
		public String getMethodCallSyntax(String obj, String method, String... args) {
			throw new UnsupportedOperationException("getMethodCallSyntax is not implemented");
		}

		@Override
		public List<String> getMimeTypes() {
			return mimeTypes;
		}

		@Override
		public List<String> getNames() {
			return names;
		}

		@Override
		public String getOutputStatement(String o) {
			throw new UnsupportedOperationException("getOutputStatement is not implemented");
		}

		@Override
		public Object getParameter(String p) {
			if (p == null)
				return null;

			// this is fucking retarded
			if (p.equals(ScriptEngine.ENGINE))
				return getEngineName();

			if (p.equals(ScriptEngine.ENGINE_VERSION))
				return getEngineVersion();

			if (p.equals(ScriptEngine.NAME))
				return "jep";

			if (p.equals(ScriptEngine.LANGUAGE))
				return getLanguageName();

			if (p.equals(ScriptEngine.LANGUAGE_VERSION))
				return getLanguageVersion();

			return null;
		}

		@Override
		public String getProgram(String... statements) {
			throw new UnsupportedOperationException(
					"getProgram is not implemented for the JepScriptEngineFactory class");
		}

		@Override
		public ScriptEngine getScriptEngine() {

			JepScriptEngine e = new JepScriptEngine();
			e.setFactory(this);
			return e;

		}

	}

}
