package edu.ucsb.nceas.mdqengine.model;

import java.util.Date;
import java.util.List;

public class Run {
	
	private Date timestamp;
	
	private String objectIdentifier;
		
	private List<Result> results;

	public Date getTimestamp() {
		return timestamp;
	}

	public void setTimestamp(Date timestamp) {
		this.timestamp = timestamp;
	}

	public List<Result> getResults() {
		return results;
	}

	public void setResults(List<Result> results) {
		this.results = results;
	}

	public String getObjectIdentifier() {
		return objectIdentifier;
	}

	public void setObjectIdentifier(String objectIdentifier) {
		this.objectIdentifier = objectIdentifier;
	}

}
