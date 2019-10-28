package edu.ucsb.nceas.mdqengine.scheduler;

import edu.ucsb.nceas.mdqengine.MDQconfig;
import org.apache.commons.configuration2.ex.ConfigurationException;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.http.HttpEntity;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.dataone.client.auth.AuthTokenSession;
import org.dataone.client.rest.HttpMultipartRestClient;
import org.dataone.client.rest.MultipartRestClient;
import org.dataone.client.v2.impl.MultipartCNode;
import org.dataone.client.v2.impl.MultipartMNode;
import org.dataone.cn.indexer.XmlDocumentUtility;
import org.dataone.mimemultipart.SimpleMultipartEntity;
import org.dataone.service.exceptions.NotAuthorized;
import org.dataone.service.types.v1.*;
import org.dataone.service.types.v2.SystemMetadata;
import org.dataone.service.util.TypeMarshaller;
import org.quartz.*;
import org.w3c.dom.Document;

import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathExpression;
import javax.xml.xpath.XPathFactory;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;

/**
 * <p>
 * Run a MetaDIG Quality Engine Scheduler task, for example,
 * query a member node for new pids and request that a quality
 * report is created for each one.
 * </p>
 *
 * @author Peter Slaughter
 */
@PersistJobDataAfterExecution
@DisallowConcurrentExecution
public class RequestGraphJob implements Job {

    private Log log = LogFactory.getLog(RequestGraphJob.class);

    class ListResult {
        Integer resultCount;
        ArrayList<String> result = new ArrayList<>();

        void setResult(ArrayList result) {
            this.result = result;
        }

        ArrayList getResult() {
            return this.result;
        }

        void setResultCount(Integer count) {
            this.resultCount = count;
        }

        Integer getResultCount() {
            return this.resultCount;
        }
    }

    // Since Quartz will re-instantiate a class every time it
    // gets executed, members non-static member variables can
    // not be used to maintain state!

    /**
     * <p>
     * Empty constructor for job initialization
     * </p>
     * <p>
     * Quartz requires a public empty constructor so that the
     * scheduler can instantiate the class whenever it needs.
     * </p>
     */
    public RequestGraphJob() {
    }

    /**
     * <p>
     * Called by the <code>{@link org.quartz.Scheduler}</code> when a
     * <code>{@link org.quartz.Trigger}</code> fires that is associated with
     * the <code>Job</code>.
     * </p>
     *
     * @throws JobExecutionException if there is an exception while executing the job.
     */
    public void execute(JobExecutionContext context)
            throws JobExecutionException {

        String graphServiceUrl  = null;
        try {
            MDQconfig cfg = new MDQconfig();
            graphServiceUrl = cfg.getString("graph.serviceUrl");
        } catch (ConfigurationException | IOException ce) {
            JobExecutionException jee = new JobExecutionException("Error executing task.");
            jee.initCause(ce);
            throw jee;
        }

        //Log log = LogFactory.getLog(RequestGraphJob.class);
        JobKey key = context.getJobDetail().getKey();
        JobDataMap dataMap = context.getJobDetail().getJobDataMap();

        String authToken = dataMap.getString("authToken");
        String pidFilter = dataMap.getString("pidFilter");
        String suiteId = dataMap.getString("suiteId");
        String nodeId = dataMap.getString("nodeId");
        String nodeServiceUrl = dataMap.getString("nodeServiceUrl");
        String startHarvestDatetimeStr = dataMap.getString("startHarvestDatetime");
        int harvestDatetimeInc = dataMap.getInt("harvestDatetimeInc");
        int countRequested = dataMap.getInt("countRequested");
        MultipartRestClient mrc = null;
        MultipartMNode mnNode = null;
        MultipartCNode cnNode = null;
        Boolean isCN = false;

        log.debug("Executing task for node: " + nodeId + ", suiteId: " + suiteId);

        try {
            mrc = new HttpMultipartRestClient();
        } catch (Exception e) {
            log.error("Error creating rest client: " + e.getMessage());
            JobExecutionException jee = new JobExecutionException(e);
            jee.setRefireImmediately(false);
            throw jee;
        }

        Subject subject = new Subject();
        subject.setValue("public");
        Session session = null;
        if(authToken == null || authToken.equals("")) {
            session = new Session();
            //session.setSubject(subject);
        } else {
            session = new AuthTokenSession(authToken);
        }

        //log.info("Created session with subject: " + session.getSubject().getValue().toString());

        // Don't know node type yet from the id, so have to manually check if it's a CN
        if(nodeId.equalsIgnoreCase("urn:node:CN")) {
            cnNode = new MultipartCNode(mrc, nodeServiceUrl, session);
            isCN = true;
        } else {
            mnNode = new MultipartMNode(mrc, nodeServiceUrl, session);
        }

        Integer startCount = new Integer(0);
        ListResult result = null;
        Integer resultCount = null;

        boolean morePids = true;
        while(morePids) {
            ArrayList<String> pidsToProcess = null;
            log.info("Getting aggregation pids for node: " + nodeId);

            try {
                result = getPidsToProcess(cnNode, mnNode, isCN, session, nodeId, pidFilter, startCount, countRequested);
                pidsToProcess = result.getResult();
                resultCount = result.getResultCount();
            } catch (Exception e) {
                JobExecutionException jee = new JobExecutionException("Unable to get pids to process", e);
                jee.setRefireImmediately(false);
                throw jee;
            }

            log.info("Found " + resultCount + " pids" + " for node: " + nodeId);
            for (String pidStr : pidsToProcess) {
                try {
                    log.info("submitting pid: " + pidStr);
                    submitGraphRequest(cnNode, mnNode, isCN, session, graphServiceUrl, pidStr, suiteId);
                } catch (Exception e) {
                    JobExecutionException jee = new JobExecutionException("Unable to submit request to create new quality reports", e);
                    jee.setRefireImmediately(false);
                    throw jee;
                }
            }

            // Check if DataONE returned the max number of results. If so, we have to request more by paging through
            // the results.
            if(resultCount >= countRequested) {
                morePids = true;
                startCount = startCount + resultCount;
                log.info("Paging through more results, current start is " + startCount);
            } else {
                morePids = false;
            }
        }
    }

