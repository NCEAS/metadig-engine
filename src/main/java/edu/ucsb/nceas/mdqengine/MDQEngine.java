package edu.ucsb.nceas.mdqengine;

import edu.ucsb.nceas.mdqengine.dispatch.Dispatcher;
import edu.ucsb.nceas.mdqengine.dispatch.MDQCache;
import edu.ucsb.nceas.mdqengine.exception.MetadigException;
import edu.ucsb.nceas.mdqengine.model.*;
import edu.ucsb.nceas.mdqengine.processor.GroupLookupCheck;
import edu.ucsb.nceas.mdqengine.processor.XMLDialect;
import edu.ucsb.nceas.mdqengine.serialize.JsonMarshaller;
import edu.ucsb.nceas.mdqengine.serialize.XmlMarshaller;
import edu.ucsb.nceas.mdqengine.store.InMemoryStore;
import edu.ucsb.nceas.mdqengine.store.MDQStore;
import org.apache.commons.configuration2.ex.ConfigurationException;
import org.apache.commons.io.IOUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.dataone.client.v2.itk.D1Client;
import org.dataone.exceptions.MarshallingException;
import org.dataone.service.exceptions.NotImplemented;
import org.dataone.service.exceptions.ServiceFailure;
import org.dataone.service.types.v1.NodeReference;
import org.dataone.service.types.v2.SystemMetadata;
import org.dataone.service.types.v2.TypeFactory;
import org.dataone.service.util.TypeMarshaller;
import org.xml.sax.SAXException;

import javax.script.ScriptException;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.xpath.XPathExpressionException;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

public class MDQEngine {

	// private static final String RESOLVE_PREFIX =
	// getConfiguration().getString("D1Client.CN_URL") + "/v2/resolve/";

	/**
	 * Default store uses the in-memory implementation
	 */

	private MDQStore store = null;

	protected Log log = LogFactory.getLog(this.getClass());
	private static String metadigDataDir = null;

	public MDQEngine() throws MetadigException, IOException, ConfigurationException {
		store = new InMemoryStore();
		// store = new MNStore();
		MDQconfig cfg = new MDQconfig();
		metadigDataDir = cfg.getString("metadig.data.dir");
		MDQCache.initialize(null);
	}

	/**
	 * Executes the given suite for a given object
	 * 
	 * @param suite
	 * @param input  the InputStream for the object to QC
	 * @param params optional additional parameters to make available for the suite
	 * @return the Run results for this execution
	 * @throws MalformedURLException
	 * @throws IOException
	 * @throws SAXException
	 * @throws ParserConfigurationException
	 * @throws XPathExpressionException
	 * @throws ScriptException
	 */
	public Run runSuite(Suite suite, InputStream input, Map<String, Object> params, SystemMetadata sysMeta)
			throws MalformedURLException, IOException, SAXException,
			ParserConfigurationException, XPathExpressionException, ScriptException {

		// Make the location of the data directory available to checks that need to
		// read data files located there.
		if (metadigDataDir != null || !params.containsKey("metadigDataDir")) {
			log.debug("Setting metadigDataDir: " + metadigDataDir);
			params.put("metadigDataDir", metadigDataDir);
		}

		log.debug("Running suite: " + suite.getId());

		String content = IOUtils.toString(input, "UTF-8");
		String metadataContent = content;

		XMLDialect xml = new XMLDialect(IOUtils.toInputStream(metadataContent, "UTF-8"));
		xml.setSystemMetadata(sysMeta);
		Path tempDir = Files.createTempDirectory("mdq_run");
		xml.setDirectory(tempDir.toFile().getAbsolutePath());
		// include the default namespaces from the suite
		xml.mergeNamespaces(suite.getNamespace());

		// make a run to capture results
		Run run = new Run();
		run.setSuiteId(suite.getId());
		run.setId(UUID.randomUUID().toString());
		run.setTimestamp(Calendar.getInstance().getTime());
		List<Result> results = new ArrayList<Result>();

		// get list of data pids
		NodeReference nodeId = sysMeta.getAuthoritativeMemberNode();
		ArrayList<String> dataPids = null;
		try {
			dataPids = findDataPids(nodeId, sysMeta.getIdentifier().getValue());
		} catch (MetadigException e) {
			log.error("Could not retrieve data objects for pid:" + sysMeta.getIdentifier().getValue() + ", node:"
					+ nodeId.getValue());
		}
		params.put("dataPids", dataPids);

		xml.setParams(params);

		// run the checks in the suite to get results
		for (Check check : suite.getCheck()) {
			// is this a reference to existing check?
			if (check.getCode() == null && check.getId() != null) {
				// then load it
				Check origCheck = check;
				check = store.getCheck(origCheck.getId());

				// handle missing references gracefully
				if (check == null) {
					String msg = "Could not locate referenced check in store: " + origCheck.getId();
					log.warn(msg);
					Result r = new Result();
					r.setCheck(origCheck);
					r.setStatus(Status.SKIP);
					r.setOutput(new Output(msg));
					results.add(r);
					continue;
				}

				// The check type and level from the suite definition file takes precedence over
				// the check type
				// and level defined in the check definition file.
				if (origCheck.getLevel() != null)
					check.setLevel(origCheck.getLevel());
				if (origCheck.getType() != null)
					check.setType(origCheck.getType());
			}
			Result result = xml.runCheck(check);
			results.add(result);
		}
		run.setResult(results);

		Dispatcher.getDispatcher("python").close();

		log.trace("Run results: " + JsonMarshaller.toJson(run));

		// clean up
		tempDir.toFile().delete();

		return run;

	}

