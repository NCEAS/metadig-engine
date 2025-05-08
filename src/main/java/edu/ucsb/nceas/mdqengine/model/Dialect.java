package edu.ucsb.nceas.mdqengine.model;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;

/**
 * Dialect is used to conditionally apply the check or ensure Check is valid. They
 * contain a name, xpath and/or expression element which is used to extract an
 * element that can be used to verify the check should be run on the document.
 */

@XmlAccessorType(XmlAccessType.FIELD)
public interface Dialect {
	/**
	 * Gets the name of the dialect.
	 *
	 * @return the dialect name
	 */
	public String getName();

	/**
	 * Sets the name of the dialect.
	 *
	 * @param name the dialect name to set
	 */
	public void setName(String name);

	/**
	 * Gets the xpath expression used to determine if this dialect applies to a
	 * given metadata document.
	 *
	 * @return the xpath expression string
	 */
	public String getXpath();

	/**
	 * Sets the xpath for this dialect.
	 *
	 * @param xpath the xpath expression to set
	 */
	public void setXpath(String xpath);

	/**
	 * Gets the expression object used to evaluate the dialect condition. This can
	 * be used instead of or in addition to xpath to support other expression
	 * syntaxes.
	 *
	 * @return the Expression object
	 */
	public Expression getExpression();

	/**
	 * Sets the expression object. This can be used instead of or in addition to
	 * xpath to support other expression syntaxes.
	 *
	 * @param expression the Expression to set
	 */
	public void setExpression(Expression expression);
}
