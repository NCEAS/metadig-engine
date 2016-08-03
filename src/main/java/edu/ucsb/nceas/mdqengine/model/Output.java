package edu.ucsb.nceas.mdqengine.model;

public class Output {
	
	private String value;
	
	private String type;

	public Output(String value) {
		this.value = value;
	}
	
	public String getValue() {
		return value;
	}

	public void setValue(String value) {
		this.value = value;
	}

	public String getType() {
		return type;
	}

	public void setType(String type) {
		this.type = type;
	}

}
