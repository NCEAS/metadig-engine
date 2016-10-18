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
import edu.ucsb.nceas.mdqengine.model.Suite;
import edu.ucsb.nceas.mdqengine.model.SuiteFactory;
import edu.ucsb.nceas.mdqengine.model.Result;
import edu.ucsb.nceas.mdqengine.model.Run;
import edu.ucsb.nceas.mdqengine.model.Status;
import edu.ucsb.nceas.mdqengine.processor.XMLDialect;
import edu.ucsb.nceas.mdqengine.serialize.JsonMarshaller;
import edu.ucsb.nceas.mdqengine.serialize.XmlMarshaller;
import edu.ucsb.nceas.mdqengine.store.InMemoryStore;

public class LTERSuiteTest{
	
	protected Log log = LogFactory.getLog(this.getClass());
	
	// for looking up the "built-in" suite
	private static MDQStore store = null;

	// Metadata for dataset that has single entity
	//private String metadataId = "knb-lter-sbc.18.18";
	//private String metadataId = "knb-lter-sbc.1203.5";
	//private String metadataId = "knb-lter-sbc.1101.6";
	// Metadata for dataset that has multiple entities
	private String metadataId = "knb-lter-sbc.1001.7";
	private Suite suite = null;
	
	@BeforeClass
	public static void setUpStore() {
		store = new InMemoryStore();
	}
	
	@Test
	//@Ignore("ignoring LTERSuiteTest")
	public void runMDQEtestsForId() {
		MDQEngine mdqe = new MDQEngine();
		Run run = null;
		try {
			
//			String xmlStr = IOUtils.toString(this.getClass().getResourceAsStream("/suites/test-lter-suite.xml"), "UTF-8");
//			log.debug("XML serialization: " + xmlStr);
//			suite = (Suite) XmlMarshaller.fromXml(xmlStr, Suite.class);
			
			suite = store.getSuite("test-lter-suite.1.1");
			
			// retrieve the metadata content
			String cnURL = Settings.getConfiguration().getString("D1Client.CN_URL");
			log.error("CN URL: " + cnURL);
			String metadataURL = "https://cn.dataone.org/cn/v2/object/" + metadataId;
			log.error("metadata URL: " + metadataURL);
			InputStream input = new URL(metadataURL).openStream();
			// run the suite on it
			run = mdqe.runSuite(suite, input, null);
			run.setObjectIdentifier(metadataId);
			log.trace("Run results XML: " + XmlMarshaller.toXml(run));
			
			
			// run a check in the suite
			for (Result result: run.getResult()) {
				log.debug("Check result: " + XmlMarshaller.toXml(result));
				assertTrue(result.getStatus() != Status.ERROR);
			}	
			
		} catch (Exception e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
			fail(e.getMessage());
		}		
		
	}
}