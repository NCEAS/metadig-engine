package edu.ucsb.nceas.mdqengine;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
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

import edu.ucsb.nceas.mdqengine.dispatch.MDQCache;
import edu.ucsb.nceas.mdqengine.model.Check;
import edu.ucsb.nceas.mdqengine.model.Suite;
import edu.ucsb.nceas.mdqengine.model.Result;
import edu.ucsb.nceas.mdqengine.model.Run;
import edu.ucsb.nceas.mdqengine.processor.XMLDialect;
import edu.ucsb.nceas.mdqengine.serialize.JsonMarshaller;
import edu.ucsb.nceas.mdqengine.serialize.XmlMarshaller;

public class MDQEngine {
	
	private static final String RESOLVE_PREFIX = Settings.getConfiguration().getString("D1Client.CN_URL") + "/v2/resolve/";
	
	private MDQStore store = null;
	
	protected Log log = LogFactory.getLog(this.getClass());
	
	public MDQEngine() {
		MDQCache.initialize(null);
	}

	/**
	 * Executes the given suite for a given object identifier
	 * @param suite
	 * @param input the InputStream for the object to QC
	 * @return the Run results for this execution
	 * @throws MalformedURLException
	 * @throws IOException
	 * @throws SAXException
	 * @throws ParserConfigurationException
	 * @throws XPathExpressionException
	 * @throws ScriptException
	 */
	public Run runSuite(Suite suite, InputStream input) 
			throws MalformedURLException, IOException, SAXException, 
			ParserConfigurationException, XPathExpressionException, ScriptException {
			
		log.debug("Running suite: " + JsonMarshaller.toJson(suite));

		String content = IOUtils.toString(input, "UTF-8");
		String metadataContent = content;
		Map<String, String> dataUrls = null;
		
		// check if this is an ORE package
		boolean tryORE = false;
		if (tryORE) {
			DataPackage dp = null;
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
				log.warn("Input does not appear to be an ORE package, defaulting to metadata parsing. " + e.getCause());
				metadataContent = content;
			}
		}
		
		XMLDialect xml = new XMLDialect(IOUtils.toInputStream(metadataContent, "UTF-8"));
		xml.setDataUrls(dataUrls);
		
		// make a run to capture results
		Run run = new Run();
		run.setId(UUID.randomUUID().toString());
		run.setTimestamp(Calendar.getInstance().getTime());
		List<Result> results = new ArrayList<Result>();

		// run the checks in the suite to get results
		for (Check check: suite.getCheck()) {
			// is this a reference to existing check?
			if (check.getCode() == null) {
				// then load it
				check = store.getCheck(check.getId());
			}
			Result result = xml.runCheck(check);
			results.add(result);
		}
		run.setResult(results);
		
		log.debug("Run results: " + JsonMarshaller.toJson(run));
		
		return run;
		
	}
	
	/** 
	 * To enable checks-by-id-reference, set the store so that checks can be retrieved
	 * if not specified inline
	 * @param store The storage implementation to use for retrieving existing checks
	 */
	public void setStore(MDQStore store) {
		this.store = store;
	}
	
	/**
	 * Run a suite on a given metadata document. Prints Run XML results.
	 * @param args first is the suite file path, second is the metadata file path
	 * 
	 */
	public static void main(String args[]) {
		MDQEngine engine = new MDQEngine();
		try {
			String xml = IOUtils.toString(new FileInputStream(args[0]), "UTF-8");
			Suite suite = (Suite) XmlMarshaller.fromXml(xml , Suite.class);
			InputStream input = new FileInputStream(args[1]);
			Run run = engine.runSuite(suite, input);
			System.out.println(XmlMarshaller.toXml(run));
		} catch (Exception e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		
		
	}

}
