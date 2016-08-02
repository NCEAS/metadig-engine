package edu.ucsb.nceas.mdqengine.model;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import javax.xml.bind.JAXBException;

import org.apache.commons.io.IOUtils;

import edu.ucsb.nceas.mdqengine.model.Check;
import edu.ucsb.nceas.mdqengine.model.Suite;
import edu.ucsb.nceas.mdqengine.model.Selector;
import edu.ucsb.nceas.mdqengine.serialize.XmlMarshaller;

public class SuiteFactory {
	
	public static Suite getLTERSuite() throws IOException, JAXBException {
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
		Check titleCheck = new Check();
		titleCheck.setId("check.1.1");
		titleCheck.setName("titleLength");
		titleCheck.setEnvironment("r");
		titleCheck.setLevel(Level.WARN);
		List<Selector> selectors = new ArrayList<Selector>();
		Selector s1 = new Selector();
		s1.setName("title");
		s1.setXpath("//dataset/title");
		selectors.add(s1);
		titleCheck.setSelector(selectors);
		titleCheck.setCode("status <- ifelse( nchar(title) > 10, 'SUCCESS', 'FAILURE')");
		checks.add(titleCheck);

		// entityCount
		Check entityCount = new Check();
		entityCount.setId("check.2.1");
		entityCount.setName("entityCount");
		entityCount.setEnvironment("JavaScript");
		entityCount.setLevel(Level.INFO);
		selectors = new ArrayList<Selector>();
		s1 = new Selector();
		s1.setName("entityCount");
		s1.setXpath("count(//dataset/dataTable | //dataset/otherEntity)");
		selectors.add(s1);
		entityCount.setSelector(selectors);
		entityCount.setCode("status = (entityCount > 0 ? 'SUCCESS' : 'FAILURE')");
		checks.add(entityCount);

		// attributeNames
		Check attributeNames = new Check();
		attributeNames.setId("check.3.1");
		attributeNames.setName("attributeNames");
		attributeNames.setEnvironment("r");
		attributeNames.setLevel(Level.SEVERE);
		selectors = new ArrayList<Selector>();
		s1 = new Selector();
		s1.setName("attributeNames");
		s1.setXpath("//attribute/attributeName");
		selectors.add(s1);
		attributeNames.setSelector(selectors);
		attributeNames.setCode("status <- ifelse(any(duplicated(attributeNames)), 'FAILURE', 'SUCCESS')");
		checks.add(attributeNames);

		suite.setCheck(checks);

		return suite;
	}
}
