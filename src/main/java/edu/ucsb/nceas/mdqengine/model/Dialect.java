package edu.ucsb.nceas.mdqengine.model;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;

@XmlAccessorType(XmlAccessType.FIELD)
public interface Dialect {

	public String getName();

	public void setName(String name);

	public String getXpath();

	public void setXpath(String xpath);

	public Expression getExpression();

	public void setExpression(Expression expression);

}
