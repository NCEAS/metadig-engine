package edu.ucsb.nceas.mdqengine.model;

import javax.xml.bind.annotation.adapters.XmlAdapter;

public class CDataAdapter extends XmlAdapter<String, String> {

    // /**
    //  * @param ch The array of characters.
    //  * @param start The starting position.
    //  * @param length The number of characters to use.
    //  * @param isAttVal true if this is an attribute value literal.
    //  */
    //public void escape(char[] ch, int start, int length, boolean isAttVal, Writer writer) throws IOException {
    //    writer.write( ch, start, length );
    //}

    @Override
    public String marshal(String str) throws Exception {
        str = "<![CDATA[" + str + "]]>";
        return str;
    }

    @Override
    public String unmarshal(String str) throws Exception {
        /* Remove "<![CDATA[", "]]>" string from an XML value */
        str = str.replaceAll("\\<!\\[CDATA\\[", "");
        str = str.replaceAll("\\]\\]\\>", "");
        return str;
    }
}
