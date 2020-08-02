package edu.ucsb.nceas.mdqengine;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import edu.ucsb.nceas.mdqengine.exception.MetadigProcessException;
import org.dataone.client.auth.AuthTokenSession;
import org.dataone.client.rest.MultipartRestClient;
import org.dataone.client.v2.impl.MultipartD1Node;
import org.dataone.service.types.v1.Session;
import edu.ucsb.nceas.mdqengine.exception.MetadigException;
import org.dataone.client.rest.DefaultHttpMultipartRestClient;
import org.dataone.client.v2.impl.MultipartCNode;
import org.dataone.client.v2.impl.MultipartMNode;
import org.dataone.service.types.v1.Subject;
import org.dataone.service.types.v1.SubjectInfo;
import org.w3c.dom.Document;
import org.xml.sax.InputSource;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class DataONE {


    private static Log log = LogFactory.getLog(DataONE.class);

    /**
     * Get a DataONE subject information object
     * @param serviceUrl the service URL of the DataONE node to request the subject info from
     * @param authToken the authorization token to use for the request
     * @return a DataONE subject information object
     * @throws MetadigProcessException
     */
    public static SubjectInfo getSubjectInfo(Subject rightsHolder, String serviceUrl, String subjectId, String authToken) throws MetadigProcessException {

        log.debug("Getting subject info for: " + rightsHolder.getValue());
        MultipartCNode cnNode = null;
        MetadigProcessException metadigException = null;

        SubjectInfo subjectInfo = null;
        Session session = DataONE.getSession(subjectId, authToken);

        // Identity node as either a CN or MN based on the serviceUrl
        String pattern = "https*://cn.*?\\.dataone\\.org|https*://cn.*?\\.test\\.dataone\\.org";
        Pattern r = Pattern.compile(pattern);
        Matcher m = r.matcher(serviceUrl);
        if (!m.find()) {
            log.error("Must call a CN to get subject information");
            metadigException = new MetadigProcessException("Must call a CN to get subject information.");
            throw metadigException;
        }

        // Only CNs can call the 'subjectInfo' service (aka accounts), so we have to use
        // a MultipartCNode instance here.
        try {
            cnNode = (MultipartCNode) getMultipartD1Node(session, serviceUrl);
        } catch (Exception ex) {
            metadigException = new MetadigProcessException("Unable to create multipart D1 node: " + subjectId + ": " + ex.getMessage());
            metadigException.initCause(ex);
            throw metadigException;
        }

        try {
            subjectInfo = cnNode.getSubjectInfo(session, rightsHolder);
        } catch (Exception ex) {
            metadigException = new MetadigProcessException("Unable to get subject information." + ex.getMessage());
            metadigException.initCause(ex);
            throw metadigException;
        }

        return subjectInfo;
    }

    /**
     * Get a DataONE MultipartCNode object, which will be used to communication with a CN
     *
     * @param session a DataONE authentication session
     * @param serviceUrl the service URL for the node we are connecting to
     * @return a DataONE MultipartCNode object
     * @throws MetadigException
     */
    public static MultipartD1Node getMultipartD1Node(Session session, String serviceUrl) throws MetadigException {

        MultipartRestClient mrc = null;
        MultipartD1Node d1Node = null;
        MetadigProcessException metadigException = null;

        // First create an HTTP client
        try {
            mrc = new DefaultHttpMultipartRestClient();
        } catch (Exception ex) {
            log.error("Error creating rest client: " + ex.getMessage());
            metadigException = new MetadigProcessException("Unable to get collection pids");
            metadigException.initCause(ex);
            throw metadigException;
        }

        Boolean isCN = isCN(serviceUrl);

        // Now create a DataONE object that uses the rest client
        if (isCN) {
            log.debug("creating cn MultipartMNode" + ", subjectId: " + session.getSubject().getValue());
            d1Node = new MultipartCNode(mrc, serviceUrl, session);
        } else {
            log.debug("creating mn MultipartMNode" + ", subjectId: " + session.getSubject().getValue());
            d1Node = new MultipartMNode(mrc, serviceUrl, session);
        }
        return d1Node;
    }

    /**
     * Send a query to the DataONE Query Service , using the DataONE CN or MN API
     *
     * @param queryStr the query string to pass to the Solr server
     * @param startPos the start of the query result to return, if query pagination is being used
     * @param countRequested the number of results to return
     * @return an XML document containing the query result
     * @throws Exception
     */
    //public static Document querySolr(String queryStr, int startPos, int countRequested, MultipartCNode cnNode,
    //                                 MultipartMNode mnNode, Boolean isCN,
    //                                 Session session) throws MetadigProcessException {
    public static Document querySolr(String queryStr, int startPos, int countRequested, MultipartD1Node d1Node,
                Session session) throws MetadigProcessException {

//        // Polymorphism doesn't work with D1 node classes, so have to use the derived classes
//        MultipartD1Node d1Node = null;
//        Session session = DataONE.getSession(subjectId, authToken);

        // Add the start and count, if pagination is being used
        queryStr = queryStr + "&start=" + startPos + "&rows=" + countRequested;
        // Query the MN or CN Solr engine to get the query associated with this project that will return all project related pids.
        InputStream qis = null;
        MetadigProcessException metadigException = null;

//        try {
//            d1Node = getMultipartD1Node(session, serviceUrl);
//            log.debug("Created MultipartD1Node, nodeId: " + d1Node.getNodeId().getValue());
//        } catch (Exception ex) {
//            log.error("Unable to create MultipartD1Node for Solr query");
//            metadigException = new MetadigProcessException("Unable to create multipart node client to query DataONE solr: " + ex.getMessage());
//            metadigException.initCause(ex);
//            throw metadigException;
//        }

        log.debug("Sending query: " + queryStr);
        // Send a query to a CN or MN
//        try {
//            if(isCN) {
//                qis = cnNode.query(session, "solr", queryStr);
//            } else {
//                qis = mnNode.query(session, "solr", queryStr);
//            }
//            log.debug("Sent query");
//        } catch (Exception e) {
//            log.error("Error retrieving pids: " + e.getMessage());
//            metadigException = new MetadigProcessException("Unable to query dataone node: " + e.getMessage());
//            metadigException.initCause(e);
//            throw metadigException;
//        }
        try {
            qis = d1Node.query(session, "solr", queryStr);
            log.debug("Sent query");
        } catch (Exception e) {
            log.error("Error retrieving pids: " + e.getMessage());
            metadigException = new MetadigProcessException("Unable to query dataone node: " + e.getMessage());
            metadigException.initCause(e);
            throw metadigException;
        }

        log.debug("Creating xml doc with results");
        Document xmldoc = null;
        DocumentBuilder builder = null;

        try {
            // If results were returned, create an XML document from them
            log.debug("qis available: " + qis.available());
            if (qis.available() > 0) {
                try {
                    DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
                    builder = factory.newDocumentBuilder();
                    xmldoc = builder.parse(new InputSource(qis));
                    log.debug("Created xml doc: " + xmldoc.toString());
                } catch (Exception e) {
                    log.error("Unable to create w3c Document from input stream", e);
                    e.printStackTrace();
                } finally {
                    qis.close();
                }
            } else {
                log.info("No results returned from D1 Solr query");
                qis.close();
            }
        } catch (IOException ioe) {
            log.debug("IO exception: " + ioe.getMessage());
            metadigException = new MetadigProcessException("Unable prepare query result xml document: " + ioe.getMessage());
            metadigException.initCause(ioe);
            throw metadigException;
        }

        log.debug("Created results xml doc");

        return xmldoc;
    }
    /**
     * Get a DataONE authenticated session
     * <p>
     *     If no subject or authentication token are provided, a public session is returned
     * </p>
     * @param authToken the authentication token
     * @return the DataONE session
     */
    public static Session getSession(String subjectId, String authToken) {

        Session session;

        // query Solr - either the member node or cn, for the project 'solrquery' field
        if (authToken == null || authToken.isEmpty()) {
            log.debug("Creating public sessioni");
            session = new Session();
        } else {
            log.debug("Creating authentication session for subjectId: " + subjectId + ", token: " + authToken.substring(0, 5) + "...");
            session = new AuthTokenSession(authToken);
        }

        if (subjectId != null && !subjectId.isEmpty()) {
            Subject subject = new Subject();
            subject.setValue(subjectId);
            session.setSubject(subject);
            log.debug("Set session subjectId to: " + session.getSubject().getValue());
        }

        return session;
    }

    public static Boolean isCN(String serviceUrl) {

        Boolean isCN = false;
        // Identity node as either a CN or MN based on the serviceUrl
        String pattern = "https*://cn.*?\\.dataone\\.org|https*://cn.*?\\.test\\.dataone\\.org";
        Pattern r = Pattern.compile(pattern);
        Matcher m = r.matcher(serviceUrl);
        if (m.find()) {
            isCN = true;
            log.debug("service URL is for a CN: " + serviceUrl);
        } else {
            log.debug("service URL is not for a CN: " + serviceUrl);
            isCN = false;
        }
        return isCN;
    }
}
