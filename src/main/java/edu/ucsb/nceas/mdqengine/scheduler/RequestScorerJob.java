package edu.ucsb.nceas.mdqengine.scheduler;

import edu.ucsb.nceas.mdqengine.Controller;
import edu.ucsb.nceas.mdqengine.MDQconfig;
import edu.ucsb.nceas.mdqengine.DataONE;
import edu.ucsb.nceas.mdqengine.exception.MetadigException;
import edu.ucsb.nceas.mdqengine.exception.MetadigProcessException;
import edu.ucsb.nceas.mdqengine.exception.MetadigStoreException;
import edu.ucsb.nceas.mdqengine.model.Task;
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
import org.dataone.client.v2.impl.MultipartD1Node;
import org.dataone.service.types.v1.*;
import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.joda.time.format.DateTimeFormat;
import org.joda.time.format.DateTimeFormatter;
import org.quartz.*;
import org.w3c.dom.Document;

import javax.xml.xpath.*;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;

/**
 * <p>
 * Run a MetaDIG Quality Engine Scheduler task, for example,
 * query a member node for new pids and request that an aggregated quality
 * graphic is created for each one.
 * </p>
 *
 * @author Peter Slaughter
 */
@PersistJobDataAfterExecution
@DisallowConcurrentExecution
public class RequestScorerJob implements Job {

    private Log log = LogFactory.getLog(RequestScorerJob.class);
    private static Controller metadigCtrl = null;

    class ListResult {
        Integer resultCount;
        ArrayList<String> result = new ArrayList<>();

        // The scheduler keeps track of Solr 'dateModified' of the last pid harvested,
        // which will be used as the starting time of the next harvest.
        private DateTime lastDateModifiedDT = null;

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

        void setLastDateModified(DateTime date) {
            this.lastDateModifiedDT = date;
        }

        public DateTime getLastDateModified() { return this.lastDateModifiedDT; }
    }

    // Since Quartz will re-instantiate a class every time it
    // gets executed, non-static member variables can
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
    public RequestScorerJob() {
    }

