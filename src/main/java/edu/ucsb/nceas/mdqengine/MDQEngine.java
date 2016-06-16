package edu.ucsb.nceas.mdqengine;

import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;

import javax.script.ScriptException;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.xpath.XPathExpressionException;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.xml.sax.SAXException;

import edu.ucsb.nceas.mdqengine.model.Check;
import edu.ucsb.nceas.mdqengine.model.Recommendation;
import edu.ucsb.nceas.mdqengine.model.Result;
import edu.ucsb.nceas.mdqengine.model.Run;
import edu.ucsb.nceas.mdqengine.processor.XMLDialect;
import edu.ucsb.nceas.mdqengine.serialize.JsonMarshaller;

public class MDQEngine {
	
	protected Log log = LogFactory.getLog(this.getClass());

	/**
	 * Executes the given recommendation for a given object identifier
	 * @param recommendation
	 * @param id
	 * @return the Run results for this execution
	 * @throws MalformedURLException
	 * @throws IOException
	 * @throws SAXException
	 * @throws ParserConfigurationException
	 * @throws XPathExpressionException
	 * @throws ScriptException
	 */
	public Run runRecommendation(Recommendation recommendation, String id) 
			throws MalformedURLException, IOException, SAXException, 
			ParserConfigurationException, XPathExpressionException, ScriptException {
			
		log.debug("Running recommendation: " + JsonMarshaller.toJson(recommendation));

		// parse the metadata content
		// TODO: configurable repo for fetching content by id
		String metadataURL = "https://cn.dataone.org/cn/v2/object/" + id;
		InputStream input = new URL(metadataURL).openStream();
		XMLDialect xml = new XMLDialect(input);
		
		// make a run
		Run run = new Run();
		run.setObjectIdentifier(id);
		run.setTimestamp(Calendar.getInstance().getTime());
		List<Result> results = new ArrayList<Result>();

		// run the checks in the recommendation
		for (Check check: recommendation.getCheck()) {
			Result result = xml.runCheck(check);
			results.add(result);
		}
		run.setResults(results);
		
		log.debug("Run results: " + JsonMarshaller.toJson(run));
		
		return run;
		
	}

}
