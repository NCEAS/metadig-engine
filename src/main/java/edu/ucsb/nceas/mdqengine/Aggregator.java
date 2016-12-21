package edu.ucsb.nceas.mdqengine;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang.ArrayUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.http.NameValuePair;
import org.apache.http.client.utils.URLEncodedUtils;
import org.dataone.client.v2.CNode;
import org.dataone.client.v2.MNode;
import org.dataone.client.v2.itk.D1Client;
import org.dataone.service.types.v2.SystemMetadata;
import org.dataone.service.util.TypeMarshaller;

import edu.ucsb.nceas.mdqengine.model.Run;
import edu.ucsb.nceas.mdqengine.model.Suite;
import edu.ucsb.nceas.mdqengine.serialize.XmlMarshaller;
import edu.ucsb.nceas.mdqengine.store.MNStore;

public class Aggregator {
	
	public static String[] docColumns = {
		"id",
		"formatId",
		"datasource",
		"dataUrl",
		"rightsHolder"
		//, "funder"

	};
	
	/**
	 * Don't let the user override these parameters when querying, otherwise the batch will be broken
	 */
	public static String[] ignoredParams = {
		"fl",
		"wt"
	};
	
	
	public static Log log = LogFactory.getLog(Aggregator.class);
		
	private MDQEngine engine = new MDQEngine();
	
	private String baseUrl = null;

	public Aggregator() {
		this(null);
	}
		
	public Aggregator(String baseUrl) {
		this.baseUrl = baseUrl;
	}
	
	/**
	 * run batch and save to MN
	 * @param args
	 */
	public static void main(String args[]) {
		
		// use sandbox CN for this
		//Settings.getConfiguration().setProperty("D1Client.CN_URL", "https://cn-sandbox.test.dataone.org/cn");
		
		// default query
		String query = "q=formatId:\"eml://ecoinformatics.org/eml-2.1.1\" ";
		//String query = "q=rightsHolder:\"CN=Lauren Walker A10489,O=Google,C=US,DC=cilogon,DC=org\" +formatId:\"eml://ecoinformatics.org/eml-2.1.1\"";
		
		try {
			
			// save to MN?
			String baseUrl = null;
			if (args.length > 1) {
				baseUrl = args[1];
			}
			
			// use optional query arg
			if (args.length > 2) {
				query = args[2];
			}
			
			log.warn("baseUrl: " + baseUrl);
			log.warn("query: " + query);


			// parse the query syntax
			List<NameValuePair> params = URLEncodedUtils.parse(query, Charset.forName("UTF-8"));
			
			String xml = IOUtils.toString(new FileInputStream(args[0]), "UTF-8");
			Suite suite = (Suite) XmlMarshaller.fromXml(xml , Suite.class);
			Aggregator aggregator = new Aggregator(baseUrl);
			List<Run> runs = aggregator.runBatch(params, suite);
			
			if (baseUrl != null) {
				MNStore mnStore = new MNStore(baseUrl);
				for (Run run: runs) {
					log.warn("saving run to Node store: " + run.getId());
					mnStore.createRun(run);
					//break; //just one for testing
				}
			} else {
				// write the aggregate content to the file
				System.out.println(runs);
			}
			
			System.exit(0);
			
		} catch (Exception e) {
			e.printStackTrace();
		}

	}
	
