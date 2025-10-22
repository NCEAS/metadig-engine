package edu.ucsb.nceas.mdqengine.model;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlElement;

/**
 * Dialect is used to conditionally apply the check or ensure Check is valid.
 * They
 * contain a name, xpath and/or expression element which is used to extract an
 * element that can be used to verify the check should be run on the document.
 */

@XmlAccessorType(XmlAccessType.FIELD)
public class Dialect {

    /**
     * The name or label to associate with this dialect definition
     */
    @XmlElement(required = false)
    private String name;

    /**
     * The XPath expression that is used to determine if a document is of this
     * dialect. The expression should evaluate to a boolean value, where true
     * indicates the document is of this dialect.
     */
    @XmlElement(required = false)
    private String xpath;

    @XmlElement(required = false)
	private Expression expression;


    /**
     * Gets the name of the dialect.
     *
     * @return the dialect name
     */
    public String getName() {
        return name;
    }

    /**
     * Sets the name of the dialect.
     *
     * @param name the dialect name to set
     */
    public void setName(String name) {
        this.name = name;
    }

    /**
     * Gets the xpath expression used to determine if this dialect applies to a
     * given metadata document.
     *
     * @return the xpath expression string
     */
    public String getXpath() {
        return xpath;
    }

    /**
     * Sets the xpath for this dialect.
     *
     * @param xpath the xpath expression to set
     */
    public void setXpath(String xpath) {
        this.xpath = xpath;
    }

    	/**
	 * Gets the expression object used to evaluate the dialect condition. This can
	 * be used instead of or in addition to xpath to support other expression
	 * syntaxes.
	 *
	 * @return the Expression object
	 */
	public Expression getExpression() {
		return expression;
	}

	/**
	 * Sets the expression object. This can be used instead of or in addition to
	 * xpath to support other expression syntaxes.
	 *
	 * @param expression the Expression to set
	 */
	public void setExpression(Expression expression) {
		this.expression = expression;
	}

}
