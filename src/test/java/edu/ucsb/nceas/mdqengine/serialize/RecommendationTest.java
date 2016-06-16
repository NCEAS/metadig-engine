package edu.ucsb.nceas.mdqengine.serialize;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

import org.apache.commons.io.IOUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.junit.Test;

import edu.ucsb.nceas.mdqengine.MDQEngine;
import edu.ucsb.nceas.mdqengine.RecommendationFactory;
import edu.ucsb.nceas.mdqengine.model.Recommendation;
import edu.ucsb.nceas.mdqengine.model.Run;

public class RecommendationTest {
	
	private String id = "doi:10.5063/AA/tao.1.1";

	protected Log log = LogFactory.getLog(this.getClass());
	
	@Test
	public void testJsonRoundTrip() {

		try {
			String json = IOUtils.toString(this.getClass().getResourceAsStream("/test-docs/test-recommendation.json"), "UTF-8");
			Recommendation recommendation = (Recommendation) JsonMarshaller.fromJson(json, Recommendation.class);
			MDQEngine mdqe = new MDQEngine();
			Run run = mdqe.runRecommendation(recommendation, id);
			log.debug("Run result: " + JsonMarshaller.toJson(run));
		} catch (Exception e) {
			e.printStackTrace();
			fail(e.getMessage());
		}
	}
	
	@Test
	public void testXml() {
		Recommendation recommendation = RecommendationFactory.getMockRecommendation();
		try {
			String xml = XmlMarshaller.toXml(recommendation);
			log.debug("XML serialization: " + xml);
			Recommendation r = (Recommendation) XmlMarshaller.fromXml(xml, recommendation.getClass());
			assertEquals(recommendation.getName(), r.getName());
		} catch (Exception e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
			fail(e.getMessage());
		}
		
	}
	

}
