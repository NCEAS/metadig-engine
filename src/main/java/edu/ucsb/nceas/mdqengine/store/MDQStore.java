package edu.ucsb.nceas.mdqengine.store;

import edu.ucsb.nceas.mdqengine.exception.MetadigStoreException;
import edu.ucsb.nceas.mdqengine.model.Check;
import edu.ucsb.nceas.mdqengine.model.Node;
import edu.ucsb.nceas.mdqengine.model.Run;
import edu.ucsb.nceas.mdqengine.model.Suite;
import org.dataone.service.types.v2.SystemMetadata;

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
	public Run getRun(String suite, String id );
	public void saveRun(Run run, SystemMetadata systemMetadata) throws MetadigStoreException;
	public void createRun(Run run);
	public void deleteRun(Run run);

	public void shutdown();

	public boolean isAvailable();
	public void renew() throws MetadigStoreException;

	public Node getNode(String nodeId);
	public void saveNode(Node node) throws MetadigStoreException;

}
