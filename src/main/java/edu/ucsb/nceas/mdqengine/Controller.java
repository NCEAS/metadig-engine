package edu.ucsb.nceas.mdqengine;

import com.rabbitmq.client.*;
import edu.ucsb.nceas.mdqengine.authorization.BookkeeperClient;
import edu.ucsb.nceas.mdqengine.scorer.ScorerQueueEntry;
import edu.ucsb.nceas.mdqengine.exception.MetadigException;
import edu.ucsb.nceas.mdqengine.scheduler.MonitorJob;
import org.apache.commons.configuration2.ex.ConfigurationException;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.dataone.bookkeeper.api.Usage;
import org.dataone.bookkeeper.api.UsageStatus;
import org.dataone.exceptions.MarshallingException;
import org.dataone.service.types.v2.SystemMetadata;
import org.dataone.service.types.v2.TypeFactory;
import org.dataone.service.util.TypeMarshaller;
import org.joda.time.DateTime;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.File;
import java.io.InputStream;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.ObjectOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.BufferedInputStream;
import java.io.ByteArrayInputStream;
import java.io.ObjectInput;
import java.io.ObjectInputStream;
import java.io.ObjectOutput;

import java.lang.reflect.InvocationTargetException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static org.quartz.CronScheduleBuilder.cronSchedule;
import static org.quartz.JobBuilder.newJob;
import static org.quartz.TriggerBuilder.newTrigger;
import org.quartz.impl.StdSchedulerFactory;
import org.quartz.CronTrigger;
import org.quartz.JobDetail;
import org.quartz.Scheduler;
import org.quartz.SchedulerException;
import org.quartz.SchedulerFactory;

/**
 * The Controller class accepts requests for generating quality reports from
 * metadata documents. As the report generation process can take a significant
 * amount of time, the controller delegates report generation to worker
 * processes
 * via a RabbitMQ queue that the worker processes reads from.
 *
 * @author Peter Slaughter
 * @version %I%, %G%
 * @since 1.0
 */
public class Controller {

    private final static String EXCHANGE_NAME = "metadig";
    private final static String QUALITY_QUEUE_NAME = "quality";
    private final static String SCORER_QUEUE_NAME = "scorer";
    private final static String COMPLETED_QUEUE_NAME = "completed";

    private final static String QUALITY_ROUTING_KEY = "quality";
    private final static String SCORER_ROUTING_KEY = "scorer";
    private final static String COMPLETED_ROUTING_KEY = "completed";
    private final static String MESSAGE_TYPE_QUALITY = "quality";
    private final static String MESSAGE_TYPE_SCORER = "scorer";

    private static com.rabbitmq.client.Connection RabbitMQconnection;
    private static com.rabbitmq.client.Channel RabbitMQchannel;

    // Default values for the RabbitMQ message broker server. The value of
    // 'localhost' is valid for a RabbitMQ server running on a 'bare metal' server,
    // inside a VM, or within a Kubernetes cluster where metadig-controller and the
    // RabbitMQ server are running in containers that belong to the same Pod. These
    // defaults will be used if the properties file cannot be read. These values are
    // read from a config file, see class 'MDQconfig'
    private static Boolean bookkeeperEnabled = false;
    private static String RabbitMQhost = null;
    private static int RabbitMQport = 0;
    private static String RabbitMQpassword = null;
    private static String RabbitMQusername = null;
    private static String monitorSchedule = null; // quartz monitor schedule, as a quartz-style crontab
    private static boolean monitor = true; // whether or not quartz monitor should be turned on
    private static Controller instance;
    private boolean isStarted = false;
    private int testCount = 0;
    private int runCount = 0;
    private long totalElapsedSeconds = 0;
    private long startTime = 0;
    private boolean testMode = false;
    public static Log log = LogFactory.getLog(Controller.class);

