package edu.ucsb.nceas.mdqengine.store;

import edu.ucsb.nceas.mdqengine.MDQEngine;
import edu.ucsb.nceas.mdqengine.model.*;
import org.dataone.service.types.v2.SystemMetadata;
import org.junit.BeforeClass;
import org.junit.Ignore;
import org.junit.Test;

import java.io.InputStream;
import java.net.URL;
import java.util.Calendar;
import java.util.Collection;

import static org.junit.Assert.*;

@Ignore
public class MDQStoreTest {
	
	private String id = "doi:10.5063/AA/tao.1.1";

	private static MDQStore store = null;
	
	@BeforeClass
	public static void initStore() {
		
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
