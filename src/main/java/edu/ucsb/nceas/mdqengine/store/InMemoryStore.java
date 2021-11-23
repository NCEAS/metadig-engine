package edu.ucsb.nceas.mdqengine.store;

import edu.ucsb.nceas.mdqengine.MDQconfig;
import edu.ucsb.nceas.mdqengine.exception.MetadigStoreException;
import edu.ucsb.nceas.mdqengine.model.*;
import edu.ucsb.nceas.mdqengine.serialize.XmlMarshaller;
import org.apache.commons.configuration2.ex.ConfigurationException;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang.ArrayUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.dataone.service.types.v2.Node;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.xml.sax.SAXException;

import javax.xml.bind.JAXBException;
import java.io.IOException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

/**
 * Place-holder storage implementation for 
 * Checks, Suites, and Runs
 * NOT INTENDED FOR PRODUCTION USE
 * @author leinfelder
 *
 */
public class InMemoryStore implements MDQStore{
	
	Map<String, Suite> suites = new HashMap<String, Suite>();
	
	Map<String, Check> checks = new HashMap<String, Check>();
	
	Map<String, Run> runs = new HashMap<String, Run>();
	
	public InMemoryStore() throws MetadigStoreException {
		this.init();
	}
	protected Log log = LogFactory.getLog(this.getClass());
	
	private void init() throws MetadigStoreException {

		MDQconfig cfg = null;

	    try {
			cfg = new MDQconfig();
		} catch (IOException | ConfigurationException e) {

		}
		String storeDirectory;

		try {
			storeDirectory = cfg.getString("metadig.base.directory");
		} catch (ConfigurationException cex) {
			log.error("Unable to read configuration");
			MetadigStoreException mse = new MetadigStoreException("Unable to read config properties");
			mse.initCause(cex.getCause());
			throw mse;
		}
		
		PathMatchingResourcePatternResolver resolver = new PathMatchingResourcePatternResolver();
		
		Resource[] suiteResources = null;
		// load all the resources from local files
		try {
			suiteResources  = resolver.getResources("classpath*:/suites/*.xml");
			// do we have an additional location for these?
			if (storeDirectory != null) {
				log.debug("Reading suites from: file://" + storeDirectory + "/suites");
				Resource[] additionalSuiteResources = resolver.getResources("file://" + storeDirectory + "/suites/*.xml");
				log.debug("Adding " + additionalSuiteResources.length + " additional suites");
				suiteResources = (Resource[]) ArrayUtils.addAll(suiteResources, additionalSuiteResources);
			}
		} catch (IOException e) {
			log.error("Could not read local suite resources: " + e.getMessage(), e);
		}
		if (suiteResources != null) {
			for (Resource resource: suiteResources) {
				Suite suite = null;
				try {
					URL url = resource.getURL();
					log.trace("Loading suite found at: " + url.toString());
					String xml = IOUtils.toString(url.openStream(), "UTF-8");
					suite = (Suite) XmlMarshaller.fromXml(xml, Suite.class);
				} catch (JAXBException | IOException | SAXException e) {
					log.warn("Could not load suite '" + resource.getFilename() + "' due to an error: " + e.getMessage() + ".");
					continue;
				}
				this.createSuite(suite);
	
			}
		}
		
		// checks
		Resource[] checkResources = null;
		// load all the resources from local files
		try {
			checkResources  = resolver.getResources("classpath*:/checks/*.xml");
			// do we have an additional location for these?
			if (storeDirectory != null) {
				log.trace("Reading checks from: file://" + storeDirectory + "/checks");
				Resource[] additionalCheckResources = resolver.getResources("file://" + storeDirectory + "/checks/*.xml");
				checkResources = (Resource[]) ArrayUtils.addAll(checkResources, additionalCheckResources);
				log.trace("Adding " + additionalCheckResources.length + " additional checks");
			}
		} catch (IOException e) {
			log.error("Could not read local check resources: " + e.getMessage(), e);
		}
		if (checkResources != null) {
			for (Resource resource: checkResources) {
				
				Check check = null;
				try {
					URL url = resource.getURL();
					log.trace("Loading check found at: " + url.toString());
					String xml = IOUtils.toString(url.openStream(), "UTF-8");
					check = (Check) XmlMarshaller.fromXml(xml, Check.class);
				} catch (JAXBException | IOException | SAXException e) {
					log.warn("Could not load check '" + resource.getFilename() + "' due to an error: " + e.getMessage() + ".");
					continue;
				}
				this.createCheck(check);
			}
		}		
	}
	
	@Override
	public Collection<String> listSuites() {
		return suites.keySet();
	}

	@Override
	public Suite getSuite(String id) {
		return suites.get(id);
	}

	@Override
	public void createSuite(Suite rec) {
		suites.put(rec.getId(), rec);
	}

	@Override
	public void updateSuite(Suite rec) {
		suites.put(rec.getId(), rec);
	}

	@Override
	public void deleteSuite(Suite rec) {
		suites.remove(rec.getId());		
	}

	@Override
	public Collection<String> listChecks() {
		return checks.keySet();
	}

	@Override
	public Check getCheck(String id) {
		return checks.get(id);
	}

	@Override
	public void createCheck(Check check) { checks.put(check.getId(), check); }

	@Override
	public void updateCheck(Check check) {
		checks.put(check.getId(), check);		
	}

	@Override
	public void deleteCheck(Check check) {
		checks.remove(check.getId());
	}

	@Override
	public Collection<String> listRuns() {
		return runs.keySet();
	}

	@Override
	public Run getRun(String suite, String id) {
		return runs.get(id);
	}

	@Override
	public void saveRun(Run run) { }

	@Override
	public void createRun(Run run) {
		runs.put(run.getId(), run);
	}

	@Override
	public void deleteRun(Run run) {
		runs.remove(run.getId());
	}

	@Override
	public boolean isAvailable() { return true; }

	@Override
	public void renew() {};

//	@Override
//	public Node getNode(String nodeId, String jobName) { return new Node(); }
//
//	@Override
//	public void saveNode(Node node) throws MetadigStoreException { }

	@Override
	public Task getTask(String taskName, String taskType, String nodeId) { return new Task(); }

	@Override
	public void saveTask(Task task, String nodeId) throws MetadigStoreException { }

	@Override
	public void shutdown() {};


	@Override
	public Node getNode (String nodeId) { return new Node(); };

	@Override
	public void saveNode(Node node) throws MetadigStoreException {}

	@Override
	public Identifier getIdentifier(String metadataId) throws MetadigStoreException {
		return null;
	}

	@Override
	public Integer saveIdentifier(Identifier identifier) throws MetadigStoreException {
	    return 0;
	}

	@Override
	public ArrayList<Node> getNodes() { return new ArrayList<> (); };

	@Override
	public Result getResult(String metadataId, String suiteId, String checkId) throws MetadigStoreException {
		return null;
	};

	@Override
	public void saveResult(Result result, String metadataId, String suiteId) throws MetadigStoreException {};

}
