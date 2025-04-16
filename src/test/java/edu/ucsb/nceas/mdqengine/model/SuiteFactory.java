package edu.ucsb.nceas.mdqengine.model;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import javax.xml.bind.JAXBException;
import javax.xml.parsers.ParserConfigurationException;

import org.apache.commons.io.IOUtils;
import org.xml.sax.SAXException;

import edu.ucsb.nceas.mdqengine.serialize.XmlMarshaller;

public class SuiteFactory {
	
	public static Suite getLTERSuite() throws ParserConfigurationException, IOException, JAXBException, SAXException {
		String xmlStr = IOUtils.toString(SuiteFactory.class.getResourceAsStream("/suites/test-lter-suite.xml"), "UTF-8");
		Suite suite = (Suite) XmlMarshaller.fromXml(xmlStr, Suite.class);
		return suite;
	}

	public static Suite getMockSuite() {
		// make a suite
		Suite suite = new Suite();
		suite.setName("Testing suite");
		suite.setId("suite.2.1");

		// set-up checks
		List<Check> checks = new ArrayList<Check>();

		// title
		Check titleCheck = CheckV2.newCheck();
		titleCheck.setId("check.1.1");
		titleCheck.setName("titleLength");
		titleCheck.setEnvironment("r");
		titleCheck.setLevel(Level.OPTIONAL);
		List<Selector> selectors = new ArrayList<Selector>();
		Selector s1 = SelectorV2.newSelector();
		s1.setName("title");
		s1.setXpath("//dataset/title");
		selectors.add(s1);
		titleCheck.setSelector(selectors);
		titleCheck.setCode("status <- ifelse( nchar(title) > 10, 'SUCCESS', 'FAILURE')");
		checks.add(titleCheck);

		// entityCount
		Check entityCount = CheckV2.newCheck();
		entityCount.setId("check.2.1");
		entityCount.setName("entityCount");
		entityCount.setEnvironment("JavaScript");
		entityCount.setLevel(Level.INFO);
		selectors = new ArrayList<Selector>();
		Selector s2 = SelectorV2.newSelector();
		s2.setName("entityCount");
		s2.setXpath("count(//dataset/dataTable | //dataset/otherEntity)");
		selectors.add(s2);
		entityCount.setSelector(selectors);
		entityCount.setCode("status = (entityCount > 0 ? 'SUCCESS' : 'FAILURE')");
		checks.add(entityCount);

		// attributeNames
		Check attributeNames = CheckV2.newCheck();
		attributeNames.setId("check.3.1");
		attributeNames.setName("attributeNames");
		attributeNames.setEnvironment("r");
		attributeNames.setLevel(Level.REQUIRED);
		selectors = new ArrayList<Selector>();
		Selector s3 = SelectorV2.newSelector();
		s3.setName("attributeNames");
		s3.setXpath("//attribute/attributeName");
		selectors.add(s3);
		attributeNames.setSelector(selectors);
		attributeNames.setCode("status <- ifelse(any(duplicated(attributeNames)), 'FAILURE', 'SUCCESS')");
		checks.add(attributeNames);

		suite.setCheck(checks);

		return suite;
	}
}
