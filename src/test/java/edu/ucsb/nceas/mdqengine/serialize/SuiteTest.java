package edu.ucsb.nceas.mdqengine.serialize;

import edu.ucsb.nceas.mdqengine.model.*;
import org.apache.commons.io.IOUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.fail;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertEquals;

public class SuiteTest {
	
	protected Log log = LogFactory.getLog(this.getClass());

	@Test
	public void testIterateWithNoChecks() {
		try {
			Suite suite = new Suite();

			if (suite.getCheck() != null) {
				for (Check check: suite.getCheck()) {
					continue;
				}
			}

		} catch (Exception e) {
			fail(e.getMessage());
		}
	}
	
	@Test
	public void testXml() {
		Suite suite = SuiteFactory.getMockSuite();
		try {
			String xml = XmlMarshaller.toXml(suite, true);
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
			assertEquals(suite.getCheck().size(), r.getCheck().size());

		} catch (Exception e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
			fail(e.getMessage());
		}
		
	}
	
	@Test
	public void testRunXmlRoundTrip() {
		Run run = RunFactory.getMockRun();
		try {
			String xml = XmlMarshaller.toXml(run, true);
			log.debug("Run XML serialization: " + xml);
		} catch (Exception e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
			fail(e.getMessage());
		}
		
	}
	

}
