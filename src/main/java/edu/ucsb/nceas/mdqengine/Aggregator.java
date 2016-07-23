package edu.ucsb.nceas.mdqengine;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Date;
import java.util.Iterator;
import java.util.List;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVPrinter;
import org.apache.commons.csv.CSVRecord;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang.ArrayUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.http.NameValuePair;
import org.apache.http.client.utils.URLEncodedUtils;
import org.dataone.client.v2.CNode;
import org.dataone.client.v2.itk.D1Client;

import edu.ucsb.nceas.mdqengine.model.Check;
import edu.ucsb.nceas.mdqengine.model.Level;
import edu.ucsb.nceas.mdqengine.model.Metadata;
import edu.ucsb.nceas.mdqengine.model.Result;
import edu.ucsb.nceas.mdqengine.model.Run;
import edu.ucsb.nceas.mdqengine.model.Status;
import edu.ucsb.nceas.mdqengine.model.Suite;
import edu.ucsb.nceas.mdqengine.serialize.XmlMarshaller;

public class Aggregator {
	
	public static String[] runColumns = {
		"runId",
		"suiteId",
		"checkId",
		"checkName",
		"type",
		"environment",
		"level",
		"status",
		"message",
		"value",
		"timestamp",
		"pid",
		"formatId",
		"datasource",
		"dataUrl",
		"rightsHolder"

	};
	
	public static String[] docColumns = {
		"id",
		"formatId",
		"datasource",
		"dataUrl",
		"rightsHolder"
	};
	
	/**
	 * Don't let the user override these parameters when querying, otherwise the batch will be broken
	 */
	public static String[] ignoredParams = {
		"fl",
		"wt"
	};
	
	
	protected Log log = LogFactory.getLog(this.getClass());
		
	private MDQEngine engine = new MDQEngine();
	
	/**
	 * run and graph given suite on a corpus returned by optional query param (solr)
	 * @param args
	 */
	public static void main(String args[]) {
		
		// default query
		String query = "q=formatId:\"eml://ecoinformatics.org/eml-2.1.1\" ";
		String format = "pdf";
		try {
			
			// use optional query arg
			if (args.length > 1) {
				query = args[1];
			}
			
			// use optional format arg
			if (args.length > 2) {
				format = args[2];
			}

			// parse the query syntax
			List<NameValuePair> params = URLEncodedUtils.parse(query, Charset.forName("UTF-8"));
			
			String xml = IOUtils.toString(new FileInputStream(args[0]), "UTF-8");
			Suite suite = (Suite) XmlMarshaller.fromXml(xml , Suite.class);
			Aggregator aggregator = new Aggregator();
			aggregator.graphBatch(params, suite, format);

		} catch (Exception e) {
			e.printStackTrace();
		}

	}
	
