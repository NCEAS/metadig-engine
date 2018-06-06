package edu.ucsb.nceas.mdqengine.processor;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.StringWriter;
import java.net.URL;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashMap;
import java.util.Iterator;
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
import org.dataone.service.types.v2.SystemMetadata;
import org.dataone.service.util.TypeMarshaller;
import org.springframework.util.xml.SimpleNamespaceContext;
import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

import edu.ucsb.nceas.mdqengine.dispatch.Dispatcher;
import edu.ucsb.nceas.mdqengine.model.Check;
import edu.ucsb.nceas.mdqengine.model.Dialect;
import edu.ucsb.nceas.mdqengine.model.Namespace;
import edu.ucsb.nceas.mdqengine.model.Output;
import edu.ucsb.nceas.mdqengine.model.Result;
import edu.ucsb.nceas.mdqengine.model.Selector;
import edu.ucsb.nceas.mdqengine.model.Status;

public class XMLDialect {
	
	private Document document;
	
	private Document nsAwareDocument;
	
	private SystemMetadata systemMetadata;

	private XPathFactory xPathfactory;
	
	private Map<String, Object> params;
		
	private Map<String,Namespace> namespaces = new HashMap<String, Namespace>();
	
	private String directory;
	
	private Dispatcher dispatcher;
	
	public static Log log = LogFactory.getLog(XMLDialect.class);
	
	public XMLDialect(InputStream input) throws SAXException, IOException, ParserConfigurationException {
		DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
		factory.setNamespaceAware(false);
		DocumentBuilder builder = factory.newDocumentBuilder();
		
		DocumentBuilderFactory nsFactory = DocumentBuilderFactory.newInstance();
		nsFactory.setNamespaceAware(true);
		DocumentBuilder nsBuilder = nsFactory.newDocumentBuilder();
		
		byte[] bytes = IOUtils.toByteArray(input);
		
		document = builder.parse(new ByteArrayInputStream(bytes));
		nsAwareDocument = nsBuilder.parse(new ByteArrayInputStream(bytes));

		// please let garbage collection take this space back
		bytes = null;
		
		xPathfactory = XPathFactory.newInstance();
		
		// now we can extract the namespaces from the source document
		this.extractNamespaces();

	}
	
	private void extractNamespaces() {
		XPath xpath = xPathfactory.newXPath();
		NodeList nodes = null;
		try {
			//String selectorPath = "//*[namespace-uri()]/concat(substring-before(name(), ':'),':',namespace-uri())";
			String selectorPath = "//*[namespace-uri()]";

			nodes = (NodeList) xpath.evaluate(selectorPath , this.nsAwareDocument, XPathConstants.NODESET);
			if (nodes != null && nodes.getLength() > 0) {
				for (int i = 0; i <nodes.getLength(); i++) {
					Node node = nodes.item(i);
					String uri = node.getNamespaceURI();
					String prefix = node.getPrefix();
					
					Namespace ns = new Namespace();
					ns.setPrefix(prefix);
					ns.setUri(uri);
					if (!this.namespaces.containsKey(uri)) {
						this.namespaces.put(uri, ns);
					}
				}
			}
		} catch (XPathExpressionException e) {
			log.error("Could not extract the namespaces from document", e);
		}
	}
	
	// include additional namespaces
	public void mergeNamespaces(List<Namespace> namespaces) {
		if (namespaces != null) {
			for (Namespace namespace: namespaces) {
				this.namespaces.put(namespace.getPrefix(), namespace);
			}
		}
	}
	
	public Result runCheck(Check check) throws XPathExpressionException {
		
		Result result = null;
		
		log.debug("Running Check: " + check.getId());
		
		// only bother dispatching if check can be applied to this document
		if (this.isCheckValid(check)) {
		
			// gather the variable name/value details
			Map<String, Object> variables = new HashMap<String, Object>();
			if (check.getSelector() != null) {
				for (Selector selector: check.getSelector()) {
					
					Document docToUse = document;
					if (selector.isNamespaceAware()) {
						docToUse = nsAwareDocument;
					}
					
					String name = selector.getName();
					Object value = this.selectPath(selector, docToUse);
					
					// make available in script
					variables.put(name, value);
				}
			}
			
			// make the entire dom available
			// TODO: string seems like only viable option for all env
			variables.put("document", toXmlString(document));
			
			// include system metadata if available
			if (this.systemMetadata != null) {
				try {
					ByteArrayOutputStream baos = new ByteArrayOutputStream();
					TypeMarshaller.marshalTypeToOutputStream(systemMetadata, baos);
					variables.put("systemMetadata", baos.toString("UTF-8"));
					variables.put("systemMetadataPid", systemMetadata.getIdentifier().getValue());
                    variables.put("authoritativeMemberNode", systemMetadata.getAuthoritativeMemberNode());
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
					// TODO: loading random code from a URL is very risky!
					log.debug("Loading library code from URL: " + library);
					// read the library from given URL
					try {
						libraryContent += IOUtils.toString(library.openStream(), "UTF-8");
					} catch (IOException e) {
						log.error("Could not load code library: " + e.getMessage(), e);
						// report this
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
		
		// combine the found namespaces and any additional ones asserted by selector. order matters here
		List<Namespace> selectorNamespaces = new ArrayList<Namespace>();
		
		if (this.namespaces.values() != null) {
			selectorNamespaces.addAll(this.namespaces.values());
		}
		if (selector.getNamespace() != null) {
			selectorNamespaces.addAll(selector.getNamespace());
		}

		// do we have any to actually set now?
		if (selectorNamespaces != null && selectorNamespaces.size() > 0) {
			SimpleNamespaceContext nsContext = new SimpleNamespaceContext();
			Iterator<Namespace> nsIter = selectorNamespaces.iterator();
			while (nsIter.hasNext()) {
				Namespace entry = nsIter.next();
				String prefix = entry.getPrefix();
				String uri = entry.getUri();
				// make sure we are overriding the found namespace[s] with the asserted ones
				String existing = nsContext.getNamespaceURI(prefix);
				if (existing == null) {
					nsContext.removeBinding(prefix);
				}
				nsContext.bindNamespaceUri(prefix, uri);
			}
			xpath.setNamespaceContext(nsContext);	
		}
		
		
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
			try {
				value = xpath.evaluate(selectorPath, contextNode);
				value = retypeObject(value);
			} catch (XPathExpressionException xpee2) {
				log.error("Could not select single value with given Xpath: " + xpee2.getCause().getMessage());
				value = null;
			}
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

	public Map<String, Object> getParams() {
		return params;
	}

	public void setParams(Map<String, Object> params) {
		this.params = params;
	}
	
	public void setDirectory(String dir) {
		this .directory = dir;
	}
	
	public SystemMetadata getSystemMetadata() {
		return systemMetadata;
	}

	public void setSystemMetadata(SystemMetadata systemMetadata) {
		this.systemMetadata = systemMetadata;
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
