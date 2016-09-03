package edu.ucsb.nceas.mdqengine.model;

import java.util.List;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlElementWrapper;
import javax.xml.bind.annotation.XmlType;

@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(propOrder = {"name", "xpath", "subSelector", "namespace"})
public class Selector {
	
	@XmlElement(required = true)
	private String name;
	
	@XmlElement(required = true)
	private String xpath;
	
	@XmlElementWrapper(name = "namespaces", required = false)
	private List<Namespace> namespace;
	
	@XmlElement(required = false)
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

	public List<Namespace> getNamespace() {
		return namespace;
	}

	public void setNamespace(List<Namespace> namespace) {
		this.namespace = namespace;
	}

}
