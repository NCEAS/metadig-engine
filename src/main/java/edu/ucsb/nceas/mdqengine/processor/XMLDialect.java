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

public class XMLDialect {
	
	private Document document;
	
	private XPathFactory xPathfactory;
	
	protected Log log = LogFactory.getLog(this.getClass());
	
	public XMLDialect(InputStream input) throws SAXException, IOException, ParserConfigurationException {
		
		DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
		DocumentBuilder builder = factory.newDocumentBuilder();
		document = builder.parse(input);
		
		xPathfactory = XPathFactory.newInstance();

	}
	
	public Result runCheck(Check check) throws XPathExpressionException, ScriptException {
		
		// gather the required information
		Map<String, Object> variables = new HashMap<String, Object>();
		for (Selector selector: check.getSelectors()) {
			
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
		
		// dispatch to checker impl
		Dispatcher dispatcher = Dispatcher.getDispatcher(check.getEnvironment());
		
		String returnVal = dispatcher.dispatch(variables, check.getCode());

		// summarize the result
		Result result = new Result();
		result.setCheck(check);
		result.setTimestamp(Calendar.getInstance().getTime());
		result.setMessage(returnVal);
		if (check.getExpected() != null) {
			result.setSuccess(returnVal == check.getExpected());
		}
		
		return result;
	}

}