	/**
	 * Executes the given check for a given object
	 * 
	 * @param check
	 * @param input  the InputStream for the object to QC
	 * @param params optional additional parameters to make available for the check
	 * @return the Run results for this execution
	 * @throws MalformedURLException
	 * @throws IOException
	 * @throws SAXException
	 * @throws ParserConfigurationException`
	 * @throws XPathExpressionException
	 * @throws ScriptException
	 */
	public Run runCheck(Check check, InputStream input, Map<String, Object> params, SystemMetadata sysMeta)
			throws MalformedURLException, IOException, SAXException,
			ParserConfigurationException, XPathExpressionException, ScriptException {

		String content = IOUtils.toString(input, "UTF-8");
		String metadataContent = content;

		XMLDialect xml = new XMLDialect(IOUtils.toInputStream(metadataContent, "UTF-8"));
		xml.setParams(params);
		xml.setSystemMetadata(sysMeta);
		Path tempDir = Files.createTempDirectory("mdq_run");
		xml.setDirectory(tempDir.toFile().getAbsolutePath());

		// make a run to capture results
		Run run = new Run();
		run.setId(UUID.randomUUID().toString());
		run.setTimestamp(Calendar.getInstance().getTime());
		List<Result> results = new ArrayList<Result>();

		// run the check to get results
		Result result = xml.runCheck(check);
		results.add(result);
		run.setResult(results);

		log.trace("Run results: " + JsonMarshaller.toJson(run));

		// clean up
		tempDir.toFile().delete();

		return run;

	}

	/**
	 * To enable checks-by-id-reference, set the store so that checks can be
	 * retrieved
	 * if not specified inline
	 * 
	 * @param store The storage implementation to use for retrieving existing checks
	 */
	public void setStore(MDQStore store) {
		this.store = store;
	}

	public ArrayList<String> findDataPids(NodeReference nodeId, String identifier) throws MetadigException {
		ArrayList<String> dataObjects = new ArrayList<>();
		String dataOneAuthToken = null;
		try {
			MDQconfig cfg = new MDQconfig();
			dataOneAuthToken = System.getenv("DATAONE_AUTH_TOKEN");
			if (dataOneAuthToken == null) {
				dataOneAuthToken = cfg.getString("DataONE.authToken");
				log.debug("Got token from properties file.");
			} else {
				log.debug("Got token from env.");
			}
		} catch (ConfigurationException | IOException ce) {
			MetadigException jee = new MetadigException("error executing task.");
			jee.initCause(ce);
			throw jee;
		}

		if (nodeId.getValue().matches(".*[Tt]est.*")) {
			try {
				D1Client.setCN("https://cn-stage.test.dataone.org/cn");
			} catch (NotImplemented | ServiceFailure e) {
				log.error("Problem confiruging test CN" + e);
			}
		}

		try {
			String nodeEndpoint = D1Client.getMN(nodeId).getNodeBaseServiceUrl();
//			String encodedId = URLEncoder.encode(identifier, "UTF-8");
//			String queryUrl = nodeEndpoint + "/query/solr/?q=isDocumentedBy:" + "\"" + encodedId + "\"" + "&fl=id";
			String queryUrl = nodeEndpoint + "/query/solr/?q=isDocumentedBy:" + "\"" + identifier + "\"" + "&fl=id";
			log.debug("queryURL: " + queryUrl);

			URL url = new URL(queryUrl);
			HttpURLConnection connection = (HttpURLConnection) url.openConnection();
			connection.setRequestMethod("GET");
			connection.setRequestProperty("Accept", "application/xml");
//			if (dataOneAuthToken != null) {
//				connection.setRequestProperty("Authorization", "Bearer " + dataOneAuthToken);
//			}

			if (connection.getResponseCode() != 200) {
				throw new RuntimeException("Failed : HTTP error code : " + connection.getResponseCode());
			}

			InputStream xml = connection.getInputStream();
			DocumentBuilderFactory dbFactory = DocumentBuilderFactory.newInstance();
			DocumentBuilder dBuilder = dbFactory.newDocumentBuilder();
			Document doc = dBuilder.parse(xml);
			doc.getDocumentElement().normalize();

			NodeList nodeList = doc.getElementsByTagName("str");
			for (int i = 0; i < nodeList.getLength(); i++) {
				Element element = (Element) nodeList.item(i);
				if ("id".equals(element.getAttribute("name"))) {
					if (!element.getTextContent().equals(identifier)) {
						dataObjects.add(element.getTextContent());
					}
				}
			}

			connection.disconnect();
		} catch (ServiceFailure e) {
			log.error("Could not retrieve member node while finding data objects: " + e);
		} catch (ParserConfigurationException | SAXException e) {
			log.error("Could parse response while finding data objects:" + e);
		} catch (IOException e) {
			log.error("Could not retrieve data objects:" + e);
		}
		return (dataObjects);
	}

