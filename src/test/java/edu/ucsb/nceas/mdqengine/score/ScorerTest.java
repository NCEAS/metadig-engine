package edu.ucsb.nceas.mdqengine.score;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

import java.io.InputStream;
import java.net.URL;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.dataone.configuration.Settings;
import org.junit.Before;
import org.junit.Test;

import edu.ucsb.nceas.mdqengine.MDQEngine;
import edu.ucsb.nceas.mdqengine.model.Recommendation;
import edu.ucsb.nceas.mdqengine.model.RecommendationFactory;
import edu.ucsb.nceas.mdqengine.model.Run;

public class ScorerTest {
	
	protected Log log = LogFactory.getLog(this.getClass());

	private String id = "doi:10.5063/AA/tao.1.1";

	private Recommendation recommendation = null;
	
	@Before
	public void setUpRecommendation() {
		recommendation = RecommendationFactory.getMockRecommendation();
	}
	
	@Test
	public void testRunRecommendationForId() {
		MDQEngine mdqe = new MDQEngine();
		Run run = null;
		try {
			// retrieve the metadata content
			String cnURL = Settings.getConfiguration().getString("D1Client.CN_URL");
			log.error("CN URL: " + cnURL);
			String metadataURL = "https://cn.dataone.org/cn/v2/object/" + id;
			InputStream input = new URL(metadataURL).openStream();
			// run the recommendation on it
			run = mdqe.runRecommendation(recommendation, input);
			run.setObjectIdentifier(id);
			
			log.debug("Run score: " + Scorer.getCompositeScore(run));
			
			assertEquals(3.0, Scorer.getCompositeScore(run), 0);
			
		} catch (Exception e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
			fail(e.getMessage());
		}

	}

}
