package edu.ucsb.nceas.mdqengine;

import edu.ucsb.nceas.mdqengine.exception.MetadigException;
import edu.ucsb.nceas.mdqengine.model.Result;
import edu.ucsb.nceas.mdqengine.model.Run;
import edu.ucsb.nceas.mdqengine.model.Status;
import edu.ucsb.nceas.mdqengine.model.Suite;
import edu.ucsb.nceas.mdqengine.serialize.XmlMarshaller;
import edu.ucsb.nceas.mdqengine.store.InMemoryStore;
import edu.ucsb.nceas.mdqengine.store.MDQStore;
import org.apache.commons.configuration2.ex.ConfigurationException;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.dataone.service.types.v2.SystemMetadata;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;

import static org.dataone.configuration.Settings.getConfiguration;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

@Disabled
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

	@BeforeAll
	public static void setUpStore() throws MetadigException, IOException, ConfigurationException {
		store = new InMemoryStore();
	}

	@Test
	@Disabled("ignoring LTERSuiteTest")
	public void runMDQEtestsForId() throws MetadigException, IOException, ConfigurationException {
		MDQEngine mdqe = new MDQEngine();
		Run run = null;
		try {

//			String xmlStr = IOUtils.toString(this.getClass().getResourceAsStream("/suites/test-lter-suite.xml"), "UTF-8");
//			log.debug("XML serialization: " + xmlStr);
//			suite = (Suite) XmlMarshaller.fromXml(xmlStr, Suite.class);

			suite = store.getSuite("test-lter-suite.1.1");

			// retrieve the metadata content
			String cnURL = getConfiguration().getString("D1Client.CN_URL");
			log.error("CN URL: " + cnURL);
			String metadataURL = "https://cn.dataone.org/cn/v2/object/" + metadataId;
			log.error("metadata URL: " + metadataURL);
			InputStream input = new URL(metadataURL).openStream();
			SystemMetadata sysMeta = null;
			// run the suite on it
			run = mdqe.runSuite(suite, input, null, sysMeta);
			run.setObjectIdentifier(metadataId);
			log.trace("Run results XML: " + XmlMarshaller.toXml(run, true));


			// run a check in the suite
			for (Result result: run.getResult()) {
				log.debug("Check result: " + XmlMarshaller.toXml(result, true));
				assertTrue(result.getStatus() != Status.ERROR);
			}

		} catch (Exception e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
			fail(e.getMessage());
		}

	}
}
