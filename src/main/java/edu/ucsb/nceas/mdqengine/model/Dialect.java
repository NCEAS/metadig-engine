package edu.ucsb.nceas.mdqengine.model;

import javax.xml.bind.annotation.*;

@XmlRootElement
@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(propOrder = {"name", "xpath", "jsonpath"})
public class Dialect {

	/**
	 * The name or label to associate with this dialect definition
	 */
	@XmlElement(required = true)
	private String name;

	/**
	 * The XPath expression that is used to determine if a document is of this dialect.
	 * The expression should evaluate to a boolean value, where true indicates the document
	 * is of this dialect.
	 */
	@XmlElement(required = false)
	private String xpath;

	/**
	 * The JSONPath expression that is used to determine if a document is of this dialect.
	 * The expression should evaluate to a boolean value, where true indicates the document
	 * is of this dialect.
	 */
	@XmlElement(required = false)
	private JSONpath jsonpath;

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

	public void setJsonpath(JSONpath jsonpath) {
		this.jsonpath = jsonpath;
	}

	public String getJsonpath() {
		// This element might not be present
		if(jsonpath == null) {
			return(null);
		} else {
			return jsonpath.getJsonpath();
		}
	}

	public String getMatch() {
		return this.jsonpath.getMatch();
	}

	public void setMatch(String match) {
		this.jsonpath.setMatch(match);
	}
}
