package edu.ucsb.nceas.mdqengine;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.io.InputStream;
import java.net.URL;

import org.apache.commons.io.IOUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.dataone.client.v2.CNode;
import org.dataone.client.v2.itk.D1Client;
import org.dataone.configuration.Settings;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

import edu.ucsb.nceas.mdqengine.model.Check;
import edu.ucsb.nceas.mdqengine.model.Recommendation;
import edu.ucsb.nceas.mdqengine.model.RecommendationFactory;
import edu.ucsb.nceas.mdqengine.model.Result;
import edu.ucsb.nceas.mdqengine.model.Run;
import edu.ucsb.nceas.mdqengine.processor.XMLDialect;
import edu.ucsb.nceas.mdqengine.serialize.JsonMarshaller;
import edu.ucsb.nceas.mdqengine.serialize.XmlMarshaller;

public class LTERRecommendationTest{
	
	protected Log log = LogFactory.getLog(this.getClass());

	// Metadata for dataset that has single entity
	//private String metadataId = "knb-lter-sbc.18.18";
	// Metadata for dataset that has multiple entities
	private String metadataId = "knb-lter-sbc.1001.7";
	// This dataset has a single data entity
	private String packageId = "doi:10.6073/pasta/d90872297e30026b263a119d4f5bca9f";
	// This dataset has mulitple entities
	// private String packageId = "doi:10.6073/pasta/f3a07cde3f91983bbd63f02fb0496569";
	private Recommendation recommendation = null;
	
	@Test
	public void runMDQEtestsForId() {
		MDQEngine mdqe = new MDQEngine();
		Run run = null;
		try {
			String xmlStr = IOUtils.toString(this.getClass().getResourceAsStream("/test-docs/test-lter-recommendation.xml"), "UTF-8");
			log.debug("XML serialization: " + xmlStr);
			recommendation = (Recommendation) XmlMarshaller.fromXml(xmlStr, Recommendation.class);
			// retrieve the metadata content
			String cnURL = Settings.getConfiguration().getString("D1Client.CN_URL");
			log.error("CN URL: " + cnURL);
			String metadataURL = "https://cn.dataone.org/cn/v2/object/" + metadataId;
			log.error("metadata URL: " + metadataURL);
			InputStream input = new URL(metadataURL).openStream();
			// run the recommendation on it
			//run = mdqe.runRecommendation(recommendation, input);
			//run.setObjectIdentifier(metadataId);
			//log.debug("Run results XML: " + XmlMarshaller.toXml(run));
			
			XMLDialect xml = new XMLDialect(input);
			
			// run a check in the recommendation
			for (Check check: recommendation.getCheck()) {
				Result result = xml.runCheck(check);
				log.debug("Check result: " + XmlMarshaller.toXml(result));
				assertEquals(check.getExpected(), result.getValue());
			}	
			
		} catch (Exception e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
			fail(e.getMessage());
		}		
		
	}
}
//	@Test
//	public void runMDQEtestsForPackage() {
//		try {
//			// use the ORE id
//			String packageURL = "https://cn.dataone.org/cn/v2/object/" + packageId;
//			InputStream input = new URL(packageURL).openStream();
//			// Read the recommendation from the recommendation file, which is
//			// easier to add longer 'code' sections to than the mock recommendation.
//			
//			try {
//				String xml = IOUtils.toString(this.getClass().getResourceAsStream("/test-docs/test-mdqe-recommendation.xml"), "UTF-8");
//				log.debug("XML serialization: " + xml);
//				recommendation = (Recommendation) XmlMarshaller.fromXml(xml, Recommendation.class);
//			} catch (Exception e) {
//				// TODO Auto-generated catch block
//				e.printStackTrace();
//				fail(e.getMessage());
//			}
//
//			run = mdqe.runRecommendation(recommendation, input);
//			run.setObjectIdentifier(packageId);
//			log.debug("Run results XML: " + XmlMarshaller.toXml(run));
//		} catch (Exception e) {
//			// TODO Auto-generated catch block
//			e.printStackTrace();
//			fail(e.getMessage());
//		}
//	}