    public ListResult getPidsToProcess(MultipartCNode cnNode, MultipartMNode mnNode, Boolean isCN, Session session, String nodeId,
                                       String pidFilter, int startCount, int countRequested) throws Exception {

        ArrayList<String> pids = new ArrayList<String>();
        InputStream qis = null;

        // Get MN or CN base url from nodeId to get base of queryStr
        // TODO: replace this hard-coded query url
        String queryStr = "https://dev.nceas.ucsb.edu/knb/d1/mn/v2/query/solr/q=projectName:*+-obsoletedBy:*&fl=projectName,seriesId";

        // There may be many pids in the result set, so update the query to allow paging through them.
        // Page though the results, requesting a certain amount of pids at each request
        queryStr += "&start=" + startCount + "&count=" + countRequested;
        queryStr += "&q.op=AND&sort=dateUploaded%20desc";

        log.debug("Sending Solr query:" + queryStr);

        // TODO: use CN or MN depending on nodeId
        // TODO: may need to create an authorized session
        try {
            qis = mnNode.query(session, "solr", queryStr);
            log.info("Sent query: " + queryStr);
        } catch (Exception e) {
            log.error("Error retrieving pids: " + e.getMessage());
            throw e;
        }

        XPathFactory xPathfactory = XPathFactory.newInstance();
        XPath xpath = xPathfactory.newXPath();
        XPathExpression idXpath = null;
        Document xmldoc = null;

        if (qis != null) {
            try {
                xmldoc = XmlDocumentUtility.generateXmlDocument(qis);
            } catch (Exception e) {
                log.error("Unable to create w3c Document from input stream", e);
                e.printStackTrace();
            } finally {
                qis.close();
            }
        } else {
            qis.close();
        }

        //idXpath = xpath.compile("str[@name='id']/text()")dd;
        idXpath = xpath.compile("//result/doc/str[@name='seriesId']/text()");

        org.w3c.dom.NodeList xpathResult = (org.w3c.dom.NodeList) idXpath.evaluate(xmldoc, XPathConstants.NODESET);
        String currentPid = null;

        int pidCount = 0;
        for(int index = 0; index < xpathResult.getLength(); index ++) {
            org.w3c.dom.Node node = xpathResult.item(index);
            currentPid = node.getTextContent();
            pidCount++;
            pids.add(currentPid);
        }

        ListResult result = new ListResult();
        result.setResultCount(pidCount);
        result.setResult(pids);

        return result;

    }

    public void submitGraphRequest(MultipartCNode cnNode, MultipartMNode mnNode, Boolean isCN, Session session,
                                   String graphServiceUrl, String pidStr, String suiteId) throws Exception {

        SystemMetadata sysmeta = null;
        InputStream objectIS = null;
        InputStream runResultIS = null;

        Identifier pid = new Identifier();
        pid.setValue(pidStr);

        try {
            if (isCN) {
                sysmeta = cnNode.getSystemMetadata(session, pid);
            } else {
                sysmeta = mnNode.getSystemMetadata(session, pid);
            }
        } catch (NotAuthorized na) {
            log.error("Not authorized to read sysmeta for pid: " + pid.getValue() + ", continuing with next pid...");
            return;
        } catch (Exception e) {
            throw(e);
        }

        try {
            if(isCN) {
                objectIS = cnNode.get(session, pid);
            } else  {
                objectIS = mnNode.get(session, pid);
            }
            log.debug("Retrieved metadata object for pid: " + pidStr);
        } catch (NotAuthorized na) {
            log.error("Not authorized to read pid: " + pid + ", continuing with next pid...");
            return;
        } catch (Exception e) {
            throw(e);
        }

        // quality suite service url, i.e. "http://docke-ucsb-1.dataone.org:30433/quality/suites/knb.suite.1/run
        graphServiceUrl = graphServiceUrl + "/suites/" + suiteId + "/run";
        HttpPost post = new HttpPost(graphServiceUrl);

        try {
            // add document
            SimpleMultipartEntity entity = new SimpleMultipartEntity();
            entity.addFilePart("document", objectIS);

            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            TypeMarshaller.marshalTypeToOutputStream(sysmeta, baos);
            entity.addFilePart("systemMetadata", new ByteArrayInputStream(baos.toByteArray()));

            // make sure we get XML back
            post.addHeader("Accept", "application/xml");

            // send to service
            log.trace("submitting: " + graphServiceUrl);
            post.setEntity((HttpEntity) entity);
            CloseableHttpClient client = HttpClients.createDefault();
            CloseableHttpResponse response = client.execute(post);

            // retrieve results
            HttpEntity reponseEntity = response.getEntity();
            if (reponseEntity != null) {
                runResultIS = reponseEntity.getContent();
            }
        } catch (Exception e) {
            throw(e);
        }
    }
}

