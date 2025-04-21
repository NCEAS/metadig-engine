package edu.ucsb.nceas.mdqengine.model;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlElement;

@XmlAccessorType(XmlAccessType.FIELD)
public class DialectV1 implements Dialect {

    public DialectV1() {}

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

	@Override
    public Expression getExpression() {
        return null;
    }
    @Override
    public void setExpression(Expression expression) {
        return;
    }


}
