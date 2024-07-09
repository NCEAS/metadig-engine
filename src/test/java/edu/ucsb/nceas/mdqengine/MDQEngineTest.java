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
import java.util.ArrayList;
import java.util.Arrays;

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

	@Test
    public void testFindDataObjects() throws Exception {
		MDQEngine mdqe = new MDQEngine();
		String nodeId = "urn:node:ARCTIC";
        ArrayList<String> objs = mdqe.findDataObjects(nodeId, "doi:10.18739/A26M33558");

		ArrayList<String> expected = new ArrayList<>(Arrays.asList(
            "urn:uuid:d1773868-1e6e-4a42-b21e-4ea744b1c1ae",
            "urn:uuid:0ed1454d-a157-4d44-91fd-62e14e3d364a",
            "urn:uuid:8b9437bc-9e67-4a25-b71d-2b2094c41857",
            "urn:uuid:ddb94621-2e01-4b78-9c3a-f5b0d8a34ae3",
            "urn:uuid:9ec2b784-faa1-4938-aa62-8e07c30bc7a3",
            "urn:uuid:ff172b9d-2cf8-4c48-a121-8ea7cc11a272",
            "urn:uuid:ad2fb749-c692-40c9-ba08-17654495a840",
            "urn:uuid:7685e2d8-bd70-41a7-afba-bc37434f7903",
            "urn:uuid:e9b30627-e03a-4241-aa67-789f730e18e6",
            "urn:uuid:345596a8-7729-4f00-a8e9-5ff41e853c5e"
        ));

        assertEquals(expected, objs);

	}
}
