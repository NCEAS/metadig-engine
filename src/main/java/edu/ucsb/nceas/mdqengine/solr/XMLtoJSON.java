/**
 * This work was created by participants in the DataONE project, and is
 * jointly copyrighted by participating institutions in DataONE. For
 * more information on DataONE, see our web site at http://dataone.org.
 *
 *   Copyright ${year}
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 * $Id$
 */

package edu.ucsb.nceas.mdqengine.solr;


import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.dataone.cn.indexer.parser.ISolrField;
import org.dataone.cn.indexer.solrhttp.SolrElementField;
import org.json.JSONObject;
import org.json.XML;
import org.json.JSONException;
import org.w3c.dom.Document;

import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import javax.xml.xpath.XPath;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.List;

/**
 * ISolrField implementation that stores the entire input XML document as a JSON string value.
 *
 * @author slaughter
 *
 */
public class XMLtoJSON implements ISolrField {

    private String name = "full_report";
    private List<ISolrField> fieldList = new ArrayList<ISolrField>();

    @Override
    public void initExpression(XPath xpathObject) {
    }

    @Override
    public List<SolrElementField> getFields(Document doc, String identifier) throws Exception {
        List<SolrElementField> fields = new ArrayList<SolrElementField>();
        String xmlString = null;
        int PRETTY_PRINT_INDENT_FACTOR = 4;
        String jsonString = null;
        Log log = LogFactory.getLog(XMLtoJSON.class);

        // Convert the document that is being indexed to an XML string, then to a JSON string.
        // The field is stored as JSON so that it will be easy to parse by clients.
        try {
            DOMSource domSource = new DOMSource(doc);
            StringWriter writer = new StringWriter();
            StreamResult result = new StreamResult(writer);
            TransformerFactory tf = TransformerFactory.newInstance();
            Transformer transformer = tf.newTransformer();
            transformer.transform(domSource, result);
            xmlString = writer.toString();

            JSONObject xmlJSONObj = XML.toJSONObject(xmlString);
            jsonString = xmlJSONObj.toString(PRETTY_PRINT_INDENT_FACTOR);
        } catch(TransformerException ex) {
            ex.printStackTrace();
            log.error("Error processing field " + this.name + ": " + ex.getMessage());
            return fields;
        } catch (JSONException je) {
            log.error("Error processing field " + this.name + ": " + je.getMessage());
            return fields;
        }

        fields.add(new SolrElementField(this.name, jsonString));
        return fields;
    }

    public String getName() {
        return name;
    }

    /**
     * The name of the search index field this SolrField instance is generating.
     *
     * @param name
     */
    public void setName(String name) {
        this.name = name;
    }
}