	public File graphBatch(List<NameValuePair> params, Suite suite, String format) {

		File outputFile = null;
		File script = null;
		File batchFile = null;

		try {
			String runContent = this.runBatch(params, suite);

			log.debug("Tabular Batch Result: \n" + runContent);
			
			// now try doing some analysis
			// have to do this so R can access it
			InputStream scriptStream = this.getClass().getResourceAsStream("/code/plot.R");
			script = File.createTempFile("mdq_script", ".R");
			IOUtils.copy(scriptStream, new FileOutputStream(script));

			batchFile = File.createTempFile("mdqe_batch", ".csv");
			IOUtils.write(runContent, new FileOutputStream(batchFile), "UTF-8");
			String input = batchFile.getAbsolutePath();
			//String output = input + ".pdf";
			String output = input.replace("csv", format);
			
			ProcessBuilder pb = new ProcessBuilder("Rscript", "--vanilla", script.getAbsolutePath(), input, output);
			Process p = pb.start();
			int ret = p.waitFor();
			log.info("stdOut:" + IOUtils.toString(p.getInputStream(), "UTF-8"));
			log.info("stdErr:" + IOUtils.toString(p.getErrorStream(), "UTF-8"));
			
			log.info("Barplot available here: \n" + output);
			outputFile = new File(output);
					
		} catch (Exception e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} finally {
			script.delete();
			batchFile.delete();
			//outputFile.deleteOnExit();
		}
		
		return outputFile;
		
	}
	
	
	/**
	 * Runs suite result
	 * @param query
	 * @param suite
	 * @return
	 * @throws IOException
	 */
	public String runBatch(List<NameValuePair> params, Suite suite) throws IOException {
		
		String results = null;
		
		List<Run> runs = new ArrayList<Run>();
		
		CSVParser docsCsv = this.queryCSV(params);
		if (docsCsv != null) {
			Iterator<CSVRecord> recordIter = docsCsv.iterator();
			while (recordIter.hasNext()) {
				CSVRecord docRecord = recordIter.next();
				String id = docRecord.get("id");
				String dataUrl = docRecord.get("dataUrl");
				String formatId = docRecord.get("formatId");
				String datasource = docRecord.get("datasource");
				String rightsHolder = docRecord.get("rightsHolder");
				
				Metadata metadata = new Metadata();
				metadata.setDatasource(datasource);
				metadata.setDataUrl(dataUrl);
				metadata.setFormatId(formatId);
				metadata.setRightsHolder(rightsHolder);
				
				try {
					InputStream input = new URL(dataUrl).openStream();
					Run run = engine.runSuite(suite, input);
					run.setObjectIdentifier(id);
					run.setMetadata(metadata);
					run.setSuiteId(suite.getId());
					runs.add(run);
					
				} catch (Exception e) {
					log.error("Could not run suite on id: " + id, e);
					continue;
				}
			}
			
			// write the aggregate content to the file
			results = toCSV(runs.toArray(new Run[] {}));
			//IOUtils.write(runContent, new FileOutputStream(file), "UTF-8");
		}
							
		return results;
		
	}

	
	public CSVParser queryCSV(List<NameValuePair> pairs) {
		
		try {
			// query system for object
			String solrQuery = "?";
			
			// add the user-supplied parameters, for ones that are allowed
			for (NameValuePair param: pairs) {
				String name = param.getName();
				String value = param.getValue();

				if (!ArrayUtils.contains(ignoredParams, name)) {
					solrQuery += "&" + name + "=" + URLEncoder.encode(value, "UTF-8");
					
					// make sure we only include current objects in case the user has not specified such
					if (name.equals("q") && !solrQuery.contains("obsoletedBy")) {
						solrQuery += URLEncoder.encode(" -obsoletedBy:* ", "UTF-8");
					}
				}
			}
			
			
			// include the field list
			solrQuery += "&fl=";
			for (String field: docColumns) {
				solrQuery += field + ",";
			}
			solrQuery = solrQuery.substring(0, solrQuery.length()-1); // get rid of the last comma

			// add additional params if missing
			if (!solrQuery.contains("sort")) {
				solrQuery += "&sort=dateUploaded%20desc";
			}
			if (!solrQuery.contains("rows")) {
				solrQuery += "&rows=10";
			}
			
			// add additional required parameters
			solrQuery += "&wt=csv";

			log.debug("solrQuery = " + solrQuery);

			// search the index
			CNode node = D1Client.getCN();
			InputStream solrResultStream = node.query(null, "solr", solrQuery);
			
			CSVParser parser = new CSVParser(new InputStreamReader(solrResultStream, "UTF-8"), CSVFormat.DEFAULT.withHeader());
			return parser;
			
		} catch (Exception e) {
			log.error(e.getMessage(), e);
		}
		return null;
	}
	
	/**
	 * Output the run[s] as a set of CSV records
	 * @param run
	 * @return
	 * @throws IOException 
	 */
	public static String toCSV(Run... runs) throws IOException {
		
		StringBuffer sb = new StringBuffer();
		CSVPrinter csv = CSVFormat.DEFAULT.withHeader(runColumns).print(sb);

		for (Run run: runs) {
			String pid = run.getObjectIdentifier();
			String runId = run.getId();
			String suiteId = run.getSuiteId();

			Date timestamp = run.getTimestamp();
	
			for (Result result: run.getResult()) {
				
				Check check = result.getCheck();
				String checkId = check.getId();
				String checkName = check.getName();

				String type = check.getType();
				String environment = check.getEnvironment();
				Level level = check.getLevel();
				
				Status status = result.getStatus();
				String message = result.getMessage();
				String value = result.getValue();
				
				Metadata metadata = run.getMetadata();
				String formatId = null;
				String datasource = null;
				String dataUrl = null;
				String rightsHolder = null;
				if (metadata != null) {
					formatId = metadata.getFormatId();
					datasource = metadata.getDatasource();
					dataUrl = metadata.getDataUrl();
					rightsHolder = metadata.getRightsHolder();
				}
				
				// create a csv record from this entry
				csv.printRecord(
						runId,
						suiteId,
						checkId,
						checkName,
						type,
						environment,
						level,
						status,
						message,
						value,
						timestamp,
						
						// document  info
						pid,
						formatId,
						datasource,
						dataUrl,
						rightsHolder
						
						);
				
			}
		}
		
		return sb.toString();
		
	}

}
