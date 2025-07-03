package edu.ucsb.nceas.mdqengine;

import com.rabbitmq.client.*;
import edu.ucsb.nceas.mdqengine.collections.Runs;
import edu.ucsb.nceas.mdqengine.dispatch.Dispatcher;
import edu.ucsb.nceas.mdqengine.exception.MetadigException;
import edu.ucsb.nceas.mdqengine.exception.MetadigIndexException;
import edu.ucsb.nceas.mdqengine.exception.MetadigProcessException;
import edu.ucsb.nceas.mdqengine.model.Result;
import edu.ucsb.nceas.mdqengine.model.Run;
import edu.ucsb.nceas.mdqengine.model.Suite;
import edu.ucsb.nceas.mdqengine.model.SysmetaModel;
import edu.ucsb.nceas.mdqengine.processor.GroupLookupCheck;
import edu.ucsb.nceas.mdqengine.serialize.XmlMarshaller;
import edu.ucsb.nceas.mdqengine.solr.IndexApplicationController;
import edu.ucsb.nceas.mdqengine.store.InMemoryStore;
import edu.ucsb.nceas.mdqengine.store.MDQStore;
import org.apache.commons.codec.digest.DigestUtils;
import org.apache.commons.configuration2.ex.ConfigurationException;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.solr.client.solrj.SolrClient;
import org.dataone.client.auth.AuthTokenSession;
import org.dataone.client.v2.MNode;
import org.dataone.client.v2.itk.D1Client;
import org.dataone.service.exceptions.*;
import org.dataone.service.types.v1.Checksum;
import org.dataone.service.types.v1.Identifier;
import org.dataone.service.types.v1.ObjectFormatIdentifier;
import org.dataone.service.types.v1.Session;
import org.dataone.service.types.v2.SystemMetadata;

import java.io.*;
import java.math.BigInteger;
import java.net.InetAddress;
import java.util.*;
import java.util.concurrent.*;

/**
 * The Worker class contains methods that create quality reports for metadata
 * documents and uploads these reports to DataONE member nodes for cataloging
 * and indexing. A worker reads from a RabbitMQ queue that a controller process
 * writes to.
 *
 * @author Peter Slaughter
 * @version %I%, %G%
 * @since 1.0
 */
public class Worker {

    private final static String EXCHANGE_NAME = "metadig";
    private final static String QUALITY_QUEUE_NAME = "quality";
    private final static String COMPLETED_QUEUE_NAME = "completed";

    private final static String QUALITY_ROUTING_KEY = "quality";
    private final static String COMPLETED_ROUTING_KEY = "completed";
    private final static String MESSAGE_TYPE_QUALITY = "quality";

    private final static String springConfigFileURL = "/solr/metadig-index-processor-context.xml";
    private final static String qualityReportObjectType = "https://nceas.ucsb.edu/mdqe/v1";

    private static com.rabbitmq.client.Connection RabbitMQconnection;
    private static com.rabbitmq.client.Channel RabbitMQchannel;

    public static Log log = LogFactory.getLog(Worker.class);
    // These values are read from a config file, see class 'MDQconfig'.
    private static String RabbitMQhost = null;
    private static int RabbitMQport = 0;
    private static String RabbitMQpassword = null;
    private static String RabbitMQusername = null;
    private static String authToken = null;
    private static SolrClient client = null;
    private static Boolean indexLatest = false; // Determine the latest report in the month and record in the index
                                                // create a sequenceId based on obsolesence chain. Currently this item
                                                // is always performed, but it could be controlled by a
                                                // metadig.properties config item in the future if desired.
    private static Boolean indexSequenceId = true;

    private static long startTimeIndexing;
    private static long startTimeProcessing;
    private static long elapsedTimeSecondsIndexing;
    private static long elapsedTimeSecondsProcessing;
    private static long totalElapsedTimeSeconds;
    private static Integer runLimit; // configurable in metadig.properties, the number of tries to attempt a run

