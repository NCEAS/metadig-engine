package edu.ucsb.nceas.mdqengine.scheduler;

import edu.ucsb.nceas.mdqengine.exception.MetadigStoreException;
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
import org.dataone.service.types.v1.Session;
import org.dataone.service.exceptions.NotAuthorized;
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
            throw new JobExecutionException("Cannot create store, unable to schedule job", e);
        }

        if (!store.isAvailable()) {
            try {
                store.renew();
            } catch (MetadigStoreException e) {
                e.printStackTrace();
                throw new JobExecutionException("Cannot renew store, unable to schedule job", e);
            }
        }

        // query database
        try {
            processing = store.listInProcessRuns();
        } catch (MetadigStoreException e) {
            e.printStackTrace();
        }

        // request job via rabbitMQ
        for (Run run : processing) {
            log.info("Requesting monitor job: " + run.getId() + run.getStatus() + run.getNodeId());
            Session session = getSession();
            String pidStr = run.getId();
            String suiteId = run.getSuiteId();
            String nodeId = run.getNodeId();
            // the methods below are pretty clunky
            InputStream metadata = getMetadata(pidStr, nodeId, session);
            InputStream sysmeta = getSystemMetadata(pidStr, nodeId, session);

            String localFilePath = null;
            DateTime requestDateTime = new DateTime(DateTimeZone.forOffsetHours(-7));
            try {
                controller.processQualityRequest(nodeId, pidStr, metadata, suiteId,
                        localFilePath, requestDateTime,
                        sysmeta);
            } catch (IOException io) {
                io.printStackTrace();
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
     * @throws JobExecutionException if there is an error executing the quartz task
     */
    public Session getSession() throws JobExecutionException {

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
            JobExecutionException jee = new JobExecutionException("error executing task.");
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
     * @param pidStr  the pid of the object
     * @param nodeId  the identifier of the node where the object is located
     * @param session the session used to authenticate the request
     * @return an InputStream containing the metadata of the specified object
     * 
     * @throws JobExecutionException if there is an error executing the quartz task
     */
    public InputStream getMetadata(String pidStr, String nodeId, Session session) throws JobExecutionException {

        String nodeServiceUrl = null;
        MultipartRestClient mrc = null;
        MultipartMNode mnNode = null;
        MultipartCNode cnNode = null;
        InputStream objectIS = null;
        Identifier pid = new Identifier();
        pid.setValue(pidStr);

        try {
            MDQconfig cfg = new MDQconfig();
            String nodeAbbr = nodeId.replace("urn:node:", "");
            nodeServiceUrl = cfg.getString(nodeAbbr + ".serviceUrl");

        } catch (ConfigurationException | IOException ce) {
            JobExecutionException jee = new JobExecutionException("Monitor: error executing task.");
            jee.initCause(ce);
            throw jee;
        }

        try {
            mrc = new HttpMultipartRestClient();
        } catch (Exception e) {
            log.error("Monitor: error creating rest client: " + e.getMessage());
            JobExecutionException jee = new JobExecutionException(e);
            jee.setRefireImmediately(false);
            throw jee;
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
            log.trace("Monitor: Retrieved metadata object for pid: " + pidStr);
        } catch (NotAuthorized na) {
            log.error("Monitor: Not authorized to read pid: " + pidStr + ", unable to retrieve object");
        } catch (NotImplemented ni) {
            log.error("Monitor: Service not implemented for pid: " + pidStr + ", unable to retrieve object");
        } catch (InvalidToken it) {
            log.error("Monitor: Invalid token for pid: " + pidStr + ", unable to retrieve object");
        } catch (NotFound nf) {
            log.error("Monitor: Object not found for pid: " + pidStr + ", unable to retrieve object");
        } catch (ServiceFailure sf) {
            log.error("Monitor: Service failure for pid: " + pidStr + ", unable to retrieve object");
        } catch (InsufficientResources ir) {
            log.error("Monitor: Insufficient resources for pid: " + pidStr + ", unable to retrieve object");
        }

        return objectIS;

    }

    /**
     * Retrieves the system metadata of an object specified by the provided pid
     * from a specified node using the given session.
     * 
     * @param pidStr  the pid of the object
     * @param nodeId  the identifier of the node where the object is located
     * @param session the session used to authenticate the request
     * @return an InputStream containing the system metadata of the specified object
     * 
     * @throws JobExecutionException if there is an error executing the quartz task
     */
    public InputStream getSystemMetadata(String pidStr, String nodeId, Session session) throws JobExecutionException {

        String nodeServiceUrl = null;
        MultipartRestClient mrc = null;
        MultipartMNode mnNode = null;
        MultipartCNode cnNode = null;
        SystemMetadata sysmeta = null;
        InputStream sysmetaIS = null;
        Identifier pid = new Identifier();
        pid.setValue(pidStr);

        try {
            MDQconfig cfg = new MDQconfig();
            String nodeAbbr = nodeId.replace("urn:node:", "");
            nodeServiceUrl = cfg.getString(nodeAbbr + ".serviceUrl");

        } catch (ConfigurationException | IOException ce) {
            JobExecutionException jee = new JobExecutionException("Monitor: error executing task.");
            jee.initCause(ce);
            throw jee;
        }

        try {
            mrc = new HttpMultipartRestClient();
        } catch (Exception e) {
            log.error("Monitor: error creating rest client: " + e.getMessage());
            JobExecutionException jee = new JobExecutionException(e);
            jee.setRefireImmediately(false);
            throw jee;
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

                }

            } else {
                sysmeta = mnNode.getSystemMetadata(session, pid);
                try {
                    ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
                    TypeMarshaller.marshalTypeToOutputStream(sysmeta, outputStream);
                    sysmetaIS = new ByteArrayInputStream(outputStream.toByteArray());
                } catch (MarshallingException | IOException me) {

                }
            }
            log.trace("Monitor: Retrieved metadata object for pid: " + pidStr);
        } catch (NotAuthorized na) {
            log.error("Monitor: Not authorized to read pid: " + pid + ", unable to retrieve object");
        } catch (NotImplemented ni) {
            log.error("Monitor: Service not implemented for pid: " + pid + ", unable to retrieve object");
        } catch (InvalidToken it) {
            log.error("Monitor: Invalid token for pid: " + pid + ", unable to retrieve object");
        } catch (NotFound nf) {
            log.error("Monitor: Object not found for pid: " + pid + ", unable to retrieve object");
        } catch (ServiceFailure sf) {
            log.error("Monitor: Service failure for pid: " + pid + ", unable to retrieve object");
        }

        return sysmetaIS;

    }

}
