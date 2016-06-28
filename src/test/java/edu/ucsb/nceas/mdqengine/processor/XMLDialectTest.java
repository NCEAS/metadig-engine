package edu.ucsb.nceas.mdqengine.processor;

import static org.junit.Assert.fail;

import java.io.InputStream;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;

import org.junit.Test;

import edu.ucsb.nceas.mdqengine.model.Check;
import edu.ucsb.nceas.mdqengine.model.Level;
import edu.ucsb.nceas.mdqengine.model.Result;
import edu.ucsb.nceas.mdqengine.model.Selector;

public class XMLDialectTest {
	
	private String id = "doi:10.5063/F10V89RP";
	
	
	@Test
	public void testSubSelector() {

		Check complexCheck = new Check();
		
		Selector selector = new Selector();
		selector.setName("entityListOfAttributeCounts");
		selector.setXpath("//dataset/dataTable");

		Selector subselector = new Selector();
		subselector.setName("attributeCount");
		subselector.setXpath("./attributeList/attribute/attributeName");
		
		selector.setSubSelector(subselector);
		
		List<Selector> selectors = new ArrayList<Selector>();
		selectors.add(selector);
		complexCheck.setSelector(selectors);
		
		complexCheck.setCode("entityListOfAttributeCounts");
		complexCheck.setEnvironment("r");
		complexCheck.setLevel(Level.INFO);
		complexCheck.setName("testing subselector");
		
		
		// now run the check
		try {
			
			// parse the metadata content
			String metadataURL = "https://knb.ecoinformatics.org/knb/d1/mn/v2/object/" + id;
			InputStream input = new URL(metadataURL).openStream();
			XMLDialect xml = new XMLDialect(input);
			
			// run the complex check
			Result result = xml.runCheck(complexCheck);
			// TODO: what is the result?
			
		} catch (Exception e) {
			e.printStackTrace();
			fail(e.getMessage());
		}
			
	}

}
