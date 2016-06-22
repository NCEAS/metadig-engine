package edu.ucsb.nceas.mdqengine;

import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import javax.script.ScriptException;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.xpath.XPathExpressionException;

import org.apache.commons.io.IOUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.dataone.client.v2.itk.DataPackage;
import org.dataone.configuration.Settings;
import org.dataone.service.types.v1.Identifier;
import org.xml.sax.SAXException;

import edu.ucsb.nceas.mdqengine.model.Check;
import edu.ucsb.nceas.mdqengine.model.Recommendation;
import edu.ucsb.nceas.mdqengine.model.Result;
import edu.ucsb.nceas.mdqengine.model.Run;
import edu.ucsb.nceas.mdqengine.processor.XMLDialect;
import edu.ucsb.nceas.mdqengine.serialize.JsonMarshaller;

public class MDQEngine {
	
	private static final String RESOLVE_PREFIX = Settings.getConfiguration().getString("D1Client.CN_URL") + "/v2/resolve/";
	
	protected Log log = LogFactory.getLog(this.getClass());

	/**
	 * Executes the given recommendation for a given object identifier
	 * @param recommendation
	 * @param input the InputStream for the object to QC
	 * @return the Run results for this execution
	 * @throws MalformedURLException
	 * @throws IOException
	 * @throws SAXException
	 * @throws ParserConfigurationException
	 * @throws XPathExpressionException
	 * @throws ScriptException
	 */
	public Run runRecommendation(Recommendation recommendation, InputStream input) 
			throws MalformedURLException, IOException, SAXException, 
			ParserConfigurationException, XPathExpressionException, ScriptException {
			
		log.debug("Running recommendation: " + JsonMarshaller.toJson(recommendation));

		// check if this is an ORE package
		DataPackage dp = null;
		String metadataContent = null;
		String content = IOUtils.toString(input, "UTF-8");
		Map<String, String> dataUrls = null;
		try {
			
			// parse as ORE
			dp = DataPackage.deserializePackage(content);
			
			// assume single metadata item in package
			List<Identifier> dataIds = dp.getMetadataMap().values().iterator().next();
			Identifier metadataId = dp.getMetadataMap().keySet().iterator().next();
			dataUrls = new HashMap<String, String>();
			for (Identifier id: dataIds) {
				dataUrls.put(id.getValue(), RESOLVE_PREFIX + id.getValue());
			}
			//  fetch the metadata content for checker
			metadataContent = IOUtils.toString(dp.get(metadataId).getData(), "UTF-8");
			
		} catch (Exception e) {
			// guess it is not an ORE!
			log.warn("Input does not appear to be an ORE package, defaulting to metadata parsing. " + e.getCause(), e);
			metadataContent = content;
		}
		
		XMLDialect xml = new XMLDialect(IOUtils.toInputStream(metadataContent, "UTF-8"));
		xml.setDataUrls(dataUrls);
		
		// make a run to capture results
		Run run = new Run();
		run.setId(UUID.randomUUID().toString());
		run.setTimestamp(Calendar.getInstance().getTime());
		List<Result> results = new ArrayList<Result>();

		// run the checks in the recommendation to get results
		for (Check check: recommendation.getCheck()) {
			Result result = xml.runCheck(check);
			results.add(result);
		}
		run.setResult(results);
		
		log.debug("Run results: " + JsonMarshaller.toJson(run));
		
		return run;
		
	}

}
