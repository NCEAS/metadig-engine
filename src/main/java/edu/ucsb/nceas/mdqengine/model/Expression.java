package edu.ucsb.nceas.mdqengine.model;

import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlValue;

/**
 * The more general expression optionally replaces xpath, with an attribute
 * syntax giving the syntax of the expression (default is xpath, could also be
 * json-path).
 */
public class Expression {

    @XmlAttribute(name = "syntax")
    private String syntax = "xpath"; // default if not specified

    @XmlValue
    private String value;

    public String getSyntax() {
        return syntax;
    }

    public void setSyntax(String syntax) {
        this.syntax = syntax;
    }

    public String getValue() {
        return value;
    }

    public void setValue(String value) {
        this.value = value;
    }
}
