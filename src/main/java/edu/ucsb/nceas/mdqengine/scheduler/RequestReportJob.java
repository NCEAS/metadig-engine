package edu.ucsb.nceas.mdqengine.scheduler;

import edu.ucsb.nceas.mdqengine.MDQconfig;
import edu.ucsb.nceas.mdqengine.exception.MetadigStoreException;
import edu.ucsb.nceas.mdqengine.model.Run;
import edu.ucsb.nceas.mdqengine.store.DatabaseStore;
import edu.ucsb.nceas.mdqengine.store.MDQStore;
import org.apache.commons.configuration2.ex.ConfigurationException;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.http.HttpEntity;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.dataone.client.rest.DefaultHttpMultipartRestClient;
import org.dataone.client.rest.MultipartRestClient;
import org.dataone.client.v2.impl.MultipartMNode;
import org.dataone.cn.indexer.XmlDocumentUtility;
import org.dataone.mimemultipart.SimpleMultipartEntity;
import org.dataone.service.types.v1.Identifier;
import org.dataone.service.types.v2.SystemMetadata;
import org.dataone.service.util.TypeMarshaller;
import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.joda.time.format.DateTimeFormat;
import org.joda.time.format.DateTimeFormatter;
import org.quartz.*;
import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

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
public class RequestReportJob implements Job {

    private Log log = LogFactory.getLog(RequestReportJob.class);

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
    public RequestReportJob() {
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

        String qualityServiceUrl = null;
        try {
            MDQconfig cfg = new MDQconfig();
            qualityServiceUrl = cfg.getString("quality.serviceUrl");
        } catch (ConfigurationException | IOException ce) {
            JobExecutionException jee = new JobExecutionException("Error executing task.");
            jee.initCause(ce);
            throw jee;
        }

        //Log log = LogFactory.getLog(RequestReportJob.class);
        JobKey key = context.getJobDetail().getKey();
        JobDataMap dataMap = context.getJobDetail().getJobDataMap();

        String queryStr = dataMap.getString("queryStr");
        String suiteId = dataMap.getString("suiteId");
        String nodeId = dataMap.getString("nodeId");
        String nodeServiceUrl = dataMap.getString("nodeServiceUrl");
        String startHarvestDatetimeStr = dataMap.getString("startHarvestDatetime");
        int harvestDatetimeInc = dataMap.getInt("harvestDatetimeInc");
        MultipartRestClient mrc = null;
        MultipartMNode mnNode = null;

        try {
            mrc = new DefaultHttpMultipartRestClient();
        } catch (Exception e) {
            log.error("Error creating rest client: " + e.getMessage());
        }

        mnNode = new MultipartMNode(mrc, nodeServiceUrl);
        MDQStore store = null;

        try {
            store = new DatabaseStore();
        } catch (Exception e) {
            e.printStackTrace();
        }

        if(!store.isAvailable()) {
            try {
                store.renew();
            } catch (MetadigStoreException e) {
                e.printStackTrace();
            }
        }

        // Set UTC as the default time zone for all DateTime operations.
        // Get current datetime, which may be used for start time range.
        DateTimeZone.setDefault(DateTimeZone.UTC);
        DateTime currentDT = new DateTime(DateTimeZone.UTC);
        DateTimeFormatter dtfOut = DateTimeFormat.forPattern("yyyy-MM-dd'T'HH:mm:ss.SS'Z'");
        String currentDatetimeStr = dtfOut.print(currentDT);

        DateTime startDateTimeRange = null;
        DateTime endDateTimeRange = null;

        edu.ucsb.nceas.mdqengine.model.Node node;
        node = store.getNode(nodeId);
        String lastHarvestDateStr = null;

        // If a 'node' entry has not beeen saved for this nodeId yet, then a 'lastHarvested'
        // DataTime will not be available, in which case the 'startHarvestDataTime' from the
        // config file will be used.
        if(node.getNodeId() != null) {
            lastHarvestDateStr = node.getLastHarvestDatetime();
        } else {
            node.setNodeId(nodeId);
            lastHarvestDateStr = startHarvestDatetimeStr;
        }

        DateTime lastHarvestDate = new DateTime(lastHarvestDateStr);
        // Set the search start datetime to the last harvest datetime, unless it is in the
        // future. (This can happen when the previous time range end was for the current day,
        // as the end datetime range for the previous task run will have been stored as the
        // new lastharvestDateTime.
        DateTime startDTR = null;
        if(lastHarvestDate.isAfter(currentDT.toInstant())) {
            startDTR = currentDT;
        } else {
            startDTR = new DateTime(lastHarvestDate);
        }

        DateTime endDTR = new DateTime(startDTR);
        endDTR = endDTR.plusDays(1);
        if(endDTR.isAfter(currentDT.toInstant())) {
            endDTR = currentDT;
        }

        String startDTRstr = dtfOut.print(startDTR);
        String endDTRstr = dtfOut.print(endDTR);

        ArrayList<String> pidsToProcess = null;
        try {
            pidsToProcess = getPidsToProcess(mnNode, queryStr, suiteId, nodeId, startDTRstr, endDTRstr);
        } catch (Exception e) {
            throw new JobExecutionException(e.getMessage());
        }

        for (String pidStr : pidsToProcess) {
            submitReportRequest(mnNode, qualityServiceUrl, pidStr, suiteId);
        }

        try {
            node.setLastHarvestDatetime(endDTRstr);
            store.saveNode(node);
        } catch (MetadigStoreException mse) {
            log.error("error saveing node: " + node.getNodeId());
        }
    }

