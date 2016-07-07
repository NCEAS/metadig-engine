package edu.ucsb.nceas.mdqengine;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.fail;

import java.io.File;
import java.io.FileInputStream;
import java.net.URL;

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
		suite = SuiteFactory.getMockSuite();
	}

	@Ignore
	@Test
	public void testBatchEML() {
		String query = "formatId:\"eml://ecoinformatics.org/eml-2.1.1\"";

		Aggregator aggregator = new Aggregator();
		String tabularResult = null;
		try {
			File file = aggregator.runBatch(query, suite);

			tabularResult = IOUtils.toString(new FileInputStream(file), "UTF-8");
			log.debug("Tabular Batch Result: \n" + tabularResult);
			assertNotNull(tabularResult);
			
			// now try doing some analysis
			URL url = this.getClass().getResource("/test-docs/plot.R");
			String script = url.getPath();

			String input = file.getAbsolutePath();
			String output = input + ".pdf";
			
			log.debug("Tabular Batch file: \n" + input);

			ProcessBuilder pb = new ProcessBuilder("Rscript", "--vanilla", script, input, output);
			Process p = pb.start();
			int ret = p.waitFor();
			log.debug("stdOut:" + IOUtils.toString(p.getInputStream(), "UTF-8"));
			log.debug("stdErr:" + IOUtils.toString(p.getErrorStream(), "UTF-8"));
			assertEquals(0, ret);
			
			log.debug("Barplot available here: \n" + output);
					
		} catch (Exception e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
			fail(e.getMessage());
		}
		
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