    public static void main(String[] argv) throws Exception {

        Worker wkr = new Worker();
        MDQconfig cfg = new MDQconfig();
        Dispatcher.setupJep();

        try {
            RabbitMQpassword = cfg.getString("RabbitMQ.password");
            RabbitMQusername = cfg.getString("RabbitMQ.username");
            RabbitMQhost = cfg.getString("RabbitMQ.host");
            RabbitMQport = cfg.getInt("RabbitMQ.port");
            indexLatest = Boolean.parseBoolean(cfg.getString("index.latest"));
            runLimit = cfg.getInt("quartz.monitor.run.limit");
        } catch (ConfigurationException cex) {
            log.error("Unable to read configuration");
            MetadigException me = new MetadigException("Unable to read config properties");
            me.initCause(cex.getCause());
            throw me;
        }

        wkr.setupQueues();

        /*
         * This method is overridden from the RabbitMQ library and serves as the
         * callback that is invoked whenever an entry added to the 'RabbitMQchannel' and
         * this particular instance of the Worker is selected for delivery of the queue
         * message.
         */
        final Consumer consumer = new DefaultConsumer(RabbitMQchannel) {
            @Override
            public void handleDelivery(String consumerTag, Envelope envelope, AMQP.BasicProperties properties,
                    byte[] body) throws IOException {

                Runs runsInSequence = new Runs();
                ByteArrayInputStream bis = new ByteArrayInputStream(body);
                ObjectInput in = new ObjectInputStream(bis);
                QueueEntry qEntry = null;
                // long startTime = System.nanoTime();
                startTimeProcessing = System.currentTimeMillis();
                elapsedTimeSecondsIndexing = 0L;
                elapsedTimeSecondsProcessing = 0L;
                totalElapsedTimeSeconds = 0L;

                try {
                    qEntry = (QueueEntry) in.readObject();
                } catch (java.lang.ClassNotFoundException e) {
                    log.error("Unable to process quality report");
                    e.printStackTrace();
                    return;
                }

                String metadataPid = qEntry.getMetadataPid();
                String suiteId = qEntry.getQualitySuiteId();
                // TODO: Moving forward, sysmeta at this point should not be necessary
                SystemMetadata sysmeta = qEntry.getSystemMetadata();
                String nodeId = qEntry.getMemberNode();

                // see if there is an existing run
                Run run = null;
                try {
                    run = Run.getRun(metadataPid, suiteId);
                } catch (MetadigException me) {
                    log.info("Unable to get run for pid: " + metadataPid + "suite: " + suiteId);
                } catch (ConfigurationException ce) {
                    log.error("Unable to read configuration");
                    // consume, since a new run will be created below anyway

                }

                if (run == null) {
                    run = new Run();
                    // if no run exists
                    run.setObjectIdentifier(metadataPid);
                    run.setSuiteId(suiteId);
                    run.setNodeId(nodeId);
                    run.setErrorDescription("");
                    run.setRunCount(0);
                }

                // set the run_count to count + 1
                Integer runCount = run.getRunCount() + 1;
                run.setRunCount(runCount);

                // log and exit if 10 attempts have been made
                if (run.getRunCount() > runLimit) {
                    log.info("Limit " + runLimit + " attempts hit for pid: " + metadataPid + "and suite: " + suiteId
                            + " , logging with a FAILED status.");
                    run.setRunStatus(Run.FAILURE);
                    run.setErrorDescription("Run has been attempted " + runLimit + " times, aborting.");
                    try {
                        run.save();
                    } catch (MetadigException me) { // requeue the message if unable to save the status
                        log.error("Unable to save run with status 'failure': " + metadataPid);
                        RabbitMQchannel.basicNack(envelope.getDeliveryTag(), false, true);
                    }
                    // make sure to ack the message even though we aren't attempting the run
                    RabbitMQchannel.basicAck(envelope.getDeliveryTag(), false);
                    return;
                }

                // set run status and continue otherwise
                run.setRunStatus(Run.PROCESSING);
                // update database
                try {
                    run.save();
                } catch (MetadigException me) { // requeue the message if unable to save the status
                    log.error("Unable to save run with status 'processing': " + metadataPid);
                    RabbitMQchannel.basicNack(envelope.getDeliveryTag(), false, true);
                }

                // ack the quality message
                RabbitMQchannel.basicAck(envelope.getDeliveryTag(), false);

                long difference;
                Worker wkr = new Worker();
                String runXML = null;

                // Fail fast for each of these tasks: create run, save run, index run
                // If any one of these fails, send an 'ack' back to the controller, try to
                // return a report query entry (that also contains the exception) and return
                boolean failFast = false;

                // Create the quality report
                try {
                    // Set host name so controller can print stats info, referring to this worker.
                    // TODO: Reminder, qEntry will not have system metadata or metadata docs
                    qEntry.setHostname(InetAddress.getLocalHost().getHostName());
                    run = wkr.processReport(qEntry);
                    if (run.getObjectIdentifier() == null) {
                        run.setObjectIdentifier(metadataPid);
                    }
                    runXML = XmlMarshaller.toXml(run, true);
                    qEntry.setRunXML(runXML);
                    difference = System.currentTimeMillis() - startTimeProcessing;
                    elapsedTimeSecondsProcessing = TimeUnit.MILLISECONDS.toSeconds(difference);
                    qEntry.setProcessingElapsedTimeSeconds(elapsedTimeSecondsProcessing);
                    log.debug("Completed running quality suite.");
                } catch (java.lang.Exception e) {
                    failFast = true;
                    log.error("Unable to run quality suite.");
                    e.printStackTrace();
                    // Store an exception in the queue entry. This will be returned to the
                    // Controller so that it can take the appropriate action, for example, to
                    // resubmit the entry or to log the error in an easily assessible location, or
                    // to notify a user.
                    MetadigException me = new MetadigProcessException("Unable to run quality suite.");
                    me.initCause(e);
                    qEntry.setException(me);
                    // Note: Don't explicitly call 'return' from this routine causes the worker to
                    // silently lose connection to rabbitmq, i.e. the message to the completed
                    // queue doesn't appear to be queued

                    // Even though the run didn't complete, save the processing report to
                    // persistent storage, so that we can save the error and status of the run.
                    try {
                        log.debug("Saving quality run status after error");
                        // convert String into InputStream
                        if (run == null) {
                            run = new Run();
                        }
                        run.setRunStatus(Run.FAILURE);
                        run.setErrorDescription(e.getMessage());
                        run.setRunCount(runCount);
                        run.save();
                        log.debug("Saved quality run status after error");
                    } catch (MetadigException ex) {
                        log.error("Processing failed, then unable to save the quality report to database:"
                                + ex.getMessage());
                    }
                }
                // if run has been marked as failed elsewhere, don't try to index it
                if (run.getRunStatus() == Run.FAILURE) {
                    failFast = true;
                    run.setRunCount(runCount);
                    try {
                        run.save();
                    } catch (MetadigException ex) {
                        log.error("Processing failed, then unable to save the quality report to database:"
                                + ex.getMessage());
                    }
                    log.debug("Saved quality run status after error");
                }

                String sequenceId = null;
                /* Save the processing report to persistent storage */
                if (!failFast)
                    try {
                        // Determine the sequence identifier for the metadata pids DataONE obsolescence
                        // chain. This is not the DataONE seriesId, which may not exist for a pid, but
                        // instead is a quality engine maintained sequence id, that is needed to
                        // determine the highest score for a obs. chain for each month.
                        log.debug("Searching for sequence id for pid: " + run.getObjectIdentifier());
                        // Add current run to collection, it will be saved during the run.update
                        run.setObjectIdentifier(metadataPid);
                        run.setRunStatus(Run.SUCCESS);
                        run.setErrorDescription("");
                        // Should a 'sequenceId' and 'isLatest' be added to the Solr index?
                        if (indexSequenceId) {
                            // Add the current run to the collection, as a starting point for the sequence
                            // id search
                            runsInSequence.addRun(run.getObjectIdentifier(), run);

                            // Traverse through the collection, stopping if the sequenceId is found. If the
                            // sequenceId is already found, then all pids in the chain that are stored
                            // should already have this sequenceId Boolean stopWhenSIfound = true;
                            Boolean stopWhenSIfound = false;
                            runsInSequence.getRunSequence(run, suiteId, stopWhenSIfound);
                            sequenceId = runsInSequence.getSequenceId();
                            // Ok, a sequence id wasn't set for these runs (if any), so generate a new one
                            // Only assign a new pid if the first pid in the sequence is found, so that we
                            // don't have multiple segments of a chain with different sequenceIds.
                            if (sequenceId == null && runsInSequence.getFoundFirstPid()) {
                                sequenceId = runsInSequence.getFirstPidInSequence();
                                runsInSequence.setSequenceId(sequenceId);
                                log.debug("Setting sequenceId to first pid in sequence: " + sequenceId);
                            } else {
                                log.debug("Using found sequenceId: " + sequenceId);
                            }

                            run.setSequenceId(sequenceId);
                            run.setRunCount(runCount);
                        }

                        run.save();

                        // Update runs in persist storage with sequenceId for this obsolescence chain
                        if (indexSequenceId && sequenceId != null) {
                            log.debug("Updating sequenceId to " + sequenceId);
                            // sequenceId = runsInSequence.getSequenceId();
                            runsInSequence.updateSequenceId(sequenceId);
                            runsInSequence.update();
                        }
                    } catch (MetadigException me) {
                        // Store an exception in the queue entry. This will be returned to the
                        // Controller so that it can take the appropriate action, for example, to
                        // resubmit the entry or to log the error in an easily assessible location, or
                        // to notify a user.
                        failFast = true;
                        log.error("Unable to save (then index) quality report to database.");
                        qEntry.setException(me);
                    }

                /*
                 * Once the quality report has been created and saved to persistent storage,
                 * it can be added to the Solr index
                 */
                if (!failFast) {
                    log.debug("Indexing report");
                    try {
                        startTimeIndexing = System.currentTimeMillis();
                        runXML = XmlMarshaller.toXml(run, true);
                        // log.trace("report: " + runXML);
                        // For now, use fallback solr location, which will be selected by the indexer
                        // if null is passed in.
                        String solrLocation = null;
                        log.debug("calling indexReport");
                        // TODO: Determine which system metadata values are required to index
                        wkr.indexReport(metadataPid, runXML, suiteId, sysmeta, solrLocation);

                        // Update any runs in this sequence that have been modified, either set as
                        // latest in sequence
                        // or unset as latest in sequence.
                        if (indexLatest) {
                            // Put files to be updated in a HashMap (can update multiple fields)
                            HashMap<String, Object> fields = new HashMap<>();
                            for (Run r : runsInSequence.getModifiedRuns()) {
                                log.debug("Updating Solr index with modified run with pid: " + r.getObjectIdentifier()
                                        + ", isLatest: " + r.getIsLatest().toString() + ", dateUploaded: "
                                        + r.getDateUploaded());
                                fields.put("isLatest", r.getIsLatest());
                                try {
                                    wkr.updateIndex(r.getObjectIdentifier(), r.getSuiteId(), fields, solrLocation);
                                } catch (MetadigIndexException mie) {
                                    // Retry the update if the first attemp fails
                                    log.debug("Retrying updating Solr index with modified run with pid: "
                                            + r.getObjectIdentifier() + ", isLatest: " + r.getIsLatest().toString()
                                            + ", dateUploaded: " + r.getDateUploaded());
                                    try {
                                        wkr.updateIndex(r.getObjectIdentifier(), r.getSuiteId(), fields, solrLocation);
                                        log.debug("Successfully updated Solr index with modified run with pid: "
                                                + r.getObjectIdentifier() + ", isLatest: " + r.getIsLatest().toString()
                                                + ", dateUploaded: " + r.getDateUploaded());
                                    } catch (Exception mie2) {
                                        log.error("Failed 2nd attempt to update Solr index with modified run with pid: "
                                                + r.getObjectIdentifier() + ", isLatest: " + r.getIsLatest().toString()
                                                + ", dateUploaded: " + r.getDateUploaded());
                                    }
                                }
                            }
                        }

                        // Now update one or more runs in the Solr index with the sequenceId
                        if (indexSequenceId && sequenceId != null) {
                            // Put files to be updated in a HashMap (can update multiple fields)
                            HashMap<String, Object> fields = new HashMap<>();
                            fields.put("sequenceId", sequenceId);
                            for (Run r : runsInSequence.getModifiedRuns()) {
                                log.debug("Updating Solr index with sequenceId: " + sequenceId + " for pid: "
                                        + r.getObjectIdentifier());
                                wkr.updateIndex(r.getObjectIdentifier(), r.getSuiteId(), fields, solrLocation);
                            }
                        }

                        difference = System.currentTimeMillis() - startTimeIndexing;
                        elapsedTimeSecondsIndexing = TimeUnit.MILLISECONDS.toSeconds(difference);
                        qEntry.setIndexingElapsedTimeSeconds(elapsedTimeSecondsIndexing);
                    } catch (Exception e) {
                        log.error("Unable to index quality report..");
                        e.printStackTrace();
                        MetadigException me = new MetadigIndexException("Unable index the generated quality report.");
                        me.initCause(e);
                        qEntry.setException(me);
                    }
                }

                // Send the report (completed or not) to the controller, with errors that were
                // encountered.
                try {
                    log.debug("Sending report info back to controller...");
                    totalElapsedTimeSeconds = elapsedTimeSecondsProcessing + elapsedTimeSecondsIndexing;
                    qEntry.setTotalElapsedTimeSeconds(totalElapsedTimeSeconds);
                    wkr.returnReport(metadataPid, suiteId, qEntry, envelope, this);
                    log.debug("Sent report info back to controller...");
                } catch (IOException ioe) {
                    log.error("Unable to return quality report to controller.");
                    ioe.printStackTrace();
                }

                log.debug("Worker completed task");
            }
        };

        log.info("Calling basicConsume");
        RabbitMQchannel.basicConsume(QUALITY_QUEUE_NAME, false, consumer);
    }

