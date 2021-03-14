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
import org.dataone.service.types.v2.SystemMetadata;
import org.dataone.service.util.TypeMarshaller;
import org.python.core.buffer.Strided1DBuffer;
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
    private static final String SOLR_COLLECTION = "quality";
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

        log.debug("Subprocessor returning " + docs.size() + " docs");
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
    private void checkParams(String pid, SystemMetadata systemMetadata, String objectPath) throws SolrServerException {
        if(pid == null || pid == null || pid.trim().equals("")) {
            throw new SolrServerException("The identifier of the indexed document should not be null or blank.");
        }
        if(systemMetadata == null) {
            throw new SolrServerException("The system metadata of the indexed document "+pid+ " should not be null.");
        }
        if(objectPath == null) {
            throw new SolrServerException("The indexed document itself for pid "+pid+" should not be null.");
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
    public synchronized void insert(String pid, SystemMetadata systemMetadata, String objectPath)
            throws IOException, SAXException, ParserConfigurationException,
            XPathExpressionException, SolrServerException, MarshallingException, EncoderException, NotImplemented, NotFound, UnsupportedType {
        log.trace("Identifier: " + pid);
        log.trace("sysmeta pid" + systemMetadata.getIdentifier().getValue());
        log.trace("objectPath: " + objectPath);

        checkParams(pid, systemMetadata, objectPath);
        Map<String, SolrDoc> docs = process(pid, systemMetadata, objectPath);

        //transform the Map to the SolrInputDocument which can be used by the solr server
        if(docs != null) {
            Set<String> ids = docs.keySet();
            for(String id : ids) {
                if(id != null) {
                    SolrDoc doc = docs.get(id);
                    insertToIndex(doc);
                    log.debug("SolrIndex.insert - inserted the solr document object for pid "+id+", which relates to object "+pid+", into the solr server.");
                }
            }
            log.debug("SolrIndex.insert - finished to insert the solrDoc for object "+pid);
        } else {
            log.debug("SolrIndex.insert - the generated solrDoc is null. So we will not index the object "+pid);
        }
    }

    /*
     * Insert a SolrDoc to the solr server.
     */
    private synchronized void insertToIndex(SolrDoc doc) throws SolrServerException, IOException {
        log.debug("insertToIndex");
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
                    log.debug("Updating collection: " + SOLR_COLLECTION);
                    UpdateResponse response = solrClient.add(SOLR_COLLECTION, solrDoc);
                    solrClient.commit(SOLR_COLLECTION);
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
        QueryResponse response = solrClient.query(SOLR_COLLECTION, query);
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
     * Update a Solr document with new values for the specified field.
     */
    public synchronized void update(String metadataId, String suiteId, HashMap<String, Object> fields, String updateFieldModifier) throws SolrServerException, IOException {

        SolrQuery query = new SolrQuery("metadataId:" + '"' +  metadataId + '"' + "+suiteId:" + suiteId);
        query.setRows(1);
        QueryResponse response = solrClient.query(SOLR_COLLECTION, query);

        SolrDocumentList docs = response.getResults();

        // Use the default field modifier type, if not specified in the arg list.
        if (updateFieldModifier == null) updateFieldModifier = "set";

        String runId = null;
        SolrDocument resultDoc = null;
        if(docs != null) {
            log.info("Updating Solr index entry for metadataId: " + metadataId + ", suiteId: " + suiteId + ", updating...");
            resultDoc = docs.get(0);
            SolrInputDocument solrDoc = new SolrInputDocument();

            // Use the 'optimistic' concurrency update Solr update method, where the '_version_' field
            // from the retrieved document must match the document in the index. If it doesn't then this
            // indicates that the record has been updated by someone else.
            // We are amending this feature here, by not sending the update if all the fields in the retrieved
            // document have the same value as the fields in the list to update (no update necessary, another
            // worker did it for us). This makes sense especially for the 'sequenceId', as this will always be
            // set to the first pid in the chain, so all workers updating a chain are trying to update with the
            // same value.
            // Note: the unique key 'runId' will be added here
            // Also note: the "_version_" field will be added here, but needs to be added as a 'parameter' below
            // in order to enable optimistic concurrency
            for (String n : resultDoc.getFieldNames()) {
                // Don't add fields twice, i.e. from updated filds and from existing Solr doc
                if(fields.containsKey(n)) continue;
                log.debug("Adding existing field: " + n);
                solrDoc.addField(n, resultDoc.getFieldValue(n));
            }

            // Now check if all the fields to be updated already have the desired value. If yes, then exit.

            Boolean updateNeeded = false;
            String str1 = null;
            String str2 = null;
            // Add requested fields with updated values
            for (Map.Entry<String, Object> entry : fields.entrySet()) {
                // Check if all the fields to be updated already have the desired value. If yes, then exit.
                // Don't check if we have already determined that the update is needed.
                if(!updateNeeded) {
                    if(!resultDoc.containsKey(entry.getKey())) {
                        updateNeeded = true;
                    } else {
                        str1 = resultDoc.getFieldValue(entry.getKey()).toString();
                        str2 = entry.getValue().toString();
                        if(str1.compareTo(str2) != 0) {
                            updateNeeded = true;
                        }
                    }
                }
            }

            if(updateNeeded) {
                // Set the fields to be updated
                for (Map.Entry<String, Object> entry : fields.entrySet()) {
                    log.debug("Setting field: " + entry.getKey());
                    solrDoc.setField(entry.getKey(), entry.getValue());
                }
            } else {
                log.debug("Update not needed, fields already updated for metadataId: " + metadataId + ", suiteId: " + suiteId + ", ");
                return;
            }

            UpdateRequest updateRequest = new UpdateRequest();
            updateRequest.setAction(UpdateRequest.ACTION.COMMIT, false, false);

            UpdateResponse rsp = null;
            try {
                log.debug("Processing update request to collection: " + SOLR_COLLECTION);
                updateRequest.add(solrDoc);
                //updateRequest.add(resultDoc);
                String version = resultDoc.getFieldValue("_version_").toString();
                // Enable 'optimistic concurrency' update
                updateRequest.setParam("version", version);
                updateRequest.setParam("collection", SOLR_COLLECTION);
                //UpdateResponse rsp = updateRequest.process(solrClient);
                log.debug("* Commiting updating to Solr doc with version: " + version);
                rsp = updateRequest.commit(solrClient, SOLR_COLLECTION);
                log.debug("Update response: " + rsp.getStatus());
                //solrClient.commit();
            } catch (SolrServerException e) {
                log.error("Unable to update Solr document for metadataId: " + metadataId + ": " + e.getMessage());
                if(rsp != null) log.error("Update response: " + rsp.getStatus());
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