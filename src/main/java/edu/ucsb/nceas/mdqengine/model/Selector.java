package edu.ucsb.nceas.mdqengine.model;

import java.util.Map;

public class Selector {
	
	private String name;
	
	private String xpath;
	
	private Map<String, String> namespace;
	
	private Selector subSelector;

	public String getName() {
		return name;
	}

	public void setName(String name) {
		this.name = name;
	}

	public String getXpath() {
		return xpath;
	}

	public void setXpath(String xpath) {
		this.xpath = xpath;
	}

	public Selector getSubSelector() {
		return subSelector;
	}

	public void setSubSelector(Selector subSelector) {
		this.subSelector = subSelector;
	}

	public Map<String, String> getNamespace() {
		return namespace;
	}

	public void setNamespace(Map<String, String> namespace) {
		this.namespace = namespace;
	}

}
