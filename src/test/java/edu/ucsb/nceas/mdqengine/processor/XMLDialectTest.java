package edu.ucsb.nceas.mdqengine.processor;

import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.io.InputStream;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;

import org.junit.Before;
import org.junit.Test;

import edu.ucsb.nceas.mdqengine.model.Check;
import edu.ucsb.nceas.mdqengine.model.Recommendation;
import edu.ucsb.nceas.mdqengine.model.Result;
import edu.ucsb.nceas.mdqengine.model.Selector;

public class XMLDialectTest {
	
	private Recommendation recommendation = null;
	
	@Before
	public void setUpRecommendation() {
		
		// make a recommendation
		recommendation = new Recommendation();
		recommendation.setName("Testing suite");
	
		// set-up checks
		List<Check> checks = new ArrayList<Check>();
		
		// title
		Check titleCheck = new Check();
		titleCheck.setName("titleLength");
		titleCheck.setEnvironment("r");
		titleCheck.setLevel("WARN");
		List<Selector> selectors = new ArrayList<Selector>();
		Selector s1 = new Selector();
		s1.setName("title");
		s1.setXpath("//dataset/title");
		selectors.add(s1);
		titleCheck.setSelectors(selectors);
		titleCheck.setCode("nchar(title) > 10");
		titleCheck.setExpected("TRUE");
		checks.add(titleCheck);
		
		// entityCount
		Check entityCount = new Check();
		entityCount.setName("entityCount");
		entityCount.setEnvironment("JavaScript");
		entityCount.setLevel("INFO");
		selectors = new ArrayList<Selector>();
		s1 = new Selector();
		s1.setName("entityCount");
		s1.setXpath("count(//dataset/dataTable | //dataset/otherEntity)");
		selectors.add(s1);
		entityCount.setSelectors(selectors);
		entityCount.setCode("entityCount > 0");
		entityCount.setExpected("true");
		checks.add(entityCount);
		
		// attributeNames
		Check attributeNames = new Check();
		attributeNames.setName("attributeNames");
		attributeNames.setEnvironment("r");
		attributeNames.setLevel("ERROR");
		selectors = new ArrayList<Selector>();
		s1 = new Selector();
		s1.setName("attributeNames");
		s1.setXpath("//attribute/attributeName");
		selectors.add(s1);
		attributeNames.setSelectors(selectors);
		attributeNames.setCode("any(duplicated(attributeNames))");
		attributeNames.setExpected("FALSE");
		checks.add(attributeNames);
		
		recommendation.setChecks(checks);
		
	}
	
	@Test
	public void testMetadataRecommendation() {
		try {
			
			// parse the metadata content
			String metadataURL = "https://knb.ecoinformatics.org/knb/d1/mn/v2/object/doi:10.5063/AA/tao.1.1";
			InputStream input = new URL(metadataURL).openStream();
			XMLDialect xml = new XMLDialect(input);
			
			// run the checks in the recommendation
			for (Check check: recommendation.getChecks()) {
				Result result = xml.runCheck(check);
				assertTrue(result.isSuccess());
			}
			
		} catch (Exception e) {
			e.printStackTrace();
			fail(e.getMessage());
		}
	}

}
