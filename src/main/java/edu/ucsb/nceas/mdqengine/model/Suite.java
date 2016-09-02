package edu.ucsb.nceas.mdqengine.model;

import java.util.Collections;
import java.util.List;

import javax.xml.bind.annotation.XmlRootElement;

@XmlRootElement
public class Suite {
	
	private String id;

	private String name;
	
	private List<Check> check;

	public String getId() {
		return id;
	}

	public void setId(String id) {
		this.id = id;
	}

	public String getName() {
		return name;
	}

	public void setName(String name) {
		this.name = name;
	}

	public List<Check> getCheck() {
		return check;
//		if (check == null) {
//			return Collections.<Check>emptyList();
//		} else {
//			return check;
//		}
	}

	public void setCheck(List<Check> check) {
		this.check = check;
	}

}
