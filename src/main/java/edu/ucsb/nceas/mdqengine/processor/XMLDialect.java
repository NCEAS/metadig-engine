package edu.ucsb.nceas.mdqengine.processor;

import edu.ucsb.nceas.mdqengine.dispatch.Dispatcher;
import edu.ucsb.nceas.mdqengine.model.*;
import org.apache.commons.io.IOUtils;
import org.dataone.service.util.TypeMarshaller;
import org.springframework.util.xml.SimpleNamespaceContext;
import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

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
import java.io.*;
import java.net.URL;
import java.text.SimpleDateFormat;
import java.util.*;

/**
 * A concrete implementation of MetadataDialect for handling XML metadata
 * documents.
 * 
 * This dialect provides xpath-based querying and namespace handling to support
 * extraction of values from XML-based metadata standards.
 */
public class XMLDialect extends AbstractMetadataDialect {

	private Document document;
	private Document nsAwareDocument;
	private XPathFactory xPathfactory;
	private Dispatcher dispatcher;

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

	@Override
	public void extractNamespaces() {
		XPath xpath = xPathfactory.newXPath();
		NodeList nodes = null;
		try {
			String selectorPath = "//*[namespace-uri()]";

			nodes = (NodeList) xpath.evaluate(selectorPath, this.nsAwareDocument, XPathConstants.NODESET);
			if (nodes != null && nodes.getLength() > 0) {
				for (int i = 0; i < nodes.getLength(); i++) {
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
	@Override
	public void mergeNamespaces(List<Namespace> namespaces) {
		if (namespaces != null) {
			for (Namespace namespace : namespaces) {
				this.namespaces.put(namespace.getPrefix(), namespace);
			}
		}
	}

	@Override
	public Result runCheck(Check check) throws XPathExpressionException {

		Result result = null;

		log.debug("Running Check: " + check.getId());

		// only bother dispatching if check can be applied to this document
		if (this.isCheckValid(check)) {

			// gather the variable name/value details
			Map<String, Object> variables = new HashMap<String, Object>();
			if (check.getSelector() != null) {
				for (Selector selector : check.getSelector()) {

					Document docToUse = document;
					if (selector.isNamespaceAware()) {
						docToUse = nsAwareDocument;
					}
					String name = selector.getName();
					if (selector.getXpath() == null && selector.getExpression() == null) {
						continue;
					}
					if (selector.getExpression() != null) {
						if (!"xpath".equals(selector.getExpression().getSyntax())) {
							continue;
						}
					}
					Object value = this.selectXPath(selector, docToUse);
					// make available in script
					variables.put(name, value);
				}
			}

			// make the entire dom available
			// TODO: string seems like only viable option for all env
			variables.put("document", toXmlString(document));

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
			// if data pids are present, make them available to the check code
			if (this.params.get("dataPids") != null) {
				variables.put("dataPids", this.params.get("dataPids"));
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
				log.debug("Creating initial check dispatcher for " + check.getEnvironment());
			} else {

				// ensure that we can reuse the dispatcher to inherit previous state
				if (!dispatcher.isEnvSupported(check.getEnvironment())) {

					// use the bindings from previous dispatcher
					Map<String, Object> bindings = dispatcher.getBindings();
					if (bindings == null) {
						// nothing to inherit
						result = new Result();
						result.setStatus(Status.ERROR);
						result.setOutput(
								new Output("Check cannot use persistent state from previous differing environment"));
						return result;
					} else {

						// use the bindings from previous dispatcher
						for (String key : bindings.keySet()) {
							Object value = bindings.get(key);
							value = ProcessorUtils.retypeObject(value.toString());
							log.trace("binding: " + key + "=" + value);
							variables.put(key, value);
						}

						log.debug("Binding existing variables for new dispatcher");

						// and a fresh dispatcher
						dispatcher = Dispatcher.getDispatcher(check.getEnvironment());
						log.debug("Creating new check dispatcher for " + check.getEnvironment());
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
				for (URL library : libraries) {
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

	@Override
	public boolean isCheckValid(Check check) throws XPathExpressionException {

		if (check.getDialect() == null) {
			log.debug("No dialects have been specified for check, assuming it is valid for this document");
			return true;
		}

		XPath xpath = xPathfactory.newXPath();

		for (Dialect dialect : check.getDialect()) {

			String name = dialect.getName();
			String expression = dialect.getXpath();
			if (expression == null) {
				continue;
			}
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

	/**
	 * Evaluates the xpath expression defined in the given {@link Selector} against
	 * the provided XML node.
	 * 
	 * This method extracts value(s) from the XML document based on the xpath
	 * specified in the selector. If namespaces are defined in the selector, they
	 * are registered and applied during the xpath evaluation. The result may be a
	 * single value, a list of values, or null if no match is found.
	 *
	 * @param selector    the {@link Selector} containing the xpath expression and
	 *                    optional namespace context
	 * @param contextNode the XML {@link Node} to evaluate the xpath expression
	 *                    against
	 * @return the value(s) extracted by the xpath expression, or null if no match
	 *         is found
	 * @throws XPathExpressionException if the xpath expression is invalid or cannot
	 *                                  be evaluated
	 */
	public Object selectXPath(Selector selector, Node contextNode) throws XPathExpressionException {

		Object value = null;

		// TODO: account for possible xpaths in expression element here

		// select one or more values from document
		String selectorPath = selector.getXpath();

		XPath xpath = xPathfactory.newXPath();

		// combine the found namespaces and any additional ones asserted by selector.
		// order matters here
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
				// Some metadata files may have improper xmlns declarations that don't include
				// a prefix (encountered in Dryad Data), so skip these.
				if (prefix == null) {
					continue;
				}

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

				// just return single value, as a String
				value = nodes.item(0).getTextContent();
				value = ProcessorUtils.retypeObject(value);

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
						Object subvalue = this.selectXPath(subSelector, node);
						values.add(subvalue);
					} else {
						// otherwise just add the node value
						value = node.getTextContent();
						value = ProcessorUtils.retypeObject(value);
						values.add(value);
					}
				}
				// return the list
				value = values;
			}
		} catch (XPathExpressionException xpee) {
			log.debug("Defaulting to single value selection: " + xpee.getCause().getMessage());

			// try just a single value
			try {
				value = xpath.evaluate(selectorPath, contextNode);
				value = ProcessorUtils.retypeObject(value);
			} catch (XPathExpressionException xpee2) {
				log.error("Selector '" + selector.getName() + "'" + " could not select single value with given Xpath: "
						+ xpee2.getCause().getMessage());
				value = null;
			}
		}

		return value;

	}

	/**
	 * Converts the given Document object to a well-formed XML string
	 * representation.
	 * 
	 * @param document the {@link Document} object to be converted to XML string
	 * 
	 * @return the XML string representation of the document, or null if an
	 *         error occurs
	 */
	public String toXmlString(Document document) {
		try {
			Transformer transformer = TransformerFactory.newInstance().newTransformer();
			StreamResult result = new StreamResult(new StringWriter());
			DOMSource source = new DOMSource(document);
			transformer.transform(source, result);
			return result.getWriter().toString();
		} catch (TransformerException ex) {
			log.error("Error transforming to XML string. " + ex.getMessage());
			return null;
		}

	}

}
