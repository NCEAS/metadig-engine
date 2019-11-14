package edu.ucsb.nceas.mdqengine.solr;

/**
 *  Copyright: 2013 Regents of the University of California and the
 *             National Center for Ecological Analysis and Synthesis
 *
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 2 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA
 */

import org.apache.commons.codec.EncoderException;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.solr.client.solrj.SolrClient;
import org.apache.solr.client.solrj.SolrQuery;
import org.apache.solr.client.solrj.SolrServerException;
import org.apache.solr.client.solrj.request.UpdateRequest;
import org.apache.solr.client.solrj.response.QueryResponse;
import org.apache.solr.client.solrj.response.UpdateResponse;
import org.apache.solr.common.SolrDocument;
import org.apache.solr.common.SolrDocumentList;
import org.apache.solr.common.SolrInputDocument;
import org.dataone.cn.indexer.XMLNamespaceConfig;
import org.dataone.cn.indexer.parser.BaseXPathDocumentSubprocessor;
import org.dataone.cn.indexer.parser.IDocumentSubprocessor;
import org.dataone.cn.indexer.parser.SolrField;
import org.dataone.cn.indexer.solrhttp.SolrDoc;
import org.dataone.cn.indexer.solrhttp.SolrElementField;
import org.dataone.exceptions.MarshallingException;
import org.dataone.service.exceptions.NotFound;
import org.dataone.service.exceptions.NotImplemented;
import org.dataone.service.exceptions.UnsupportedType;
import org.dataone.service.types.v1.Identifier;
import org.dataone.service.types.v2.SystemMetadata;
import org.dataone.service.util.TypeMarshaller;
import org.w3c.dom.Document;
import org.xml.sax.SAXException;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathExpressionException;
import javax.xml.xpath.XPathFactory;
import java.io.*;
import java.util.*;

/**
 * A class that performs inserts to a SOLR server
 * @author tao
 * @author slaughter
 *
 */
public class SolrIndex {

    public static final String ID = "id";
    private static final String IDQUERY = ID+":*";
    private List<IDocumentSubprocessor> subprocessors = null;

    private SolrClient solrClient = null;
    private XMLNamespaceConfig xmlNamespaceConfig = null;
    private List<SolrField> sysmetaSolrFields = null;

    private static DocumentBuilderFactory documentBuilderFactory = null;
    private static DocumentBuilder builder = null;

    private static XPathFactory xpathFactory = null;
    private static XPath xpath = null;
    Log log = LogFactory.getLog(SolrIndex.class);

    static {
        documentBuilderFactory = DocumentBuilderFactory.newInstance();
        documentBuilderFactory.setNamespaceAware(true);
        try {
            builder = documentBuilderFactory.newDocumentBuilder();
        } catch (ParserConfigurationException e) {
            e.printStackTrace();
        }
        xpathFactory = XPathFactory.newInstance();
        xpath = xpathFactory.newXPath();
    }

    /**
     * Constructor
     * @throws SAXException
     * @throws IOException
     */
    public SolrIndex(XMLNamespaceConfig xmlNamespaceConfig, List<SolrField> sysmetaSolrFields)
            throws XPathExpressionException, ParserConfigurationException, IOException, SAXException {
        this.xmlNamespaceConfig = xmlNamespaceConfig;
        this.sysmetaSolrFields = sysmetaSolrFields;
        init();
    }

    private void init() throws ParserConfigurationException, XPathExpressionException {
        xpath.setNamespaceContext(xmlNamespaceConfig);
        initExpressions();
    }

    private void initExpressions() throws XPathExpressionException {
        for (SolrField field : sysmetaSolrFields) {
            field.initExpression(xpath);
        }
    }

    /**
     * Get the list of the Subprocessors in this index.
     * @return the list of the Subprocessors.
     */
    public List<IDocumentSubprocessor> getSubprocessors() {
        return subprocessors;
    }