	/**
	 * Run a suite on a given metadata document. Prints Run XML results.
	 * 
	 * @param args first is the suite file path, second is the metadata file path
	 * 
	 */
	public static void main(String args[]) {

		MDQEngine engine;
		Map<String, Object> params = new HashMap<>();

		try {
			engine = new MDQEngine();
			String xml = IOUtils.toString(new FileInputStream(args[0]), "UTF-8");
			Suite suite = (Suite) XmlMarshaller.fromXml(xml, Suite.class);
			InputStream input = new FileInputStream(args[1]);
			InputStream sysmetaInputStream = null;
			SystemMetadata sysmeta = null;
			Object tmpSysmeta = null;

			// Read in the system metadata XML file if it is provided. Suites can be run
			// without it. The SystemMetadata can be either version 1 or 2. The current type
			// marshaller cannot handle version 1, so we have to convert v1 to v2 (seems
			// like the marshalling call should do this for us). The drawback to this
			// approach is that it will be necessary to test for sysmeta v3 when it is
			// released.
			if (args.length >= 3) {
				Class smClasses[] = { org.dataone.service.types.v2.SystemMetadata.class,
						org.dataone.service.types.v1.SystemMetadata.class };
				for (Class thisClass : smClasses) {
					sysmetaInputStream = new FileInputStream(args[2]);
					try {
						tmpSysmeta = TypeMarshaller.unmarshalTypeFromStream(thisClass, sysmetaInputStream);
						// Didn't get an error so proceed to convert to sysmeta v2, if needed.
						break;
					} catch (ClassCastException cce) {
						cce.printStackTrace();
						continue;
					} catch (InstantiationException | IllegalAccessException | IOException | MarshallingException fis) {
						fis.printStackTrace();
						continue;
					}
				}

				if (tmpSysmeta.getClass().getName().equals("org.dataone.service.types.v1.SystemMetadata")) {
					try {
						sysmeta = TypeFactory.convertTypeFromType(tmpSysmeta, SystemMetadata.class);
					} catch (InstantiationException | IllegalAccessException ce) {
						ce.printStackTrace();
					}
				} else {
					sysmeta = (SystemMetadata) tmpSysmeta;
				}
			}

			Run run = engine.runSuite(suite, input, params, sysmeta);
			run.setRunStatus("SUCCESS");

			// Add DataONE sysmeta, if it was provided.
			if (sysmeta != null) {
				SysmetaModel smm = new SysmetaModel();
				// These sysmeta fields are always provided
				smm.setOriginMemberNode(sysmeta.getOriginMemberNode().getValue());
				smm.setRightsHolder(sysmeta.getRightsHolder().getValue());
				smm.setDateUploaded(sysmeta.getDateUploaded());
				smm.setFormatId(sysmeta.getFormatId().getValue());
				// These fields aren't required.
				if (sysmeta.getObsoletes() != null)
					smm.setObsoletes(sysmeta.getObsoletes().getValue());
				if (sysmeta.getObsoletedBy() != null)
					smm.setObsoletedBy(sysmeta.getObsoletedBy().getValue());
				if (sysmeta.getSeriesId() != null)
					smm.setSeriesId(sysmeta.getSeriesId().getValue());

				// Now make the call to DataONE to get the group information for this
				// rightsHolder.
				// Only wait for a certain amount of time before we will give up.
				ExecutorService executorService = Executors.newSingleThreadExecutor();

				// Provide the rightsHolder to the DataONE group lookup.
				GroupLookupCheck glc = new GroupLookupCheck();
				glc.setRightsHolder(sysmeta.getRightsHolder().getValue());
				Future<List<String>> future = executorService.submit(glc);

				List<String> groups = null;
				for (int i = 0; i < 5; i++) {
					try {
						groups = future.get();
					} catch (Throwable thrown) {
						System.out.println("Error while waiting for thread completion");
					}
					// Sleep for 1 second

					if (groups != null)
						break;
					System.out.println("Waiting 1 second for groups");
					Thread.sleep(1000);
				}

				if (groups != null) {
					System.out.println("Setting groups");
					smm.setGroups(groups);
				} else {
					System.out.println("No groups to set");
				}
				executorService.shutdown();
				run.setSysmeta(smm);
			}

			System.out.println(XmlMarshaller.toXml(run, true));
		} catch (Exception e) {
			// TODO Auto-generated catch block
			// Store the error in the 'Run' object so it can be saved to the run store.
			try {
				Run run = new Run();
				run.setRunStatus(Run.FAILURE);
				run.setErrorDescription(e.getMessage());
				e.printStackTrace();
			} catch (Exception e2) {
				e2.printStackTrace();
			}
		}
	}
}