	/**
	 * Runs suite result
	 * @param query
	 * @param suite
	 * @return
	 * @throws IOException
	 */
	public List<Run> runBatch(List<NameValuePair> params, Suite suite) throws IOException {
		
		//ExecutorService executor = Executors.newCachedThreadPool();
		ExecutorService executor = Executors.newFixedThreadPool(100);

		List<Future<Run>> futures = new ArrayList<Future<Run>>();
		
		List<Run> runs = new ArrayList<Run>();
						
		CSVParser docsCsv = this.queryCSV(params);
		if (docsCsv != null) {
			Iterator<CSVRecord> recordIter = docsCsv.iterator();
			while (recordIter.hasNext()) {
				CSVRecord docRecord = recordIter.next();
				String id = docRecord.get("id");
				String dataUrl = null;
				String metadataUrl = null;

				if (baseUrl != null) {
					dataUrl = baseUrl + "/v2/object/" + id;
					metadataUrl = baseUrl + "/v2/meta/" + id;
					log.debug("fetching object from: " + dataUrl);
//				} else if (docRecord.isSet("dataUrl"))  {
//					dataUrl = docRecord.get("dataUrl");
//					metadataUrl = dataUrl.replace("/object/", "/meta/");
				} else {
					// have to skip if we can't retrieve it
					continue;
				}
				
				try {
					
					final String finalDataUrl = dataUrl;
					final String finalMetadataUrl = metadataUrl;

					// run asynch in thread
					Callable<Run> runner = new Callable<Run>() {
						
						@Override
						public Run call() throws Exception {
							InputStream input = new URL(finalDataUrl).openStream();
							
							SystemMetadata sysMeta = null;
							try {
								InputStream smInput = new URL(finalMetadataUrl).openStream();
								sysMeta = TypeMarshaller.unmarshalTypeFromStream(SystemMetadata.class, smInput);
							} catch (Exception e) {
								log.error("Could not retrieve SystemMetadata from: " + finalMetadataUrl, e);
							}
							Run run = engine.runSuite(suite, input, null, sysMeta);
							run.setObjectIdentifier(id);
							run.setSuiteId(suite.getId());
							return run;
						}
					};
					Future<Run> future = executor.submit(runner);
					futures.add(future);
					
				} catch (Exception e) {
					log.error("Could not submit run suite on id: " + id, e);
					continue;
				}
			}
			
			boolean complete = false;
			try {
				executor.shutdown();
				complete = executor.awaitTermination(1, TimeUnit.DAYS);
			} catch (InterruptedException e) {
				log.error("Executor shutdown error: " + e.getMessage(), e);
			}
			
			// collect the results
			if (complete) {
				for (Future<Run> future: futures) {
					try {
						runs.add(future.get());
					} catch (InterruptedException | ExecutionException e) {
						log.error("Could not fetch Run future: " + e.getMessage(), e);
						continue;
					}
				}
			}
			
			
		}
							
		return runs;
		
	}

	private CSVParser queryCSV(List<NameValuePair> pairs) {
		
		try {
			// query system for object
			String solrQuery = "?";
			
			// add the user-supplied parameters, for ones that are allowed
			int count = 0;
			for (NameValuePair param: pairs) {
				String name = param.getName();
				String value = param.getValue();

				if (!ArrayUtils.contains(ignoredParams, name)) {
					if (count > 0 ) {
						solrQuery += "&";
					}
					solrQuery += name + "=" + URLEncoder.encode(value, "UTF-8");
					
					// make sure we only include current objects in case the user has not specified such
					if (name.equals("q") && !solrQuery.contains("obsoletedBy")) {
						solrQuery += URLEncoder.encode(" -obsoletedBy:* ", "UTF-8");
					}
					count++;
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
			InputStream solrResultStream = null;
			if (baseUrl!= null) {
				if (baseUrl.contains("/cn")) {
					// use CN
					CNode node = D1Client.getCN(baseUrl);
					solrResultStream = node.query(null, "solr", solrQuery);
				}
				else {
					// use MN
					MNode node = D1Client.getMN(baseUrl);
					solrResultStream = node.query(null, "solr", solrQuery);
				}

			} else {
				// default to default CN
				CNode node = D1Client.getCN();
				solrResultStream = node.query(null, "solr", solrQuery);
			}
			
			CSVParser parser = new CSVParser(new InputStreamReader(solrResultStream, "UTF-8"), CSVFormat.DEFAULT.withHeader());
			return parser;
			
		} catch (Exception e) {
			log.error(e.getMessage(), e);
		}
		return null;
	}
	
	

}
