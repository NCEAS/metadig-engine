package edu.ucsb.nceas.mdqengine.processor;

import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.io.InputStream;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;

import org.junit.Test;

import edu.ucsb.nceas.mdqengine.model.Check;
import edu.ucsb.nceas.mdqengine.model.Result;
import edu.ucsb.nceas.mdqengine.model.Selector;

public class XMLDialectTest {
	
	@Test
	public void testTitleMetadata() {
		try {
			
			// set-up the check
			Check check = new Check();
			check.setName("titleLength");
			check.setEnvironment("r");
			check.setLevel("WARN");
			List<Selector> selectors = new ArrayList<Selector>();
			Selector s1 = new Selector();
			s1.setName("title");
			s1.setXpath("//dataset/title");
			selectors.add(s1);
			check.setSelectors(selectors);
			check.setExpected("TRUE");
			check.setCode("nchar(title) > 10");
			
			// parse the metadata content
			String metadataURL = "https://knb.ecoinformatics.org/knb/d1/mn/v2/object/doi:10.5063/AA/tao.1.1";
			InputStream input = new URL(metadataURL).openStream();
			XMLDialect xml = new XMLDialect(input);
			
			// run the check
			Result result = xml.runCheck(check );
			assertTrue(result.isSuccess());
			
		} catch (Exception e) {
			e.printStackTrace();
			fail(e.getMessage());
		}
	}

}
