package edu.ucsb.nceas.mdqengine.scheduler;

import edu.ucsb.nceas.mdqengine.MDQconfig;
import edu.ucsb.nceas.mdqengine.DataONE;
import edu.ucsb.nceas.mdqengine.exception.MetadigStoreException;
import edu.ucsb.nceas.mdqengine.model.Run;
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
import org.dataone.client.rest.HttpMultipartRestClient;
import org.dataone.client.rest.MultipartRestClient;
import org.dataone.client.v2.impl.MultipartCNode;
import org.dataone.client.v2.impl.MultipartMNode;
import org.dataone.service.types.v2.Node;
import org.dataone.mimemultipart.SimpleMultipartEntity;
import org.dataone.service.exceptions.NotAuthorized;
import org.dataone.service.types.v1.*;
import org.dataone.service.types.v2.SystemMetadata;
import org.dataone.service.util.TypeMarshaller;
import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.joda.time.format.DateTimeFormat;
import org.joda.time.format.DateTimeFormatter;
import org.quartz.*;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Date;

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

    class ListResult {
        // The total result count for all object types returned from DataONE. This is the count of all object types
        // that were retrieved for a given request. The DataONE 'listObjects' service does provide
        // parameters to filter by formatId wildcard, so we have to retrieve all pids for a time range
        // and filter the result list.
        private Integer totalResultCount = 0;
        // The filtered result count returned from DataONE.
        // The DataONE listObjects service returns all new pids for all formatIds
        // but we are typically only interested in a subset of those, i.e. EML metadata pids,
        // so this is the count of pids from the result that we are actually interested in.
        private Integer filteredResultCount = 0;
        private ArrayList<String> result = new ArrayList<>();

        // The scheduler keeps track of the sysmeta 'dateSystemMetadataModified' of the last pid harvested,
        // which will be used as the starting time of the next harvest.
        private DateTime lastDateModifiedDT = null;

        void setResult(ArrayList result) {
            this.result = result;
        }

        public ArrayList getResult() {
            return this.result;
        }

        void setTotalResultCount(Integer count) {
            this.totalResultCount = count;
        }
        void setFilteredResultCount(Integer count) { this.filteredResultCount = count; }
        void setLastDateModified(DateTime date) {
            this.lastDateModifiedDT = date;
        }

        public Integer getTotalResultCount() { return this.totalResultCount; }

        public Integer getFilteredResultCount() { return this.filteredResultCount; }

        public DateTime getLastDateModified() {
            return this.lastDateModifiedDT;
        }
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

        //Log log = LogFactory.getLog(RequestReportJob.class);
        JobKey key = context.getJobDetail().getKey();
        JobDataMap dataMap = context.getJobDetail().getJobDataMap();

        String taskName = dataMap.getString("taskName");
        String taskType = dataMap.getString("taskType");
        String pidFilter = dataMap.getString("pidFilter");
        String suiteId = dataMap.getString("suiteId");
        String nodeId = dataMap.getString("nodeId");
        String startHarvestDatetimeStr = dataMap.getString("startHarvestDatetime");
        int harvestDatetimeInc = dataMap.getInt("harvestDatetimeInc");
        int countRequested = dataMap.getInt("countRequested");
        MultipartRestClient mrc = null;
        MultipartMNode mnNode = null;
        MultipartCNode cnNode = null;

        String DataONEauthToken = null;
        String subjectId = null;
        String nodeServiceUrl = null;

        try {
            MDQconfig cfg = new MDQconfig();
            qualityServiceUrl = cfg.getString("quality.serviceUrl");
            DataONEauthToken = System.getenv("DATAONE_AUTH_TOKEN");
            if (DataONEauthToken == null) {
                DataONEauthToken =  cfg.getString("DataONE.authToken");
                log.debug("Got token from properties file.");
            } else {
                log.debug("Got token from env.");
            }
            String nodeAbbr = nodeId.replace("urn:node:", "");
            subjectId = cfg.getString(nodeAbbr + ".subjectId");
            // TODO:  Cache the node values from the CN listNode service
            nodeServiceUrl = cfg.getString(nodeAbbr + ".serviceUrl");
        } catch (ConfigurationException | IOException ce) {
            JobExecutionException jee = new JobExecutionException(taskName + ": error executing task.");
            jee.initCause(ce);
            throw jee;
        }

        log.debug("Executing task " + taskType + ", " + taskName + " for node: " + nodeId + ", suiteId: " + suiteId);

        /* Get connection to the DataONE MN or CN */
        try {
            mrc = new HttpMultipartRestClient();
        } catch (Exception e) {
            log.error(taskName + ": error creating rest client: " + e.getMessage());
            JobExecutionException jee = new JobExecutionException(e);
            jee.setRefireImmediately(false);
            throw jee;
        }

        Session session = DataONE.getSession(subjectId, DataONEauthToken);

        // Don't know node type yet from the id, so have to manually check if it's a CN
        Boolean isCN = DataONE.isCN(nodeServiceUrl);
        if(isCN) {
            cnNode = new MultipartCNode(mrc, nodeServiceUrl, session);
        } else {
            mnNode = new MultipartMNode(mrc, nodeServiceUrl, session);
        }

        // Get a connection to the database
        MDQStore store = null;

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

        ArrayList<Node> nodes = new ArrayList<>();

        /* If the CN is being harvested, then get all the nodes in the node db. The node db contains
           info about all nodes registered with the CN.
         */
        if (isCN) {
            nodes = store.getNodes();
        } else {
            Node node = store.getNode(nodeId);
            if (node.getIdentifier().getValue() == null) {
                String msg = ("Node entry not found for node: " + nodeId);
                log.error(msg);
                JobExecutionException jee = new JobExecutionException(msg);
                jee.setRefireImmediately(false);
                throw jee;
            } else {
                log.trace("Got node " + node.getIdentifier().getValue());
                nodes.add(node);
            }
        }

        /* Depending on the scheduled task, either process a single MN or if the task is for the CN,
           process all nodes current registered with the CN.
         */
        String harvestNodeId = null;
        for (Node node : nodes) {

            harvestNodeId = node.getIdentifier().getValue();
            // If processing a CN, check each MN to see if it is being synchronized and if it
            // is marked as up.
            if (isCN) {
                // The NodeList task doesn't save CN entries from the DataONE 'listNodes()' service, but check
                // just in case.
                if (node.getType().equals(NodeType.CN)) {
                    log.debug("Harvesting from CN, skipping CN entry from node list for " + node.getIdentifier().getValue());
                    continue;
                }

                // Skip MN entries that have not been synchronized
                if (! node.isSynchronize() || ! node.getState().equals(NodeState.UP)) {
                    log.trace("Skipping disabled node: " + node.getIdentifier().getValue() + ", sync: " + node.isSynchronize()
                            + ", status: " + node.getState().toString());
                    continue;
                }

                DateTime mnLastHarvestDT = new DateTime(node.getSynchronization().getLastHarvested(), DateTimeZone.UTC);
                DateTime oneMonthAgoDT = new DateTime(DateTimeZone.UTC).minusMonths(1);

                /* If an MN hasn't been harvested for a month, then skip it - we don't want to waste time contacting MNs that
                   don't have new content.
                 */
                if (mnLastHarvestDT.isBefore(oneMonthAgoDT.toInstant())) {
                    DateTimeZone.setDefault(DateTimeZone.UTC);
                    DateTimeFormatter dtfOut = DateTimeFormat.forPattern("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'");
                    log.trace("Skipping node " + node.getIdentifier().getValue() + " that hasn't been sync'd since " + dtfOut.print(mnLastHarvestDT));
                    continue;
                }

            }

            log.trace("Harvesting node: " + node.getIdentifier().getValue());

            // Set UTC as the default time zone for all DateTime operations.
            // Get current datetime, which may be used for start time range.
            DateTimeZone.setDefault(DateTimeZone.UTC);
            DateTime currentDT = new DateTime(DateTimeZone.UTC);
            DateTimeFormatter dtfOut = DateTimeFormat.forPattern("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'");
            String lastHarvestDateStr = null;

            Task task;
            task = store.getTask(taskName, taskType, harvestNodeId);
            // If a 'task' entry has not been saved for this task name yet (i.e. this is an MN that has just been
            // registerd with DataONE), then a 'lastHarvested' DataTime will not be available, in which case the
            // 'startHarvestDataTime' from the config file will be used.
            if (task.getLastHarvestDatetime(harvestNodeId) == null) {
                task.setTaskName(taskName);
                task.setTaskType(taskType);
                lastHarvestDateStr = startHarvestDatetimeStr;
                task.setLastHarvestDatetime(lastHarvestDateStr, harvestNodeId);
            } else {
                lastHarvestDateStr = task.getLastHarvestDatetime(harvestNodeId);
            }

            DateTime lastHarvestDateDT = new DateTime(lastHarvestDateStr);
            // Set the search start datetime to the last harvest datetime, unless it is in the
            // future. (This can happen when the previous time range end was for the current day,
            // as the end datetime range for the previous task run will have been stored as the
            // new lastharvestDateTime.
            DateTime startDT = null;
            if (lastHarvestDateDT.isAfter(currentDT.toInstant())) {
                startDT = currentDT;
            } else {
                startDT = new DateTime(lastHarvestDateDT);
            }

            DateTime endDT = new DateTime(currentDT);

            // If the start and end harvest dates are the same (happens for a new node), then
            // tweak the start so that DataONE listObjects doesn't complain.
            if (startDT == endDT) {
                startDT = startDT.minusMinutes(1);
            }

            // Track the sysmeta dateUploaded of the latest harvested pid. This will become the starting time of
            // the next harvest.
            DateTime lastDateModifiedDT = startDT;

            String startDTstr = dtfOut.print(startDT);
            String endDTstr = dtfOut.print(endDT);

            log.trace("start time: " + startDTstr);

            Integer startCount = new Integer(0);
            ListResult result = null;
            Integer totalResultCount = 0;
            Integer filteredResultCount = 0;
            Integer allPidsCnt = 0;

            log.trace("Getting pids for nodeId: " + harvestNodeId);
            boolean morePids = true;
            while (morePids) {
                ArrayList<String> pidsToProcess = null;
                try {
                    result = getPidsToProcess(cnNode, mnNode, isCN, session, suiteId, pidFilter, startDTstr, endDTstr, startCount, countRequested, lastDateModifiedDT, harvestNodeId, taskName);
                    pidsToProcess = result.getResult();
                    totalResultCount = result.getTotalResultCount();
                    filteredResultCount = result.getFilteredResultCount();
                    lastDateModifiedDT = result.getLastDateModified();
                } catch (Exception e) {
                    JobExecutionException jee = new JobExecutionException("Unable to get pids to process", e);
                    jee.setRefireImmediately(false);
                    throw jee;
                }

                allPidsCnt += pidsToProcess.size();
                for (String pidStr : pidsToProcess) {
                    try {
                        log.debug(taskName + ": submitting pid: " + pidStr);
                        submitReportRequest(cnNode, mnNode, isCN, session, qualityServiceUrl, pidStr, suiteId);
                    } catch (org.dataone.service.exceptions.NotFound nfe) {
                        log.error("Unable to process pid: " + pidStr + nfe.getMessage());
                        continue;
                    } catch (Exception e) {
                        log.error("Unable to process pid:  " + pidStr + " - " + e.getMessage());
                        continue;
                    }
                }

                // Check if DataONE returned the max number of results. If so, we have to request more by paging through
                // the results returned pidsToProcess (i.e. DataONE listObjects service). If the returned result is
                // less than the requested result, then all pids have been retrieved.
                if (totalResultCount >= countRequested) {
                    morePids = true;
                    startCount = startCount + totalResultCount;
                    log.trace("Paging through more results, current start is " + startCount);
                } else {
                    morePids = false;
                }
            }
            // Don't update the lastHarvestDateDT if no pids were found.
            if (allPidsCnt > 0) {
                // Add a millisecond to the last modified datetime, as this date will be used for the next scheduled
                // harvest, and we don't want to re-harvest this same pid again. Note that the DataONE object service
                // (get) does harvest based on requested milliseonds.
                task.setLastHarvestDatetime(dtfOut.print(lastDateModifiedDT.plusMillis(1)), harvestNodeId);
                log.trace("Saving lastHarvestDate: " + dtfOut.print(lastDateModifiedDT.plusMillis(1)) + " for node: " + harvestNodeId);
                try {
                    store.saveTask(task, harvestNodeId);
                } catch (MetadigStoreException mse) {
                    log.error("Error saving task: " + task.getTaskName());
                    JobExecutionException jee = new JobExecutionException("Unable to save new harvest date", mse);
                    jee.setRefireImmediately(false);
                    throw jee;
                }
                log.info(taskName + ": found " + allPidsCnt + " pids for nodeId: " + harvestNodeId + ", start: " + startDTstr + ", end: " + endDTstr + ", servierUrl: " + nodeServiceUrl);
            }
        }
        store.shutdown();
    }

    /**
     * Query a DataONE CN or MN to obtain a list of persistent identifiers (pids) for metadata objects have been
     * added to the system during a specific time period.
     * @param cnNode a DataONE CN connection client object
     * @param mnNode a DataONE MN connection client object
     * @param isCN a logical indicating whether a CN of MN object is being used
     * @param session a DataONE authentication session
     * @param suiteId the quality suite to check (if this pids has already been processed)
     * @param pidFilter the DataONE format identifies to filter for
     * @param startHarvestDatetimeStr the starting date to harvest pids from
     * @param endHarvestDatetimeStr the ending data to harvest pids from
     * @param startCount the start count for paging results from DataONE, for large results
     * @param countRequested the number of items to get from DataONE on each request
     * @param lastDateModifiedDT the sysmeta 'dateSystemMetadataModified' value of the last harvested pid
     * @param nodeIdFilter filter results for this nodeId (applies only to CN)
     * @throws Exception if there is an exception while executing the job.
     * @return a ListResult object containing the matching pids
     */
    public ListResult getPidsToProcess(MultipartCNode cnNode, MultipartMNode mnNode, Boolean isCN, Session session,
                                  String suiteId, String pidFilter, String startHarvestDatetimeStr,
                                  String endHarvestDatetimeStr, int startCount,
                                  int countRequested, DateTime lastDateModifiedDT, String nodeIdFilter, String taskName) throws Exception {

        ArrayList<String> pids = new ArrayList<String>();
        InputStream qis = null;
        ObjectList objList = null;

        ObjectFormatIdentifier formatId = null;
        NodeReference nodeRef = null;
        Identifier identifier = null;
        Boolean replicaStatus = false;

        // Do some back-flips to convert the start and end date to the ancient Java 'Date' type that is
        // used by DataONE 'listObjects()'.
        ZonedDateTime zdt = ZonedDateTime.parse(startHarvestDatetimeStr);
        // start date milliseconds since the epoch date "midnight, January 1, 1970 UTC"
        long msSinceEpoch = zdt.toInstant().toEpochMilli();
        Date startDate = new Date(msSinceEpoch);

        zdt = ZonedDateTime.parse(endHarvestDatetimeStr);
        msSinceEpoch = zdt.toInstant().toEpochMilli();
        Date endDate = new Date(msSinceEpoch);

        try {
            // Even though MultipartMNode and MultipartCNode have the same parent class D1Node, the interface for D1Node doesn't
            // include listObjects, as the parameters differ from CN to MN, so we have to use a different object for each.
            if(isCN) {
                log.trace("Getting pids for cn, for nodeid: " + nodeIdFilter);
                nodeRef = new NodeReference();
                nodeRef.setValue(nodeIdFilter);
                objList = cnNode.listObjects(session, startDate, endDate, formatId, nodeRef, identifier, startCount, countRequested);
            } else {
                log.trace("Getting pids for mn");
                objList = mnNode.listObjects(session, startDate, endDate, formatId, identifier, replicaStatus, startCount, countRequested);
            }
            //log.info("Got " + objList.getCount() + " pids for format: " + formatId.getValue() + " pids.");
        } catch (Exception e) {
            log.error(taskName + ": error retrieving pids: " + e.getMessage());
            throw e;
        }

        String thisFormatId = null;
        String thisPid = null;
        int pidCount = 0;
        DateTime thisDateModifiedDT;

        if (objList.getCount() > 0) {
            for(ObjectInfo oi: objList.getObjectInfoList()) {
                thisFormatId = oi.getFormatId().getValue();
                thisPid = oi.getIdentifier().getValue();
                log.trace("Checking pid: " + thisPid + ", format: " + thisFormatId);

                // Check all pid filters to see if this pids's format was found in the list of desired formats.
                // There could be multiple wildcard filters, which are separated by ','.
                String [] filters = pidFilter.split("\\|");
                Boolean found = false;
                for(String thisFilter:filters) {
                    if(thisFormatId.matches(thisFilter)) {
                        found = true;
                        continue;
                    }
                }

                // Always re-create a report, even if it exists for a pid, as the sysmeta could have
                // been updated (i.e. obsoletedBy, access) and the quality report and index contain
                // sysmeta fields.
                if(found) {
                //    if (!runExists(thisPid, suiteId, store)) {
                    pidCount = pidCount++;
                    pids.add(thisPid);
                    log.trace("adding pid " + thisPid + ", formatId: " + thisFormatId);
                    // If this pid's modified date is after the stored latest encountered modified date, then update
                    // the lastModified date
                    thisDateModifiedDT = new DateTime(oi.getDateSysMetadataModified());
                    // Add a millisecond to lastDateModfiedDT so that this pid won't be harvested again (in the event
                    // that this is the last pid to be harvested in this round.
                    if (thisDateModifiedDT.isAfter(lastDateModifiedDT)) {
                        lastDateModifiedDT = thisDateModifiedDT.plusMillis(1);
                        log.debug("New value for lastDateMoidifed: " + lastDateModifiedDT.toString());
                    }
                //    }
                }
            }
        }

        ListResult result = new ListResult();
        // Set the count for the number of desired pids filtered from the total result set
        result.setFilteredResultCount(pidCount);
        // Set the count for the total number of pids returned from DataONE (all formatIds) for this query
        result.setTotalResultCount(objList.getCount());
        result.setResult(pids);
        // Return the sysmeta 'dateSystemMetadataModified' of the last pid harvested.
        result.setLastDateModified(lastDateModifiedDT);

        return result;
    }


    /**
     * Check if the specified quality suite has already been run for a pid.
     * <p>
     * An additional check is made to see if the system metadata in the
     * run is older than the passed in date. Because the quality engine
     * uses fields from sysmeta (obsoletes, obsoletedBy), a run may need
     * to be performed on an existing run in order to update the sysmeta, as
     * the system is stored in the run object, and this run object is
     * parsed when the run is inserted into the Solr index.
     * </p>
     * @param pid the pid to check
     * @param suiteId the suite identifier to check (e.g. "FAIR-suite-0.3.1")
     * @param store the DataStore object to send the check request to.
     * @throws MetadigStoreException
     *
     */
    public boolean runExists(String pid, String suiteId, MDQStore store, Date dateSystemMetadataModified) throws MetadigStoreException {

        boolean found = false;
        Date runDateSystemMetadataModified = null;

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

    /**
     * Submit a request to the metadig controller to run a quality suite for the specified pid.
     * <p>
     *     The system metadata for a pid is also obtained and sent with the request
     * </p>
     *
     * @param cnNode a DataONE CN connection client object
     * @param mnNode a DataONE MN connection client object
     * @param isCN a logical indicating whether a CN of MN object
     * @param session a DataONE authentication session
     * @param qualityServiceUrl the URL of the MetaDIG quality service
     * @param pidStr the pid to submit the request for
     * @param suiteId the suite identifier to submit the request for
     *
     * @throws Exception
     */
    public void submitReportRequest(MultipartCNode cnNode, MultipartMNode mnNode, Boolean isCN,  Session session, String qualityServiceUrl, String pidStr, String suiteId) throws Exception {

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
            log.trace("Retrieved metadata object for pid: " + pidStr);
        } catch (NotAuthorized na) {
            log.error("Not authorized to read pid: " + pid + ", unable to retrieve metadata, continuing with next pid...");
            return;
        }

        // quality suite service url, i.e. "http://docke-ucsb-1.dataone.org:30433/quality/suites/knb.suite.1/run
        qualityServiceUrl = qualityServiceUrl + "/suites/" + suiteId + "/run";
        HttpPost post = new HttpPost(qualityServiceUrl);

        // add document
        SimpleMultipartEntity entity = new SimpleMultipartEntity();
        entity.addFilePart("document", objectIS);

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        TypeMarshaller.marshalTypeToOutputStream(sysmeta, baos);
        entity.addFilePart("systemMetadata", new ByteArrayInputStream(baos.toByteArray()));

        // make sure we get XML back
        post.addHeader("Accept", "application/xml");

        // send to service
        log.trace("submitting: " + qualityServiceUrl);
        post.setEntity((HttpEntity) entity);
        CloseableHttpClient client = HttpClients.createDefault();
        CloseableHttpResponse response = client.execute(post);

        // retrieve results
        HttpEntity reponseEntity = response.getEntity();
        if (reponseEntity != null) {
            runResultIS = reponseEntity.getContent();
        }
    }
}