    /**
     * Put the quality report in a queue message and return in to the controller
     * uploaded and indexed.
     * 
     * @param metadataPid The identifier of the metadata document associated with
     *                    the report
     * @param suiteId     The identifier for the suite used to score the metadata
     * @param qEntry      The message passed via RabbitMQ back to metadig-controller
     */
    private void returnReport(String metadataPid, String suiteId, QueueEntry qEntry, Envelope envelope,
            Consumer consumer) throws IOException {

        byte[] message = null;
        try {
            log.info("Elapsed time processing (seconds): "
                    + String.format("%d", elapsedTimeSecondsProcessing)
                    + " for metadataPid: " + metadataPid
                    + ", suiteId: " + suiteId
                    + "\n");

            log.info("Elapsed time indexing (seconds): "
                    + String.format("%d", elapsedTimeSecondsIndexing)
                    + " for metadataPid: " + metadataPid
                    + ", suiteId: " + suiteId
                    + "\n");

            log.info("Total elapsed time (seconds): "
                    + String.format("%d", totalElapsedTimeSeconds)
                    + " for metadataPid: " + metadataPid
                    + ", suiteId: " + suiteId
                    + "\n");
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            ObjectOutput out = new ObjectOutputStream(bos);
            out.writeObject(qEntry);
            message = bos.toByteArray();

            log.info(" [x] Done");
            try {
                this.writeCompletedQueue(message);
                log.info("Sent completed report for pid: '" + qEntry.getMetadataPid() + "'");
                log.debug("Sent task completed acknowledgement to RabbitMQ");
            } catch (AlreadyClosedException rmqe) {
                log.error("RabbitMQ connection error: " + rmqe.getMessage());
                try {
                    log.info("Resetting RabbitMQ queues and resending completed report...");
                    // destroy channel before setting up queues again
                    RabbitMQconnection.close();
                    // setup queues
                    this.setupQueues();
                    this.writeCompletedQueue(message);
                    log.info(" [x] Sent completed report for pid: '" + qEntry.getMetadataPid() + "'");
                    // Tell RabbitMQ this worker is ready for tasks
                    log.info("Calling basicConsume");
                    RabbitMQchannel.basicConsume(QUALITY_QUEUE_NAME, false, consumer);
                } catch (Exception e) {
                    log.error("Error re-establishing connection to RabbitMQ server: " + e.getMessage());
                    log.error("Unable to resend report back to controller.");
                }
            }
        } catch (Exception e) {
            // If we couldn't prepare the message, then there is nothing left to do
            log.error(" Unable to return report to controller");
            e.printStackTrace();
            throw e;
        }
    }

