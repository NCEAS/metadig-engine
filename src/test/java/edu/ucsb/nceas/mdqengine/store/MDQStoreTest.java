package edu.ucsb.nceas.mdqengine.store;

import static org.junit.Assert.*;

import java.io.InputStream;
import java.net.URL;
import java.util.Calendar;
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
//		store = new InMemoryStore();
		store = new MNStore();

		// save the testing recommendation if we don't have it already
		Recommendation rec = RecommendationFactory.getMockRecommendation();
		if (store.getRecommendation(rec.getId()) == null) {
			store.createRecommendation(rec);
		}
		
		// add the checks as stand-alones
//		for (Check check: rec.getCheck()) {
//			store.createCheck(check);
//		}
	}
	
	@Test
	public void testListRecommendations() {
		Collection<String> recs = store.listRecommendations();
		assertTrue(recs.size() > 0);
	}
	
	@Test
	public void createRecommendation() {
		Recommendation rec = new Recommendation();
		rec.setId("createRecommendation." + Calendar.getInstance().getTimeInMillis());
		rec.setName("Test create recommendation");
		store.createRecommendation(rec);
		Recommendation r = store.getRecommendation(rec.getId());
		assertEquals(rec.getName(), r.getName());
		store.deleteRecommendation(rec);
	}
	
	@Test
	public void updateRecommendation() {
		Recommendation rec = new Recommendation();
		rec.setId("updateRecommendation." + Calendar.getInstance().getTimeInMillis());
		rec.setName("Test update recommendation");
		store.createRecommendation(rec);
		Recommendation r = store.getRecommendation(rec.getId());
		assertEquals(rec.getName(), r.getName());
		rec.setName("Updated test name");
		store.updateRecommendation(rec);
		Recommendation r2 = store.getRecommendation(rec.getId());
		assertEquals(rec.getName(), r2.getName());
		store.deleteRecommendation(rec);

	}
	
	//@Test
	public void testListChecks() {
		Collection<Check> checks = store.listChecks();
		assertEquals(3, checks.size());
	}
	
	//@Test
	public void testRuns() {
		try {
			MDQEngine mdqe = new MDQEngine();
			
			String metadataURL = "https://knb.ecoinformatics.org/knb/d1/mn/v2/object/" + id;
			InputStream input = new URL(metadataURL).openStream();
			
			Recommendation recommendation = RecommendationFactory.getMockRecommendation();
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
