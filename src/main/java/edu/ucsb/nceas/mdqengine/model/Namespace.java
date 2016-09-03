package edu.ucsb.nceas.mdqengine.model;

public class Namespace {
	
	private String prefix;
	
	private String uri;

	public Namespace() {}
	
	public Namespace(String value) {
		this.prefix = value;
	}
	
	public String getPrefix() {
		return prefix;
	}

	public void setPrefix(String p) {
		this.prefix = p;
	}

	public String getUri() {
		return uri;
	}

	public void setUri(String uri) {
		this.uri = uri;
	}

}
