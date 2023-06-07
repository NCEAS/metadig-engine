package edu.ucsb.nceas.mdqengine.store;

import edu.ucsb.nceas.mdqengine.MDQEngine;
import edu.ucsb.nceas.mdqengine.exception.MetadigException;
import edu.ucsb.nceas.mdqengine.exception.MetadigStoreException;
import edu.ucsb.nceas.mdqengine.model.*;
import org.dataone.service.types.v2.SystemMetadata;
import org.apache.commons.configuration2.ex.ConfigurationException;
import org.junit.BeforeClass;
import org.junit.Ignore;
import org.junit.Test;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.Calendar;
import java.util.Collection;
import java.util.List;
import java.util.Date;
import java.time.Duration;
import java.time.Instant;

import static org.junit.Assert.*;

@Ignore
public class MDQStoreTest {
	
	private String id = "doi:10.5063/AA/tao.1.1";

	private static MDQStore store = null;
	
	@BeforeClass
	public static void initStore() throws MetadigException, IOException, ConfigurationException {
		
		// use in-memory impl for now
		store = new InMemoryStore();
//		store = new MNStore();

		// save the testing suite if we don't have it already
		Suite rec = SuiteFactory.getMockSuite();
		if (store.getSuite(rec.getId()) == null) {
			store.createSuite(rec);
		}
		
		// add the checks as independent objects if needed
		for (Check check: rec.getCheck()) {
			if (store.getCheck(check.getId()) == null) {
				store.createCheck(check);
			}
		}
	}
	
	@Test
	public void testListSuites() {
		Collection<String> recs = store.listSuites();
		assertTrue(recs.size() > 0);
	}
	
	@Test
	public void createSuite() {
		Suite rec = new Suite();
		rec.setId("createSuite." + Calendar.getInstance().getTimeInMillis());
		rec.setName("Test create suite");
		store.createSuite(rec);
		Suite r = store.getSuite(rec.getId());
		assertEquals(rec.getName(), r.getName());
		store.deleteSuite(rec);
	}
	
	@Test
	public void updateSuite() {
		Suite rec = new Suite();
		rec.setId("updateSuite." + Calendar.getInstance().getTimeInMillis());
		rec.setName("Test update suite");
		store.createSuite(rec);
		Suite r = store.getSuite(rec.getId());
		assertEquals(rec.getName(), r.getName());
		rec.setName("Updated test name");
		store.updateSuite(rec);
		Suite r2 = store.getSuite(rec.getId());
		assertEquals(rec.getName(), r2.getName());
		store.deleteSuite(rec);

	}
	
	@Test
	public void testListChecks() {
		Collection<String> checks = store.listChecks();
		assertTrue(checks.size() >= 3);
	}
	
	@Test
	public void testRuns() {
		try {
			MDQEngine mdqe = new MDQEngine();
			
			String metadataURL = "https://knb.ecoinformatics.org/knb/d1/mn/v2/object/" + id;
			InputStream input = new URL(metadataURL).openStream();
			SystemMetadata sysMeta = null;
			
			Suite suite = SuiteFactory.getMockSuite();
			Run run = mdqe.runSuite(suite, input, null, sysMeta);
			store.createRun(run);
			
			Run r = store.getRun(suite.getId(), run.getId());
			assertEquals(run.getObjectIdentifier(), r.getObjectIdentifier());
			
			store.deleteRun(run);
			
		} catch (Exception e) {
			e.printStackTrace();
			fail(e.getMessage());
		}
		
	}

	@Test
	public void testListInProcessRuns() {
		
		// Create a mock Run object with a timestamp more than 24 hours ago and PROCESSING status
		Run mockRun1 = new Run();
		mockRun1.setTimestamp(Date.from(Instant.now().minus(Duration.ofHours(25))));
		mockRun1.setStatus("PROCESSING");

		// Create a mock Run object with a timestamp less than 24 hours ago and PROCESSING status
		Run mockRun2 = new Run();
		mockRun2.setTimestamp(Date.from(Instant.now().minus(Duration.ofHours(23))));
		mockRun2.setStatus("PROCESSING");

		// Create a mock Run object with COMPLETED status
		Run mockRun3 = new Run();
		mockRun3.setStatus("COMPLETED");

		// Add mock Run objects to a mock MDQEngine object
		store.createRun(mockRun1);
		store.createRun(mockRun2);
		store.createRun(mockRun3);

		// Call the listInProcessRuns() method and verify that it returns only the mockRun1 object
		try {
			List<Run> processing = store.listInProcessRuns();
			assertEquals(1, processing.size());
			assertTrue(processing.contains(mockRun1));
		} catch (MetadigStoreException e){
			e.printStackTrace();
		}
		
	}
	
	@Test
	public void testCheckReference() {
		try {
			MDQEngine mdqe = new MDQEngine();
			
			String metadataURL = "https://knb.ecoinformatics.org/knb/d1/mn/v2/object/" + id;
			InputStream input = new URL(metadataURL).openStream();
			SystemMetadata sysMeta = null;
			
			Suite suite = SuiteFactory.getMockSuite();
			Check checkRef = new Check();
			String checkId = "check.1.1";
			checkRef.setId(checkId );
			suite.getCheck().add(checkRef);
			mdqe.setStore(store);
			Run run = mdqe.runSuite(suite, input, null, sysMeta);
			int checkCount = 0;
			for (Result r: run.getResult()) {
				Check c = r.getCheck();
				if (c.getId().equals(checkId)) {
					checkCount++;
				}
			}
			assertEquals(2, checkCount);
			
			store.deleteRun(run);
			
		} catch (Exception e) {
			e.printStackTrace();
			fail(e.getMessage());
		}
		
	}

}