    /**
     * Declare and connect to the queues that are being maintained by the
     * Controller.
     *
     * @throws IOException
     * @throws TimeoutException
     */
    public void setupQueues() throws IOException, TimeoutException {

        /*
         * Connect to the RabbitMQ queue containing entries for which quality reports
         * need to be created.
         */
        ConnectionFactory factory = new ConnectionFactory();
        boolean durable = true;
        factory.setHost(RabbitMQhost);
        factory.setPort(RabbitMQport);
        factory.setPassword(RabbitMQpassword);
        factory.setUsername(RabbitMQusername);
        // factory.setRequestedHeartbeat(60);
        // connection that will recover automatically
        // factory.setAutomaticRecoveryEnabled(true);
        // attempt recovery every 10 seconds after a failure
        // factory.setNetworkRecoveryInterval(10000);
        log.info("Set RabbitMQ host to: " + RabbitMQhost);
        log.info("Set RabbitMQ port to: " + RabbitMQport);

        try {
            RabbitMQconnection = factory.newConnection();
            RabbitMQchannel = RabbitMQconnection.createChannel();
            RabbitMQchannel.exchangeDeclare(EXCHANGE_NAME, "direct", durable);

            RabbitMQchannel.queueDeclare(QUALITY_QUEUE_NAME, durable, false, false, null);
            RabbitMQchannel.queueBind(QUALITY_QUEUE_NAME, EXCHANGE_NAME, QUALITY_ROUTING_KEY);
            // Channel will only send one request for each worker at a time.

            RabbitMQchannel.basicQos(1);
            log.info("Connected to RabbitMQ queue " + QUALITY_QUEUE_NAME);
            log.info("Waiting for messages. To exit press CTRL+C");
        } catch (Exception e) {
            log.error("Error connecting to RabbitMQ queue " + QUALITY_QUEUE_NAME);
            log.error(e.getMessage());
        }

        try {
            RabbitMQchannel.queueDeclare(COMPLETED_QUEUE_NAME, durable, false, false, null);
            log.info("Connected to RabbitMQ queue " + COMPLETED_QUEUE_NAME);
        } catch (Exception e) {
            log.error("Error connecting to RabbitMQ queue " + COMPLETED_QUEUE_NAME);
            log.error(e.getMessage());
        }
    }

