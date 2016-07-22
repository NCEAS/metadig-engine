package edu.ucsb.nceas.mdqengine;

import static org.junit.Assert.fail;

import java.nio.charset.Charset;
import java.util.List;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.http.NameValuePair;
import org.apache.http.client.utils.URLEncodedUtils;
import org.junit.BeforeClass;
import org.junit.Ignore;
import org.junit.Test;

import edu.ucsb.nceas.mdqengine.model.Run;
import edu.ucsb.nceas.mdqengine.model.RunFactory;
import edu.ucsb.nceas.mdqengine.model.Suite;
import edu.ucsb.nceas.mdqengine.model.SuiteFactory;

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

	@Ignore
	@Test
	public void testBatchEML() {
		String query = "q=formatId:\"eml://ecoinformatics.org/eml-2.1.1\"";
		List<NameValuePair> params = URLEncodedUtils.parse(query, Charset.forName("UTF-8"));
		
		Aggregator aggregator = new Aggregator();
		aggregator.graphBatch(params, suite);
		
	}

	@Test
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
