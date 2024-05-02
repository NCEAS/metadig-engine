package edu.ucsb.nceas.mdqengine.dispatch;

import edu.ucsb.nceas.mdqengine.MDQconfig;
import edu.ucsb.nceas.mdqengine.exception.MetadigException;
import edu.ucsb.nceas.mdqengine.model.Output;
import edu.ucsb.nceas.mdqengine.model.Result;
import edu.ucsb.nceas.mdqengine.model.Status;
import jep.JepException;
import jep.MainInterpreter;

import org.apache.commons.configuration2.ex.ConfigurationException;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import javax.script.ScriptContext;
import javax.script.ScriptEngine;
import javax.script.ScriptException;
import javax.script.ScriptEngineManager;
import javax.script.Invocable;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;

public class Dispatcher {

    protected Log log = LogFactory.getLog(this.getClass());

    protected ScriptEngine engine = null;
    protected String engineName = null;

    protected Map<String, Object> bindings = null;

    // create a script engine manager:
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
     * @return result of type mdqengine.Result
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
                // js, java 7
                || res.getClass().getName().equals("sun.org.mozilla.javascript.internal.InterpretedFunction")
                // js, java 8
                || res.getClass().getName().equals("jdk.nashorn.api.scripting.ScriptObjectMirror")) {
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

        if (res instanceof Result) { // if res is a Result, save it
            dr = (Result) res;
        } else if (res != null && res != "_NA_") { // if res is a string, save it
            dr.setOutput(new Output(res.toString()));
        } else {
            // for R and Python, the result has to be retrieved from engine global vars
            Object var_r = null;
            Object var_py = null;
            // do we have a result object from an R check?
            try {
                var_r = engine.get("mdq_result");
            } catch (Exception e) {
                // catch this silently since we are just fishing for results
                // the no result case is handled later
                log.trace("No result found for R check variable variable mdq_result.");
            }
            // save the result if we get one
            if (var_r != null && !var_r.toString().equals("<unbound>")) {
                log.trace("result is: " + var_r);
                log.debug("result is class: " + var_r.getClass());

                dr = (Result) var_r;
            } else {
                // try to find other result items from python checks
                try {
                    var_py = engine.get("call()"); // run the python function
                } catch (Exception e) {
                    log.error(e.getMessage());
                    log.error(e.getStackTrace());
                    dr.setOutput(new Output("ERROR: " + e.getMessage())); // catch the python stack trace
                    dr.setStatus(Status.valueOf("ERROR"));
                }
                // try to get the global output variable from python
                try {
                    var_py = engine.get("output");
                } catch (Exception e) {
                    log.trace("No result found for python check variable variable output.");
                    // catch this silently since we are just fishing
                    // the no result case is handled later
                }
                // save the output
                if (var_py != null && !var_py.toString().equals("<unbound>")) {
                    dr.setOutput(new Output(var_py.toString()));
                    var_py = null;
                }
                // try to get the global status variable from python
                try {
                    var_py = engine.get("status");
                } catch (Exception e) {
                    // catch this silently since we are just fishing
                    // the no result case is handled later
                    log.trace("No result found for python check variable variable status.");
                }
                // save the status
                if (var_py != null && !var_py.toString().equals("<unbound>")) {
                    dr.setStatus(Status.valueOf(var_py.toString()));
                } else {
                    // if we haven't found anything at this point it probably failed
                    dr.setStatus(Status.FAILURE);
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

    public void close() {
        if (engine instanceof JepScriptEngine jepEngine) {
            jepEngine.close();
            log.debug("Closed Jep interpreter");
        } else {
            log.debug("Didn't close Jep interpreter");
        }
        instances = new HashMap<>(); // reset the static variable
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

    public static void setupJep() throws MetadigException {
        // first look for an env var (this is mostly for local testing)
        String pythonFolder = System.getenv("JEP_LIBRARY_PATH");

        // then look in mdq config
        if (pythonFolder == null) {
            try {
                MDQconfig cfg = new MDQconfig();
                pythonFolder = cfg.getString("jep.path");
            } catch (ConfigurationException ce) {
                throw new RuntimeException("Error reading metadig configuration, ConfigurationException: " + ce);
            } catch (IOException io) {
                throw new RuntimeException("Error reading metadig configuration, IOException: " + io);
            }
        }
        // if its still null, throw a runtime exception
        // we don't want to start without Jep configured properly
        if (pythonFolder == null) {
            throw new RuntimeException(
                    "Could not find path to jep install. Check JEP_LIBRARY_PATH in metadig.proerties and ensure it is correct.");
        }

        // define the jep library path
        String jepPath = pythonFolder + "/libjep.jnilib";

        if (!Files.exists(Path.of(jepPath))) {
            jepPath = pythonFolder + "/libjep.so";
        }

        // set path for jep executing python
        try {
            MainInterpreter.setJepLibraryPath(jepPath);
        } catch (JepException e) {
            throw new MetadigException("Error configuring Jep: " + e);
        }

    }
}