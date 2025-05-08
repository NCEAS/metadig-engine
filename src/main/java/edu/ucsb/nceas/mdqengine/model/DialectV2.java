package edu.ucsb.nceas.mdqengine.model;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlElement;

/**
 * Dialect is used to conditionally apply the check or ensure Check is valid.
 * They
 * contain a name, xpath and/or expression element which is used to extract an
 * element that can be used to verify the check should be run on the document.
 * 
 * DialectV2 optionally supports the expression element, which allows for more
 * options in syntax than the previously used xpath element.
 */

@XmlAccessorType(XmlAccessType.FIELD)
public class DialectV2 implements Dialect {

	public DialectV2() {
	}

	public static DialectV2 newDialect() {
		return new DialectV2();
	}

	/**
	 * The name or label to associate with this dialect definition
	 */
	@XmlElement(required = false)
	private String name;

	/**
	 * The XPath expression that is used to determine if a document is of this
	 * dialect.
	 * The expression should evaluate to a boolean value, where true indicates the
	 * document
	 * is of this dialect.
	 */
	@XmlElement(required = false)
	private String xpath;

	@XmlElement(required = false)
	private Expression expression;

	@Override
	public String getName() {
		return name;
	}

	@Override
	public void setName(String name) {
		this.name = name;
	}

	@Override
	public String getXpath() {
		return xpath;
	}

	@Override
	public void setXpath(String xpath) {
		this.xpath = xpath;
	}

	public Expression getExpression() {
		return expression;
	}

	public void setExpression(Expression expression) {
		this.expression = expression;
	}

}
