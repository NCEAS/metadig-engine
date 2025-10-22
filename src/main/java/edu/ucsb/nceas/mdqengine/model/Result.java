package edu.ucsb.nceas.mdqengine.model;

import java.util.Arrays;
import java.util.Date;
import java.util.List;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;

@XmlAccessorType(XmlAccessType.FIELD)
public class Result {
	
	/**
	 * The check that was run
	 */
	private Check check;
		
	/**
	 * The timestamp of the check execution
	 */
	private Date timestamp;
		
	/**
	 * The list of output value[s] from the check execution
	 */
	private List<Output> output;
	
	/**
	 * The status of the check run, constrained to the Status enum
	 */
	private Status status;

	public List<Output> getOutput() {
		return output;
	}

	public void setOutput(Output output) {
		this.output = Arrays.asList(output);
	}
	
	public void setOutput(List<Output> output) {
		this.output = output;
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
