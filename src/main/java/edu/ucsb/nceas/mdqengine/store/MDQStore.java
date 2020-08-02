package edu.ucsb.nceas.mdqengine.store;

import edu.ucsb.nceas.mdqengine.exception.MetadigStoreException;
import edu.ucsb.nceas.mdqengine.model.*;

import java.util.Collection;

public interface MDQStore {
	
	public Collection<String> listSuites();
	public Suite getSuite(String id);
	public void createSuite(Suite suite);
	public void updateSuite(Suite suite);
	public void deleteSuite(Suite suite);

	public Collection<String> listChecks();
	public Check getCheck(String id);
	public void createCheck(Check check);
	public void updateCheck(Check check);
	public void deleteCheck(Check check);
	
	public Collection<String> listRuns();
	public Run getRun(String suite, String id ) throws MetadigStoreException;
	public void saveRun(Run run) throws MetadigStoreException;
	public void createRun(Run run);
	public void deleteRun(Run run);

	public void shutdown();

	public boolean isAvailable();
	public void renew() throws MetadigStoreException;
//
//	public Node getNode(String nodeId, String jobName);
//	public void saveNode(Node node) throws MetadigStoreException;

	public Task getTask(String taskName, String taskType);
	public void saveTask(Task task) throws MetadigStoreException;

}
