package edu.ucsb.nceas.mdqengine;

import edu.ucsb.nceas.mdqengine.exception.MetadigException;
import edu.ucsb.nceas.mdqengine.model.*;
import edu.ucsb.nceas.mdqengine.processor.XMLDialect;
import edu.ucsb.nceas.mdqengine.serialize.JsonMarshaller;
import edu.ucsb.nceas.mdqengine.serialize.XmlMarshaller;
import org.apache.commons.configuration2.ex.ConfigurationException;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.dataone.service.types.v2.SystemMetadata;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;

import static org.dataone.configuration.Settings.getConfiguration;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

@Ignore
public class MDQEngineTest {
	
	protected Log log = LogFactory.getLog(this.getClass());

	private String id = "doi:10.5063/AA/tao.1.1";

	private Suite suite = null;
	
	@Before
	public void setUpSuite() {
		suite = SuiteFactory.getMockSuite();
	}
	
	@Test
	public void testMetadataCheck() {
		try {
			
			// parse the metadata content
			String metadataURL = "https://knb.ecoinformatics.org/knb/d1/mn/v2/object/" + id;
			InputStream input = new URL(metadataURL).openStream();
			XMLDialect xml = new XMLDialect(input);
			
			// run a check in the suite
			for (Check check: suite.getCheck()) {
				Result result = xml.runCheck(check);
				log.debug("Check result: " + JsonMarshaller.toJson(result));
				assertEquals(Status.SUCCESS, result.getStatus());
				break;
			}
			
		} catch (Exception e) {
			e.printStackTrace();
			fail(e.getMessage());
		}
	}

	@Test
	public void testRunSuiteForId() throws MetadigException, IOException, ConfigurationException {
		MDQEngine mdqe = new MDQEngine();
		Run run = null;
		try {
			// retrieve the metadata content
			String cnURL = getConfiguration().getString("D1Client.CN_URL");
			log.error("CN URL: " + cnURL);
			String metadataURL = "https://cn.dataone.org/cn/v2/object/" + id;
			InputStream input = new URL(metadataURL).openStream();
			SystemMetadata sysMeta = null;
			// run the suite on it
			run = mdqe.runSuite(suite, input, null, sysMeta);
			run.setObjectIdentifier(id);
			log.trace("Run results JSON: " + JsonMarshaller.toJson(run));
		} catch (Exception e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
			fail(e.getMessage());
		}

	}
	
	@Ignore
	@Test
	public void testRunSuiteForPackage() throws MetadigException, IOException, ConfigurationException {
		MDQEngine mdqe = new MDQEngine();
		Run run = null;
		try {
			// use the ORE id
			String packageId = "resourceMap_tao.1.1";
			String packageURL = "https://cn.dataone.org/cn/v2/object/" + packageId;
			InputStream input = new URL(packageURL).openStream();
			SystemMetadata sysMeta = null;
			// run the suite on it
			run = mdqe.runSuite(suite, input, null, sysMeta);
			run.setObjectIdentifier(packageId);
			log.trace("Run results XML: " + XmlMarshaller.toXml(run, true));
		} catch (Exception e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
			fail(e.getMessage());
		}

	}
}
