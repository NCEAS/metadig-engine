package edu.ucsb.nceas.mdqengine.processor;

import com.jayway.jsonpath.Configuration;
import com.jayway.jsonpath.DocumentContext;
import com.jayway.jsonpath.JsonPath;
import edu.ucsb.nceas.mdqengine.dispatch.Dispatcher;
import edu.ucsb.nceas.mdqengine.model.*;
import org.apache.commons.codec.binary.Base64;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang.BooleanUtils;
import org.apache.commons.lang.math.NumberUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.dataone.service.types.v2.SystemMetadata;
import org.dataone.service.util.TypeMarshaller;

import javax.script.ScriptException;
import java.io.*;
import java.net.URL;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.SimpleDateFormat;
import java.util.*;

public class JSONDialect {

    private DocumentContext jsonContext;

    private SystemMetadata systemMetadata;

    private Map<String, Object> params;

    private Map<String, Namespace> namespaces = new HashMap<String, Namespace>();

    private String directory;

    private Dispatcher dispatcher;

    public static Log log = LogFactory.getLog(JSONDialect.class);

    public JSONDialect(InputStream input) throws IOException {

        byte[] bytes = IOUtils.toByteArray(input);

        jsonContext = JsonPath.parse(new ByteArrayInputStream(bytes));

        // garbage collection
        bytes = null;
    }

    public Result runCheck(Check check) {

        Result result = null;

        log.debug("Running Check: " + check.getId());

        // only bother dispatching if check can be applied to this document
        if (this.isCheckValid(check)) {

            // gather the variable name/value details
            Map<String, Object> variables = new HashMap<String, Object>();
            if (check.getSelector() != null) {
                for (Selector selector: check.getSelector()) {

                    String name = selector.getName();
                    Object value = this.selectPath(selector, jsonContext);
                    // variables are passed to the check script to be executed
                    variables.put(name, value);
                }
            }

            // make the entire dom available
            variables.put("document", jsonContext.toString());

            // include system metadata if available
            if (this.systemMetadata != null) {
                try {
                    ByteArrayOutputStream baos = new ByteArrayOutputStream();
                    TypeMarshaller.marshalTypeToOutputStream(systemMetadata, baos);
                    variables.put("systemMetadata", baos.toString("UTF-8"));
                    variables.put("datasource", systemMetadata.getOriginMemberNode().getValue());
                    // dateUploaded
                    // This unusual date format is acceptable to Solr - it must be GMT time, with
                    // no offset
                    SimpleDateFormat df = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'");
                    df.setTimeZone(TimeZone.getTimeZone("GMT"));
                    variables.put("dateUploaded", df.format(systemMetadata.getDateUploaded()));
                    variables.put("authoritativeMemberNode", systemMetadata.getAuthoritativeMemberNode().getValue());
                    variables.put("systemMetadataPid", systemMetadata.getIdentifier().getValue());
                } catch (Exception e) {
                    log.error("Could not serialize SystemMetadata for check", e);
                }
            }

            // make extra parameters available to the check if we have them
            if (this.params != null) {
                variables.put("mdq_params", params);
            }

            // give the check a place to write files during the run
            if (this.directory != null) {
                variables.put("tempDir", directory);
            }

            // dispatch to checker impl
            if (!check.isInheritState() || dispatcher == null) {
                // create a fresh dispatcher
                dispatcher = Dispatcher.getDispatcher(check.getEnvironment());
                log.debug("Creating initial check dispatcher for " + check.getEnvironment());
            } else {

                // ensure that we can reuse the dispatcher to inherit previous state
                if (!dispatcher.isEnvSupported(check.getEnvironment())) {

                    // use the bindings from previous dispatcher
                    Map<String, Object> bindings = dispatcher.getBindings();
                    if (bindings == null) {
                        // nothing to inherit
                        result = new Result();
                        result.setStatus(Status.ERROR);
                        result.setOutput(new Output("Check cannot use persistent state from previous differing environment"));
                        return result;
                    } else {

                        // use the bindings from previous dispatcher
                        for (String key: bindings.keySet()) {
                            Object value = bindings.get(key);
                            value = retypeObject(value.toString());
                            log.trace("binding: " + key + "=" + value);
                            variables.put(key, value);
                        }
                        log.debug("Binding existing variables for new dispatcher");

                        // and a fresh dispatcher
                        dispatcher = Dispatcher.getDispatcher(check.getEnvironment());
                        log.debug("Creating new check dispatcher for " + check.getEnvironment());
                    }
                } else {
                    log.debug("Reusing dispatcher for persistent state check");

                }
            }

            // assemble the code to run
            String code = check.getCode();

            // gather extra code from external resources
            List<URL> libraries = check.getLibrary();
            if (libraries != null) {
                String libraryContent = "";
                for (URL library: libraries) {
                    log.debug("Loading library code from URL: " + library);
                    // read the library from given URL
                    try {
                        libraryContent += IOUtils.toString(library.openStream(), "UTF-8");
                    } catch (IOException e) {
                        log.error("Could not load code library: " + e.getMessage(), e);
                        result = new Result();
                        result.setStatus(Status.ERROR);
                        result.setOutput(new Output(e.getMessage()));
                    }
                }
                // combine libraries and code
                code = libraryContent + code;
            }

            try {
                result = dispatcher.dispatch(variables, code);
            } catch (ScriptException e) {
                // report this
                result = new Result();
                result.setStatus(Status.ERROR);
                result.setOutput(new Output(e.getMessage()));
            }

        } else {
            // we just skip instead
            result = new Result();
            result.setStatus(Status.SKIP);
            result.setOutput(new Output("Dialect for this check is not supported"));
        }

        // set additional info before returning
        result.setCheck(check);
        result.setTimestamp(Calendar.getInstance().getTime());

        // do any further processing on the result (e.g., encode value if needed)
        result = postProcess(result);

        return result;
    }

