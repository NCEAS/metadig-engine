package edu.ucsb.nceas.mdqengine.model;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlValue;

/**
 * The namespace maps a full uri to a prefix.
 * @author leinfelder
 *
 */
@XmlAccessorType(XmlAccessType.FIELD)
public class Namespace {
	
	/**
	 * The namespace prefix. This should match the prefix used in XPath expression[s]
	 */
	@XmlAttribute(required = true)
	private String prefix;
	
	/**
	 * The full namepace URI that the prefix maps to
	 */
	@XmlValue
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
