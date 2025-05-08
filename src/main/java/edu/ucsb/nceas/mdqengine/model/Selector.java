package edu.ucsb.nceas.mdqengine.model;

import java.util.List;

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
 */

public interface Selector {

    /**
     * Returns the name of the selector.
     *
     * @return the selector's name
     */
    public String getName();

    /**
     * Sets the name of the selector.
     *
     * @param name the selector's name
     */
    public void setName(String name);

    /**
     * Returns the xpath expression used by this selector.
     *
     * @return the xpath expression string
     */
    public String getXpath();

    /**
     * Sets the xpath expression.
     *
     * @param xpath the xpath expression string
     */
    public void setXpath(String xpath);

    /**
     * Returns the optional sub-selector associated with this selector.
     *
     * @return a nested {@code Selector}, or {@code null} if not present
     */
    public Selector getSubSelector();

    /**
     * Sets the sub-selector for this selector.
     *
     * @param subSelector a nested {@code Selector}
     */
    public void setSubSelector(Selector subSelector);

    /**
     * Returns the list of namespaces.
     *
     * @return a list of {@code Namespace} objects
     */
    public List<Namespace> getNamespace();

    /**
     * Sets the list of namespaces.
     *
     * @param namespace a list of {@code Namespace} objects
     */
    public void setNamespace(List<Namespace> namespace);

    /**
     * Indicates whether this selector should consider namespaces when evaluating
     * expressions.
     *
     * @return {@code true} if namespace-aware, {@code false} otherwise
     */
    public boolean isNamespaceAware();

    /**
     * Sets whether this selector should be namespace-aware.
     *
     * @param namespaceAware {@code true} to enable namespace awareness;
     *                       {@code false} otherwise
     */
    public void setNamespaceAware(boolean namespaceAware);

    /**
     * Returns the expression used by this selector, which may be xpath or
     * json-path.
     *
     * @return the {@code Expression} object
     */
    public Expression getExpression();

    /**
     * Sets the expression used by this selector.
     *
     * @param expression the {@code Expression} object to use
     */
    public void setExpression(Expression expression);
}
