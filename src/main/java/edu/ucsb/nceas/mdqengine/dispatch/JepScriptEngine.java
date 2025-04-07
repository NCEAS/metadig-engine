package edu.ucsb.nceas.mdqengine.dispatch;

import jep.SharedInterpreter;
import jep.JepException;

import java.io.Reader;

import javax.script.Bindings;
import javax.script.ScriptContext;
import javax.script.ScriptEngine;
import javax.script.ScriptEngineFactory;
import javax.script.ScriptException;
import javax.script.SimpleBindings;

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
     * in metadig.properties as `jep.path` for production deployments. For testing
     * on other machines, the configured path can be set using the environment
     * variable JEP_LIBRARY_PATH.
     * 
     * @throws RuntimeException              for script or configuration errors
     * @throws UnsupportedOperationException for methods not implemented
     */
    public JepScriptEngine() {

        try {
            // create the interpreter for python executing
            jepInterpreter = new SharedInterpreter();
        } catch (JepException e) {
            throw new RuntimeException("Error initializing Jep interpreter: " + e);
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
     * @see jep.SharedInterpreter#exec()
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

    public void close() {
        try {
            jepInterpreter.close();
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
     * @see jep.SharedInterpreter#getValue(String)
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
     * @see jep.SharedInterpreter#set(String, Object)
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