package edu.ucsb.nceas.mdqengine.model;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;

@XmlAccessorType(XmlAccessType.FIELD)
public class Dialect {

	/**
	 * The name or label to associate with this dialect definition
	 */
	private String name;

	/**
	 * The XPath expression that is used to determine if a document is of this dialect.
	 * The expression should evaluate to a boolean value, where true indicates the document
	 * is of this dialect.
	 */
	private String xpath;

	/**
	 * The JSONPath expression that is used to determine if a document is of this dialect.
	 * The expression should evaluate to a boolean value, where true indicates the document
	 * is of this dialect.
	 */
	private String jsonpath;

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

	public String getJSONPath() {
		return jsonpath;
	}

	public void setJSONPath(String jsonpath) {
		this.jsonpath = jsonpath;
	}
}