    /**
     * Create a quality report for a single metadata document.
     * <p>
     * The processReport method runs the requested metadig-engine quality suite for
     * the provided
     * metadata document. This method appends the generated quality report to the
     * RabbitMQ
     * queue entry.
     * </p>
     *
     * @param message the RabbitMQ queue entry that contains the metadata document
     *                to score.
     * @return The quality report as a string.
     * @throws InterruptedException
     * @throws Exception
     */
    public Run processReport(QueueEntry message) throws InterruptedException, Exception {

        String suiteId = message.getQualitySuiteId();
        String metadataDoc = message.getMetadataDoc();
        InputStream input = new ByteArrayInputStream(metadataDoc.getBytes("UTF-8"));
        SystemMetadata sysmeta = message.getSystemMetadata();

        log.info("Running suite '" + message.getQualitySuiteId() + "'" + " for metadata pid "
                + message.getMetadataPid());
        // Run the Metadata Quality Engine for the specified metadata object.
        // TODO: Review how suite params are set and possibly refactor
        Map<String, Object> params = new HashMap<String, Object>();
        // To run the suite, we need the in memory store, that contains all checks and
        // suites.
        MDQStore store = new InMemoryStore();

        Run run = null;
        try {
            MDQEngine engine = new MDQEngine();
            Suite suite = store.getSuite(suiteId);
            run = engine.runSuite(suite, input, params, sysmeta);
            List<Result> results = run.getResult();
        } catch (Exception e) {
            e.printStackTrace();
            throw new MetadigException("Unable to run quality suite for pid " + message.getMetadataPid() + ", suite "
                    + suiteId + e.getMessage(), e);
        }

        // Reminder: System metadata is required for the solr index to obtain
        // statistics/reports
        // TODO: We will need a path to obtain the system metadata if its removed from
        // qEntry
        // Add DataONE sysmeta, if it was provided.
        if (sysmeta != null) {
            SysmetaModel smm = new SysmetaModel();
            // These sysmeta fields are always provided
            smm.setOriginMemberNode(sysmeta.getOriginMemberNode().getValue());
            smm.setRightsHolder(sysmeta.getRightsHolder().getValue());
            smm.setDateUploaded(sysmeta.getDateUploaded());
            smm.setFormatId(sysmeta.getFormatId().getValue());
            // These fields aren't required.
            if (sysmeta.getObsoletes() != null)
                smm.setObsoletes(sysmeta.getObsoletes().getValue());
            if (sysmeta.getObsoletedBy() != null)
                smm.setObsoletedBy(sysmeta.getObsoletedBy().getValue());
            if (sysmeta.getSeriesId() != null)
                smm.setSeriesId(sysmeta.getSeriesId().getValue());

            // Now make the call to DataONE to get the group information for this
            // rightsHolder.
            // Only wait for a certain amount of time before we will give up.
            ExecutorService executorService = Executors.newSingleThreadExecutor();

            // Provide the rightsHolder to the DataONE group lookup.
            GroupLookupCheck glc = new GroupLookupCheck();
            glc.setRightsHolder(sysmeta.getRightsHolder().getValue());
            Future<List<String>> future = executorService.submit(glc);

            List<String> groups = new ArrayList<String>();
            try {
                groups = future.get();
            } catch (Throwable thrown) {
                log.error("Error while waiting for group lookup thread completion");
            }

            if (groups != null) {
                smm.setGroups(groups);
            } else {
                log.debug("No groups to set");
            }
            executorService.shutdown();

            run.setSysmeta(smm);
        }

        return (run);
    }

