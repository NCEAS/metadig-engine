package edu.ucsb.nceas.mdqengine.processor;

import static org.junit.Assert.*;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;

import javax.xml.parsers.ParserConfigurationException;

import org.junit.Test;
import org.junit.Ignore;

import org.xml.sax.SAXException;

import edu.ucsb.nceas.mdqengine.model.Check;
import edu.ucsb.nceas.mdqengine.model.Dialect;
import edu.ucsb.nceas.mdqengine.model.Level;
import edu.ucsb.nceas.mdqengine.model.Result;
import edu.ucsb.nceas.mdqengine.model.Selector;
import edu.ucsb.nceas.mdqengine.model.Status;

public class XMLDialectTest {
	
	private String emlId = "doi:10.5063/F10V89RP";
	
	private String emlIdSingleTable = "knb-lter-sbc.18.18";
	
	private String fgdcId = "361ede66-857b-4289-b4dc-8e414abbb1f0.xml";
	
	@Test
	public void testSubSelector() {

		Check complexCheck = new Check();
		
		Selector selector = new Selector();
		selector.setName("entityListOfAttributes");
		selector.setXpath("//dataset/dataTable");

		Selector subselector = new Selector();
		subselector.setName("attributeCount");
		subselector.setXpath("./attributeList/attribute/attributeName");
		
		selector.setSubSelector(subselector);
		
		List<Selector> selectors = new ArrayList<Selector>();
		selectors.add(selector);
		complexCheck.setSelector(selectors);
		
		complexCheck.setCode("status <- ifelse(class(entityListOfAttributes) == \"list\" && class(entityListOfAttributes[[1]]) == \"list\", 'SUCCESS', 'FAILURE')");
		complexCheck.setEnvironment("r");
		complexCheck.setLevel(Level.INFO);
		complexCheck.setName("testing subselector");
		
		// run the check on EML
		try {
			
			// parse the metadata content
			String metadataURL = "https://cn.dataone.org/cn/v2/object/" + emlId;
			InputStream input = new URL(metadataURL).openStream();
			XMLDialect xml = new XMLDialect(input);
			
			// run the complex check
			Result result = xml.runCheck(complexCheck);
			assertEquals(Status.SUCCESS, result.getStatus());
			
		} catch (Exception e) {
			e.printStackTrace();
			fail(e.getMessage());
		}
		
		// run the check on EML with single table
		try {
			
			// parse the metadata content
			String metadataURL = "https://cn.dataone.org/cn/v2/object/" + emlIdSingleTable;
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
	
	@Test
	public void testDialect() {

		Check check = new Check();
		
		Selector selector = new Selector();
		selector.setName("entityCount");
		selector.setXpath("count(//dataset/dataTable)");

		List<Selector> selectors = new ArrayList<Selector>();
		selectors.add(selector);
		check.setSelector(selectors);
		
		check.setCode("status <- ifelse(entityCount > 0, 'SUCCESS', 'FAILURE')");
		check.setEnvironment("r");
		check.setLevel(Level.INFO);
		check.setName("testing dialect skipping");
		
		List<Dialect> dialects = new ArrayList<Dialect>();
		Dialect eml = new Dialect();
		eml.setName("EML");
		eml.setXpath("boolean(/*[local-name() = 'eml'])");
		
		Dialect iso = new Dialect();
		iso.setName("iso");
		iso.setXpath("boolean(/*[local-name() = 'iso'])");
		
		dialects.add(eml);
		dialects.add(iso);
		
		check.setDialect(dialects);
		
		//  run the check on EML
		try {
			
			// parse the metadata content
			String metadataURL = "https://knb.ecoinformatics.org/knb/d1/mn/v2/object/" + emlId;
			InputStream input = new URL(metadataURL).openStream();
			XMLDialect xml = new XMLDialect(input);
			
			// run check
			Result result = xml.runCheck(check);
			assertEquals(Status.SUCCESS, result.getStatus());
			
		} catch (Exception e) {
			e.printStackTrace();
			fail(e.getMessage());
		}
		
		// now run the check on FDGC
		try {
			
			// parse the metadata content
			String metadataURL = "https://knb.ecoinformatics.org/knb/d1/mn/v2/object/" + fgdcId;
			InputStream input = new URL(metadataURL).openStream();
			XMLDialect xml = new XMLDialect(input);
			
			// run check
			Result result = xml.runCheck(check);
			assertEquals(Status.SKIP, result.getStatus());
			
		} catch (Exception e) {
			e.printStackTrace();
			fail(e.getMessage());
		}
			
	}
	
	@Ignore
	@Test
	public void testValidation() {

		Check check = new Check();
		
		Selector selector = new Selector();
		selector.setName("entityCount");
		selector.setXpath("count(//dataset/dataTable)");

		List<Selector> selectors = new ArrayList<Selector>();
		selectors.add(selector);
		check.setSelector(selectors);
		
		check.setCode(SchemaCheck.class.getName());
		check.setEnvironment("Java");
		check.setLevel(Level.SEVERE);
		check.setName("Schema valid stest");
				
		//  run the check on valid EML that declares schemaLocation
		try {
			
			// parse the metadata content
			InputStream input = this.getClass().getResourceAsStream("/test-docs/eml.1.1.xml");
			XMLDialect xml = new XMLDialect(input);
			
			// run check
			Result result = xml.runCheck(check);
			assertEquals(result.getOutput().get(0).getValue(), Status.SUCCESS, result.getStatus());
			
		} catch (Exception e) {
			e.printStackTrace();
			fail(e.getMessage());
		}
		
		//  run the check on invalid EML that does not declare schema location
		try {
			
			// parse the metadata content
			InputStream input = this.getClass().getResourceAsStream("/test-docs/eml-invalid.1.1.xml");
			XMLDialect xml = new XMLDialect(input);
			
			// run check
			Result result = xml.runCheck(check);
			assertEquals(result.getOutput().get(0).getValue(), Status.FAILURE, result.getStatus());
			
		} catch (Exception e) {
			e.printStackTrace();
			fail(e.getMessage());
		}
			
	}
	
	@Test
	public void testLibrary() {

		Check check = new Check();
		
		Selector selector = new Selector();
		selector.setName("entityCount");
		selector.setXpath("count(//dataset/dataTable)");

		List<Selector> selectors = new ArrayList<Selector>();
		selectors.add(selector);
		check.setSelector(selectors);
		
		// the library has the code, so we'll just let that do everything
		check.setCode("status <- call(); ");
		
		URL library = this.getClass().getResource("/code/sampleLib.R");
		check.setLibrary(library);
		
		check.setEnvironment("r");
		check.setLevel(Level.WARN);
		check.setName("External code lib test");
				
		//  run the check on valid EML that declares schemaLocation
		try {
			
			// parse the metadata content
			InputStream input = this.getClass().getResourceAsStream("/test-docs/eml.1.1.xml");
			XMLDialect xml = new XMLDialect(input);
			
			// run check
			Result result = xml.runCheck(check);
			assertEquals(result.getOutput().get(0).getValue(), Status.SUCCESS, result.getStatus());
			
		} catch (Exception e) {
			e.printStackTrace();
			fail(e.getMessage());
		}
			
	}

	@Test
	public void testPersist() {

		Check check = new Check();
		
		Selector selector = new Selector();
		selector.setName("entityCount");
		selector.setXpath("count(//dataset/dataTable)");

		List<Selector> selectors = new ArrayList<Selector>();
		selectors.add(selector);
		check.setSelector(selectors);
		
		check.setCode("status <- ifelse(entityCount > 0, 'SUCCESS', 'FAILURE')");
		check.setEnvironment("r");
		check.setLevel(Level.INFO);
		
		// parse the metadata content
		InputStream input = this.getClass().getResourceAsStream("/test-docs/eml.1.1.xml");
		XMLDialect xml = null;
		try {
			xml = new XMLDialect(input);
		} catch (SAXException | IOException | ParserConfigurationException e) {
			// TODO Auto-generated catch block
			fail(e.getMessage());
		}
					
		//  run the first check
		try {
			
			// run check
			Result result = xml.runCheck(check);
			assertEquals(result.getOutput().get(0).getValue(), Status.SUCCESS, result.getStatus());
			
		} catch (Exception e) {
			e.printStackTrace();
			fail(e.getMessage());
		}
		
		// now run another check using information from the previous check
		check = new Check();
		
		selector = new Selector();
		selector.setName("title");
		selector.setXpath("//title");

		selectors = new ArrayList<Selector>();
		selectors.add(selector);
		check.setSelector(selectors);
		
		check.setCode("entityCount == 1");
		check.setEnvironment("r");
		check.setLevel(Level.INFO);
		check.setInheritState(true);
		
		//  run this check uses previous info
		try {
			
			// run check
			Result result = xml.runCheck(check);
			assertEquals(result.getOutput().get(0).getValue(), Status.SUCCESS, result.getStatus());
			
		} catch (Exception e) {
			e.printStackTrace();
			fail(e.getMessage());
		}
		
		// run yet another check using information from the previous check
		check = new Check();
		
		selector = new Selector();
		selector.setName("packageId");
		selector.setXpath("//@packageId");

		selectors = new ArrayList<Selector>();
		selectors.add(selector);
		check.setSelector(selectors);
		
		//check.setCode("title.length > 1");
		check.setCode("String(title).length > 1");

		check.setEnvironment("JavaScript");
		check.setLevel(Level.INFO);
		check.setInheritState(true);
		
		//  run this check uses previous info
		try {
			
			// run check
			Result result = xml.runCheck(check);
			assertEquals(result.getOutput().get(0).getValue(), Status.SUCCESS, result.getStatus());
			
		} catch (Exception e) {
			e.printStackTrace();
			fail(e.getMessage());
		}
			
	}
	
	@Test
	public void testPersistMath() {

		Check check = new Check();
		
		check.setCode(
				"x <- 1; status <- ifelse(x == 1, 'SUCCESS', 'FAILURE'); ");
		check.setEnvironment("r");
		check.setLevel(Level.INFO);
		
		// parse the metadata content
		InputStream input = this.getClass().getResourceAsStream("/test-docs/eml.1.1.xml");
		XMLDialect xml = null;
		try {
			xml = new XMLDialect(input);
		} catch (SAXException | IOException | ParserConfigurationException e) {
			// TODO Auto-generated catch block
			fail(e.getMessage());
		}
					
		//  run the first check
		try {
			
			// run check
			Result result = xml.runCheck(check);
			assertEquals(result.getOutput().get(0).getValue(), Status.SUCCESS, result.getStatus());
			
		} catch (Exception e) {
			e.printStackTrace();
			fail(e.getMessage());
		}
		
		// run yet another check using information from the previous check
		check = new Check();
		
		check.setCode("x = x + 1; status = (x == 2 ? 'SUCCESS' : 'FAILURE');");

		check.setEnvironment("JavaScript");
		check.setLevel(Level.INFO);
		check.setInheritState(true);
		
		//  run this check uses previous info
		try {
			
			// run check
			Result result = xml.runCheck(check);
			assertEquals(result.getOutput().get(0).getValue(), Status.SUCCESS, result.getStatus());
			
		} catch (Exception e) {
			e.printStackTrace();
			fail(e.getMessage());
		}
			
	}

}
