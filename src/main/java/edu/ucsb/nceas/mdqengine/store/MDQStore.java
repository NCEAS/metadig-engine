package edu.ucsb.nceas.mdqengine.store;

import edu.ucsb.nceas.mdqengine.exception.MetadigStoreException;
import edu.ucsb.nceas.mdqengine.model.*;

import java.util.Collection;

public interface MDQStore {
	
	Collection<String> listSuites();
	Suite getSuite(String id);
	void createSuite(Suite suite);
	void updateSuite(Suite suite);
	void deleteSuite(Suite suite);

	Collection<String> listChecks();
	Check getCheck(String id);
	void createCheck(Check check);
	void updateCheck(Check check);
	void deleteCheck(Check check);
	
	Collection<String> listRuns();
	Run getRun(String suite, String id ) throws MetadigStoreException;
	void saveRun(Run run) throws MetadigStoreException;
	void createRun(Run run);
	void deleteRun(Run run);

	void shutdown();

	boolean isAvailable();
	void renew() throws MetadigStoreException;

	Task getTask(String taskName, String taskType);
	void saveTask(Task task) throws MetadigStoreException;

}
