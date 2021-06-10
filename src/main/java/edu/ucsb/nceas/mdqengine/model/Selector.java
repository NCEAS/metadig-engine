package edu.ucsb.nceas.mdqengine.model;

import java.util.List;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlElementWrapper;
import javax.xml.bind.annotation.XmlType;

/**
 * Selectors are used to extract value[s] from metadata documents using XPath expressions.
 * @author leinfelder
 *
 */
@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(propOrder = {"name", "xpath", "jsonpath", "subSelector", "namespace"})
public class Selector {
	
	/**
	 * The selector name is used to create a variable in the environment specified by
	 * Check.environment and should be a valid variable for that target environment.
	 * reserved tokens like 'var', 'int', 'public', etc.. should be avoided.
	 */
	@XmlElement(required = true)
	private String name;
	
	/**
	 * The xpath expression is used to extract value[s] from the document for this named 
	 * selector.
	 * The xpath will often be a compound expression to cover a variety of metadata dialects.
	 * For example, the notion of a "title" can be stored in many different ways depending 
	 * on the metadata standard used, but conceptually the value can be checked exactly the same
	 * no matter where or how it is serialized in metadata.
	 */
	@XmlElement(required = false)
	private String xpath;

	/**
	 * The jsonpath expression is used to extract value[s] from the document for this named
	 * selector.
	 * The jsonpath is used for JSON based metadata dialects.
	 */
	@XmlElement(required = false)
	private String jsonpath;
	
	/**
	 * Specifies whether or not this selector should be namespace aware or not.
	 */
	@XmlAttribute(required = false)
	private Boolean namespaceAware;
	
	/**
	 * The optional namespace list can be used to map namespace prefixes to full namespace uris.
	 * This is useful if your xpath expressions utilize namespace prefixes and you don not want to 
	 * loose the disambiguation that they provide, say, by using local-name() predicates.
	 */
	@XmlElementWrapper(name = "namespaces", required = false)
	private List<Namespace> namespace;
	
	/**
	 * Subselectors can be used when access to complex structures is required and the structure needs to be 
	 * preserved. Often is is used to return lists of lists (e.g., when examining attributes of multiple entities).
	 */
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

	public String getJSONpath() { return jsonpath; }

	public void setJSONpath(String jsonpath) { this.jsonpath = jsonpath; }

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

	public boolean isNamespaceAware() {
		return namespaceAware == null ? false: namespaceAware;
	}

	public void setNamespaceAware(boolean namespaceAware) {
		this.namespaceAware = namespaceAware;
	}

}