    /**
     * Send a quality report to the Solr server to be added to the index.
     * <p>
     * The quality report is added to the Solr index using the DataONE index
     * processing
     * component, which has been modified for use with metadig_engine.
     * </p>
     *
     * @param metadataId The identifier of the metadata document associated with the
     *                   report
     * @param runXML
     * @param suiteId
     * @param sysmeta
     * @throws Exception
     */
    public void indexReport(String metadataId, String runXML, String suiteId, SystemMetadata sysmeta,
            String solrLocation) throws MetadigIndexException {

        log.info("Indexing metadata PID: " + metadataId + ", suite id: " + suiteId);

        // If no Solr server is specified then use the 'fallback' server from the
        // configuration
        // file.
        try {
            IndexApplicationController iac = new IndexApplicationController();
            iac.initialize(this.springConfigFileURL, solrLocation);
            InputStream runIS = new ByteArrayInputStream(runXML.getBytes());
            Identifier pid = new Identifier();
            pid.setValue(metadataId);
            ObjectFormatIdentifier objFormatId = new ObjectFormatIdentifier();
            // Update the sysmeta, setting the correct type for a metadig quality report
            objFormatId.setValue(qualityReportObjectType);
            sysmeta.setFormatId(objFormatId);
            iac.insertSolrDoc(pid, sysmeta, runIS);
            log.info("Done indexing metadata PID: " + metadataId + ", suite id: " + suiteId);
            iac.shutdown();
        } catch (Exception e) {
            throw new MetadigIndexException("Error during indexing", e);
        }
    }