    /**
     * <p>
     * Called by the <code>{@link org.quartz.Scheduler}</code> when a
     * <code>{@link org.quartz.Trigger}</code> fires that is associated with
     * the <code>Job</code>.
     * </p>
     * <p>
     * This method sends a request from the scheduler (metadig-scheduler) to the controller (metadig-controller)
     * to execute a 'scorer' request (metadig-scorer). This request goes through the controller so that it can
     * keep track and log all requests and their completion status. The current method to send requests to the
     * controller is to send a REST request to the servlet running the controller, using the metadig-engine API.
     * </p>
     *
     * @throws JobExecutionException if there is an exception while executing the job.
     */
    public void execute(JobExecutionContext context)
            throws JobExecutionException {

        String qualityServiceUrl  = null;
        MDQconfig cfg = null;

        JobKey key = context.getJobDetail().getKey();
        JobDataMap dataMap = context.getJobDetail().getJobDataMap();

        String taskName = dataMap.getString("taskName");
        String taskType = dataMap.getString("taskType");
        String pidFilter = dataMap.getString("pidFilter");
        String suiteId = dataMap.getString("suiteId");
        // The nodeId is used for filterine queries based on DataONE sysmeta 'datasource'.
        // For example, if one wished to get scores for Arctic Data Center, the urn:node:ARCTIC would be specified.
        String nodeId = dataMap.getString("nodeId");
        String startHarvestDatetimeStr = dataMap.getString("startHarvestDatetime");
        int harvestDatetimeInc = dataMap.getInt("harvestDatetimeInc");
        // Number of pids to get each query (this number of pids will be fetched each query until all pids are obtained)
        int countRequested = dataMap.getInt("countRequested");
        String requestType = null;
        String formatFamily = null;
        MultipartD1Node d1Node = null;
        String DataONEauthToken = null;
        String subjectId = null;
        String nodeServiceUrl = null;

        if (taskType.equalsIgnoreCase("score")) {
            requestType = dataMap.getString("requestType");
        }
        log.debug("Executing task " + taskType + ", " + taskName + " for node: " + nodeId + ", suiteId: " + suiteId);

        try {
            cfg = new MDQconfig();
            qualityServiceUrl = cfg.getString("quality.serviceUrl");
            log.trace("nodeId from request: " + nodeId);
            String nodeAbbr = nodeId.replace("urn:node:", "");
            subjectId = cfg.getString(nodeAbbr + ".subjectId");
            nodeServiceUrl = cfg.getString(nodeAbbr + ".serviceUrl");
            log.trace("nodeServiceUrl: " + nodeServiceUrl);

            DataONEauthToken = System.getenv("DATAONE_AUTH_TOKEN");
            if (DataONEauthToken == null) {
                DataONEauthToken =  cfg.getString("DataONE.authToken");
                log.debug("Got token from properties file.");
            } else {
                log.debug("Got token from env.");
            }
        } catch (ConfigurationException | IOException ce) {
            JobExecutionException jee = new JobExecutionException(taskName + ": Error executing task: " + ce.getMessage());
            jee.initCause(ce);
            throw jee;
        }

        if(nodeServiceUrl == null) {
            String msg = taskName + "Unable to read serviceUrl from config file for: " + nodeId;
            throw new JobExecutionException(msg);
        }

        Session session = DataONE.getSession(subjectId, DataONEauthToken);

        // Get a connection to the DataONE node (CN or MN)
        try {
            d1Node = DataONE.getMultipartD1Node(session, nodeServiceUrl);
        } catch (MetadigException mpe) {
            mpe.printStackTrace();
            throw new JobExecutionException(taskName + ": unable to create connection to service URL " + nodeServiceUrl , mpe);
        }

        MDQStore store = null;

        // Get stored task info from the last task execution
        try {
            store = new DatabaseStore();
        } catch (Exception e) {
            e.printStackTrace();
            throw new JobExecutionException("Cannot create store, unable to schedule job", e);
        }

        if(!store.isAvailable()) {
            try {
                store.renew();
            } catch (MetadigStoreException e) {
                e.printStackTrace();
                throw new JobExecutionException("Cannot renew store, unable to schedule job", e);
            }
        }

        // Set UTC as the default time zone for all DateTime operations.
        // Get current datetime, which may be used for start time range.
        DateTimeZone.setDefault(DateTimeZone.UTC);
        DateTime currentDT = new DateTime(DateTimeZone.UTC);
        DateTimeFormatter dtfOut = DateTimeFormat.forPattern("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'");
        String lastHarvestDateStr = null;

        Task task;
        task = store.getTask(taskName, taskType, nodeId);

        // If a 'task' entry has not been saved for this task name yet, then a 'lastHarvested'
        // DataTime will not be available, in which case the 'startHarvestDataTime' from the
        // config file will be used.
        if(task.getLastHarvestDatetime(nodeId) == null) {
            task = new Task();
            task.setTaskName(taskName);
            task.setTaskType(taskType);
            lastHarvestDateStr = startHarvestDatetimeStr;
            task.setLastHarvestDatetime(lastHarvestDateStr, nodeId);
        } else {
            lastHarvestDateStr = task.getLastHarvestDatetime(nodeId);
        }

        DateTime lastHarvestDateDT = new DateTime(lastHarvestDateStr);
        // Set the search start datetime to the last harvest datetime, unless it is in the
        // future. (This can happen when the previous time range end was for the current day,
        // as the end datetime range for the previous task run will have been stored as the
        // new lastharvestDateTime.
        DateTime startDT = null;
        if(lastHarvestDateDT.isAfter(currentDT.toInstant())) {
            startDT = currentDT;
        } else {
            startDT = new DateTime(lastHarvestDateDT);
        }

        DateTime endDT = new DateTime(currentDT);

        // If the start and end harvest dates are the same (happends for a new node), then
        // tweek the start so that DataONE listObjects doesn't complain.
        if(startDT == endDT ) {
            startDT = startDT.minusMinutes(1);
        }

        // Track the sysmeta dateUploaded of the latest harvested pid. This will become the starting time of
        // the next harvest.
        DateTime lastDateModifiedDT = startDT;

        String startDTstr = dtfOut.print(startDT);
        String endDTstr = dtfOut.print(endDT);

        int startCount = 0;
        RequestScorerJob.ListResult result = null;
        Integer resultCount = 0;

        // Two types of score requests can be processed - a "node" request that will get score info for an
        // entire repository (e.g. urn:node:ARCTIC) or a "portal" request that will get scores for a
        // specific portal (from the Solr portal entry collectionQuery).
        if(requestType != null && requestType.equalsIgnoreCase("node")) {
            try {
                // For a 'node' scores request, the 'collection' is the entire node, so specify
                // the nodeId as the collectionid. It is not necessary to retrieve a collectionQuery for this
                // 'node' portal, as there is no Solr entry for this type collection. All quality scores available
                // in the quality Solr server will be directly retrieved, filtering on the 'nodeId' (datasource)
                log.info("TaskName: " + taskName + ", taskType: " + taskType + " submitting node request for nodeId: "
                        + nodeId + ", suiteId: " + suiteId + "formatFamily: " + formatFamily);
                submitScorerRequest(qualityServiceUrl, nodeId, suiteId, nodeId, formatFamily);
            } catch (Exception e) {
                JobExecutionException jee = new JobExecutionException("Unable to submit request to create new node ("
                        + nodeId + ")" + " score graph/data file ", e);
                jee.setRefireImmediately(false);
                throw jee;
            }
        } else {
            Integer allIds = 0;
            boolean morePids = true;
            while (morePids) {
                // Get a list of pids selected by a collection (portal) search filter (collectionQuery) and get
                // the quality scores (from the quality Solr server) for that list of pids.
                ArrayList<String> pidsToProcess = null;
                log.trace("Getting portal pids to process, startCount: " + startCount + ", countRequested: " + countRequested);

                try {
                    result = getPidsToProcess(d1Node, session, pidFilter, startDTstr, endDTstr, startCount, countRequested, lastDateModifiedDT, taskName);
                    pidsToProcess = result.getResult();
                    resultCount = result.getResultCount();
                    lastDateModifiedDT = result.getLastDateModified();
                } catch (Exception e) {
                    JobExecutionException jee = new JobExecutionException("Unable to get pids to process", e);
                    jee.setRefireImmediately(false);
                    throw jee;
                }

                log.trace(taskName + ": found " + resultCount + " seriesIds" + " for date: " + startDTstr + " at servierUrl: " + nodeServiceUrl);
                for (String pidStr : pidsToProcess) {
                    try {
                        log.debug(taskName + ": submitting seriesId: " + pidStr);
                        submitScorerRequest(qualityServiceUrl, pidStr, suiteId, nodeId, formatFamily);
                    } catch (Exception e) {
                        JobExecutionException jee = new JobExecutionException("Unable to submit request to create new score graph/data file", e);
                        jee.setRefireImmediately(false);
                        throw jee;
                    }
                }

                // Check if DataONE returned the max number of results. If so, we have to request more by paging through
                // the results.
                allIds += pidsToProcess.size();
                if (resultCount >= countRequested) {
                    morePids = true;
                    startCount = startCount + resultCount;
                    log.trace("Paging through more results, current start is " + startCount);
                } else {
                    morePids = false;

                }
            }

            if (allIds > 0) {
                // Record the new "last harvested" date
                task.setLastHarvestDatetime(dtfOut.print(lastDateModifiedDT), nodeId);
                log.debug("Saving lastHarvestDate: " + dtfOut.print(lastDateModifiedDT));
                try {
                    store.saveTask(task, nodeId);
                } catch (MetadigStoreException mse) {
                    log.error("Error saving task: " + task.getTaskName());
                    JobExecutionException jee = new JobExecutionException("Unable to save new harvest date", mse);
                    jee.setRefireImmediately(false);
                    throw jee;
                }
                log.info(taskName + ": found " + allIds + " seriesIds" + " for start: " + startDTstr + ", end: " + endDTstr + " at servierUrl: " + nodeServiceUrl);
            }
        }
        store.shutdown();
    }

