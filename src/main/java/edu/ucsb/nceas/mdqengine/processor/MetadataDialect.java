package edu.ucsb.nceas.mdqengine.processor;

import edu.ucsb.nceas.mdqengine.model.Result;
import edu.ucsb.nceas.mdqengine.model.Check;
import edu.ucsb.nceas.mdqengine.model.Selector;
import edu.ucsb.nceas.mdqengine.model.Namespace;
import org.w3c.dom.Node;
import java.util.Map;

import javax.xml.xpath.XPathExpressionException;

import java.util.List;
import org.dataone.service.types.v2.SystemMetadata;

public interface MetadataDialect {
    void extractNamespaces();
    void mergeNamespaces(List<Namespace> namespaces);
    Result runCheck(Check check) throws XPathExpressionException;
    Result postProcess(Result result);
    boolean isCheckValid(Check check) throws XPathExpressionException;
    Object selectPath(Selector selector, Node contextNode) throws XPathExpressionException;
    Object retypeObject(Object value);
    Map<String, Object> getParams();
    void setParams(Map<String, Object> params);
    void setDirectory(String dir);
    SystemMetadata getSystemMetadata();
    void setSystemMetadata(SystemMetadata systemMetadata);
}