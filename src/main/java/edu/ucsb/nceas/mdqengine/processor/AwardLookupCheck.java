package edu.ucsb.nceas.mdqengine.processor;

import edu.ucsb.nceas.mdqengine.model.Output;
import edu.ucsb.nceas.mdqengine.model.Result;
import edu.ucsb.nceas.mdqengine.model.Status;
import net.minidev.json.JSONArray;
import net.minidev.json.JSONObject;
import net.minidev.json.parser.JSONParser;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import java.io.InputStream;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;

/**
 * Looks up award information for given award number
 * @author leinfelder
 *
 */
public class AwardLookupCheck implements Callable<Result> {
	
	private static String apiUrl = "https://www.research.gov/awardapi-service/v1/awards.json"
			+ "?printFields="
			+ "id,"
			+ "title,"
			+ "agency,"
			+ "fundProgramName,"
			+ "primaryProgram"
			+ "&id=";
		
	// the award identifier[s]
	private Object awards;
	
	public Log log = LogFactory.getLog(this.getClass());
	
	@Override
	public Result call() {
		Result result = new Result();
		List<Output> outputs = new ArrayList<Output>();
		URL url = null;
		
		if (this.awards != null) {
			result.setStatus(Status.SUCCESS);

			List<Object> awardList = new ArrayList<Object>();
			if (awards instanceof List) {
				awardList = (List<Object>) awards;
			} else {
				awardList.add(awards.toString());
			}
			
			for (Object awardId: awardList) {
				
				try {
					
					// fetch the results, extracting just the award number, if other info is in the string.
					String idString = awardId.toString().toLowerCase().replace("nsf award", "").trim();

					// Pad the award number with leading zeros if necessary
					if (awardId instanceof Number) {
						idString = String.format("%07d", awardId);
					}
					url = new URL(apiUrl + idString);
					log.debug("URL=" + url.toString());

					InputStream jsonStream = url.openStream();
					
					// parse
					JSONParser parser = new JSONParser(JSONParser.DEFAULT_PERMISSIVE_MODE);
					String apiRetStr= parser.parse(jsonStream).toString();
					log.debug("String returned from Award API: " + apiRetStr);
					JSONObject json = (JSONObject) parser.parse(apiRetStr);

					if (json == null) {
						log.warn("No award information found for " + idString);
						continue;
					}
					
					log.debug("JSON=" + json.toJSONString());

					JSONObject jsonResponse = (JSONObject) json.get("response");
					JSONArray jsonAwards = (JSONArray) jsonResponse.get("award");
					if (jsonAwards == null || jsonAwards.size() < 1) {
						log.warn("No award information found for " + idString);
						continue;
					}
					JSONObject jsonAward = (JSONObject) jsonAwards.get(0);
				
					// find the desired information about the award
					String title = (String) jsonAward.get("title");
					String agency = (String) jsonAward.get("agency");
					String id = (String) jsonAward.get("id");
					String fundProgramName = (String) jsonAward.get("fundProgramName");
					String primaryProgram = (String) jsonAward.get("primaryProgram");
					
					// add them all as outputs for search and grouping
					outputs.add(new Output(title + " (" + agency + " " + id + ")"));
					outputs.add(new Output(title));
					outputs.add(new Output(agency));
					outputs.add(new Output(id));
					outputs.add(new Output(fundProgramName));
					outputs.add(new Output(primaryProgram));
					
					// look up cross ref info as well
					outputs.addAll(this.lookupCrossRef(id));

				} catch (Exception e) {
					log.error("Could not look up award " + awardId + " at URL " + url.toString() + ": ", e);
					continue;
				}
			}
			
			// set them all
			result.setOutput(outputs);
			
		} else {
			result.setStatus(Status.FAILURE);
			result.setOutput(new Output("NA"));
			log.warn("No award id given, cannot look-up funding");
		}
		
		return result;
		
	}
	
	// TODO: implement this in a way that makes sense
	private List<Output> lookupCrossRef(String awardId) {
		
		List<Output> outputs = new ArrayList<Output>();

		// find the funder using a work with this awardId
		// TODO: I don't think this is the best way to get at funder info
		String worksUrl = "http://api.crossref.org/works?rows=1&filter=award.number:" + awardId;

		// message.items[0].funder[x].DOI
		String funderDOI = null;
		String funderId = null; //funderDOI.substring(funderDOI.lastIndexOf("/"));
		
		// find the org hierarchy for the funder
		String funderUrl = "http://api.crossref.org/funders/" + funderId;

		return outputs;
	}

	public Object getAwards() {
		return awards;
	}

	public void setAwards(Object awards) {
		this.awards = awards;
	}
	
}