    public ArrayList<String> getPidsToProcess(MultipartMNode mnNode, String queryStr, String suiteId, String nodeId,
                                              String startHarvestDatetimeStr, String endHarvestDatetimeStr) throws Exception {

        ArrayList<String> pids = new ArrayList<String>();
        InputStream qis = null;

        try {
            queryStr = queryStr + "+dateUploaded:[" + startHarvestDatetimeStr + "%20TO%20" + endHarvestDatetimeStr + "]&fl=id&rows=10000";
            qis = mnNode.query(null, "solr", queryStr);
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
            } catch (SAXException e) {
                log.error("Unable to create w3c Document from input stream", e);
                e.printStackTrace();
            } finally {
                qis.close();
            }
        } else {
            qis.close();
        }

        //idXpath = xpath.compile("str[@name='id']/text()")dd;
        idXpath = xpath.compile("//result/doc/str[@name='id']/text()");

        NodeList result = (NodeList) idXpath.evaluate(xmldoc, XPathConstants.NODESET);
        log.info("Node count: " + result.getLength());

        String currentPid = null;
        for(int index = 0; index < result.getLength(); index ++) {
            Node node = result.item(index);
            currentPid = node.getTextContent();

            if(!runExists(currentPid, suiteId)) {
                pids.add(currentPid);
                log.info("adding pid to process list: " + currentPid);
            }
        }

        return pids;
    }

    public boolean runExists(String pid, String suiteId) throws MetadigStoreException {

        boolean found = false;
        MDQStore store = null;

        if (store == null) {
            try {
                store = new DatabaseStore();
            } catch (Exception e) {
                e.printStackTrace();
                throw e;
            }
        }

        if(!store.isAvailable()) {
            try {
                store.renew();
            } catch (MetadigStoreException e) {
                e.printStackTrace();
                throw e;
            }
        }

        Run run = store.getRun(pid, suiteId);
        if(run != null) {
            found = true;
        } else {
            found = false;
        }

        return found;
    }

    public void submitReportRequest(MultipartMNode mnNode, String qualityServiceUrl, String pidStr, String suiteId) {

        SystemMetadata sysmeta = null;
        InputStream objectIS = null;
        InputStream runResultIS = null;

        Identifier pid = new Identifier();
        pid.setValue(pidStr);

        try {
            sysmeta = (SystemMetadata) mnNode.getSystemMetadata(null, pid);
        } catch (Exception e) {
            e.printStackTrace();
        }

        try {
            objectIS = mnNode.get(pid);
            log.info("Retrieved metadata object for pid: " + pidStr);
        } catch (Exception e) {
            e.printStackTrace();
        }

        //String qualityServiceUrl = "http://localhost:8080/quality/suites/" + suiteId + "/run";
        qualityServiceUrl = qualityServiceUrl + "/suites/" + suiteId + "/run";
        HttpPost post = new HttpPost(qualityServiceUrl);

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
            log.info("submitting: " + qualityServiceUrl);
            post.setEntity(entity);
            CloseableHttpClient client = HttpClients.createDefault();
            CloseableHttpResponse response = client.execute(post);

            // retrieve results
            HttpEntity reponseEntity = response.getEntity();
            if (reponseEntity != null) {
                runResultIS = reponseEntity.getContent();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
