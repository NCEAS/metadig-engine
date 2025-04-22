package edu.ucsb.nceas.mdqengine.processor;

import edu.ucsb.nceas.mdqengine.model.Result;
import edu.ucsb.nceas.mdqengine.model.Check;
import edu.ucsb.nceas.mdqengine.model.Namespace;
import java.util.Map;

import javax.xml.xpath.XPathExpressionException;
import net.thisptr.jackson.jq.exception.JsonQueryException;

import java.util.List;
import org.dataone.service.types.v2.SystemMetadata;

public interface MetadataDialect {
    void extractNamespaces();

    void mergeNamespaces(List<Namespace> namespaces);

    Result runCheck(Check check) throws XPathExpressionException, JsonQueryException;

    Result postProcess(Result result);

    boolean isCheckValid(Check check) throws XPathExpressionException;

    Map<String, Object> getParams();

    void setParams(Map<String, Object> params);

    void setDirectory(String dir);

    SystemMetadata getSystemMetadata();

    void setSystemMetadata(SystemMetadata systemMetadata);
}