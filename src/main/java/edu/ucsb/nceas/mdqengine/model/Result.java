package edu.ucsb.nceas.mdqengine.model;

import java.util.Arrays;
import java.util.Date;
import java.util.List;

public class Result {
	
	private Check check;
		
	private Date timestamp;
		
	private List<Output> output;
	
	private Status status;

	public List<Output> getOutput() {
		return output;
	}

	public void setOutput(Output... output) {
		this.output = Arrays.asList(output);
	}

	public Status getStatus() {
		return status;
	}

	public void setStatus(Status status) {
		this.status = status;
	}

	public Check getCheck() {
		return check;
	}

	public void setCheck(Check check) {
		this.check = check;
	}

	public Date getTimestamp() {
		return timestamp;
	}

	public void setTimestamp(Date timestamp) {
		this.timestamp = timestamp;
	}

}
