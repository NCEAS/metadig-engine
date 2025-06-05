package edu.ucsb.nceas.mdqengine.processor;

import edu.ucsb.nceas.mdqengine.model.Result;
import edu.ucsb.nceas.mdqengine.model.Check;
import edu.ucsb.nceas.mdqengine.model.Namespace;
import java.util.Map;

import javax.xml.xpath.XPathExpressionException;
import net.thisptr.jackson.jq.exception.JsonQueryException;

import java.util.List;
import org.dataone.service.types.v2.SystemMetadata;

/**
 * The MetadataDialect interface defines operations for interacting with and
 * evaluating metadata documents in metadig-engine according to their specific
 * dialect. Implementations of this interface are responsible for extracting
 * values, managing namespaces, running checks, and handling results.
 * 
 * This abstraction allows checks to operate on different metadata serialization
 * formats such as XML or JSON while maintaining a consistent interface.
 * 
 * @author clark
 */
public interface MetadataDialect {

    /**
     * Extracts any relevant namespaces from the metadata document.
     * This is used for xpath evaluation in XML documents.
     */
    void extractNamespaces();

    /**
     * Merges the provided list of namespaces with those already extracted
     * from the document.
     *
     * @param namespaces a list of namespaces to merge
     */
    void mergeNamespaces(List<Namespace> namespaces);

    /**
     * Executes the given check against the metadata document and returns a result.
     * The method evaluates selectors, runs user-defined code, and collects output.
     *
     * @param check the check to execute
     * @return a {@link Result} object containing the outcome of the check
     * @throws XPathExpressionException if an XPath evaluation fails
     * @throws JsonQueryException       if a JQ expression evaluation fails
     */
    Result runCheck(Check check) throws XPathExpressionException, JsonQueryException;

    /**
     * Post-processes a result after the main check execution has completed.
     * 
     * @param result the original result
     * @return the post-processed result
     */
    Result postProcess(Result result);

    /**
     * Determines whether a given check is applicable to this metadata document.
     * Uses the check's dialect conditions to evaluate applicability.
     *
     * @param check the check to evaluate
     * @return true if the check applies to this document, false otherwise
     * @throws XPathExpressionException if XPath evaluation fails
     */
    boolean isCheckValid(Check check) throws XPathExpressionException;

    /**
     * Gets additional runtime parameters associated with the dialect instance.
     *
     * @return a map of parameter names to values
     */
    Map<String, Object> getParams();

    /**
     * Sets additional runtime parameters.
     *
     * @param params a map of parameter names to values
     */
    void setParams(Map<String, Object> params);

    /**
     * Sets the directory path used for temporary file access.
     *
     * @param dir the base directory path
     */
    void setDirectory(String dir);

    /**
     * Gets the DataONE {@link SystemMetadata} associated with the current metadata
     * document.
     *
     * @return the system metadata object
     */
    SystemMetadata getSystemMetadata();

    /**
     * Sets the DataONE {@link SystemMetadata} context for this dialect instance.
     *
     * @param systemMetadata the system metadata object to associate
     */
    void setSystemMetadata(SystemMetadata systemMetadata);
}
