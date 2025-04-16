package edu.ucsb.nceas.mdqengine.model;

import java.util.List;

public interface Selector {

    public String getName();

    public void setName(String name);

    public String getXpath();

    public void setXpath(String xpath);

    public Selector getSubSelector();

    public void setSubSelector(Selector subSelector);

    public List<Namespace> getNamespace();

    public void setNamespace(List<Namespace> namespace);

    public boolean isNamespaceAware();

    public void setNamespaceAware(boolean namespaceAware);

    public Expression getExpression();

    public void setExpression(Expression expression);
}
