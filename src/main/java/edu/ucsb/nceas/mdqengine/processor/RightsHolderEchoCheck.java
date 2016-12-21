package edu.ucsb.nceas.mdqengine.processor;

import java.util.concurrent.Callable;

import org.apache.commons.io.IOUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.dataone.service.types.v2.SystemMetadata;
import org.dataone.service.util.TypeMarshaller;

import edu.ucsb.nceas.mdqengine.model.Output;
import edu.ucsb.nceas.mdqengine.model.Result;
import edu.ucsb.nceas.mdqengine.model.Status;

/**
 * Extract rightsHolder from given SystemMetadata
 * @author leinfelder
 *
 */
public class RightsHolderEchoCheck implements Callable<Result> {
	
	// the XML serialization of SystemMetadata
	private String systemMetadata;
	
	public Log log = LogFactory.getLog(this.getClass());
	
	@Override
	public Result call() {
		Result result = new Result();
		
		if (this.systemMetadata != null) {
			result.setStatus(Status.SUCCESS);
			
			try {
				// unmarshall the sysMeta from the XML
				SystemMetadata sm = TypeMarshaller.unmarshalTypeFromStream(SystemMetadata.class, IOUtils.toInputStream(systemMetadata, "UTF-8"));
				
				// get the rightsHolder
				result.setOutput(new Output(sm.getRightsHolder().getValue()));
				
			} catch (Exception e) {
				result.setStatus(Status.ERROR);
				result.setOutput(new Output(e.getMessage()));
				log.error("Could not look up system metadata field", e);
			}
		} else {
			result.setStatus(Status.FAILURE);
			result.setOutput(new Output("NA"));
			log.warn("No SystemMetadata given");
		}
		
		return result;
		
	}

	public String getSystemMetadata() {
		return systemMetadata;
	}

	public void setSystemMetadata(String sm) {
		this.systemMetadata = sm;
	}
	
}
