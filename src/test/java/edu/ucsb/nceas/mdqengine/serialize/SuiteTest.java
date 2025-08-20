package edu.ucsb.nceas.mdqengine.serialize;

import edu.ucsb.nceas.mdqengine.model.*;

import org.apache.commons.io.IOUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.fail;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

public class SuiteTest {

	protected Log log = LogFactory.getLog(this.getClass());

	@Test
	public void testIterateWithNoChecks() {
		try {
			Suite suite = new Suite();

			if (suite.getCheck() != null) {
				for (Check check : suite.getCheck()) {
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

	@Test
	public void testOldXmlSchema() throws Exception {
		InputStream inputStream = getClass().getClassLoader()
				.getResourceAsStream("test-docs/resource.abstractLength.xml");
		if (inputStream == null) {
			throw new IOException("XML file not found");
		}
		String xml = new String(inputStream.readAllBytes(), "UTF-8");
		Check check = (Check) XmlMarshaller.fromXml(xml, Check.class);

		assertNotNull(check, "Check object should be deserialized successfully from XML");
		assertEquals(edu.ucsb.nceas.mdqengine.model.Check.class, check.getClass());

		List<Selector> selector = new ArrayList<>(check.getSelector());
		for (Selector sel : selector) {
			assertEquals(edu.ucsb.nceas.mdqengine.model.Selector.class, sel.getClass());
		}

		List<Dialect> dialect = new ArrayList<>(check.getDialect());
		for (Dialect di : dialect) {
			assertEquals(edu.ucsb.nceas.mdqengine.model.Dialect.class, di.getClass());
		}

	}

	@Test
	public void testSuiteXML() throws Exception {
		InputStream inputStream = getClass().getClassLoader()
				.getResourceAsStream("test-docs/FAIR-suite-0.5.0.xml");
		if (inputStream == null) {
			throw new IOException("XML file not found");
		}
		String xml = new String(inputStream.readAllBytes(), "UTF-8");
		Suite suite = (Suite) XmlMarshaller.fromXml(xml, Suite.class);

		assertNotNull(suite, "Suite object should be deserialized successfully from XML");

	}

	@Test
	public void testNewXmlSchema() throws Exception {
		InputStream inputStream = getClass().getClassLoader()
				.getResourceAsStream("test-docs/resource.abstractLength-2.0.0.xml");
		if (inputStream == null) {
			throw new IOException("XML file not found");
		}
		String xml = new String(inputStream.readAllBytes(), "UTF-8");
		Check check = (Check) XmlMarshaller.fromXml(xml, Check.class);

		assertNotNull(check, "Check object should be deserialized successfully from XML");
		assertEquals(edu.ucsb.nceas.mdqengine.model.Check.class, check.getClass());

		List<Selector> selector = new ArrayList<>(check.getSelector());
		for (Selector sel : selector) {
			assertEquals(edu.ucsb.nceas.mdqengine.model.Selector.class, sel.getClass());
		}

		List<Dialect> dialect = new ArrayList<>(check.getDialect());
		for (Dialect di : dialect) {
			assertEquals(edu.ucsb.nceas.mdqengine.model.Dialect.class, di.getClass());
		}
	}

}
