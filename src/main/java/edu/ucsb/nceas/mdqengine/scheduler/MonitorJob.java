package edu.ucsb.nceas.mdqengine.scheduler;

import edu.ucsb.nceas.mdqengine.exception.MetadigStoreException;
import edu.ucsb.nceas.mdqengine.exception.MetadigException;
import edu.ucsb.nceas.mdqengine.model.Run;
import edu.ucsb.nceas.mdqengine.MDQconfig;
import edu.ucsb.nceas.mdqengine.DataONE;
import edu.ucsb.nceas.mdqengine.store.DatabaseStore;
import edu.ucsb.nceas.mdqengine.store.MDQStore;
import edu.ucsb.nceas.mdqengine.Controller;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.commons.configuration2.ex.ConfigurationException;

import org.dataone.client.rest.HttpMultipartRestClient;
import org.dataone.client.rest.MultipartRestClient;
import org.dataone.client.v2.impl.MultipartCNode;
import org.dataone.client.v2.impl.MultipartMNode;
import org.dataone.exceptions.MarshallingException;
import org.dataone.service.exceptions.*;
import org.dataone.service.types.v1.*;
import org.dataone.service.types.v2.SystemMetadata;
import org.dataone.service.util.*;

import org.quartz.*;

import java.util.ArrayList;
import java.util.List;
import java.io.IOException;
import java.io.InputStream;

import java.io.ByteArrayOutputStream;
import java.io.ByteArrayInputStream;
import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;

/**
 * A Quartz job implementation for monitoring for metadata quality requests
 * 'stuck' with a status of 'processing' in the persistent store for more than
 * 24 hours. This could happen if the Worker processing the job dies
 * unexpectedly after it saves the run with processing status, but before
 * finishing and updating that status to success. No jobs should take longer
 * than 24 hours to complete. If the job has been run 10 times and still has a
 * status of 'processing' the MonitorJob is not run, and the status is updated
 * to 'error' (see Controller)
 */
@DisallowConcurrentExecution
public class MonitorJob implements Job {

    private final Log log = LogFactory.getLog(RequestReportJob.class);
    private Controller controller = null;

    /**
     * Default constructor for the MonitorJob class.
     * Initializes the MonitorJob object by getting an instance of the Controller,
     * which is a singleton.
     */
    public MonitorJob() {
        this.controller = Controller.getInstance();
    }

