package edu.ucsb.nceas.mdqengine.model;

import edu.ucsb.nceas.mdqengine.exception.MetadigException;

import javax.xml.bind.annotation.*;
import java.util.List;

@XmlRootElement
@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(propOrder = {"id", "name", "description", "namespace", "check"})
public class Suite {
	
	/**
	 * The unique identifier for the suite. This should be unique, at the very least, 
	 * within the scope of the MDQ engine store. It will be used to identify which
	 * suite[s] to run on metadata documents and for organizing QC results.
	 */
	@XmlElement(required = true)
	private String id;

	/**
	 * A short and useful name for the suite that will be displayed in 
	 * user interfaces and reports.
	 */
	@XmlElement(required = true)
	private String name;
	
	/**
	 * A comprehensive sumary of the suite and what it is intended to check.
	 */
	@XmlElement(required = false)
	private String description;
	
	/**
	 * The optional namespace list can be used to map namespace prefixes to full namespace uris.
	 * This is useful if your xpath expressions utilize namespace prefixes and you don not want to 
	 * loose the disambiguation that they provide, say, by using local-name() predicates.
	 */
	@XmlElementWrapper(name = "namespaces", required = false)
	private List<Namespace> namespace;
	
	/**
	 * The list of checks to be performed. A suite must have at least one check.
	 */
	@XmlElement(required = true)
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

	public String getDescription() {
		return description;
	}

	public void setDescription(String description) {
		this.description = description;
	}

	// Return all checks contained in the suite.
	public List<Check> getCheck() {
		return check;
	}

	// Find a specific check from the list of checks
	public Check getCheck(String id) throws Exception {
		for (Check thisCheck: check) {
			if(thisCheck.equals(id) ) {
				return thisCheck;
			}
		}
		// The check was not found
		throw new MetadigException("Check with id: " + id + " was not found.");
	}

	public void setCheck(List<Check> check) {
		this.check = check;
	}
	
	public List<Namespace> getNamespace() {
		return namespace;
	}

	public void setNamespace(List<Namespace> namespace) {
		this.namespace = namespace;
	}

}
