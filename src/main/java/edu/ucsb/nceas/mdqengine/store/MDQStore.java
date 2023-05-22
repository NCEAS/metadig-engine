package edu.ucsb.nceas.mdqengine.store;

import edu.ucsb.nceas.mdqengine.exception.MetadigStoreException;
import edu.ucsb.nceas.mdqengine.model.*;
import org.dataone.service.types.v2.Node;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

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
	List<Run> listInProcessRuns() throws MetadigStoreException;
	void saveRun(Run run) throws MetadigStoreException;
	void createRun(Run run);
	void deleteRun(Run run);

	void shutdown();

	boolean isAvailable();
	void renew() throws MetadigStoreException;

	Task getTask(String taskName, String taskType, String nodeId);
	void saveTask(Task task, String nodeId) throws MetadigStoreException;

	Node getNode (String nodeId);
	void saveNode(Node node) throws MetadigStoreException;

	ArrayList<Node> getNodes();

}
