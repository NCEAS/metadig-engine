package edu.ucsb.nceas.mdqengine.model;

import java.util.List;

public class Recommendation {
	
	private String name;
	
	private List<Check> checks;

	public String getName() {
		return name;
	}

	public void setName(String name) {
		this.name = name;
	}

	public List<Check> getChecks() {
		return checks;
	}

	public void setChecks(List<Check> checks) {
		this.checks = checks;
	}

}