    /**
     * Set the list of Subprocessors.
     * @param subprocessorList  the list will be set.
     */
    public void setSubprocessors(List<IDocumentSubprocessor> subprocessorList) {
        for (IDocumentSubprocessor subprocessor : subprocessorList) {
            if (subprocessor instanceof BaseXPathDocumentSubprocessor) {
                ((BaseXPathDocumentSubprocessor)subprocessor).initExpression(xpath);
            }
        }
        this.subprocessors = subprocessorList;
    }

    /**
     * Generate the index for the given information
     * @param id
     * @param systemMetadata
     * @param objectPath
     * @return
     * @throws IOException
     * @throws SAXException
     * @throws ParserConfigurationException
     * @throws XPathExpressionException
     * @throws MarshallingException
     * @throws SolrServerException
     * @throws EncoderException
     * @throws UnsupportedType
     * @throws NotFound
     * @throws NotImplemented
     */
    private Map<String, SolrDoc> process(String id, SystemMetadata systemMetadata, String objectPath)
            throws IOException, SAXException, MarshallingException, SolrServerException {
        // Load the System Metadata document
        ByteArrayOutputStream systemMetadataOutputStream = new ByteArrayOutputStream();
        TypeMarshaller.marshalTypeToOutputStream(systemMetadata, systemMetadataOutputStream);
        ByteArrayInputStream systemMetadataStream = new ByteArrayInputStream(systemMetadataOutputStream.toByteArray());
        Document sysMetaDoc = generateXmlDocument(systemMetadataStream);
        if (sysMetaDoc == null) {
            log.error("Could not load System metadata for ID: " + id);
            return null;
        }

        // Extract the field values from the System Metadata
        List<SolrElementField> sysSolrFields = processSysmetaFields(sysMetaDoc, id);
        SolrDoc indexDocument = new SolrDoc(sysSolrFields);
        Map<String, SolrDoc> docs = new HashMap<String, SolrDoc>();
        docs.put(id, indexDocument);

        // get the format id for this object
        String formatId = indexDocument.getFirstFieldValue(SolrElementField.FIELD_OBJECTFORMAT);
        // Determine if subprocessors are available for this ID
        if (subprocessors != null) {
            // for each subprocessor loaded from the spring config
            for (IDocumentSubprocessor subprocessor : subprocessors) {
                // Does this subprocessor apply?
                if (subprocessor.canProcess(formatId)) {
                    log.debug("SolrIndex.process - using subprocessor "+ subprocessor.getClass().getName());
                    // if so, then extract the additional information from the
                    // document.
                    try {
                        FileInputStream dataStream = new FileInputStream(objectPath);
                        if (!dataStream.getFD().valid()) {
                            log.error("SolrIndex.process - subprocessor "+ subprocessor.getClass().getName() +" couldn't process since it could not load OBJECT file for ID,Path=" + id + ", "
                                    + objectPath);
                        } else {
                            log.debug("SolrIndex.process - subprocessor "+ subprocessor.getClass().getName() +" generating solr doc for id "+id);
                            docs = subprocessor.processDocument(id, docs, dataStream);
                            log.debug("SolrIndex.process - subprocessor "+ subprocessor.getClass().getName() +" generated solr doc for id "+id);
                        }
                    } catch (Exception e) {
                        e.printStackTrace();
                        log.error(e.getMessage(), e);
                        throw new SolrServerException(e.getMessage());
                    }
                }
            }
        } else {
            log.debug("Subproccor list is null");
        }

        return docs;
    }

    /*
     * Generate a Document from the InputStream
     */
    private Document generateXmlDocument(InputStream smdStream) throws SAXException {
        Document doc = null;

        try {
            doc = builder.parse(smdStream);
        } catch (IOException e) {
            log.error(e.getMessage(), e);
        }

        return doc;
    }

