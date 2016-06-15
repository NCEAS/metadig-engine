package edu.ucsb.nceas.mdqengine.processor;

import java.io.IOException;
import java.io.InputStream;
import java.util.Calendar;
import java.util.HashMap;
import java.util.Map;

import javax.script.ScriptException;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathExpressionException;
import javax.xml.xpath.XPathFactory;

import org.w3c.dom.Document;
import org.xml.sax.SAXException;

import edu.ucsb.nceas.mdqengine.dispatch.Dispatcher;
import edu.ucsb.nceas.mdqengine.model.Check;
import edu.ucsb.nceas.mdqengine.model.Result;
import edu.ucsb.nceas.mdqengine.model.Selector;

public class XMLDialect {
	
	private Document document;
	private XPathFactory xPathfactory;

	
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
			String selectorPath = selector.getXpath();
			XPath xpath = xPathfactory.newXPath();
			String value = xpath.evaluate(selectorPath, document);
			String name = selector.getName();
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
