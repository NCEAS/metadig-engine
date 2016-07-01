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

import org.apache.commons.lang.BooleanUtils;
import org.apache.commons.lang.math.NumberUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

import edu.ucsb.nceas.mdqengine.dispatch.Dispatcher;
import edu.ucsb.nceas.mdqengine.model.Check;
import edu.ucsb.nceas.mdqengine.model.Dialect;
import edu.ucsb.nceas.mdqengine.model.Result;
import edu.ucsb.nceas.mdqengine.model.Selector;
import edu.ucsb.nceas.mdqengine.model.Status;
import edu.ucsb.nceas.mdqengine.serialize.JsonMarshaller;

public class XMLDialect {
	
	private Document document;
	
	private XPathFactory xPathfactory;
	
	private Map<String, String> dataUrls;
	
	public static Log log = LogFactory.getLog(XMLDialect.class);
	
	public XMLDialect(InputStream input) throws SAXException, IOException, ParserConfigurationException {
		
		DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
		DocumentBuilder builder = factory.newDocumentBuilder();
		document = builder.parse(input);
		
		xPathfactory = XPathFactory.newInstance();

	}
	
	public Result runCheck(Check check) throws XPathExpressionException, ScriptException {
		
		Result result = null;
		
		log.debug("Running Check: " + JsonMarshaller.toJson(check));
		
		// only bother dispatching if check can be applied to this document
		if (this.isCheckValid(check)) {
		
			// gather the variable name/value details
			Map<String, Object> variables = new HashMap<String, Object>();
			for (Selector selector: check.getSelector()) {
				
				String name = selector.getName();
				Object value = this.selectPath(selector, document);
				
				// make available in script
				variables.put(name, value);
			}
			
			// make dataUrls available to the check if we have them
			if (this.dataUrls != null) {
				variables.put("dataUrls", dataUrls);
			}
			
			// dispatch to checker impl
			Dispatcher dispatcher = Dispatcher.getDispatcher(check.getEnvironment());
			
			result = dispatcher.dispatch(variables, check.getCode());
	
			// set the status if it has not been set already
			if (result.getStatus() == null && check.getExpected() != null) {
				if (result.getValue().equals(check.getExpected())) {
					result.setStatus(Status.SUCCESS);
				} else {
					result.setStatus(Status.FAILURE);
				}
				
			}
		} else {
			// we just skip instead
			result = new Result();
			result.setStatus(Status.SKIP);
			result.setMessage("Dialect for this check is not supported");
		}
		
		// set additional info before returning
		result.setCheck(check);
		result.setTimestamp(Calendar.getInstance().getTime());
		
		return result;
	}
	
	/**
	 * Determine if the check is valid for the document
	 * @param check
	 * @return
	 * @throws XPathExpressionException
	 */
	public boolean isCheckValid(Check check) throws XPathExpressionException {

		if (check.getDialect() == null) {
			log.info("No dialects have been specified for check, assuming it is valid for this document");
			return true;
		}
		
		XPath xpath = xPathfactory.newXPath();

		for (Dialect dialect: check.getDialect()) {
			
			String name = dialect.getName();
			String expression = dialect.getXpath();
			log.debug("Dialect name: " + name + ", expression: " + expression);
			String value = xpath.evaluate(expression, document);
			
			if (Boolean.valueOf(value)) {
				log.debug("Dialect " + name + " is valid for document ");
				return true;
			} else {
				log.debug("Dialect " + name + " is NOT valid for document");
			}
		}
		
		log.info("No supported check dialects found for this document");

		return false;
	}
	
	private Object selectPath(Selector selector, Node contextNode) throws XPathExpressionException {
		
		Object value = null;
		
		// select one or more values from document
		String selectorPath = selector.getXpath();
		XPath xpath = xPathfactory.newXPath();
		
		// try multiple first
		NodeList nodes = null;
		try {
			nodes = (NodeList) xpath.evaluate(selectorPath, contextNode, XPathConstants.NODESET);
			
			if (nodes.getLength() > 1) {
				// multiple values
				List<Object> values = new ArrayList<Object>();
				
				for (int i = 0; i < nodes.getLength(); i++) {
					Node node = nodes.item(i);
					// is there a subselector?
					if (selector.getSubSelector() != null) {
						Selector subSelector = selector.getSubSelector();
						// recurse
						Object subvalue = this.selectPath(subSelector, node);
						values.add(subvalue);
					} else {
						// otherwise just add the node value
						value = node.getTextContent();
						value = retypeObject(value);
						values.add(value);
					}
				}
				// return the list
				value = values;
			} else if (nodes != null && nodes.getLength() == 1) {
				// just return single value
				value = nodes.item(0).getTextContent();
				value = retypeObject(value);
				
			}
		} catch (XPathExpressionException xpee) {
			log.warn("Defaulting to single value selection: " + xpee.getCause().getMessage());
			// try just a single value
			value = xpath.evaluate(selectorPath, contextNode);
			value = retypeObject(value);
		}
		
		return value;
	
	}
	
	public static Object retypeObject(Object value) {
		Object result = value;
		// type the value correctly
		if (NumberUtils.isNumber((String)value)) {
			result = NumberUtils.createNumber((String)value);
		} else {
			// relies on this method to return null if we are not sure if it is a boolean
			Boolean bool = BooleanUtils.toBooleanObject((String)value);
			if (bool != null) {
				result = bool;
			}
		}
		
		return result;
	}

	public Map<String, String> getDataUrls() {
		return dataUrls;
	}

	public void setDataUrls(Map<String, String> dataUrls) {
		this.dataUrls = dataUrls;
	}

}
