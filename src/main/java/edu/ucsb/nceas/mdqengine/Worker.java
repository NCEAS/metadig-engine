package edu.ucsb.nceas.mdqengine;

import com.rabbitmq.client.*;
import edu.ucsb.nceas.mdqengine.model.Result;
import edu.ucsb.nceas.mdqengine.model.Run;
import edu.ucsb.nceas.mdqengine.model.Suite;
import edu.ucsb.nceas.mdqengine.serialize.XmlMarshaller;
import edu.ucsb.nceas.mdqengine.store.InMemoryStore;
import org.apache.commons.codec.digest.DigestUtils;
import org.apache.commons.configuration2.Configuration;
import org.apache.commons.configuration2.builder.fluent.Configurations;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeoutException;

/**
 * The Worker class contains methods that create quality reports for metadata documents
 * and uploads these reports to DataONE member nodes for cataloging and indexing.
 * A worker reads from a RabbitMQ queue that a controller process writes to.
 *
 * @author      Peter Slaughter
 * @version     %I%, %G%
 * @since       1.0
 */
public class Worker {

    private final static String InProcess_QUEUE_NAME = "InProcess";
    private final static String Completed_QUEUE_NAME = "Completed";

    private static Connection inProcessConnection;
    private static Channel inProcessChannel;
    private static Connection completedConnection;
    private static Channel completedChannel;

    public static Log log = LogFactory.getLog(Worker.class);
    private static String RabbitMQhost = "localhost";
    private static Integer RabbitMQport = 5672;
    private static String RabbitMQpassword = "guest";
    private static String RabbitMQusername = "guest";
    private static String authToken = null;


    public static void main(String[] argv) throws Exception {

        Worker wkr = new Worker();
        wkr.readConfig();
        wkr.setupQueues();

        /* This method is overridden from the RabbitMQ library and serves as the callback that is invoked whenenver
         * an entry added to the 'inProcessChannel' and this particular instance of the Worker is selected for
         * delivery of the queue message.
         */
        final Consumer consumer = new DefaultConsumer(inProcessChannel) {
            @Override
            public void handleDelivery(String consumerTag, Envelope envelope, AMQP.BasicProperties properties, byte[] body) throws IOException {

                byte[] message;
                ByteArrayInputStream bis = new ByteArrayInputStream(body);
                ObjectInput in = new ObjectInputStream(bis);
                QueueEntry qEntry = null;
                try {
                    qEntry = (QueueEntry) in.readObject();
                } catch (java.lang.ClassNotFoundException e) {
                    log.info("Class 'QueueEntry' not found");
                }

                //String message = new String(body, "UTF-8");
                Worker wkr = new Worker();
                String runXML = null;

                //log.info(" [x] Received '" + message + "'");
                try {
                    runXML = wkr.processReport(qEntry);
                    qEntry.setRunXML(runXML);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                } catch (java.lang.Exception e) {
                    e.printStackTrace();
                } finally {
                    //inProcessChannel.basicAck(envelope.getDeliveryTag(), false);
                }

                /* Once the quality report has been created, it can be submitted to the member node to be
                   uploaded and indexed.
                */
                try {
                    /* wkr.submitReport(qEntry); */
                    ByteArrayOutputStream bos = new ByteArrayOutputStream();
                    ObjectOutput out = new ObjectOutputStream(bos);
                    out.writeObject(qEntry);
                    message = bos.toByteArray();

                    wkr.writeCompletedQueue(message);
                    log.info(" [x] Sent completed report for pid: '" + qEntry.getMetadataPid() + "'");
                } catch (Exception e) {

                } finally {
                    log.info(" [x] Done");
                    /* Inform the controller that the report has been created and uploaded. */
                    /* TODO: include a status value and description so that when a response is sent for
                     * a failed report creation, the controller can take the appropriate action.
                     */
                    inProcessChannel.basicAck(envelope.getDeliveryTag(), false);
                }
            }
        };

        inProcessChannel.basicConsume(InProcess_QUEUE_NAME, false, consumer);
    }

