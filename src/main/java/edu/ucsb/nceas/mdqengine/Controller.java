package edu.ucsb.nceas.mdqengine;

import com.rabbitmq.client.*;
import edu.ucsb.nceas.mdqengine.exception.MetadigException;
import org.apache.commons.configuration2.ex.ConfigurationException;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.dataone.exceptions.MarshallingException;
import org.dataone.service.types.v2.SystemMetadata;
import org.dataone.service.util.TypeMarshaller;
import org.joda.time.DateTime;

import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * The Controller class accepts requests for generating quality documents from
 * metadata documents. As the report generation process can take a significant
 * amount of time, the controller delegates report generation to worker processes
 * via a RabbitMQ queue that the worker processes read from.
 *
 * @author      Peter Slaughter
 * @version     %I%, %G%
 * @since       1.0
 */
public class Controller {

    private final static String InProcess_QUEUE_NAME = "InProcess";
    private final static String Completed_QUEUE_NAME = "Completed";

    private static com.rabbitmq.client.Connection inProcessConnection;
    private static com.rabbitmq.client.Channel inProcessChannel;
    private static com.rabbitmq.client.Connection completedConnection;
    private static com.rabbitmq.client.Channel completedChannel;

    // Default values for the RabbitMQ message broker server. The value of 'localhost' is valid for
    // a RabbitMQ server running on a 'bare metal' server, inside a VM, or within a Kubernetes
    // where metadig-controller and the RabbitMQ server are running in containers that belong
    // to the same Pod. These defaults will be used if the properties file cannot be read.
    // These values are read from a config file, see class 'MDQconfig'
    private static String RabbitMQhost = null;
    private static int RabbitMQport = 0;
    private static String RabbitMQpassword = null;
    private static String RabbitMQusername = null;
    private static Controller instance;
    private boolean isStarted = false;
    private int testCount = 0;
    private int runCount = 0;
    private long totalElapsedSeconds = 0;
    private long startTime = 0;
    private boolean testMode = false;
    public static Log log = LogFactory.getLog(Controller.class);

    public static void main(String[] argv) throws Exception {

        Controller metadigCtrl = Controller.getInstance();
        metadigCtrl.start();
        if (metadigCtrl.getIsStarted()) {
            log.info("The controller has been started");
        } else {
            log.info("The controller has not been started");
        }

        log.info("# of arguments: " + argv.length);
        /* When in "test" mode (i.e. if cmd args are passed in), records will be
         read from the port number (argv[0]) which will are the metadata and
         sysmeta filenames to submit to the report generation queue. Records will
         be read in until a line with 'Done' is encountered. The outer loop here
         is so that mulitple 'test' sessions can occur, i.e. controller will wait
         again for a new client to connect to the port, and then begin again reading
         filesnames to run.
        */

        Boolean stop = false;
        if (argv.length > 0) {
            // Continue to listen on specified port for client 'test' requests
            while(true) {
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

                // Client has indicated a stop for all tests, as the first line sent to a new connection.
                if ("Stop".equals(info)) {
                    clientSocket.close();
                    serverSocket.close();
                    break;
                }

                // First line contains the number of tests that will be run
                int cnt = Integer.parseInt(info);
                log.info(cnt + " tests will be run.");
                metadigCtrl.initTests(cnt);

                while ((request = in.readLine()) != null) {
                    // Stop reading all requests
                    if ("Stop".equals(request)) {
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


                    String metadataPid = tokens[0];

                    File metadataFile = new File(tokens[1]);
                    InputStream metadata = new FileInputStream(metadataFile);

                    String suiteId = tokens[2];

                    File sysmetaFile = new File(tokens[3]);
                    InputStream sysmeta = new FileInputStream(sysmetaFile);

                    requestDateTime = new DateTime();
                    log.info("Request queuing of: " + tokens[0] + ", " + tokens[1] + ", " + tokens[2] + ", " + tokens[3]);
                    metadigCtrl.processRequest("urn:node:mnTestKNB", metadataPid,
                            metadata, suiteId, "/tmp", requestDateTime, sysmeta);
                }
                // Close current connection, then start again with new connection
                clientSocket.close();
                serverSocket.close();
                if(stop) break;
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
                    instance = new Controller();
                }
            }
        }
        return instance;
    }

    /**
     * Initialize the quality engine.
     * <p>
     * Prepare the quality engine for use by initializing the RabbitMQ queues and performing
     * and other startup tasks.
     * </p>
     */