    /**
     * Execute method for the MonitorJob.
     *
     * @param context JobExecutionContext object with environment information
     * @throws JobExecutionException
     */
    public void execute(JobExecutionContext context) throws JobExecutionException {

        MDQStore store = null;
        List<Run> processing = new ArrayList<Run>();
        // Get a connection to the database

        try {
            store = new DatabaseStore();
        } catch (MetadigStoreException e) {
            e.printStackTrace();
            JobExecutionException jee = new JobExecutionException("Cannot create store, unable to schedule job", e);
            throw jee;
        }

        if (!store.isAvailable()) {
            try {
                store.renew();
            } catch (MetadigStoreException e) {
                e.printStackTrace();
                JobExecutionException jee = new JobExecutionException("Cannot renew store, unable to schedule job", e);
                throw jee;
            }
        }

        // query database
        try {
            processing = store.listInProcessRuns();
        } catch (MetadigStoreException e) {
            JobExecutionException jee = new JobExecutionException("Monitor: Error getting in process runs from store.",
                    e);
            throw jee;
        }

        if (processing.isEmpty()) { // if no stuck jobs are found go ahead and exit
            log.info("Monitor: No stuck jobs found.");
            return;
        }

        // get a session
        Session session = null;
        try {
            session = getSession();
        } catch (MetadigException me) {
            JobExecutionException jee = new JobExecutionException("Could not connect to a DataONE session." + me);
            jee.setRefireImmediately(true);
            throw jee;
        }

        // request job via rabbitMQ
        for (Run run : processing) {
            log.info("Requesting monitor job: " + run.getId() + ", " + run.getNodeId());

            String suiteId = run.getSuiteId();
            String pidStr = run.getId();
            String nodeId = run.getNodeId();
            InputStream metadata = null;
            InputStream sysmeta = null;

            try {
                metadata = getMetadata(run, session);
            } catch (MetadigException me) {
                JobExecutionException jee = new JobExecutionException(me);
                jee.setRefireImmediately(true);
                log.error("Problem getting metadata:" + me.getMessage());
                continue; // the run will be refired immediately, continue to next run
            } catch (ConfigurationException ce) {
                JobExecutionException jee = new JobExecutionException(ce);
                jee.setRefireImmediately(false);
                log.error("Configuration error:" + ce.getMessage());
                continue; // the run will NOT be refired immediately, continue to next run
            }

            try {
                sysmeta = getSystemMetadata(run, session);
            } catch (MetadigException me) {
                JobExecutionException jee = new JobExecutionException(me);
                jee.setRefireImmediately(true);
                log.error("Problem getting metadata:" + me.getMessage());
                continue; // the run will be refired immediately, continue to next run
            } catch (ConfigurationException ce) {
                JobExecutionException jee = new JobExecutionException(ce);
                jee.setRefireImmediately(false);
                log.error("Configuration error:" + ce.getMessage());
                continue; // the run will NOT be refired immediately, continue to next run
            }

            if (metadata == null | sysmeta == null) { // any case where the metadata or sysmeta should be thrown above
                log.error("Monitor: Aborting run - Metadata or system metadata not found for " + pidStr);
                continue;
            }

            String localFilePath = null;
            DateTime requestDateTime = new DateTime(DateTimeZone.forOffsetHours(-7));
            try {
                controller.processQualityRequest(nodeId, pidStr, metadata, suiteId,
                        localFilePath, requestDateTime,
                        sysmeta);
            } catch (IOException io) {
                JobExecutionException jee = new JobExecutionException("Monitor: Error processing quality request.");
                jee.initCause(io);
                throw jee;
            }

        }
        store.shutdown();
    }

    /**
     * Establishes an authenticated session using the dataOneAuthToken in
     * metadig.properties
     * 
     * @return a Session object
     * 
     * @throws MetadigException If a session cannot be created
     */
    public Session getSession() throws MetadigException {

        String dataOneAuthToken = null;
        String subjectId = null;

        try {
            MDQconfig cfg = new MDQconfig();
            dataOneAuthToken = System.getenv("DATAONE_AUTH_TOKEN");
            if (dataOneAuthToken == null) {
                dataOneAuthToken = cfg.getString("DataONE.authToken");
                log.debug("Got token from properties file.");
            } else {
                log.debug("Got token from env.");
            }
        } catch (ConfigurationException | IOException ce) {
            MetadigException jee = new MetadigException("error executing task.");
            jee.initCause(ce);
            throw jee;
        }
        Session session = DataONE.getSession(subjectId, dataOneAuthToken);
        return session;
    }