    /**
     * Query a DataONE CN or MN object store for a list of object that match the time range and formatId filters provided.
     *
     * @param d1Node a DataONE CN or MN connection client object
     * @param session a DataONE authentication session
     * @param pidFilter the DataONE format identifies to filter for
     * @param startHarvestDatetimeStr the starting date to harvest pids from
     * @param endHarvestDatetimeStr the ending data to harvest pids from
     * @param startCount the start count for paging results from DataONE, for large results
     * @param countRequested the number of items to get from DataONE on each request
     * @param lastDateModifiedDT the sysmeta 'dateSystemMetadataModified' value of the last harvested pid
     * @throws Exception if there is an exception while executing the job.
     * @return a ListResult object containing the matching pids
     * @throws Exception
     */
    public ListResult getPidsToProcess(MultipartD1Node d1Node, Session session,
                                       String pidFilter, String startHarvestDatetimeStr, String endHarvestDatetimeStr,
                                       int startCount, int countRequested, DateTime lastDateModifiedDT, String taskName) throws Exception {

        MetadigProcessException metadigException = null;

        org.w3c.dom.NodeList xpathResult = null;
        XPathExpression fieldXpath = null;
        XPathExpression dateModifiedXpath = null;
        XPath xpath = null;
        org.w3c.dom.Node node = null;
        ArrayList<String> pids = new ArrayList<String>();
        Document xmldoc = null;

        String queryStr = "?q=formatId:" + pidFilter + "+-obsoletedBy:*" + "+dateModified:[" + startHarvestDatetimeStr + "%20TO%20"
                + endHarvestDatetimeStr + "]"
                + "&fl=seriesId,dateModified&q.op=AND";
        log.trace("query: " + queryStr);

        // Send the query to DataONE Solr to retrieve portal seriesIds for a given time frame

        // One query can return many documents, so use the paging mechanism to make sure we retrieve them all.
        // Keep paging through query results until all pids have been fetched. The last 'page' of query
        // results is indicated by the number of items returned being less than the number requested.
        int thisResultLength;
        // Now setup the xpath to retrieve the ids returned from the collection query.
        try {
            log.trace("Compiling xpath for seriesId");
            // Extract the collection query from the Solr result XML
            XPathFactory xPathfactory = XPathFactory.newInstance();
            xpath = xPathfactory.newXPath();
            fieldXpath = xpath.compile("//result/doc/str[@name='seriesId']/text()");
            dateModifiedXpath = xpath.compile("//result/doc/date[@name='dateModified']/text()");
        } catch (XPathExpressionException xpe) {
            log.error(taskName + ": error extracting id from solr result doc: " + xpe.getMessage());
            metadigException = new MetadigProcessException("Unable to get collection pids: " + xpe.getMessage());
            metadigException.initCause(xpe);
            throw metadigException;
        }

        // Loop through the Solr result. As the result may be large, page through the results, accumulating
        // the pids returned into a ListResult object.
        log.trace("Getting portal seriesIds from Solr " );
        int startPos = startCount;

        do {
            xmldoc = DataONE.querySolr(queryStr, startPos, countRequested, d1Node, session);
            if(xmldoc == null) {
                log.info("no values returned from query");
                break;
            }
            try {
                xpathResult = (org.w3c.dom.NodeList) fieldXpath.evaluate(xmldoc, XPathConstants.NODESET);
            } catch (XPathExpressionException xpe) {
                log.error(taskName + ": error extracting seriesId from solr result doc: " + xpe.getMessage());
                metadigException = new MetadigProcessException("Unable to get collection pids: " + xpe.getMessage());
                metadigException.initCause(xpe);
                throw metadigException;
            }
            String currentPid = null;
            thisResultLength = xpathResult.getLength();
            log.trace("Got " + thisResultLength + " pids this query");
            if(thisResultLength == 0) break;
            for (int index = 0; index < xpathResult.getLength(); index++) {
                node = xpathResult.item(index);
                currentPid = node.getTextContent();
                pids.add(currentPid);
                log.trace("adding pid: " + currentPid);
            }

            // Get dateModified for the returned seriesIds
            try {
                xpathResult = (org.w3c.dom.NodeList) dateModifiedXpath.evaluate(xmldoc, XPathConstants.NODESET);
            } catch (XPathExpressionException xpe) {
                log.error(taskName + ": error extracting dateModified from solr result doc: " + xpe.getMessage());
                metadigException = new MetadigProcessException("Unable to get collection pids: " + xpe.getMessage());
                metadigException.initCause(xpe);
                throw metadigException;
            }

            DateTime thisDateModified;
            thisResultLength = xpathResult.getLength();
            if(thisResultLength == 0) break;
            for (int index = 0; index < xpathResult.getLength(); index++) {
                node = xpathResult.item(index);
                String dateStr = node.getTextContent();
                log.debug("Checking date str: " + dateStr);
                thisDateModified = DateTime.parse(dateStr,
                        DateTimeFormat.forPattern("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'"));
                if(thisDateModified.isAfter(lastDateModifiedDT)) {
                    lastDateModifiedDT = thisDateModified.plusMillis(1);
                    log.debug("Updated lastDateModified to " + lastDateModifiedDT);
                }
            }

            startPos += thisResultLength;
        } while (thisResultLength > 0);

        RequestScorerJob.ListResult result = new RequestScorerJob.ListResult();
        result.setResultCount(pids.size());
        result.setResult(pids);
        // Return the sysmeta 'dateSystemMetadataModified' of the last pid harvested.
        result.setLastDateModified(lastDateModifiedDT);

        return result;
    }