    public void start() {
        /* Don't try to re-initialize the queues if they are already started */
        if (isStarted) return;

        try {
            this.readConfig();
            this.setupQueues();
            this.isStarted = true;
        } catch (java.io.IOException | java.util.concurrent.TimeoutException | ConfigurationException e) {
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
        //this.startTime = System.nanoTime();
        this.startTime = System.currentTimeMillis();
        this.testMode = true;
        this.testCount = cnt;
        this.runCount = 0;
        this.totalElapsedSeconds = 0;
    }

    public void readConfig () throws ConfigurationException, IOException {
        MDQconfig cfg = new MDQconfig();

        RabbitMQpassword = cfg.getString("RabbitMQ.password");
        RabbitMQusername = cfg.getString("RabbitMQ.username");
        RabbitMQhost = cfg.getString("RabbitMQ.host");
        RabbitMQport = cfg.getInt("RabbitMQ.port");
        log.debug("Read config:");
        log.debug("RabbitMQpassword: " + RabbitMQpassword);
        log.debug("RabbitMQusername: " + RabbitMQusername);
        log.debug("RabbitMQhost: " + RabbitMQhost);
        log.debug("RabbitMQport: " + RabbitMQport);
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
     * Forward a request to the "InProcess" queue.
     * <p>
     * A request to create a quality report is serialized and placed on the RabbitMQ "InProcess"
     * queue. This queue is read by worker processes that call the quality engine core to generate
     * the report.
     * </p>
     *
     * @param memberNode      the member node service URL to send the quality report to.
     * @param metadataPid     the identifier of the metadata document.
     * @param metadata        the metadata XML document.
     * @param qualitySuiteId  the unique identifier of the metadig-engine quality suite, i.e. 'arctic.data.center.suite.1'
     * @param localFilePath   the local directory path on the member node where data files can be located (not implemented yet)
     * @param requestDateTime the date and time of the initial report generation request
     * @param systemMetadata  the DataONE system metadata for the metadata document.
     * @return
     * @throws java.io.IOException
     */
    public void processRequest(String memberNode,
                               String metadataPid,
                               InputStream metadata,
                               String qualitySuiteId,
                               String localFilePath,
                               DateTime requestDateTime,
                               InputStream systemMetadata) throws java.io.IOException {

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

        try {
            sysmeta = TypeMarshaller.unmarshalTypeFromStream(SystemMetadata.class, systemMetadata);
        } catch (InstantiationException | IllegalAccessException | IOException | MarshallingException fis) {
            fis.printStackTrace();
        }

        // Save a skeleton run entry, to allow clients to check the status of runs that are queued.
        Run run = new Run();
        run.setRunStatus(Run.QUEUED);
        run.setObjectIdentifier(metadataPid);
        run.setSuiteId(qualitySuiteId);
        try {
            run.save(sysmeta);
        } catch (MetadigStoreException mse) {
            log.error("Unable to save run state to 'processing' before queing, continuing..");
        }

        qEntry = new QueueEntry(memberNode, metadataPid, metadataDoc, qualitySuiteId, localFilePath, requestDateTime, sysmeta,
                runXML, null);

        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        ObjectOutput out = new ObjectOutputStream(bos);
        out.writeObject(qEntry);
        message = bos.toByteArray();

        this.writeInProcessQueue(message);
        log.info(" [x] Queued report request for pid: '" + qEntry.getMetadataPid() + "'" + " quality suite " + qualitySuiteId);
    }

    /**
     * Intialize the RabbitMQ queues.
     *
     * @throws IOException
     * @throws TimeoutException
     */
    public void setupQueues() throws IOException, TimeoutException {
        ConnectionFactory factory = new ConnectionFactory();
        factory.setHost(RabbitMQhost);
        factory.setPort(RabbitMQport);
        factory.setPassword(RabbitMQpassword);
        factory.setUsername(RabbitMQusername);
        log.info("Set RabbitMQ host to: " + RabbitMQhost);
        log.info("Set RabbitMQ port to: " + RabbitMQport);

        try {
            inProcessConnection = factory.newConnection();
            inProcessChannel = inProcessConnection.createChannel();
            inProcessChannel.queueDeclare(InProcess_QUEUE_NAME, false, false, false, null);
            // Channel will only send one request for each worker at a time.
            inProcessChannel.basicQos(1);
            log.info("Connected to RabbitMQ queue " + InProcess_QUEUE_NAME);
        } catch (Exception e) {
            log.error("Error connecting to RabbitMQ queue " + InProcess_QUEUE_NAME);
            log.error(e.getMessage());
            throw e;
        }

        try {
            completedConnection = factory.newConnection();
            completedChannel = completedConnection.createChannel();
            completedChannel.queueDeclare(Completed_QUEUE_NAME, false, false, false, null);
            log.info("Connected to RabbitMQ queue " + Completed_QUEUE_NAME);
        } catch (Exception e) {
            log.error("Error connecting to RabbitMQ queue " + Completed_QUEUE_NAME);
            log.error(e.getMessage());
            throw e;
        }

        /* This method overrides the RabbitMQ library and implements a callback that is invoked whenever an entry is added
         * to 'completedChannel'.
         */
        final Consumer consumer = new DefaultConsumer(completedChannel) {
            @Override
            public void handleDelivery(String consumerTag, Envelope envelope, AMQP.BasicProperties properties, byte[] body) throws IOException {

                ByteArrayInputStream bis = new ByteArrayInputStream(body);
                ObjectInput in = new ObjectInputStream(bis);
                QueueEntry qEntry = null;
                try {
                    qEntry = (QueueEntry) in.readObject();
                } catch (java.lang.ClassNotFoundException e) {
                    log.info("Class 'QueueEntry' not found");
                } finally {
                    completedChannel.basicAck(envelope.getDeliveryTag(), false);
                }

                log.info(" [x] Controller received completed report for pid: '" + qEntry.getMetadataPid() + "'" + ", " +
                        "hostsname: " + qEntry.getHostname());
                log.info("Total processing time for worker " + qEntry.getHostname() + " for PID " + qEntry.getMetadataPid() + ": " + qEntry.getProcessingElapsedTimeSeconds());
                log.info("Total indexing time for worker " + qEntry.getHostname() + " for PID " + qEntry.getMetadataPid() + ": " + qEntry.getIndexingElapsedTimeSeconds());
                log.info("Total elapsed time for worker " + qEntry.getHostname() + " for PID " + qEntry.getMetadataPid() + ": " + qEntry.getTotalElapsedTimeSeconds());

                /* An exception caught by the worker will be passed back to the controller via the queue entry
                 * 'exception' field. Check this now and take the appropriate action.
                 */
                Exception me = qEntry.getException();
                if (me instanceof MetadigException) {
                    log.error("Error running suite: " + qEntry.getQualitySuiteId()+ ", pid: " + qEntry.getMetadataPid() + ", error msg: ");
                    log.error("\t" + me.getMessage());
                    Throwable thisCause = me.getCause();
                    if(thisCause != null) {
                        log.error("\tcause: " + thisCause.getMessage());
                    }
                    return;
                }
                if(testMode) {
                    long elapsedSeconds = qEntry.getTotalElapsedTimeSeconds();
                    totalElapsedSeconds += elapsedSeconds;
                    runCount += 1;
                    if(runCount == testCount) {
                        log.info("Tests for this run are complete.");
                        log.info("Number of tests run: " + runCount);
                        log.info("Cummulative elapsed time for all workers: " + TimeUnit.SECONDS.toMinutes(totalElapsedSeconds) + " minutes");
                        log.info("Average worker elapsed time: " + totalElapsedSeconds/runCount + " seconds");
                        log.info("Total elapsed time for controller: "
                                + String.format("%d", totalElapsedSeconds) + " seconds");
                        disableTestMode();
                    }
                }
            }
        };

        completedChannel.basicConsume(Completed_QUEUE_NAME, false, consumer);
    }

    /**
     * Write an entry to the "InProcess" queue.
     *
     * @param message
     * @throws IOException
     */
    public void writeInProcessQueue(byte[] message) throws IOException {

        inProcessChannel.basicPublish("", InProcess_QUEUE_NAME, MessageProperties.PERSISTENT_TEXT_PLAIN, message);
    }

    /**
     * @throws IOException
     * @throws TimeoutException
     */
    public void shutdown() throws IOException, TimeoutException {
        inProcessChannel.close();
        inProcessConnection.close();

        completedChannel.close();
        completedConnection.close();
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
            metadigCtrl.processRequest("urn:node:mnTestKNB", "1234",
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

        //Get file from resources folder
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

