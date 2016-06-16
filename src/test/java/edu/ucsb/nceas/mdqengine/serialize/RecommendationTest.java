package edu.ucsb.nceas.mdqengine.serialize;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import org.apache.commons.io.IOUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.junit.Test;

import edu.ucsb.nceas.mdqengine.model.RecommendationFactory;
import edu.ucsb.nceas.mdqengine.model.Recommendation;

public class RecommendationTest {
	
	protected Log log = LogFactory.getLog(this.getClass());
	
	@Test
	public void testJsonRoundTrip() {

		try {
			String json = IOUtils.toString(this.getClass().getResourceAsStream("/test-docs/test-recommendation.json"), "UTF-8");
			Recommendation recommendation = (Recommendation) JsonMarshaller.fromJson(json, Recommendation.class);
			String j = JsonMarshaller.toJson(recommendation);
			assertTrue(j.contains("Testing suite"));
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
	
	@Test
	public void testXmlRoundTrip() {
		Recommendation recommendation = RecommendationFactory.getMockRecommendation();
		try {
			String xml = IOUtils.toString(this.getClass().getResourceAsStream("/test-docs/test-recommendation.xml"), "UTF-8");
			log.debug("XML serialization: " + xml);
			Recommendation r = (Recommendation) XmlMarshaller.fromXml(xml, Recommendation.class);
			assertEquals(recommendation.getName(), r.getName());
		} catch (Exception e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
			fail(e.getMessage());
		}
		
	}
	

}
