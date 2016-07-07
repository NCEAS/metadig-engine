package edu.ucsb.nceas.mdqengine.serialize;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import org.apache.commons.io.IOUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.junit.Test;

import edu.ucsb.nceas.mdqengine.model.SuiteFactory;
import edu.ucsb.nceas.mdqengine.model.Suite;

public class SuiteTest {
	
	protected Log log = LogFactory.getLog(this.getClass());
	
	@Test
	public void testJsonRoundTrip() {

		try {
			String json = IOUtils.toString(this.getClass().getResourceAsStream("/test-docs/test-suite.json"), "UTF-8");
			Suite suite = (Suite) JsonMarshaller.fromJson(json, Suite.class);
			String j = JsonMarshaller.toJson(suite);
			assertTrue(j.contains("Testing suite"));
		} catch (Exception e) {
			e.printStackTrace();
			fail(e.getMessage());
		}
	}
	
	@Test
	public void testXml() {
		Suite suite = SuiteFactory.getMockSuite();
		try {
			String xml = XmlMarshaller.toXml(suite);
			log.debug("XML serialization: " + xml);
			Suite r = (Suite) XmlMarshaller.fromXml(xml, suite.getClass());
			assertEquals(suite.getName(), r.getName());
		} catch (Exception e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
			fail(e.getMessage());
		}
		
	}
	
	@Test
	public void testXmlRoundTrip() {
		Suite suite = SuiteFactory.getMockSuite();
		try {
			String xml = IOUtils.toString(this.getClass().getResourceAsStream("/test-docs/test-suite.xml"), "UTF-8");
			log.debug("XML serialization: " + xml);
			Suite r = (Suite) XmlMarshaller.fromXml(xml, Suite.class);
			assertEquals(suite.getName(), r.getName());
		} catch (Exception e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
			fail(e.getMessage());
		}
		
	}
	

}
