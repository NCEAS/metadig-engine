package edu.ucsb.nceas.mdqengine.processor;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.script.ScriptException;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathExpressionException;
import javax.xml.xpath.XPathFactory;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

import edu.ucsb.nceas.mdqengine.dispatch.Dispatcher;
import edu.ucsb.nceas.mdqengine.model.Check;
import edu.ucsb.nceas.mdqengine.model.Result;
import edu.ucsb.nceas.mdqengine.model.Selector;
import edu.ucsb.nceas.mdqengine.model.Status;
import edu.ucsb.nceas.mdqengine.serialize.JsonMarshaller;

public class XMLDialect {
	
	private Document document;
	
	private XPathFactory xPathfactory;
	
	private Map<String, String> dataUrls;
	
	protected Log log = LogFactory.getLog(this.getClass());
	
	public XMLDialect(InputStream input) throws SAXException, IOException, ParserConfigurationException {
		
		DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
		DocumentBuilder builder = factory.newDocumentBuilder();
		document = builder.parse(input);
		
		xPathfactory = XPathFactory.newInstance();

	}
	
	public Result runCheck(Check check) throws XPathExpressionException, ScriptException {
		
		log.debug("Running Check: " + JsonMarshaller.toJson(check));
		
		// gather the required information
		Map<String, Object> variables = new HashMap<String, Object>();
		for (Selector selector: check.getSelector()) {
			
			String name = selector.getName();
			Object value = null;
			
			// select one or more values from document
			String selectorPath = selector.getXpath();
			XPath xpath = xPathfactory.newXPath();
			
			// try multiple first
			NodeList nodes = null;
			try {
				nodes = (NodeList) xpath.evaluate(selectorPath, document, XPathConstants.NODESET);
				if (nodes.getLength() > 1) {
					// multiple values
					List<String> values = new ArrayList<String>();
					for (int i = 0; i < nodes.getLength(); i++) {
						Node node = nodes.item(i);
						values.add(node.getTextContent());
					}
					value = values;
				} else {
					// single value
					value = nodes.item(0).getTextContent();
				}
			} catch (XPathExpressionException xpee) {
				log.warn("Defaulting to single value selection: " + xpee.getCause().getMessage());
				// try just a single value
				value = xpath.evaluate(selectorPath, document);
			}
			
			// make available in script
			variables.put(name, value);
		}
		
		// make dataUrls available to the check if we have them
		if (this.dataUrls != null) {
			variables.put("dataUrls", dataUrls);
		}
		
		// dispatch to checker impl
		Dispatcher dispatcher = Dispatcher.getDispatcher(check.getEnvironment());
		
		Result result = dispatcher.dispatch(variables, check.getCode());

		// set the status if it has not been set already
		if (result.getStatus() == null && check.getExpected() != null) {
			if (result.getValue().equals(check.getExpected())) {
				result.setStatus(Status.SUCCESS);
			}
			
		}
		// summarize the result
		result.setCheck(check);
		result.setTimestamp(Calendar.getInstance().getTime());
		
		return result;
	}

	public Map<String, String> getDataUrls() {
		return dataUrls;
	}

	public void setDataUrls(Map<String, String> dataUrls) {
		this.dataUrls = dataUrls;
	}

}
