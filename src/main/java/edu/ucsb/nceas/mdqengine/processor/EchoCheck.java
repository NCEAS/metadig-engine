package edu.ucsb.nceas.mdqengine.processor;

import java.util.concurrent.Callable;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import edu.ucsb.nceas.mdqengine.model.Output;
import edu.ucsb.nceas.mdqengine.model.Result;
import edu.ucsb.nceas.mdqengine.model.Status;

/**
 * Check to print selected value
 * @author leinfelder
 *
 */
public class EchoCheck implements Callable<Result> {
	
	private String value;
	
	public Log log = LogFactory.getLog(this.getClass());
	
	@Override
	public Result call() {
		Result result = new Result();
		
		if (this.value != null && this.value.length() > 0) {
			result.setStatus(Status.SUCCESS);
		} else {
			result.setStatus(Status.FAILURE);
		}
		result.setOutput(new Output("Found value:" + this.value));
		
		return result;
		
	}

	public String getValue() {
		return value;
	}

	public void setValue(String value) {
		this.value = value;
	}
	
}
