package edu.ucsb.nceas.mdqengine.dispatch;

import javax.script.ScriptEngine;
import javax.script.ScriptEngineFactory;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

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
        return "python";
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