    /**
     * Declare and connect to the queues that are being maintained by the Controller.
     *
     * @throws IOException
     * @throws TimeoutException
     */
    public void setupQueues () throws IOException, TimeoutException {

        /* Connect to the RabbitMQ queue containing entries for which quality reports
           need to be created.
         */
        ConnectionFactory factory = new ConnectionFactory();
        factory.setHost(RabbitMQhost);
        factory.setPort(RabbitMQport);
        factory.setPassword(RabbitMQpassword);
        factory.setUsername(RabbitMQusername);
        log.info("Set RabbitMQ host to: " + RabbitMQhost);
        log.info("Set RabbitMQ port to: " + RabbitMQport);

        inProcessConnection = factory.newConnection();
        inProcessChannel = inProcessConnection.createChannel();
        inProcessChannel.queueDeclare(InProcess_QUEUE_NAME, false, false, false, null);
        log.info(" [*] Waiting for messages. To exit press CTRL+C");

        // Only send one request to each worker at a time.
        inProcessChannel.basicQos(1);

        // Queue to send generated reports back to controller
        factory = new ConnectionFactory();
        factory.setHost(RabbitMQhost);
        completedConnection = factory.newConnection();
        completedChannel = completedConnection.createChannel();
        completedChannel.queueDeclare(Completed_QUEUE_NAME, false, false, false, null);
    }

    /**
     * Create a quality report for a single metadata document.
     * <p>
     * The processReport method runs the requested metadig-engine quality suite for the provided
     * metadata document. This method appends the generated quality report to the RabbitMQ
     * queue entry.
     * </p>
     *
     * @param message the RabbitMQ queue entry that contains the metadata document to score.
     * @return The quality report as a string.
     * @throws InterruptedException
     * @throws Exception
     */
    public String processReport(QueueEntry message) throws InterruptedException, Exception {

        String suiteId = message.getQualitySuiteId();
        log.info(" [x] Running suite '" + message.getQualitySuiteId() + "'" + " for metadata pid " + message.getMetadataPid());

        String metadataDoc = message.getMetadataDoc();
        InputStream input = new ByteArrayInputStream(metadataDoc.getBytes("UTF-8"));

        SystemMetadata sysmeta = message.getSystemMetadata();

        // Run the Metadata Quality Engine for the specified metadata object.
        // TODO: set suite params correctly
        Map<String, Object> params = new HashMap<String, Object>();
        MDQStore store = new InMemoryStore();

        MDQEngine engine = new MDQEngine();
        Suite suite = store.getSuite(suiteId);
        Run run = engine.runSuite(suite, input, params, sysmeta);
        List<Result> results = run.getResult();

        String runXML = XmlMarshaller.toXml(run);

        return(runXML);
    }

    /**
     * Submit (upload) the newly generated quality report to a member node
     * <p>>
     * The quality report is submitted to the authoritative member node of the source
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

        /* Read the sysmeta for the metadata pid and use appropriate values from it for the
           quality report sysmeta.
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
        log.info(" Created session for subject: " + session.getSubject());

        memberNodeServiceUrl = message.getMemberNode();

        /* Upload the quality report to the source member node */
        MNode mn = null;
        try {
            mn = D1Client.getMN(memberNodeServiceUrl);
        } catch (ServiceFailure ex) {
            ex.printStackTrace();
            log.error("Error connecting to DataONE client at URL: " + memberNodeServiceUrl);
        }

        log.info(" Uploading quality report with pid: " + newId.getValue() + ", rightsHolder: " + sm.getRightsHolder().getValue());

        Identifier returnPid = null;
        try {
            returnPid = mn.create(session, newId, qualityReport, sm);
        } catch (InvalidRequest | NotAuthorized | InvalidToken | InvalidSystemMetadata | UnsupportedType | IdentifierNotUnique | InsufficientResources
                | NotImplemented | ServiceFailure ex) {
            log.error(ex);
            log.error("Error uploading object with PID: " + returnPid.getValue());
        }

        log.info("Uploaded pid " + returnPid.getValue() + " to member node " + mn.getNodeId().getValue());

        return (returnPid.getValue());
    }

    /**
     *
     * @param message
     * @throws IOException
     */
    public void writeCompletedQueue (byte[] message) throws IOException {
        completedChannel.basicPublish("", Completed_QUEUE_NAME, MessageProperties.PERSISTENT_TEXT_PLAIN, message);
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
        log.info(file.getAbsolutePath());

        InputStream is = classLoader.getResourceAsStream(fileName);

        return is;
    }

    /**
     * Read a configuration file for parameter values.
     */
    private void readConfig() {

        Configurations configs = new Configurations();
        Configuration config = null;
        //try {
        //    config = configs.properties(new File("./config/metadig.properties"));
            //RabbitMQhost = config.getString("RabbitMQ.host", RabbitMQhost);
            //RabbitMQport = config.getInteger("RabbitMQ.port", RabbitMQport);
        RabbitMQhost = "metadig-controller.default.svc.cluster.local";
        RabbitMQport = 5672;
        RabbitMQpassword = "guest";
        RabbitMQusername = "guest";
        //} catch (ConfigurationException cex) {
         //   log.error("Unable to read configuration, using default values.");
       // }
    }
}

