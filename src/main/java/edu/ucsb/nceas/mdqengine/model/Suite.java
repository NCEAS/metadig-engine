package edu.ucsb.nceas.mdqengine.model;

import java.util.List;

import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.bind.annotation.XmlType;

@XmlRootElement
@XmlType(propOrder = {"id", "name", "description", "check"})
public class Suite {
	
	private String id;

	private String name;
	
	private String description;
	
	private List<Check> check;

	@XmlElement(required = true)
	public String getId() {
		return id;
	}

	public void setId(String id) {
		this.id = id;
	}

	@XmlElement(required = true)
	public String getName() {
		return name;
	}

	public void setName(String name) {
		this.name = name;
	}

	@XmlElement(required = false)
	public String getDescription() {
		return description;
	}

	public void setDescription(String description) {
		this.description = description;
	}

	@XmlElement(required = true)
	public List<Check> getCheck() {
		return check;
	}

	public void setCheck(List<Check> check) {
		this.check = check;
	}

}
