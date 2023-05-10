package edu.ucsb.nceas.mdqengine.scheduler;

import edu.ucsb.nceas.mdqengine.exception.MetadigStoreException;
import edu.ucsb.nceas.mdqengine.model.Run;
import edu.ucsb.nceas.mdqengine.model.SysmetaModel;
import edu.ucsb.nceas.mdqengine.MDQconfig;
import edu.ucsb.nceas.mdqengine.DataONE;
import edu.ucsb.nceas.mdqengine.store.DatabaseStore;
import edu.ucsb.nceas.mdqengine.store.MDQStore;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.commons.configuration2.ex.ConfigurationException;
import org.apache.http.HttpEntity;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;

import org.dataone.client.rest.HttpMultipartRestClient;
import org.dataone.client.rest.MultipartRestClient;
import org.dataone.client.v2.impl.MultipartCNode;
import org.dataone.client.v2.impl.MultipartMNode;
import org.dataone.service.types.v1.Session;
import org.dataone.mimemultipart.SimpleMultipartEntity;
import org.dataone.service.exceptions.NotAuthorized;
import org.dataone.service.types.v1.*;
import org.dataone.service.types.v2.SystemMetadata;
import org.dataone.service.util.TypeMarshaller;

import org.quartz.*;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.io.IOException;
import java.io.InputStream;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;


// the job to run
@DisallowConcurrentExecution
public class MonitorJob implements Job {

    private Log log = LogFactory.getLog(RequestReportJob.class);

    // Default constructor
    public MonitorJob() {
    }

    public void execute(JobExecutionContext context) throws JobExecutionException {

        log.info("job");
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
            processing = store.getProcessing();
        } catch (MetadigStoreException e) {
            e.printStackTrace();
        }

        // request job via the API
        for (Run run : processing) {
            log.info(run.getId() + run.getStatus());
            MultipartRestClient mrc = null;
            String pidStr = run.getId();
            String suiteId = run.getSuiteId();
            SysmetaModel sysmeta = run.getSysmeta();
            String nodeId = sysmeta.getOriginMemberNode();
            MultipartMNode mnNode = null;
            MultipartCNode cnNode = null;


            Session session = getSession();
            HashMap<String, String> urls = getServiceUrls(nodeId);

            try {
                mrc = new HttpMultipartRestClient();
            } catch (Exception e) {
                log.error("Monitor: error creating rest client: " + e.getMessage());
                JobExecutionException jee = new JobExecutionException(e);
                jee.setRefireImmediately(false);
                throw jee;
            }

            String nodeServiceUrl = urls.get("nodeServiceUrl");
            String qualityServiceUrl = urls.get("qualityServiceUrl");
    

            // Don't know node type yet from the id, so have to manually check if it's a CN
            Boolean isCN = DataONE.isCN(nodeServiceUrl);
            if (isCN) {
                cnNode = new MultipartCNode(mrc, nodeServiceUrl, session);
            } else {
                mnNode = new MultipartMNode(mrc, nodeServiceUrl, session);
            }

            try {
                log.debug("Monitor: submitting pid: " + pidStr);
                submitReportRequest(cnNode, mnNode, isCN, session, qualityServiceUrl, pidStr, suiteId);
            } catch (org.dataone.service.exceptions.NotFound nfe) {
                log.error("Unable to process pid: " + pidStr + nfe.getMessage());
                continue;
            } catch (Exception e) {
                log.error("Unable to process pid:  " + pidStr + " - " + e.getMessage());
                continue;
            }
        }
    }

    public Session getSession() throws JobExecutionException {

        String DataONEauthToken = null;
        String subjectId = null;

        try {
            MDQconfig cfg = new MDQconfig();
            DataONEauthToken = System.getenv("DATAONE_AUTH_TOKEN");
            if (DataONEauthToken == null) {
                DataONEauthToken = cfg.getString("DataONE.authToken");
                log.debug("Got token from properties file.");
            } else {
                log.debug("Got token from env.");
            }
        } catch (ConfigurationException | IOException ce) {
            JobExecutionException jee = new JobExecutionException("error executing task.");
            jee.initCause(ce);
            throw jee;
        }


        Session session = DataONE.getSession(subjectId, DataONEauthToken);
        return session;
    }

    public HashMap<String, String> getServiceUrls(String nodeId) throws JobExecutionException {

        String nodeServiceUrl = null;
        String qualityServiceUrl = null;
        HashMap<String, String> urls = new HashMap<String, String>();

        try {
            MDQconfig cfg = new MDQconfig();
            qualityServiceUrl = cfg.getString("quality.serviceUrl");
            String nodeAbbr = nodeId.replace("urn:node:", "");
            nodeServiceUrl = cfg.getString(nodeAbbr + ".serviceUrl");
            urls.put("qualityServiceURL", qualityServiceUrl);
            urls.put("nodeServiceURL", nodeServiceUrl);

        } catch (ConfigurationException | IOException ce) {
            JobExecutionException jee = new JobExecutionException("Monitor: error executing task.");
            jee.initCause(ce);
            throw jee;
        }
        return urls;
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