    /**
     * Update a value in a quality report on the Solr server
     * <p>
     * The quality report is added to the Solr index using the DataONE index
     * processing
     * component, which has been modified for use with metadig_engine.
     * </p>
     *
     * @param metadataId The identifier of the metadata document associated with the
     *                   report
     * @param suiteId    The identifier for the suite used to score the metadata
     * @param fields     : field names and values to update in the Solr index entry
     * @throws Exception
     */
    public void updateIndex(String metadataId, String suiteId, HashMap<String, Object> fields, String solrLocation)
            throws MetadigIndexException {

        try {
            IndexApplicationController iac = new IndexApplicationController();
            iac.initialize(this.springConfigFileURL, solrLocation);
            Identifier pid = new Identifier();
            pid.setValue(metadataId);
            // Update the solr doc fields, replacing the current value (other types of
            // updates are available)
            String updateFieldModifier = "set";
            iac.updateSolrDoc(pid, suiteId, fields, updateFieldModifier);
            iac.shutdown();
        } catch (Exception e) {
            throw new MetadigIndexException("Error during index updating", e);
        }
        log.debug("Done updating entry for pid: " + metadataId + ", suite id: " + suiteId);
    }

    /**
     * Submit (upload) the newly generated quality report to a member node
     * <p>
     * >
     * The quality report is submitted to the authoritative member node of the
     * source
     * metadata document, if the member node name/location was provided.
     * </p>
     *
     * @param message containing the metadata document and generated quality report.
     * @return The identifier of the uploaded qualtiy report, as a String.
     */
    public String submitReport(QueueEntry message) {

        Integer rptLength = 0;
        String qualityXML = null;
        InputStream qualityReport;
        String memberNodeServiceUrl = null;

        try {
            qualityXML = message.getRunXML();
            qualityReport = new ByteArrayInputStream(qualityXML.getBytes("UTF-8"));
            rptLength = qualityXML.getBytes("UTF-8").length;
        } catch (UnsupportedEncodingException ex) {
            log.error("Unable to read quality report for metadata pid: " + message.getMetadataPid());
            log.error(ex);
            return null;
        }

        /*
         * Read the sysmeta for the metadata pid and use appropriate values from it for
         * the
         * quality report sysmeta.
         */
        SystemMetadata sysmeta = message.getSystemMetadata();

        Identifier newId = new Identifier();
        UUID uuid = UUID.randomUUID();
        newId.setValue("urn:uuid" + uuid.toString());

        SystemMetadata sm = new SystemMetadata();
        sm.setIdentifier(newId);
        ObjectFormatIdentifier fmtid = new ObjectFormatIdentifier();
        fmtid.setValue("https://nceas.ucsb.edu/mdqe/v1");
        sm.setFormatId(fmtid);

        sm.setSize(BigInteger.valueOf(rptLength));
        Checksum cs = new Checksum();
        cs.setAlgorithm("SHA-1");
        cs.setValue(DigestUtils.shaHex(qualityXML));
        sm.setChecksum(cs);

        sm.setRightsHolder(sysmeta.getRightsHolder());
        sm.setSubmitter(sysmeta.getRightsHolder());
        sm.setAccessPolicy(sysmeta.getAccessPolicy());

        /* The auth token is read from the config file. */
        Session session = new AuthTokenSession(authToken);
        log.debug(" Created session for subject: " + session.getSubject());

        memberNodeServiceUrl = message.getMemberNode();

        /* Upload the quality report to the source member node */
        MNode mn = null;
        try {
            mn = D1Client.getMN(memberNodeServiceUrl);
        } catch (ServiceFailure ex) {
            ex.printStackTrace();
            log.error("Error connecting to DataONE client at URL: " + memberNodeServiceUrl);
        }

        log.info(" Uploading quality report with pid: " + newId.getValue() + ", rightsHolder: "
                + sm.getRightsHolder().getValue());

        Identifier returnPid = null;
        try {
            returnPid = mn.create(session, newId, qualityReport, sm);
        } catch (InvalidRequest | NotAuthorized | InvalidToken | InvalidSystemMetadata | UnsupportedType
                | IdentifierNotUnique | InsufficientResources
                | NotImplemented | ServiceFailure ex) {
            log.error(ex);
            log.error("Error uploading object with PID: " + returnPid.getValue());
        }

        log.info("Uploaded pid " + returnPid.getValue() + " to member node " + mn.getNodeId().getValue());

        return (returnPid.getValue());
    }