    /**
     * Submit a requst to the metadig controller to get quality score info and create a graph for the specified collection.
     *
     * @param qualityServiceUrl the URL of the MetaDIG quality service
     * @param collectionId the DataONE collection (portal) seriesId
     * @param suiteId the quality suite to run for the collection
     * @param nodeId the DataONE node identifier that the collection is hosted on
     * @param formatFamily the format identifier family (e.g. "eml" for all EML format identifier versions)
     *
     * @throws Exception
     *
     */
    public void submitScorerRequest(String qualityServiceUrl, String collectionId, String suiteId, String nodeId, String formatFamily) throws  Exception {

        InputStream runResultIS = null;

        String scorerServiceUrl = qualityServiceUrl + "/scores" + "?suite=" + suiteId;

        if(collectionId != null && ! collectionId.isEmpty()) {
            scorerServiceUrl += "&id=" + collectionId;
        }

        if(nodeId != null && ! nodeId.isEmpty()) {
            scorerServiceUrl += "&node=" + nodeId;
        }

        if(formatFamily != null && ! formatFamily.isEmpty()) {
            scorerServiceUrl += "&format=" + formatFamily;
        }

        HttpPost post = new HttpPost(scorerServiceUrl);

        try {
            // make sure we get XML back
            post.addHeader("Accept", "application/xml");

            // send to service
            log.trace("submitting scores request : " + scorerServiceUrl);
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

