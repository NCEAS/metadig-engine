package edu.ucsb.nceas.mdqengine.processor;

import java.io.IOException;
import java.io.InputStream;
import java.io.StringWriter;
import java.io.UnsupportedEncodingException;
import java.net.URL;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.script.ScriptException;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathExpressionException;
import javax.xml.xpath.XPathFactory;

import org.apache.commons.codec.binary.Base64;
import org.apache.commons.io.IOUtils;
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
	
	private String directory;
	
	private Dispatcher dispatcher;
	
	public static Log log = LogFactory.getLog(XMLDialect.class);
	
	public XMLDialect(InputStream input) throws SAXException, IOException, ParserConfigurationException {
		
		DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
		DocumentBuilder builder = factory.newDocumentBuilder();
		document = builder.parse(input);
		
		xPathfactory = XPathFactory.newInstance();

	}
	
	public Result runCheck(Check check) throws XPathExpressionException {
		
		Result result = null;
		
		log.debug("Running Check: " + JsonMarshaller.toJson(check));
		
		// only bother dispatching if check can be applied to this document
		if (this.isCheckValid(check)) {
		
			// gather the variable name/value details
			Map<String, Object> variables = new HashMap<String, Object>();
			if (check.getSelector() != null) {
				for (Selector selector: check.getSelector()) {
					
					String name = selector.getName();
					Object value = this.selectPath(selector, document);
					
					// make available in script
					variables.put(name, value);
				}
			}
			
			// make the entire dom available
			// TODO: string seems like only viable option for all env
			variables.put("document", toXmlString(document));
			
			// make dataUrls available to the check if we have them
			if (this.dataUrls != null) {
				variables.put("dataUrls", dataUrls);
			}
			
			// give the check a place to write files during the run
			if (this.directory != null) {
				variables.put("tempDir", directory);
			}
			
			// dispatch to checker impl
			if (!check.isInheritState() || dispatcher == null) {
				// create a fresh dispatcher 
				dispatcher = Dispatcher.getDispatcher(check.getEnvironment());
				log.debug("Using new dispatcher for check");
			} else {

				// ensure that we can reuse the dispatcher to inherit previous state
				if (!dispatcher.isEnvSupported(check.getEnvironment())) {
					
					// use the bindings from previous dispatcher
					Map<String, Object> bindings = dispatcher.getBindings();
					if (bindings == null) {
						// nothing to inherit
						result = new Result();
						result.setStatus(Status.ERROR);
						result.setOutput("Check cannot use persistent state from previous differing environment");
						return result;
					} else {
						
						// use the bindings from previous dispatcher
						for (String key: bindings.keySet()) {
							Object value = bindings.get(key);
							value = retypeObject(value.toString());
							log.debug("binding: " + key + "=" + value);
							variables.put(key, value);
						}
						log.debug("Binding existing variables for new dispatcher");

						// and a fresh dispatcher
						dispatcher = Dispatcher.getDispatcher(check.getEnvironment());
					}
				} else {
					log.debug("Reusisng dispatcher for persistent state check");

				}
			}
			
			// gather extra code from external resources
			String code = check.getCode();
			URL library = check.getLibrary();

			// TODO: loading random code from a URL is very risky!
			if (library != null) {
				log.debug("Loading library code from URL: " + library);
				// read the library from given URL
				try {
					String libraryContent = IOUtils.toString(library.openStream(), "UTF-8");
					code = libraryContent + code;
				} catch (IOException e) {
					log.error("Could not load code library: " + e.getMessage(), e);
					// report this
					result = new Result();
					result.setStatus(Status.ERROR);
					result.setOutput(e.getMessage());
				}
			}
			
			try {
				result = dispatcher.dispatch(variables, code);
			} catch (ScriptException e) {
				// report this
				result = new Result();
				result.setStatus(Status.ERROR);
				result.setOutput(e.getMessage());
			}
	
		} else {
			// we just skip instead
			result = new Result();
			result.setStatus(Status.SKIP);
			result.setOutput("Dialect for this check is not supported");
		}
		
		// set additional info before returning
		result.setCheck(check);
		result.setTimestamp(Calendar.getInstance().getTime());
		
		// do any further processing on the result (e.g., encode value if needed)
		result = postProcess(result);
		
		return result;
	}
	
	private Result postProcess(Result result) {
	
		// check if we need to encode the output value
		String value = result.getOutput();
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
					encoded = Base64.encodeBase64String(value.getBytes("UTF-8"));
					result.setOutput(encoded);
					//TODO: set mime-type when we have support for that
				} catch (UnsupportedEncodingException e) {
					log.error(e.getMessage());
				}				
			}
			
		}
		
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
			
			if (nodes != null && nodes.getLength() == 1 && selector.getSubSelector() == null) {
				
				// just return single value
				value = nodes.item(0).getTextContent();
				value = retypeObject(value);
				
			}
			
			else if (nodes.getLength() > 0 || selector.getSubSelector() != null) {
				
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
	
	public void setDirectory(String dir) {
		this .directory = dir;
	}
	
	private String toXmlString(Document document) {
		try {
		    Transformer transformer = TransformerFactory.newInstance().newTransformer();
		    StreamResult result = new StreamResult(new StringWriter());
		    DOMSource source = new DOMSource(document);
		    transformer.transform(source, result);
		    return result.getWriter().toString();
		} catch(TransformerException ex) {
		    ex.printStackTrace();
		    return null;
		}

	}

}
