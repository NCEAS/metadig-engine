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
import org.apache.solr.client.solrj.response.QueryResponse;
import org.apache.solr.client.solrj.response.UpdateResponse;
import org.apache.solr.common.SolrDocument;
import org.apache.solr.common.SolrDocumentList;
import org.apache.solr.common.SolrInputDocument;
import org.dataone.cn.indexer.XMLNamespaceConfig;
import org.dataone.cn.indexer.parser.*;
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
 * A class does insert, update and remove indexes to a SOLR server
 * @author tao
 *
 */
public class SolrIndex {

    public static final String ID = "id";
    private static final String IDQUERY = ID+":*";
    private List<IDocumentSubprocessor> subprocessors = null;
    //private List<IDocumentDeleteSubprocessor> deleteSubprocessors = null;

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

    //public List<IDocumentDeleteSubprocessor> getDeleteSubprocessors() {
    //    return deleteSubprocessors;
    //}

    //public void setDeleteSubprocessors(
    //        List<IDocumentDeleteSubprocessor> deleteSubprocessors) {
    //    this.deleteSubprocessors = deleteSubprocessors;
    //}

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
        log.info("SolrIndex.process - trying to generate the solr doc object for the pid "+id);
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
        log.info("SolrIndex.process - the object format id for the pid "+id+" is "+formatId);
        // Determine if subprocessors are available for this ID
        if (subprocessors != null) {
            // for each subprocessor loaded from the spring config
            for (IDocumentSubprocessor subprocessor : subprocessors) {
                // Does this subprocessor apply?
                log.info("SolrIndex.process - trying subprocessor "+ subprocessor.getClass().getName());
                //List<String> slist = subprocessor.getMatchDocuments();
                //log.info(Arrays.toString(slist.toArray()));
                if (subprocessor.canProcess(formatId)) {
                    log.info("SolrIndex.process - using subprocessor "+ subprocessor.getClass().getName());
                    // if so, then extract the additional information from the
                    // document.
                    try {
                        // docObject = the resource map document or science
                        // metadata document.
                        // note that resource map processing touches all objects
                        // referenced by the resource map.
                        FileInputStream dataStream = new FileInputStream(objectPath);
                        if (!dataStream.getFD().valid()) {
                            log.error("SolrIndex.process - subprocessor "+ subprocessor.getClass().getName() +" couldn't process since it could not load OBJECT file for ID,Path=" + id + ", "
                                    + objectPath);
                            //throw new Exception("Could not load OBJECT for ID " + id );
                        } else {
                            log.info("SolrIndex.process - subprocessor "+ subprocessor.getClass().getName() +" generating solr doc for id "+id);
                            docs = subprocessor.processDocument(id, docs, dataStream);
                            log.info("SolrIndex.process - subprocessor "+ subprocessor.getClass().getName() +" generated solr doc for id "+id);
                        }
                    } catch (Exception e) {
                        e.printStackTrace();
                        log.error(e.getMessage(), e);
                        throw new SolrServerException(e.getMessage());
                    }
                }
            }
        } else {
            log.info("Subproccor list is null");
        }

        return docs;
    }

//    /*
//     * If the given field name is a system metadata field.
//     */
//    private boolean isSystemMetadataField(String fieldName) {
//        boolean is = false;
//        if (fieldName != null && !fieldName.trim().equals("") && sysmetaSolrFields != null) {
//            for(SolrField field : sysmetaSolrFields) {
//                if(field !=  null && field.getName() != null && field.getName().equals(fieldName)) {
//                    log.debug("SolrIndex.isSystemMetadataField - the field name "+fieldName+" matches one record of system metadata field list. It is a system metadata field.");
//                    is = true;
//                    break;
//                }
//            }
//        }
//        return is;
//    }
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
        log.info("Insert: checking params");
        log.info("Identifier: " + pid.getValue());
        log.info("sysmeta pid" + systemMetadata.getIdentifier().getValue());
        log.info("objectPath: " + objectPath);

        checkParams(pid, systemMetadata, objectPath);
        log.info("SolrIndex.insert - trying to insert the solrDoc for object "+pid.getValue());
        Map<String, SolrDoc> docs = process(pid.getValue(), systemMetadata, objectPath);

        //transform the Map to the SolrInputDocument which can be used by the solr server
        if(docs != null) {
            Set<String> ids = docs.keySet();
            for(String id : ids) {
                if(id != null) {
                    SolrDoc doc = docs.get(id);
                    insertToIndex(doc);
                    log.debug("SolrIndex.insert - inserted the solr-doc object of pid "+id+", which relates to object "+pid.getValue()+", into the solr server.");
                }
            }
            log.debug("SolrIndex.insert - finished to insert the solrDoc for object "+pid.getValue());
        } else {
            log.debug("SolrIndex.insert - the genered solrDoc is null. So we will not index the object "+pid.getValue());
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
                //solrDoc.addField(METACATPIDFIELD, pid);
                Iterator<SolrElementField> iterator = list.iterator();
                while (iterator.hasNext()) {
                    SolrElementField field = iterator.next();
                    if(field != null) {
                        String value = field.getValue();
                        String name = field.getName();
                        log.info("SolrIndex.insertToIndex - add name/value pair - "+name+"/"+value);
                        solrDoc.addField(name, value);
                    }
                }
            }
            if(!solrDoc.isEmpty()) {
                try {
                    //UpdateResponse response = solrClient.add("quality", solrDoc);
                    UpdateResponse response = solrClient.add(solrDoc);
                    solrClient.commit();
                } catch (SolrServerException e) {
                    throw e;
                } catch (IOException e) {
                    throw e;

                }
                //System.out.println("=================the response is:\n"+response.toString());
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
                //System.out.println("======================== "+identifier);
                list.add(identifier);
            }
        }
        return list;
    }
}