    /**
     * Retrieves the metadata of an object specified by the provided pid
     * from a specified node using the given session.
     * 
     * @param run     a Run object
     * @param session the session used to authenticate the request
     * @return an InputStream containing the metadata of the specified object
     * 
     * @throws MetadigException       If a NotAuthorized, InvalidToken,
     *                                ServiceFailure,
     *                                InsufficientResources, or NotFound exception
     *                                is hit,
     *                                or if a failed run cannot be saved to the DB
     * 
     * @throws ConfigurationException If a node is not supported in the
     *                                configuration file.
     */
    public InputStream getMetadata(Run run, Session session) throws MetadigException, ConfigurationException {

        String nodeServiceUrl = null;
        MultipartRestClient mrc = null;
        MultipartMNode mnNode = null;
        MultipartCNode cnNode = null;
        InputStream objectIS = null;
        String pidStr = run.getId();
        String nodeId = run.getNodeId();
        Identifier pid = new Identifier();
        pid.setValue(pidStr);

        try {
            MDQconfig cfg = new MDQconfig();
            String nodeAbbr = nodeId.replace("urn:node:", "");
            nodeServiceUrl = cfg.getString(nodeAbbr + ".serviceUrl");

            if (nodeServiceUrl == null) {
                log.warn("Node is not supported in the configuration file, trying the CN.");
                nodeServiceUrl = cfg.getString("CN.serviceUrl");
            }

        } catch (IOException ce) {
            MetadigException me = new MetadigException("Monitor: Error reading configuration.");
            throw me;
        }

        try {
            mrc = new HttpMultipartRestClient();
        } catch (Exception e) {
            log.error("Monitor: error creating rest client: " + e.getMessage());
            MetadigException me = new MetadigException("Monitor: error creating rest client");
            throw me;
        }

        Boolean isCN = DataONE.isCN(nodeServiceUrl);
        if (isCN) {
            cnNode = new MultipartCNode(mrc, nodeServiceUrl, session);
        } else {
            mnNode = new MultipartMNode(mrc, nodeServiceUrl, session);
        }

        try {
            if (isCN) {
                objectIS = cnNode.get(session, pid);
            } else {
                objectIS = mnNode.get(session, pid);
            }
            log.debug("Monitor: Retrieved metadata object for pid: " + pidStr);
        } catch (NotAuthorized na) { // handle this in the caller by refiring, possible token just expired and needed
                                     // to be renewed
            log.warn("Monitor: Not authorized to read pid: " + pidStr + ", unable to retrieve object");
            MetadigException jee = new MetadigException(na);
            throw jee;
        } catch (NotImplemented ni) { // save this to the DB, absorb and don't retry the run
            log.error("Monitor: Service not implemented for pid: " + pidStr
                    + ", unable to retrieve object. Saving run with status: failure");
            // set a failure status for the run
            run.setRunStatus(Run.FAILURE);
            run.setErrorDescription("DataONE exception: service not implemented.");
            try {
                run.save();
            } catch (MetadigException me) {
                MetadigException jee = new MetadigException(me);
                throw jee;
            }
        } catch (InvalidToken it) { // handle this in the caller by refiring, possible token just expired and needed
                                    // to be renewed
            log.warn("Monitor: Invalid token for pid: " + pidStr + ", unable to retrieve object");
            MetadigException jee = new MetadigException(it);
            throw jee;
        } catch (NotFound nf) { // handle this in caller and refire it
            log.warn("Monitor: Object not found for pid: " + pidStr + ", unable to retrieve object");
            MetadigException jee = new MetadigException(nf);
            throw jee;
        } catch (ServiceFailure sf) { // handle this in the caller by refiring, possible random datone outage
            log.error("Monitor: Service failure for pid: " + pidStr + ", unable to retrieve object");
            MetadigException me = new MetadigException(sf);
            throw me;
        } catch (InsufficientResources ir) { // handle this in the caller by refiring, possible random datone outage
            log.error("Monitor: Insufficient resources for pid: " + pidStr + ", unable to retrieve object");
            MetadigException me = new MetadigException(ir);
            throw me;
        }

        return objectIS;

    }

