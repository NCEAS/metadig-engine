package edu.ucsb.nceas.mdqengine;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.fail;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.net.URL;

import javax.xml.bind.JAXBException;

import org.apache.commons.io.IOUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.junit.BeforeClass;
import org.junit.Ignore;
import org.junit.Test;

import edu.ucsb.nceas.mdqengine.model.Suite;
import edu.ucsb.nceas.mdqengine.model.SuiteFactory;
import edu.ucsb.nceas.mdqengine.model.Run;
import edu.ucsb.nceas.mdqengine.model.RunFactory;

public class AggregatorTest {
	
	protected Log log = LogFactory.getLog(this.getClass());
	
	private static Suite suite;
	
	@BeforeClass
	public static void init() {
		//suite = SuiteFactory.getMockSuite();
		try {
			suite = SuiteFactory.getLTERSuite();
		} catch (Exception e) {
			e.printStackTrace();
			fail(e.getMessage());
		}
	}

	//@Ignore
	@Test
	public void testBatchEML() {
		String query = "formatId:\"eml://ecoinformatics.org/eml-2.1.1\"";

		Aggregator aggregator = new Aggregator();
		aggregator.graphBatch(query, suite);
		
	}

	//@Test
	public void testCSVRun() {
		
		Run run = RunFactory.getMockRun();
		
		try {
			String csv = Aggregator.toCSV(run);
			log.debug("Tabular Run format: \n" + csv);
		} catch (Exception e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
			fail(e.getMessage());
		}
		
	}
}
