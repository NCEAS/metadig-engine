package edu.ucsb.nceas.mdqengine.model;

import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlValue;

/**
 * The expression field contains some kind of expression that enables extraction
 * of a particular piece of metadata from the overall document. This more
 * general expression optionally replaces the xpath element. It contains a
 * syntax attribute which gives the syntax of the expression (default is xpath,
 * could also be json-path if looking in json documents as opposed to xml).
 */
public class Expression {

    @XmlAttribute(name = "syntax")
    private String syntax = "xpath"; // default if not specified

    @XmlValue
    private String value;

    /**
     * Returns the syntax used to interpret the expression.
     *
     * This value is mapped from the syntax XML attribute.
     * If not explicitly set, it defaults to "xpath".
     *
     * @return the syntax string (e.g., "xpath", "json-path", etc.)
     */
    public String getSyntax() {
        return syntax;
    }

    /**
     * Sets the syntax used to interpret the expression.
     *
     * This value corresponds to the {@code syntax} XML attribute.
     *
     * @param syntax the syntax string to set (e.g., "xpath", "json-path".)
     */
    public void setSyntax(String syntax) {
        this.syntax = syntax;
    }

    /**
     * Returns the expression value.
     *
     * This is the text content of the XML element.
     *
     * @return the expression string
     */
    public String getValue() {
        return value;
    }

    /**
     * Sets the expression value.
     *
     * This sets the text content of the XML element.
     *
     * @param value the expression string to set
     */
    public void setValue(String value) {
        this.value = value;
    }

}
