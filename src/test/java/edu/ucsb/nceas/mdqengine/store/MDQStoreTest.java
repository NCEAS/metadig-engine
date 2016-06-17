package edu.ucsb.nceas.mdqengine.store;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

import java.io.InputStream;
import java.net.URL;
import java.util.Collection;

import org.junit.BeforeClass;
import org.junit.Test;

import edu.ucsb.nceas.mdqengine.MDQEngine;
import edu.ucsb.nceas.mdqengine.MDQStore;
import edu.ucsb.nceas.mdqengine.model.Check;
import edu.ucsb.nceas.mdqengine.model.Recommendation;
import edu.ucsb.nceas.mdqengine.model.RecommendationFactory;
import edu.ucsb.nceas.mdqengine.model.Run;

public class MDQStoreTest {
	
	private String id = "doi:10.5063/AA/tao.1.1";

	private static MDQStore store = null;
	
	@BeforeClass
	public static void initStore() {
		
		// use in-memory impl for now
		store = new InMemoryStore();
		
		// save the testing recommendation
		Recommendation rec = RecommendationFactory.getMockRecommendation();
		store.createRecommendation(rec);
		
		// add the checks as stand-alones
		for (Check check: rec.getCheck()) {
			store.createCheck(check);
		}
	}
	
	@Test
	public void testListRecommendations() {
		Collection<Recommendation> recs = store.listRecommendations();
		assertEquals(1, recs.size());
	}
	
	@Test
	public void testListChecks() {
		Collection<Check> checks = store.listChecks();
		assertEquals(3, checks.size());
	}
	
	@Test
	public void testRuns() {
		try {
			MDQEngine mdqe = new MDQEngine();
			
			String metadataURL = "https://knb.ecoinformatics.org/knb/d1/mn/v2/object/" + id;
			InputStream input = new URL(metadataURL).openStream();
			
			Recommendation recommendation = store.listRecommendations().iterator().next();
			Run run = mdqe.runRecommendation(recommendation, input);
			store.createRun(run);
			
			Collection<Run> runs = store.listRuns();
			assertEquals(1, runs.size());
			
			Run r = store.getRun(run.getId());
			assertEquals(run.getObjectIdentifier(), r.getObjectIdentifier());
			
			store.deleteRun(run);
			assertEquals(0, runs.size());
			
		} catch (Exception e) {
			e.printStackTrace();
			fail(e.getMessage());
		}
		
	}

}