    /*
     * Index the fields of the system metadata
     */
    private List<SolrElementField> processSysmetaFields(Document doc, String identifier) {

        List<SolrElementField> fieldList = new ArrayList<SolrElementField>();
        // solrFields is the list of fields defined in the application context

        for (SolrField field : sysmetaSolrFields) {
            try {
                // the field.getFields method can return a single value or
                // multiple values for multi-valued fields
                // or can return multiple SOLR document fields.
                fieldList.addAll(field.getFields(doc, identifier));
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        return fieldList;
    }

    /**
     * Check the parameters of the insert or update methods.
     * @param pid
     * @param systemMetadata
     * @param objectPath
     * @throws SolrServerException
     */
    private void checkParams(Identifier pid, SystemMetadata systemMetadata, String objectPath) throws SolrServerException {
        if(pid == null || pid.getValue() == null || pid.getValue().trim().equals("")) {
            throw new SolrServerException("The identifier of the indexed document should not be null or blank.");
        }
        if(systemMetadata == null) {
            throw new SolrServerException("The system metadata of the indexed document "+pid.getValue()+ " should not be null.");
        }
        if(objectPath == null) {
            throw new SolrServerException("The indexed document itself for pid "+pid.getValue()+" should not be null.");
        }
    }

    /**
     * Insert the indexes for a document.
     * @param pid  the id of this document
     * @param systemMetadata  the system metadata associated with the data object
     * @param objectPath the path to the object file itself
     * @throws SolrServerException
     * @throws MarshallingException
     * @throws EncoderException
     * @throws UnsupportedType
     * @throws NotFound
     * @throws NotImplemented
     */
    public synchronized void insert(Identifier pid, SystemMetadata systemMetadata, String objectPath)
            throws IOException, SAXException, ParserConfigurationException,
            XPathExpressionException, SolrServerException, MarshallingException, EncoderException, NotImplemented, NotFound, UnsupportedType {
        log.trace("Identifier: " + pid.getValue());
        log.trace("sysmeta pid" + systemMetadata.getIdentifier().getValue());
        log.trace("objectPath: " + objectPath);

        checkParams(pid, systemMetadata, objectPath);
        Map<String, SolrDoc> docs = process(pid.getValue(), systemMetadata, objectPath);

        //transform the Map to the SolrInputDocument which can be used by the solr server
        if(docs != null) {
            Set<String> ids = docs.keySet();
            for(String id : ids) {
                if(id != null) {
                    SolrDoc doc = docs.get(id);
                    insertToIndex(doc);
                    log.debug("SolrIndex.insert - inserted the solr document object for pid "+id+", which relates to object "+pid.getValue()+", into the solr server.");
                }
            }
            log.debug("SolrIndex.insert - finished to insert the solrDoc for object "+pid.getValue());
        } else {
            log.debug("SolrIndex.insert - the generated solrDoc is null. So we will not index the object "+pid.getValue());
        }
    }

    /*
     * Insert a SolrDoc to the solr server.
     */
    private synchronized void insertToIndex(SolrDoc doc) throws SolrServerException, IOException {
        if(doc != null ) {
            SolrInputDocument solrDoc = new SolrInputDocument();
            List<SolrElementField> list = doc.getFieldList();
            if(list != null) {
                Iterator<SolrElementField> iterator = list.iterator();
                while (iterator.hasNext()) {
                    SolrElementField field = iterator.next();
                    if(field != null) {
                        String value = field.getValue();
                        String name = field.getName();
                        solrDoc.addField(name, value);
                    }
                }
            }
            if(!solrDoc.isEmpty()) {
                try {
                    UpdateResponse response = solrClient.add(solrDoc);
                    solrClient.commit();
                } catch (SolrServerException e) {
                    throw e;
                } catch (IOException e) {
                    throw e;
                }
            }
        }
    }

    /**
     * Get the Solr client instance
     * @return
     */
    public SolrClient getSolrServer() {
        return solrClient;
    }

    /**
     * Set the Solr client.
     * @param solrClient
     */
    public void setSolrClient(SolrClient solrClient) {
        this.solrClient = solrClient;
    }

    /**
     * Get all indexed ids in the solr server.
     * @return an empty list if there is no index.
     * @throws SolrServerException
     */
    public List<String> getSolrIds() throws SolrServerException, IOException {
        List<String> list = new ArrayList<String>();
        SolrQuery query = new SolrQuery(IDQUERY);
        query.setRows(Integer.MAX_VALUE);
        query.setFields(ID);
        QueryResponse response = solrClient.query(query);
        SolrDocumentList docs = response.getResults();
        if(docs != null) {
            for(SolrDocument doc :docs) {
                String identifier = (String)doc.getFieldValue(ID);
                list.add(identifier);
            }
        }
        return list;
    }

    /*
     * Update a Solr document with a new value for the specified field.
     */
    public synchronized void update(String metadataId, String suiteId, HashMap<String, Object> fields, String updateFieldModifier) throws SolrServerException, IOException {

        log.debug("Updating entry in Solr index...");
        SolrQuery query = new SolrQuery("metadataId:" + '"' +  metadataId + '"' + "+suiteId:" + suiteId);
        query.setRows(1);
        QueryResponse response = solrClient.query(query);
        SolrDocumentList docs = response.getResults();

        // Use the default field modifier type, if not specified in the arg list.
        if (updateFieldModifier == null) updateFieldModifier = "set";

        String runId = null;
        SolrDocument resultDoc = null;
        if(docs != null) {
            log.info("Found entry for metadataId: " + metadataId + ", suiteId: " + suiteId + ", updating...");
            resultDoc = docs.get(0);
            runId = (String)resultDoc.getFieldValue("runId");
            log.info("RunId: " + runId);
            SolrInputDocument solrDoc = new SolrInputDocument();

            // The Atomic update capability with Solr doesn't appear to work with our index or I'm using it
            // incorrectly - so we have to update the entire document by exporting the result document to
            // a new document, with any updated fields that have been passed in. Because this method uses
            // the '_version_' field from the result document for the new one, the Solr 'optimistic concurrency'
            // mechanism should be enabled, preventing this update from updating another update to this record that
            // came before us.
            for (String n : resultDoc.getFieldNames()) {
                // Don't add fields twice, i.e. from updated fields and from existing Solr doc
                if(fields.containsKey(n)) continue;
                log.info("Adding field: " + n);
                solrDoc.addField(n, resultDoc.getFieldValue(n));
            }

            // Add requested fields with updated values
            for (Map.Entry<String, Object> entry : fields.entrySet()) {
                log.info("Adding field: " + entry.getKey());
                solrDoc.addField(entry.getKey(), entry.getValue());
            }

            // Atomic update strategy from http://yonik.com/solr/atomic-updates/
            // Atomic updates are enabled by using the 'field modifier' for the fields to be updated.
//            solrDoc.setField("runId", runId);
//            solrDoc.setField("_version_", String.valueOf(resultDoc.getFieldValue("_version_")));
//            for (Map.Entry<String, Object> entry : fields.entrySet()) {
//                //Map<String, String> fieldModifier = new HashMap<>(1);
//                Map<String, Object> fieldModifier = new HashMap<>(1);
//                // If the 'result' (existing) doc already contains the item, then use 'set' for the Solr field modifier
//                // If not, then use 'add'.
//                if (resultDoc.containsKey(entry.getKey())) {
//                    log.info("Setting field: " + entry.getKey() + ", value: " + entry.getValue());
//                    fieldModifier.put("set", entry.getValue());
//                } else {
//                    log.info("Adding field: " + entry.getKey() + ", value: " + entry.getValue());
//                    //fieldModifier.put(updateFieldModifier, entry.getValue());
//                    fieldModifier.put("add", entry.getValue());
//                }
//                solrDoc.addField(entry.getKey(), fieldModifier);
//            }

            UpdateRequest updateRequest = new UpdateRequest();
            updateRequest.setAction( UpdateRequest.ACTION.COMMIT, false, false);

            try {
                updateRequest.add(solrDoc);
                UpdateResponse rsp = updateRequest.process(solrClient);
                //solrClient.commit();
            } catch (SolrServerException e) {
                log.error("Unable to update Solr document for metadataId: " + metadataId + ": " + e.getMessage());
                throw e;
            } catch (IOException ioe) {
                log.error("IO Error during update of SOlr document for metadataId, : " + metadataId + ": " + ioe.getMessage());
                throw ioe;
            }
            log.info("Updated entry for metadataId: " + metadataId + ", suiteId: " + suiteId);
        } else {
            log.error("Did not find entry for metadataId: " + metadataId + ", suiteId: " + suiteId + ", unable to update.");
        }
    }

}