package edu.ucsb.nceas.mdqengine.scheduler;

import edu.ucsb.nceas.mdqengine.DataONE;
import edu.ucsb.nceas.mdqengine.MDQconfig;
import edu.ucsb.nceas.mdqengine.exception.MetadigStoreException;
import edu.ucsb.nceas.mdqengine.store.DatabaseStore;
import edu.ucsb.nceas.mdqengine.store.MDQStore;
import org.apache.commons.configuration2.ex.ConfigurationException;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.dataone.client.rest.HttpMultipartRestClient;
import org.dataone.client.rest.MultipartRestClient;
import org.dataone.client.v2.impl.MultipartCNode;
import org.dataone.service.exceptions.NotImplemented;
import org.dataone.service.exceptions.ServiceFailure;
import org.dataone.service.types.v1.*;
import org.dataone.service.types.v2.Node;
import org.dataone.service.types.v2.Property;
import org.quartz.*;

import java.io.IOException;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.TimeZone;

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
public class NodeList implements Job {

    private Log log = LogFactory.getLog(NodeList.class);

    // Since Quartz will re-instantiate a class every time it
    // gets executed, non-static member variables can
    // not be used to maintain state!

    /**
     * <p>
     * Called by the <code>{@link org.quartz.Scheduler}</code> when a
     * <code>{@link org.quartz.Trigger}</code> fires that is associated with
     * the <code>Job</code>.
     * </p>
     *
     * @throws JobExecutionException if there is an exception while executing the
     *                               job.
     */
    public void execute(JobExecutionContext context)
            throws JobExecutionException {

        Log log = LogFactory.getLog(NodeList.class);
        JobKey key = context.getJobDetail().getKey();
        JobDataMap dataMap = context.getJobDetail().getJobDataMap();

        String taskName = dataMap.getString("taskName");
        String taskType = dataMap.getString("taskType");
        String nodeId = dataMap.getString("nodeId");
        MultipartRestClient mrc = null;
        MultipartCNode cnNode = null;

        String nodeServiceUrl = null;

        try {
            MDQconfig cfg = new MDQconfig();
            String nodeAbbr = nodeId.replace("urn:node:", "");
            // TODO: Cache the node values from the CN listNode service
            nodeServiceUrl = cfg.getString(nodeAbbr + ".serviceUrl");
        } catch (ConfigurationException | IOException ce) {
            JobExecutionException jee = new JobExecutionException(taskName + ": error executing task.");
            jee.initCause(ce);
            throw jee;
        }

        log.debug("Executing task " + taskType + ", " + taskName + " for node: " + nodeId);

        Session session = DataONE.getSession(null, null);

        try {
            mrc = new HttpMultipartRestClient();
        } catch (Exception e) {
            log.error(taskName + ": error creating rest client: " + e.getMessage());
            JobExecutionException jee = new JobExecutionException(e);
            jee.setRefireImmediately(false);
            throw jee;
        }

        cnNode = new MultipartCNode(mrc, nodeServiceUrl, session);
        org.dataone.service.types.v2.NodeList nodeList = null;

        try {
            nodeList = cnNode.listNodes();
        } catch (NotImplemented | ServiceFailure e) {
            log.error(taskName + ": cannot renew store, unable to schedule job", e);
            throw new JobExecutionException(taskName + ": cannot renew store, unable to schedule job", e);
        }

        // Get a connection to the database

        try (DatabaseStore store = new DatabaseStore()) {
            // TODO: consider removing this if block? seems like it will never be hit
            if (!store.isAvailable()) {
                try {
                    store.renew();
                } catch (MetadigStoreException e) {
                    log.error(taskName + ": cannot renew store, unable to schedule job" + e);
                    throw new JobExecutionException(taskName + ": cannot renew store, unable to schedule job", e);
                }
            }

            Property property = null;
            ArrayList<Property> plist = null;
            for (Node node : nodeList.getNodeList()) {
                log.debug("node: " + node.getName());
                log.debug("type: " + node.getType().toString());
                log.debug("id: " + node.getIdentifier().getValue());
                log.debug("state: " + node.getState().toString());
                log.debug("is synchonized: " + node.isSynchronize());

                if (!node.isSynchronize()) {
                    log.debug(taskName + ": Skipping unsynchronized node " + node.getIdentifier().getValue());
                    continue;
                } else if (node.getType().toString().equalsIgnoreCase("MN")) {
                    log.debug(taskName + ": saving node " + node.getIdentifier().getValue());
                    try {
                        store.saveNode(node);
                    } catch (MetadigStoreException mse) {
                        log.error("Cannot save node to store." + mse.getStackTrace());
                        throw new JobExecutionException(
                                "Cannot save node " + node.getIdentifier().getValue() + " to store",
                                mse);
                    }
                } else {
                    log.debug(taskName + ": skipping CN node: " + node.getIdentifier().getValue());
                }
            }

            // For debugging purposes: retrieve and print out all node entries if trace
            // logging is enabled.
            if (log.isTraceEnabled()) {
                log.trace("Retrieving and printing out all saved node harvest dates...");

                ArrayList<Node> nodes = store.getNodes();
                for (Node node : nodes) {
                    log.trace("identifier: " + node.getIdentifier().getValue());

                    DateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'");
                    dateFormat.setTimeZone(TimeZone.getTimeZone("GMT"));
                    String lastHarvestDatetimeStr = dateFormat.format(node.getSynchronization().getLastHarvested());

                    log.trace("harvest: " + lastHarvestDatetimeStr);
                    log.trace("synchronize: " + node.isSynchronize());
                    log.trace("state: " + node.getState().toString());
                    log.trace("baseURL: " + node.getBaseURL());
                }
            }

        } catch (Exception e) {
            log.error(taskName + ": cannot create store, unable to schedule job", e);
            throw new JobExecutionException(taskName + ": cannot create store, unable to schedule job", e);
        }
    }

}