    /**
     * Write a message to the RabbitMQ 'completed' queue, which will be read by
     * metadig-controller
     *
     * @param message The message to send to the controller
     * @throws IOException
     */
    public void writeCompletedQueue(byte[] message) throws IOException {
        // Include the message type in this queue entry sent back to the controller. The
        // controller gets messages
        // to the "Completed" queue from different clients (aggregator, quality worker)
        // which each have different
        // messages types and formsts, so the controller needs to know what type of
        // message it is getting.
        AMQP.BasicProperties basicProperties = new AMQP.BasicProperties.Builder()
                .contentType("text/plain")
                .deliveryMode(2) // set this message to persistent
                .type(MESSAGE_TYPE_QUALITY)
                .build();
        RabbitMQchannel.basicPublish(EXCHANGE_NAME, COMPLETED_ROUTING_KEY, basicProperties, message);
    }

    /**
     * Read a file from a Java resources folder.
     *
     * @param fileName the relative path of the file to read.
     * @return THe resources file as a stream.
     */
    private InputStream getResourceFile(String fileName) {

        StringBuilder result = new StringBuilder("");

        // Get file from resources folder
        ClassLoader classLoader = getClass().getClassLoader();
        File file = new File(classLoader.getResource(fileName).getFile());
        log.info(file.getAbsolutePath());

        InputStream is = classLoader.getResourceAsStream(fileName);

        return is;
    }
}