    public static void main(String[] argv) throws Exception {

        // System.setProperty("lo4j2.debug", "true");
        // System.setProperty("log4j.configurationFile", "log4j2.xml");
        Controller metadigCtrl = Controller.getInstance();
        metadigCtrl.start();
        if (metadigCtrl.getIsStarted()) {
            log.info("The controller has been started");
        } else {
            log.info("The controller has not been started");
        }

        log.info("# of arguments: " + argv.length);
        /*
         * When in "test" mode (i.e. if cmd args are passed in), records will be
         * read from the port number (argv[0]) which are the metadata and
         * sysmeta filenames to submit to the report generation queue. Records will
         * be read in until a line with 'Done' is encountered. The outer loop here
         * is so that mulitple 'test' sessions can occur, i.e. controller will wait
         * again for a new client to connect to the port, and then begin again reading
         * filenames to run.
         */

        Boolean stop = false;
        if (argv.length > 0) {
            // Continue to listen on specified port for client 'test' requests
            while (true) {
                int portNumber = Integer.parseInt(argv[0]);
                log.info("Controller test mode is enabled.");
                log.info("Controller listening on port " + portNumber + "for filenames to submit");
                DateTime requestDateTime = new DateTime();
                ServerSocket serverSocket = new ServerSocket(portNumber);
                log.info("Waiting to establish connection to test client:");
                Socket clientSocket = serverSocket.accept();
                BufferedReader in = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));
                String request;

                String info = in.readLine();

                // Client has indicated a stop for all tests, as the first line sent to a new
                // connection.
                if ("Stop".equals(info)) {
                    clientSocket.close();
                    serverSocket.close();
                    break;
                }

                // First line contains the type of test, either 'quality' or 'score'
                // Second line contains the number of tests that will be run
                String requestType = info;
                log.debug("Processing request type: " + requestType);
                // Now read the number of requests that will be sent from the client
                info = in.readLine();
                int cnt = Integer.parseInt(info);
                log.info(cnt + " tests will be run.");
                metadigCtrl.initTests(cnt);

                while ((request = in.readLine()) != null) {
                    // Stop reading all requests
                    if ("Stop".equals(request)) {
                        log.info("Stopping tests as client has requested.");
                        stop = true;
                        break;
                    }
                    // Stop reading from the current port connection
                    if ("Done".equals(request)) {
                        log.info("Controller test mode complete.");
                        break;
                    }
                    String delims = "[,]";
                    String[] tokens = request.split(delims);
                    String nodeId = null;

                    switch (requestType) {
                        case "score":
                            log.debug("Processing score request");
                            String collectionId = tokens[0];
                            nodeId = tokens[1];
                            String formatFamily = tokens[2];
                            String qualitySuiteId = tokens[3];

                            requestDateTime = new DateTime();
                            log.info("Request queuing of: " + tokens[0] + ", " + tokens[1] + ", " + tokens[2] + ", "
                                    + tokens[3]);

                            metadigCtrl.processScorerRequest(collectionId, nodeId, formatFamily, qualitySuiteId,
                                    requestDateTime);
                            break;
                        case "quality":
                            log.debug("Processing quality request");
                            String metadataPid = tokens[0];

                            File metadataFile = new File(tokens[1]);
                            InputStream metadata = new FileInputStream(metadataFile);

                            File sysmetaFile = new File(tokens[2]);
                            InputStream sysmeta = new FileInputStream(sysmetaFile);

                            String suiteId = tokens[3];
                            requestDateTime = new DateTime();
                            nodeId = tokens[4];
                            log.info("Request queuing of: " + tokens[0] + ", " + tokens[3] + ", " + tokens[4]);
                            metadigCtrl.processQualityRequest(nodeId, metadataPid, metadata, suiteId, "/tmp",
                                    requestDateTime, sysmeta);
                            break;
                        default:
                            log.error("Invalid request type recieved by controller: " + requestType);
                    }
                }
                // Close current connection, then start again with new connection
                clientSocket.close();
                serverSocket.close();
                if (stop) {
                    break;
                }
            }
        }
    }

    private Controller() {
    }

    /**
     * Implement a Singleton pattern using "double checked locking" pattern.
     *
     * @return a singleton instance of the Controller class.
     */
    public static Controller getInstance() {
        if (instance == null) {
            synchronized (Controller.class) {
                if (instance == null) {
                    log.debug("Creating new controller instance");
                    instance = new Controller();
                }
            }
        }
        return instance;
    }

    /**
     * Initialize the quality engine.
     * <p>
     * Prepare the quality engine for use by initializing the RabbitMQ queues and
     * performing
     * and other startup tasks.
     * </p>
     */

    public void start() {
        /* Don't try to re-initialize the queues if they are already started */
        if (isStarted) {
            return;
        }

        try {
            this.readConfig();
            this.setupQueues();
            if (monitor) {
                this.monitor();
            }
            this.isStarted = true;
            log.debug("Controller is started");
        } catch (java.io.IOException | java.util.concurrent.TimeoutException | ConfigurationException | SchedulerException e) {
            e.printStackTrace();
            log.error("Error starting queue:");
            log.error(e.getMessage());
            this.isStarted = false;
        }
    }

    /**
     * Initialize the test statistics
     * <p>
     * Initialize state variables for test statistics.
     * </p>
     */
    public void initTests(int cnt) {
        this.testMode = true;
        this.testCount = cnt;
        this.runCount = 0;
        this.totalElapsedSeconds = 0;
    }

    /**
     * Reads metadig configuration settings and initializes instance variables.
     *
     * @throws ConfigurationException if an error occurs while reading the
     *                                configuration.
     * @throws IOException            if an I/O error occurs while accessing the
     *                                configuration file.
     */
    public void readConfig() throws ConfigurationException, IOException {
        MDQconfig cfg = new MDQconfig();

        RabbitMQpassword = cfg.getString("RabbitMQ.password");
        RabbitMQusername = cfg.getString("RabbitMQ.username");
        RabbitMQhost = cfg.getString("RabbitMQ.host");
        RabbitMQport = cfg.getInt("RabbitMQ.port");
        bookkeeperEnabled = Boolean.parseBoolean(cfg.getString("bookkeeper.enabled"));
        monitorSchedule = cfg.getString("quartz.monitor.schedule");
        monitor = Boolean.parseBoolean(cfg.getString("quartz.monitor"));
    }

    public String readConfigParam(String paramName) throws ConfigurationException, IOException {
        String paramValue = null;
        try {
            MDQconfig cfg = new MDQconfig();
            paramValue = cfg.getString(paramName);
        } catch (Exception e) {
            log.error("Could not read configuration for param: " + paramName + ": " + e.getMessage());
            throw e;
        }
        return paramValue;
    }

    /**
     * Disable test mode
     * <p>
     * Disable test mode so that the controller resumes normal operation.
     * </p>
     */
    public void disableTestMode() {
        this.testMode = false;
        this.testCount = 0;
        this.runCount = 0;
        this.totalElapsedSeconds = 0;
    }

    /**
     * Query DataONE bookkeeper service to determine if a portal is active
     *
     * <p>
     * Before generating a metadata assessment graph for a portal, check
     * if the portal is active. A portal can be marked to inactive by
     * the portal owner, or by the bookkeeper admin if usage fees are
     * delinquent.
     * </p>
     * 
     * @param collectionId The DataONE collection identifier
     * @return
     * @throws MetadigException
     */
    // Check the portal quota with DataONE bookkeaper
    public Boolean isPortalActive(String collectionId) throws MetadigException {
        // Check the portal quota with DataONE bookkeeper
        log.info("Checking bookkeeper portal Usage for collection: " + collectionId);
        String msg = null;
        BookkeeperClient bkClient = BookkeeperClient.getInstance();
        List<Usage> usages = null;
        UsageStatus usageStatus = null;
        try {
            // Set status = null so that any usage will be returned.
            String status = null;
            // usages = bkClient.listUsages(0, collectionId, "portal", status , subjects);
            usageStatus = bkClient.getUsageStatus(collectionId, "portal");
            log.info("Usage status for portal " + collectionId + " is " + usageStatus.getStatus());
            if (usageStatus.getStatus().compareToIgnoreCase("active") == 0) {
                return true;
            } else {
                return false;
            }
        } catch (Exception e) {
            msg = "Unable to get usage status from bookkeeper for collection id: " + collectionId;
            throw (new MetadigException(msg));
        }
    };

    /**
     * Forward a request to the "InProcess" queue.
     * <p>
     * A request to create a quality report is serialized and placed on the RabbitMQ
     * "InProcess" queue. This queue is read by worker processes that call the
     * quality engine core to generate the report.
     * </p>
     *
     * @param memberNode      the member node service URL to send the quality report
     *                        to.
     * @param metadataPid     the identifier of the metadata document.
     * @param metadata        the metadata XML document.
     * @param qualitySuiteId  the unique identifier of the metadig-engine quality
     *                        suite, i.e. 'arctic.data.center.suite.1'
     * @param localFilePath   the local directory path on the member node where data
     *                        files can be located (not implemented yet)
     * @param requestDateTime the date and time of the initial report generation
     *                        request
     * @param systemMetadata  the DataONE system metadata for the metadata document.
     * @return
     * @throws java.io.IOException
     */
    public void processQualityRequest(String memberNode,
            String metadataPid,
            InputStream metadata,
            String qualitySuiteId,
            String localFilePath,
            DateTime requestDateTime,
            InputStream systemMetadata) throws java.io.IOException {

        log.info("Processing quality report request, id: " + metadataPid + ", suite: " + qualitySuiteId);
        QueueEntry qEntry = null;
        SystemMetadata sysmeta = null;
        byte[] message = null;

        String runXML = null;

        BufferedInputStream bis = new BufferedInputStream(metadata);
        ByteArrayOutputStream buf = new ByteArrayOutputStream();
        int result = bis.read();
        while (result != -1) {
            buf.write((byte) result);
            result = bis.read();
        }
        // StandardCharsets.UTF_8.name() > JDK 7
        String metadataDoc = buf.toString("UTF-8");

        InputStream sysmetaInputStream = null;
        Object tmpSysmeta = null;

        Class<?>[] smClasses = { org.dataone.service.types.v2.SystemMetadata.class,
                org.dataone.service.types.v1.SystemMetadata.class };
        for (Class thisClass : smClasses) {
            try {
                tmpSysmeta = TypeMarshaller.unmarshalTypeFromStream(thisClass, systemMetadata);
                // Didn't get an error so proceed to convert to sysmeta v2, if needed.
                break;
            } catch (ClassCastException cce) {
                cce.printStackTrace();
                continue;
            } catch (InstantiationException | IllegalAccessException | IOException | MarshallingException fis) {
                fis.printStackTrace();
                continue;
            }
        }

        if (tmpSysmeta.getClass().getName().equals("org.dataone.service.types.v1.SystemMetadata")) {
            try {
                sysmeta = TypeFactory.convertTypeFromType(tmpSysmeta, SystemMetadata.class);
            } catch (InstantiationException | IllegalAccessException | InvocationTargetException
                    | NoSuchMethodException ce) {
                ce.printStackTrace();
            }
        } else {
            sysmeta = (SystemMetadata) tmpSysmeta;
        }

        qEntry = new QueueEntry(memberNode, metadataPid, metadataDoc, qualitySuiteId, localFilePath, requestDateTime,
                sysmeta,
                runXML, null);

        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        ObjectOutput out = new ObjectOutputStream(bos);
        out.writeObject(qEntry);
        message = bos.toByteArray();

        this.writeInProcessChannel(message, QUALITY_ROUTING_KEY);
        log.info(" [x] Queued report request for pid: '" + qEntry.getMetadataPid() + "'" + " quality suite "
                + qualitySuiteId);
    }

    /**
     * Forward a scorer request to the "InProcess" queue.
     * <p>
     * A request to create a graph of aggregated quality scores is serialized and
     * placed on the RabbitMQ "InProcess" queue. This queue is read by worker
     * processes that call the scorer program to obtain the quality scores and
     * create the graph from them.
     * </p>
     *
     * @param collectionId    the DataONE collection identifier (the portal
     *                        seriesId)
     * @param nodeId          the node identifier the collection resides on
     * @param formatFamily    a string representing the DataONE formats to create
     *                        score for ("eml", "iso"), optional
     * @param qualitySuiteId  the quality suite used to create the score graph
     * @param requestDateTime the datetime that the request was made
     *
     * @return
     * @throws java.io.IOException
     */
    public void processScorerRequest(String collectionId,
            String nodeId,
            String formatFamily, // Optional format filter, if creating a graph for a submit of metadata formats
                                 // ("eml", "iso")
            String qualitySuiteId,
            DateTime requestDateTime) throws java.io.IOException {

        log.info("Processing scorer request, collection: " + collectionId + ", suite: " + qualitySuiteId
                + ", nodeId: " + nodeId + ", formatFamily: " + formatFamily);
        ScorerQueueEntry qEntry = null;
        byte[] message = null;

        /**
         * Bookkeeper checking can be disabled via a metadig-engine configuration
         * parameter. The primary use case for doing this is for testing purposes,
         * otherwise checking should always be enabled.
         */
        if (bookkeeperEnabled) {
            try {
                // Bookkeeper creates a portal usage with the portal sid as the 'instanceId',
                // however
                if (!isPortalActive(collectionId)) {
                    log.info("Skipping Scorer request for inactive portal with pid: '" + collectionId + "'"
                            + ", quality suite " + qualitySuiteId);
                    return;
                } else {
                    log.info("Bookkeeper check indicates portal for pid: " + collectionId + " is active.");
                    log.info("Processing with Scorer request for inactive portal with pid: '" + collectionId + "'"
                            + ", quality suite " + qualitySuiteId);
                }
            } catch (MetadigException me) {
                log.error("Unable to contact DataONE bookkeeper: " + me.getMessage()
                        + "\nSkipping Scorer request for portal with pid: '" + collectionId
                        + "'" + ", quality suite " + qualitySuiteId);
                return;
            }
        } else {
            log.info("Bookkeeper quota checking is disabled, proceeding with Scorer request for portal, collectionld: '"
                    + collectionId
                    + "'" + ", quality suite " + qualitySuiteId);
        }

        qEntry = new ScorerQueueEntry(collectionId, qualitySuiteId, nodeId, formatFamily, requestDateTime);

        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        ObjectOutput out = new ObjectOutputStream(bos);
        out.writeObject(qEntry);
        message = bos.toByteArray();

        this.writeInProcessChannel(message, SCORER_ROUTING_KEY);
        log.info(" [x] Queued Scorer request for id: '" + qEntry.getCollectionId() + "'" + ", quality suite "
                + qualitySuiteId + ", nodeId: " + nodeId + ", formatFamily: " + formatFamily);
    }

    /**
     * Intialize the RabbitMQ queues.
     *
     * @throws IOException
     * @throws TimeoutException
     */
    public void setupQueues() throws IOException, TimeoutException {
        ConnectionFactory factory = new ConnectionFactory();
        boolean durable = true;
        factory.setHost(RabbitMQhost);
        factory.setPort(RabbitMQport);
        factory.setPassword(RabbitMQpassword);
        factory.setUsername(RabbitMQusername);
        // connection that will recover automatically
        factory.setAutomaticRecoveryEnabled(true);
        // attempt recovery every 10 seconds after a failure
        factory.setNetworkRecoveryInterval(10000);
        log.debug("Set RabbitMQ host to: " + RabbitMQhost);
        log.debug("Set RabbitMQ port to: " + RabbitMQport);

        // Setup the 'InProcess' queue with a routing key - messages consumed by this
        // queue require that this routine key be used. The routine key
        // QUALITY_ROUTING_KEY sends messages to the quality report worker, the routing
        // key SCORER_ROUTING_KEY sends messages to the aggregation stats scorer.
        try {
            RabbitMQconnection = factory.newConnection();
            RabbitMQchannel = RabbitMQconnection.createChannel();
            RabbitMQchannel.exchangeDeclare(EXCHANGE_NAME, "direct", durable);

            RabbitMQchannel.queueDeclare(QUALITY_QUEUE_NAME, durable, false, false, null);
            RabbitMQchannel.queueBind(QUALITY_QUEUE_NAME, EXCHANGE_NAME, QUALITY_ROUTING_KEY);

            RabbitMQchannel.queueDeclare(SCORER_QUEUE_NAME, durable, false, false, null);
            RabbitMQchannel.queueBind(SCORER_QUEUE_NAME, EXCHANGE_NAME, SCORER_ROUTING_KEY);

            // Channel will only send one request for each worker at a time.
            RabbitMQchannel.basicQos(1);
            log.info("Connected to RabbitMQ queue " + QUALITY_QUEUE_NAME);
            log.info("Connected to RabbitMQ queue " + SCORER_QUEUE_NAME);
        } catch (Exception e) {
            log.error("Error connecting to RabbitMQ queue " + QUALITY_QUEUE_NAME);
            log.error(e.getMessage());
            throw e;
        }

        try {
            RabbitMQchannel.queueDeclare(COMPLETED_QUEUE_NAME, durable, false, false, null);
            RabbitMQchannel.queueBind(COMPLETED_QUEUE_NAME, EXCHANGE_NAME, COMPLETED_ROUTING_KEY);
            log.info("Connected to RabbitMQ queue " + COMPLETED_QUEUE_NAME);
        } catch (Exception e) {
            log.error("Error connecting to RabbitMQ queue " + COMPLETED_QUEUE_NAME);
            log.error(e.getMessage());
            throw e;
        }

        /*
         * This method overrides the RabbitMQ library and implements a callback that is
         * invoked whenever an entry is added
         * to the 'completed' queue. This queue is used to send messages back to the
         * controller after a worker has completed
         * a task. The returned message contains status information about the task that
         * the worker performed.
         */
        final Consumer consumer = new DefaultConsumer(RabbitMQchannel) {
            @Override
            public void handleDelivery(String consumerTag, Envelope envelope, AMQP.BasicProperties properties,
                    byte[] body) throws IOException {

                ByteArrayInputStream bis = new ByteArrayInputStream(body);
                ObjectInput in = new ObjectInputStream(bis);
                // Which client is this message being sent from? The Controller can receive
                // messages from either an assessment worker (type="quality")
                // or from a scorer worker (type="scorer"). These messages contain status
                // information about the task that the workers performed.
                if (properties.getType().equalsIgnoreCase(MESSAGE_TYPE_QUALITY)) {
                    QueueEntry qEntry = null;
                    try {
                        qEntry = (QueueEntry) in.readObject();
                    } catch (java.lang.ClassNotFoundException e) {
                        log.error("Class 'QueueEntry' not found");
                        return; // if we've this this catch something very serious has occurred, just return
                                // since the whole program has probably crashed anyway
                    } finally {
                        RabbitMQchannel.basicAck(envelope.getDeliveryTag(), false);
                    }

                    log.info(" [x] Controller received completed report for pid: '" + qEntry.getMetadataPid()
                            + ", hostname: " + qEntry.getHostname());
                    log.info("Total processing time for worker " + qEntry.getHostname() + " for PID "
                            + qEntry.getMetadataPid() + ": " + qEntry.getProcessingElapsedTimeSeconds());
                    log.info("Total indexing time for worker " + qEntry.getHostname() + " for PID "
                            + qEntry.getMetadataPid() + ": " + qEntry.getIndexingElapsedTimeSeconds());
                    log.info("Total elapsed time for worker " + qEntry.getHostname() + " for PID "
                            + qEntry.getMetadataPid() + ": " + qEntry.getTotalElapsedTimeSeconds());

                    /*
                     * An exception caught by the worker will be passed back to the controller via
                     * the queue entry 'exception' field. Check this now and take the appropriate
                     * action.
                     */
                    Exception me = qEntry.getException();
                    if (me instanceof MetadigException) {
                        log.error("Error running suite: " + qEntry.getQualitySuiteId() + ", pid: "
                                + qEntry.getMetadataPid() + ", error msg: ");
                        log.error("\t" + me.getMessage());
                        Throwable thisCause = me.getCause();
                        if (thisCause != null) {
                            log.error("\tcause: " + thisCause.getMessage());
                        }
                        return;
                    }
                    if (testMode) {
                        long elapsedSeconds = qEntry.getTotalElapsedTimeSeconds();
                        totalElapsedSeconds += elapsedSeconds;
                        runCount += 1;
                        if (runCount == testCount) {
                            log.info("Tests for this run are complete.");
                            log.info("Number of tests run: " + runCount);
                            log.info("Cumulative elapsed time for all workers: "
                                    + TimeUnit.SECONDS.toMinutes(totalElapsedSeconds) + " minutes");
                            log.info("Average worker elapsed time: " + totalElapsedSeconds / runCount + " seconds");
                            log.info("Total elapsed time for controller: "
                                    + String.format("%d", totalElapsedSeconds) + " seconds");
                            disableTestMode();
                        }
                    }
                } else if (properties.getType().equalsIgnoreCase(MESSAGE_TYPE_SCORER)) {
                    ScorerQueueEntry qEntry = null;
                    try {
                        qEntry = (ScorerQueueEntry) in.readObject();
                    } catch (java.lang.ClassNotFoundException e) {
                        log.error("Class 'ScorerQueueEntry' not found");
                        return; // if we've this this catch something very serious has occurred, just return
                                // since the whole program has probably crashed anyway
                    } finally {
                        RabbitMQchannel.basicAck(envelope.getDeliveryTag(), false);
                    }

                    log.info(" [x] Controller received notification of completed score for: '"
                            + qEntry.getCollectionId() + "', hostname: " + qEntry.getHostname());
                    log.info("Total processing time for worker " + qEntry.getHostname() + " for PID "
                            + qEntry.getCollectionId() + ": " + qEntry.getProcessingElapsedTimeSeconds());

                    /*
                     * An exception caught by the worker will be passed back to the controller via
                     * the queue entry
                     * 'exception' field. Check this now and take the appropriate action.
                     */
                    Exception me = qEntry.getException();
                    if (me instanceof MetadigException) {
                        log.error("Error running suite: " + qEntry.getQualitySuiteId() + ", pid: "
                                + qEntry.getCollectionId() + ", error msg: ");
                        log.error("\t" + me.getMessage());
                        Throwable thisCause = me.getCause();
                        if (thisCause != null) {
                            log.error("\tcause: " + thisCause.getMessage());
                        }
                        return;
                    }
                } else {
                    log.error("Unknown RabbitMQ message from from client type: " + properties.getType());
                }
            }
        };

        RabbitMQchannel.basicConsume(COMPLETED_QUEUE_NAME, false, consumer);
    }

    /**
     * Monitors for pids stuck processing using Quartz Scheduler.
     * Starts the scheduler, creates the MonitorJob, and schedules it with a cron
     * trigger, the schedule of which is stored in metadig.properties as
     * quartz.monitor.schedule.
     */
    public void monitor() throws SchedulerException{
        log.debug("Creating stuck processing job monitor.");

        try {
            SchedulerFactory sf = new StdSchedulerFactory();
            Scheduler scheduler = sf.getScheduler();
            scheduler.start();

            String taskName = "processing";
            String groupName = "monitor";
            JobDetail job = null;
            job = newJob(MonitorJob.class)
                    .withIdentity(taskName, groupName)
                    .build();

            CronTrigger trigger = newTrigger()
                    .withIdentity(taskName + "-trigger", groupName)
                    .withSchedule(cronSchedule(monitorSchedule))
                    .build();

            scheduler.scheduleJob(job, trigger);

        } catch (Exception e) { 
            log.error("Monitor: Unable to start Quartz scheduler.");
            SchedulerException s = new SchedulerException(e.getMessage());
            throw s;  // log an error if the monitor can't be started but the rest of the engine can still run
        }
    }

    /**
     * Write an entry to the "InProcess" queue.
     *
     * @param message
     * @throws IOException
     */
    public void writeInProcessChannel(byte[] message, String routingKey) throws IOException {

        AMQP.BasicProperties basicProperties = new AMQP.BasicProperties.Builder()
                .contentType("text/plain")
                .deliveryMode(2) // set this message to persistent
                .build();
        RabbitMQchannel.basicPublish(EXCHANGE_NAME, routingKey, basicProperties, message);
    }

    /**
     * @throws IOException
     * @throws TimeoutException
     */
    public void shutdown() throws IOException, TimeoutException {
        RabbitMQchannel.close();
        RabbitMQconnection.close();

        isStarted = false;
    }

    /**
     * Run a simple test of the report generation facility.
     *
     * @return a boolean set to true if the engine has been started.
     */
    public boolean test() {
        DateTime requestDateTime = new DateTime();

        Controller metadigCtrl = Controller.getInstance();

        InputStream metadata = metadigCtrl.getResourceFile("data/knb.1101.1.xml");
        InputStream sysmeta = metadigCtrl.getResourceFile("data/sysmeta.xml");

        if (!metadigCtrl.getIsStarted()) {
            metadigCtrl.start();
        }

        try {
            metadigCtrl.processQualityRequest("urn:node:mnTestKNB", "1234",
                    metadata, "metadig-test.suite.1", "/tmp", requestDateTime, sysmeta);
        } catch (IOException ioe) {
            ioe.printStackTrace();
            return false;
        }

        return true;
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
        log.info(file.getName());

        InputStream is = classLoader.getResourceAsStream(fileName);

        return is;
    }

    /**
     * Check if the quality engine has been initialized.
     *
     * @return a boolean set to true if the engine has been started.
     */
    public boolean getIsStarted() {
        return isStarted;
    }
}