    private Result postProcess(Result result) {
        // Return the result as-is if there are no outputs to post-process
        if (result.getOutput() == null) {
            log.debug("Skipping postProcess step because this result's output is null.");
            return(result);
        }

        // Post-process each output (if needed)
        for (Output output: result.getOutput()) {
            if (output == null) {
                log.debug("Output was null.");
                continue;
            }

            String value = output.getValue();
            if (value != null) {
                Path path = null;
                try {
                    path = Paths.get(value);
                } catch (InvalidPathException e) {
                    // NOPE
                    return result;
                }

                if (path.toFile().exists()) {
                    // encode it
                    String encoded = null;
                    try {
                        encoded = Base64.encodeBase64String(IOUtils.toByteArray(path.toUri()));
                        output.setValue(encoded);
                        //TODO: set mime-type when we have support for that, or assume they did it already?
                    } catch (IOException e) {
                        log.error(e.getMessage());
                    }
                }
            }
        }

        return result;
    }

    /**
     * Determine if the check is valid for the document
     * @param check
     * @return
     */
    public boolean isCheckValid(Check check) {

        if (check.getDialect() == null) {
            log.error("No dialects have been specified for check, assuming it is valid for this document");
            return true;
        }

        for (Dialect dialect: check.getDialect()) {
            String name = dialect.getName();
            String expression = dialect.getJsonpath();
            log.debug("Dialect name: " + name + ", expression: " + expression);

            if(expression == null) {
                log.trace("Skipping dialect named: " + name);
                continue;
            }
            String value = jsonContext.read(expression);
            log.trace("json value: " + value);
            String matchRegex = dialect.getMatch();
            log.trace("match: " + matchRegex);

            if(matchRegex != null && value.matches(matchRegex)) {
                log.debug("Dialect " + name + " is valid for document ");
                return true;
            } else {
                log.debug("Dialect " + name + " is NOT valid for document");
            }
        }

        log.warn("No supported check dialects found for this document");

        return false;
    }

    private Object selectPath(Selector selector, DocumentContext context) {

        Object value = null;

        // select one or more values from document
        String selectorPath = selector.getJSONpath();
        //XPath xpath = xPathfactory.newXPath();

        // try multiple first
        List<Object> objects = null;
        // Can the JSONPath return a single value or multiple values?
        JsonPath compiled = JsonPath.compile(selectorPath);
        Boolean isDefinite = compiled.isDefinite();

        if (isDefinite) {
            value = context.read(selectorPath).toString();
            log.trace("Definite value: " + value);
            // just return single value, as a String
            value = retypeObject(value);
        } else {
            // multiple values
            List<Object> values = context.read(selectorPath);
            log.trace("Got " + values.size() + " values");

            for (int i = 0; i < values.size(); i++) {
                value = values.get(i).toString();
                log.trace("Got i " + i + ", value" + value);
                value = retypeObject(value);
                values.add(value);
            }
            // return the list
            value = values;
        }

        log.trace("value type: " + value.getClass().getName());
        return value;
    }

    /* Retype an object based on a few simple assumptions. A "String" value is
     * typically passed in. If only numeric characters are present in the String, then
     * the object is caste to type "Number". If the string value appears to be an
     * "affermative" or "negative" value (e.g. "Y", "Yes", "N", "No", ...) then the
     * value is caste to "Boolean".
     */
    public static Object retypeObject(Object value) {
        Object result = value;
        // type the value correctly
        if (NumberUtils.isNumber((String)value)) {
            result = NumberUtils.createNumber((String)value);
            log.trace("value retyped to number: " + result);
        } else {
            // relies on this method to return null if we are not sure if it is a boolean
            Boolean bool = BooleanUtils.toBooleanObject((String)value);
            if (bool != null) {
                log.trace("value retyped to boolean: " + result);
                result = bool;
            }
        }

        return result;
    }

    public Map<String, Object> getParams() {
        return params;
    }

    public void setParams(Map<String, Object> params) {
        this.params = params;
    }

    public void setDirectory(String dir) {
        this.directory = dir;
    }

    public SystemMetadata getSystemMetadata() {
        return systemMetadata;
    }

    public void setSystemMetadata(SystemMetadata systemMetadata) {
        this.systemMetadata = systemMetadata;
    }
}
