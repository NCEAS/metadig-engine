package edu.ucsb.nceas.mdqengine.processor;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import net.thisptr.jackson.jq.BuiltinFunctionLoader;
import net.thisptr.jackson.jq.JsonQuery;
import net.thisptr.jackson.jq.Scope;
import net.thisptr.jackson.jq.Versions;
import net.thisptr.jackson.jq.exception.JsonQueryException;
import edu.ucsb.nceas.mdqengine.dispatch.Dispatcher;
import edu.ucsb.nceas.mdqengine.model.*;

import javax.script.ScriptException;

import org.apache.commons.io.IOUtils;
import org.dataone.service.util.TypeMarshaller;

import java.io.*;
import java.net.URL;
import java.text.SimpleDateFormat;
import java.util.*;

public class JSONDialect extends AbstractMetadataDialect {
    private JsonNode rootNode;
    private Dispatcher dispatcher;
    // Create a scope with the default built-in functions
    private Scope rootScope = Scope.newEmptyScope();

    public JSONDialect(InputStream input) throws IOException {
        ObjectMapper mapper = new ObjectMapper();
        this.rootNode = mapper.readTree(input);
        // Use BuiltinFunctionLoader to load built-in functions from the classpath.
        BuiltinFunctionLoader.getInstance().loadFunctions(Versions.JQ_1_6, rootScope);
        // For import statements to work, you need to set ModuleLoader. Not sure if we
        // need or not BuiltinModuleLoader uses ServiceLoader mechanism to load Module
        // implementations.
        // rootScope.setModuleLoader(BuiltinModuleLoader.getInstance());
    }

    @Override
    public void extractNamespaces() {
        // not implemented for json docs
        return;
    }

    @Override
    public void mergeNamespaces(List<Namespace> namespaces) {
        // not implemented for json docs
        return;
    }

    /**
     * Executes a quality check against a JSON document and returns the result.
     *
     * @param check the {@link Check} to be executed
     * @return a {@link Result} object representing the outcome of the check
     * @throws JsonQueryException
     */
    @Override
    public Result runCheck(Check check) throws JsonQueryException {

        Result result = new Result();

        log.debug("Running Check: " + check.getId());

        if (!this.isCheckValid(check)) {
            result.setStatus(Status.SKIP);
            result.setOutput(new Output("Check is not valid for this document"));
            return result;
        }

        Map<String, Object> variables = new HashMap<>();

        // get json selectors
        if (check.getSelector() != null) {
            for (Selector selector : check.getSelector()) {
                Expression expression = selector.getExpression();
                if (expression == null) {
                    continue;
                }
                String syntax = expression.getSyntax();
                // NB: we don't have to worry about sub-selectors here because our jq
                // expressions instead just chain together within a single selector element
                if ("json-path".equals(syntax)) {
                    String jq = expression.getValue();
                    Object value = this.selectJsonPath(jq, rootNode);
                    variables.put(selector.getName(), value);
                }
            }
        }

        // Add full JSON document
        variables.put("document", rootNode.toPrettyString());

        // reset the global values to null
        // this prevents the next check from accidentally inheriting results
        variables.put("status", null);
        variables.put("output", null);

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
        if (this.params != null) {
            variables.put("mdq_params", params);
        }
        if (this.directory != null) {
            variables.put("tempDir", directory);
        }

        String code = check.getCode();
        if (check.getLibrary() != null) {
            for (URL library : check.getLibrary()) {
                try {
                    code = IOUtils.toString(library.openStream(), "UTF-8") + code;
                } catch (IOException e) {
                    log.error("Could not load library", e);
                }
            }
        }

        try {
            if (!check.isInheritState() || dispatcher == null) {
                dispatcher = Dispatcher.getDispatcher(check.getEnvironment());
            }
            result = dispatcher.dispatch(variables, code);
        } catch (ScriptException e) {
            result.setStatus(Status.ERROR);
            result.setOutput(new Output(e.getMessage()));
        }

        result.setCheck(check);
        result.setTimestamp(Calendar.getInstance().getTime());
        return postProcess(result);
    }

    /**
     * Determine if the check is valid for the document
     * 
     * @param check
     * @return
     */
    @Override
    public boolean isCheckValid(Check check) {

        if (check.getDialect() == null) {
            log.debug("No dialects have been specified for check, assuming it is valid for this document");
            return true;
        }

        for (Dialect dialect : check.getDialect()) {

            String name = dialect.getName();
            Expression expression = dialect.getExpression();
            if (expression == null) {
                continue;
            }
            log.debug("Dialect name: " + name + ", expression: " + expression.getValue());
            String jq = expression.getValue();
            try {
                Object value = this.selectJsonPath(jq, rootNode);
                if (value.equals("https://schema.org/")) {
                    return true;
                }
            } catch (JsonQueryException e) {
                log.error(e.getMessage());
            }
        }

        return true;
    }

    /**
     * Applies a jq-style JSON path expression to the given JSON document and
     * returns the resulting value.
     *
     * @param jqExpression the jq expression (e.g., ".author.name")
     * @param jsonDoc      the JSON document represented as a JsonNode
     * @return the value resulting from the jq query
     * @throws JsonQueryException if the jq expression is invalid or evaluation
     *                            fails
     */

    public Object selectJsonPath(String jqExpression, JsonNode jsonDoc) throws JsonQueryException {
        Object value = null;

        // compile the jq expression
        JsonQuery query;

        query = JsonQuery.compile(jqExpression, Versions.JQ_1_6);

        // create child scope, this is very lightweight and we don't modify the root
        // scope
        Scope childScope = Scope.newChildScope(rootScope);

        // apply the expression to the input JSON document
        List<JsonNode> resultNodes = new ArrayList<>();
        query.apply(childScope, jsonDoc, resultNodes::add);

        // if only one node, make sure to return a single value
        if (resultNodes.size() == 1) {
            JsonNode node = resultNodes.get(0);
            if (node.isTextual()) {
                // If it's a TextNode, get the text
                value = node.asText();
                value = ProcessorUtils.retypeObject(value);
            } else if (node.isObject() || node.isArray()) {
                // If it's an ObjectNode or ArrayNode, handle accordingly
                value = node.toString();
                value = ProcessorUtils.retypeObject(value);
            }
        } else { // otherwise we can send back a list
            List<Object> values = new ArrayList<>();
            for (JsonNode result : resultNodes) {
                String resText = null;
                if (result.isTextual()) {
                    // For TextNode, extract the text
                    resText = result.asText();
                } else if (result.isObject() || result.isArray()) {
                    // For ObjectNode or ArrayNode, extract the string representation
                    resText = result.toString();
                }
                values.add(ProcessorUtils.retypeObject(resText));
            }
            value = values;
        }

        return value;
    }
}
