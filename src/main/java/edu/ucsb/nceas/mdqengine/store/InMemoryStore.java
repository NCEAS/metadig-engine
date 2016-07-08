package edu.ucsb.nceas.mdqengine.store;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

import edu.ucsb.nceas.mdqengine.MDQStore;
import edu.ucsb.nceas.mdqengine.model.Check;
import edu.ucsb.nceas.mdqengine.model.Suite;
import edu.ucsb.nceas.mdqengine.model.Run;

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
