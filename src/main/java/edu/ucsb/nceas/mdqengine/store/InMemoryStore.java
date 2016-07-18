package edu.ucsb.nceas.mdqengine.store;

import java.io.IOException;
import java.net.URL;
import java.util.Collection;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Map;

import javax.xml.bind.JAXBException;

import org.apache.commons.io.IOUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import edu.ucsb.nceas.mdqengine.MDQStore;
import edu.ucsb.nceas.mdqengine.model.Check;
import edu.ucsb.nceas.mdqengine.model.Suite;
import edu.ucsb.nceas.mdqengine.model.Run;
import edu.ucsb.nceas.mdqengine.serialize.XmlMarshaller;

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
	
	protected Log log = LogFactory.getLog(this.getClass());
	
	public InMemoryStore() {
		//this.init();
	}
	
	// NOTE: does not work with wildcards - investigating alternatives
	private void init() {
		// load all the resources from local files
		Enumeration<URL> suiteResources = null;
		try {
			suiteResources = this.getClass().getClassLoader().getResources("/suites/*.xml");
		} catch (IOException e) {
			log.error("Could not read local suite resources: " + e.getMessage(), e);
			return;
		}
		while (suiteResources.hasMoreElements()) {
			URL url = suiteResources.nextElement();
			log.debug("Loading suite found at: " + url.toString());
			Suite suite = null;
			try {
				String xml = IOUtils.toString(url.openStream(), "UTF-8");
				suite = (Suite) XmlMarshaller.fromXml(xml, Suite.class);
			} catch (JAXBException | IOException e) {
				log.warn("Could not load suite: " + e.getMessage());
				continue;
			}
			this.createSuite(suite);

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
	public void createCheck(Check check) {
		checks.put(check.getId(), check);
	}

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
	public Run getRun(String id) {
		return runs.get(id);
	}

	@Override
	public void createRun(Run run) {
		runs.put(run.getId(), run);
	}

	@Override
	public void deleteRun(Run run) {
		runs.remove(run.getId());
	}

}
