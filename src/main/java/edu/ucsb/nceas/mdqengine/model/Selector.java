package edu.ucsb.nceas.mdqengine.model;

import java.util.List;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlElementWrapper;

/**
 * A Selector is used to extract value(s) from a metadata document, using xpath
 * or other types of query syntax. Each selector defines how metadata should be
 * located and named for use in checks. Selectors can reference XPath
 * expressions, custom expressions, and define sub-selectors to support nested
 * or complex structures. Selectors support optional namespace resolution,
 * allowing expressions to use prefixed paths when necessary. They are
 * associated with variable names that are injected into the check’s scripting
 * environment. These variable names must be valid identifiers in the target
 * scripting language and should avoid reserved keywords (e.g., `var`, `int`,
 * `public`).
 * 
 * @author leinfelder
 *
 */
@XmlAccessorType(XmlAccessType.FIELD)
public class Selector {

	/**
	 * The selector name is used to create a variable in the environment specified
	 * by Check.environment and should be a valid variable for that target
	 * environment. reserved tokens like 'var', 'int', 'public', etc. should be
	 * avoided.
	 */
	@XmlElement(required = true)
	private String name;

	/**
	 * The xpath expression is used to extract value[s] from the document for this
	 * named selector. The xpath will often be a compound expression to cover a
	 * variety of metadata dialects. For example, the notion of a "title" can be
	 * stored in many different ways depending on the metadata standard used, but
	 * conceptually the value can be checked exactly the same no matter where or how
	 * it is serialized in metadata.
	 */
	@XmlElement(required = false)
	private String xpath;

	/**
	 * Specifies whether or not this selector should be namespace aware or not.
	 */
	@XmlAttribute(required = false)
	private Boolean namespaceAware;

	/**
	 * The optional namespace list can be used to map namespace prefixes to full
	 * namespace uris. This is useful if your xpath expressions utilize namespace
	 * prefixes and you don not want to loose the disambiguation that they provide,
	 * say, by using local-name() predicates.
	 */
	@XmlElementWrapper(name = "namespaces", required = false)
	private List<Namespace> namespace;

	/**
	 * Subselectors can be used when access to complex structures is required and
	 * the structure needs to be preserved. Often is is used to return lists of
	 * lists (e.g., when examining attributes of multiple entities).
	 */
	@XmlElement(name = "subSelector", type = Selector.class, required = false)
	protected Selector subSelector;
	/**
	 * Returns the name of the selector.
	 *
	 * @return the selector's name
	 */

	@XmlElement(required = false)
	private Expression expression;

	public String getName() {
		return name;
	}

	/**
	 * Sets the name of the selector.
	 *
	 * @param name the selector's name
	 */
	public void setName(String name) {
		this.name = name;
	}

	/**
	 * Returns the xpath expression used by this selector.
	 *
	 * @return the xpath expression string
	 */
	public String getXpath() {
		return xpath;
	}

	/**
	 * Sets the xpath expression.
	 *
	 * @param xpath the xpath expression string
	 */
	public void setXpath(String xpath) {
		this.xpath = xpath;
	}

	/**
	 * Returns the optional sub-selector associated with this selector.
	 *
	 * @return a nested {@code Selector}, or {@code null} if not present
	 */
	public Selector getSubSelector() {
		return subSelector;
	}

	/**
	 * Sets the sub-selector for this selector.
	 *
	 * @param subSelector a nested {@code Selector}
	 */
	public void setSubSelector(Selector subSelector) {
		this.subSelector = subSelector;
	}

	/**
	 * Returns the list of namespaces.
	 *
	 * @return a list of {@code Namespace} objects
	 */
	public List<Namespace> getNamespace() {
		return namespace;
	}

	/**
	 * Sets the list of namespaces.
	 *
	 * @param namespace a list of {@code Namespace} objects
	 */
	public void setNamespace(List<Namespace> namespace) {
		this.namespace = namespace;
	}

	/**
	 * Indicates whether this selector should consider namespaces when evaluating
	 * expressions.
	 *
	 * @return {@code true} if namespace-aware, {@code false} otherwise
	 */
	public boolean isNamespaceAware() {
		return namespaceAware == null ? false : namespaceAware;
	}

	/**
	 * Sets whether this selector should be namespace-aware.
	 *
	 * @param namespaceAware {@code true} to enable namespace awareness;
	 *                       {@code false} otherwise
	 */
	public void setNamespaceAware(boolean namespaceAware) {
		this.namespaceAware = namespaceAware;
	}

	/**
	 * Returns the expression used by this selector, which may be xpath or
	 * json-path.
	 *
	 * @return the {@code Expression} object
	 */
	public Expression getExpression() {
		return expression;
	}

	/**
	 * Sets the expression used by this selector.
	 *
	 * @param expression the {@code Expression} object to use
	 */
	public void setExpression(Expression expression) {
		this.expression = expression;
	}

}
