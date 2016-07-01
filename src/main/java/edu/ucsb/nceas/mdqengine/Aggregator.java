package edu.ucsb.nceas.mdqengine;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.StringReader;
import java.net.URL;
import java.net.URLEncoder;
import java.util.Iterator;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVPrinter;
import org.apache.commons.csv.CSVRecord;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.dataone.client.v2.CNode;
import org.dataone.client.v2.itk.D1Client;

import edu.ucsb.nceas.mdqengine.model.Recommendation;
import edu.ucsb.nceas.mdqengine.model.Run;
import edu.ucsb.nceas.mdqengine.score.Scorer;

public class Aggregator {
	
	protected Log log = LogFactory.getLog(this.getClass());
		
	private MDQEngine engine = new MDQEngine();
	
	public String runBatch(String query, Recommendation recommendation) throws IOException {
		
		StringBuffer results = new StringBuffer();
		CSVPrinter csvPrinter = new CSVPrinter(results, CSVFormat.DEFAULT);
		
		CSVParser docsCsv = this.queryCSV(query);
		if (docsCsv != null) {
			Iterator<CSVRecord> recordIter = docsCsv.iterator();
			while (recordIter.hasNext()) {
				CSVRecord docRecord = recordIter.next();
				String id = docRecord.get("id");
				String dataUrl = docRecord.get("dataUrl");

				try {
					InputStream input = new URL(dataUrl).openStream();
					Run run = engine.runRecommendation(recommendation, input);
					// this is a silly step to get the columns back
					String runString = Scorer.toCSV(run);
					CSVParser runCsv = new CSVParser(new StringReader(runString), CSVFormat.DEFAULT.withHeader());
					Iterator<CSVRecord> runIter = runCsv.iterator();
					while (runIter.hasNext()) {
						CSVRecord runRecord = runIter.next();
						
						// print out run information
						Iterator<String> runValueIter = runRecord.iterator();
						while (runValueIter.hasNext()){
							csvPrinter.print(runValueIter.next());
						}
						
						// print out doc information on the same line
						Iterator<String> valueIter = docRecord.iterator();
						while (valueIter.hasNext()){
							csvPrinter.print(valueIter.next());
						}
						
						// add the record delimiter
						csvPrinter.println();
						
					}
					
				} catch (Exception e) {
					log.error("Could not run QC on id: " + id, e);
				}
				
			}
			
		}
		
		csvPrinter.close();
		
		return results.toString();
		
	}

	public CSVParser queryCSV(String query) {
		
		try {
			// query system for object
			String solrQuery = "?q=" + URLEncoder.encode(query, "UTF-8");
			solrQuery += URLEncoder.encode("-obsoletedBy:*", "UTF-8");
			solrQuery += "&fl=id,formatId,datasource,dataUrl,rightsHolder&wt=csv&rows=10";
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

}
