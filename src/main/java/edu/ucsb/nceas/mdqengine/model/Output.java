package edu.ucsb.nceas.mdqengine.model;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlValue;

import org.eclipse.persistence.oxm.annotations.XmlCDATA;

/**
 * The output of check code is captured as Output. Many kinds of output are
 * allowed:
 * Scalar values( e.g., message strings, numbers, booleans)
 * Complex structures serialized as strings (e.g., lists, tabular data, JSON)
 * Base64 encoded binary data (e.g., png images)
 * The type attribute should be used to indicate the kind of output value
 * 
 * @author leinfelder
 *
 */
@XmlAccessorType(XmlAccessType.FIELD)
public class Output {

	/**
	 * The output value as a string representation
	 */
	@XmlCDATA
	@XmlValue
	private String value;

	/**
	 * The type of output value. If omitted, a scalar string value will be assumed.
	 * Standard MIME-types should be used if including binary base64 encoded data.
	 */
	@XmlAttribute(required = false)
	private String type;

	/**
	 * The identifier of the object that was checked.
	 */
	@XmlAttribute(required = false)
	private String identifier;

	public Output() {
	}

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

	public void setIdentifier(String identifier) {
		this.identifier = identifier;
	}

}
