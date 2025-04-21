package edu.ucsb.nceas.mdqengine.model;

import java.util.List;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlElementWrapper;
import javax.xml.bind.annotation.XmlType;

/**
 * Selectors are used to extract value[s] from metadata documents using XPath
 * expressions.
 * 
 * @author leinfelder
 *
 */
@XmlAccessorType(XmlAccessType.FIELD)
public class SelectorV2 implements Selector {

    public static SelectorV2 newSelector() {
        return new SelectorV2();
    }

   	/**
	 * The selector name is used to create a variable in the environment specified
	 * by Check.environment and should be a valid variable for that target
	 * environment. reserved tokens like 'var', 'int', 'public', etc.. should be
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

	@XmlElement(required = false)
	private Expression expression;

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
	@XmlElement(name = "subSelector", type = SelectorV1.class, required = false)
	//@XmlElement(required = false)
	protected SelectorV2 subSelector;

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
	@Override
	public Selector getSubSelector() {
		return subSelector;
	}
	@Override
	public void setSubSelector(Selector subSelector) {
		this.subSelector = (SelectorV2) subSelector;
	}
	@Override
	public List<Namespace> getNamespace() {
		return namespace;
	}
	@Override
	public void setNamespace(List<Namespace> namespace) {
		this.namespace = namespace;
	}
	@Override
	public boolean isNamespaceAware() {
		return namespaceAware == null ? false : namespaceAware;
	}
	@Override
	public void setNamespaceAware(boolean namespaceAware) {
		this.namespaceAware = namespaceAware;
	}
	@Override
    public Expression getExpression() {
        return expression;
    }
    @Override
    public void setExpression(Expression expression) {
        this.expression = expression;
    }

}
