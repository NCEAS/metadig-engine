package edu.ucsb.nceas.mdqengine.model;

import javax.xml.bind.annotation.*;

@XmlRootElement(name="jsonpath")
@XmlAccessorType(XmlAccessType.FIELD)
public class JSONpath{

    @XmlValue()
    private String jsonpath;

    /**
     * Specifies the pattern to match for a dialect.
     * <p>
     *     The JSONpath component used to extract information from a JSON document does not provide
     *     the ability to test the values extracted and return a boolean value if a match is found.
     *     Therefore, in order to determine the dialect of a JSON document, the required string is
     *     extracted, then compared to the 'match' attribute, which is treated as a regular expression.
     *     This attribute is only used by 'jsonpath' dialects.
     * </p>
     */
    @XmlAttribute(required = false)
    private String match;

    public String getJsonpath() {
        return jsonpath;
    }

    public void setJsonpath(String jsonpath) {
        this.jsonpath = jsonpath;
    }

    public String getMatch() {
        return match;
    }

    public void setMatch(String match) {
        this.match = match;
    }
}