    /**
     * Retrieves the system metadata of an object specified by the provided pid
     * from a specified node using the given session.
     * 
     * @param run     a Run object
     * @param session the session used to authenticate the request
     * @return an InputStream containing the system metadata of the specified object
     * 
     * @throws MetadigException       If a NotAuthorized, InvalidToken,
     *                                ServiceFailure,
     *                                InsufficientResources, NotFound exception is
     *                                hit, or
     *                                if a failed run (NotImplemented) cannot be
     *                                saved to
     *                                the DB
     * 
     * @throws ConfigurationException If a node is not supported in the
     *                                configuration file.
     */
    public InputStream getSystemMetadata(Run run, Session session) throws MetadigException, ConfigurationException {
        // throw a different exception here

        String nodeServiceUrl = null;
        MultipartRestClient mrc = null;
        MultipartMNode mnNode = null;
        MultipartCNode cnNode = null;
        SystemMetadata sysmeta = null;
        InputStream sysmetaIS = null;
        String pidStr = run.getId();
        String nodeId = run.getNodeId();
        Identifier pid = new Identifier();
        pid.setValue(pidStr);

        try {
            MDQconfig cfg = new MDQconfig();
            String nodeAbbr = nodeId.replace("urn:node:", "");
            nodeServiceUrl = cfg.getString(nodeAbbr + ".serviceUrl");

            if (nodeServiceUrl == null) {
                log.warn("Node is not supported in the configuration file, trying the CN.");
                nodeServiceUrl = cfg.getString("CN.serviceUrl");
            }

        } catch (IOException ce) {
            MetadigException me = new MetadigException("Monitor: Error reading configuration.");
            throw me;
        }

        try {
            mrc = new HttpMultipartRestClient();
        } catch (Exception e) {
            log.error("Monitor: error creating rest client: " + e.getMessage());
            MetadigException me = new MetadigException("Monitor: error creating rest client");
            throw me;
        }

        Boolean isCN = DataONE.isCN(nodeServiceUrl);
        if (isCN) {
            cnNode = new MultipartCNode(mrc, nodeServiceUrl, session);
        } else {
            mnNode = new MultipartMNode(mrc, nodeServiceUrl, session);
        }

        try {
            if (isCN) {
                sysmeta = cnNode.getSystemMetadata(session, pid);
                try {
                    ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
                    TypeMarshaller.marshalTypeToOutputStream(sysmeta, outputStream);
                    sysmetaIS = new ByteArrayInputStream(outputStream.toByteArray());
                } catch (MarshallingException | IOException me) {
                    log.error("Monitor: Unable to convert system metadta to InputStream");
                    MetadigException jee = new MetadigException(me);
                    throw jee;
                }

            } else {
                sysmeta = mnNode.getSystemMetadata(session, pid);
                try {
                    ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
                    TypeMarshaller.marshalTypeToOutputStream(sysmeta, outputStream);
                    sysmetaIS = new ByteArrayInputStream(outputStream.toByteArray());
                } catch (MarshallingException | IOException me) {
                    log.error("Monitor: Unable to convert system metadta to InputStream");
                    MetadigException jee = new MetadigException(me);
                    throw jee;
                }
            }
            log.trace("Monitor: Retrieved metadata object for pid: " + pidStr);
        } catch (NotAuthorized na) { // handle this in the caller by refiring, possible token just expired and needed
            // to be renewed
            log.warn("Monitor: Not authorized to read pid: " + pidStr + ", unable to retrieve object");
            MetadigException jee = new MetadigException(na);
            throw jee;
        } catch (NotImplemented ni) { // save this to the DB, absorb and don't retry the run
            log.error("Monitor: Service not implemented for pid: " + pidStr
                    + ", unable to retrieve object. Saving run with status: failure");
            // set a failure status for the run
            run.setRunStatus(Run.FAILURE);
            run.setErrorDescription("DataONE exception: service not implemented.");
            try {
                run.save();
            } catch (MetadigException me) {
                MetadigException jee = new MetadigException(me);
                throw jee;
            }
        } catch (InvalidToken it) { // handle this in the caller by refiring, possible token just expired and needed
            // to be renewed
            log.warn("Monitor: Invalid token for pid: " + pidStr + ", unable to retrieve object");
            MetadigException jee = new MetadigException(it);
            throw jee;
        } catch (NotFound nf) { // handle this in caller and refire it
            log.warn("Monitor: Object not found for pid: " + pidStr + ", unable to retrieve object");
            MetadigException jee = new MetadigException(nf);
            throw jee;
        } catch (ServiceFailure sf) { // handle this in the caller by refiring, possible random datone outage
            log.error("Monitor: Service failure for pid: " + pidStr + ", unable to retrieve object");
            MetadigException me = new MetadigException(sf);
            throw me;
        }

        return sysmetaIS;

    }

}
