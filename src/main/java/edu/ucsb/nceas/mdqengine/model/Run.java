package edu.ucsb.nceas.mdqengine.model;

import java.util.Date;
import java.util.List;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.bind.annotation.XmlType;

@XmlRootElement
@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(propOrder = {"id", "timestamp", "objectIdentifier", "metadata", "suiteId", "result"})
public class Run {
	
	/**
	 * The unique identifier for the QC run. This will likely be long and opaque
	 * (like a UUID) considering the quantity of runs that will likely be performed 
	 * over the content in a large repository.
	 */
	@XmlElement(required = true)
	private String id;

	/**
	 * The timestamp of the QC run
	 */
	@XmlElement(required = true)
	private Date timestamp;
	
	/**
	 * The identifier of the metadata document that was QCed.
	 * This is optional since in some cases, we will be performing 
	 * QC on objects that are being actively edited/created and 
	 * do not yet exist with persistent identifiers.
	 */
	@XmlElement(required = false)
	private String objectIdentifier;
	
	/**
	 * The list of results for this run. Results contain the check that was run and 
	 * the outcome of the check.
	 */
	@XmlElement(required = false)
	private List<Result> result;
	
	/**
	 * The identifier for the suite that was run
	 */
	@XmlElement(required = false)
	private String suiteId;
	
	/**
	 * Additional information about the document that was QCed.
	 * This can be helpful when analyzing and aggregating batches of run results.
	 */
	@XmlElement(required = false)
	private Metadata metadata;

	public String getId() {
		return id;
	}

	public void setId(String id) {
		this.id = id;
	}

	public Date getTimestamp() {
		return timestamp;
	}

	public void setTimestamp(Date timestamp) {
		this.timestamp = timestamp;
	}

	public List<Result> getResult() {
		return result;
	}

	public void setResult(List<Result> result) {
		this.result = result;
	}

	public String getObjectIdentifier() {
		return objectIdentifier;
	}

	public void setObjectIdentifier(String objectIdentifier) {
		this.objectIdentifier = objectIdentifier;
	}

	public Metadata getMetadata() {
		return metadata;
	}

	public void setMetadata(Metadata metadata) {
		this.metadata = metadata;
	}

	public String getSuiteId() {
		return suiteId;
	}

	public void setSuiteId(String suiteId) {
		this.suiteId = suiteId;
	}

}
