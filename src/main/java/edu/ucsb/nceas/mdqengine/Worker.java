package edu.ucsb.nceas.mdqengine;

import com.rabbitmq.client.*;

import java.io.*;
import java.io.IOException;
import java.math.BigInteger;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeoutException;
import java.util.UUID;
import java.io.InputStream;

import org.apache.commons.configuration2.Configuration;
import org.apache.commons.configuration2.builder.fluent.Configurations;
import org.apache.commons.configuration2.ex.ConfigurationException;
import org.apache.log4j.Logger;

import edu.ucsb.nceas.mdqengine.model.Result;
import edu.ucsb.nceas.mdqengine.model.Run;
import edu.ucsb.nceas.mdqengine.model.Suite;
import edu.ucsb.nceas.mdqengine.store.InMemoryStore;
import edu.ucsb.nceas.mdqengine.serialize.XmlMarshaller;

import org.apache.commons.codec.digest.DigestUtils;
import org.dataone.client.auth.AuthTokenSession;
import org.dataone.client.v2.itk.D1Client;
import org.dataone.client.v2.MNode;
import org.dataone.service.exceptions.*;
import org.dataone.service.types.v1.*;
import org.dataone.service.types.v2.SystemMetadata;

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

    private final static String GENERATE_REPORT_QUEUE_NAME = "generateReport";
    private final static String REPORT_CREATED_QUEUE_NAME = "reportCreated";

    private static Connection generateReportConnection;
    private static Channel generateReportChannel;
    private static Connection reportCreatedConnection;
    private static Channel reportCreatedChannel;

    private static Logger logger = Logger.getLogger(Worker.class);
    private static String RabbitMQhost = null;
    private static String authToken = null;

    public static void main(String[] argv) throws Exception {

        Worker wkr = new Worker();
        wkr.setupQueues();

        Configurations configs = new Configurations();
        Configuration config = null;
        try {
            config = configs.properties(new File("./config/metadig.properties"));
            // access configuration properties
        } catch (ConfigurationException cex) {
            // Something went wrong
        }

        /* The host on which the Controller process is running. The RabbitMQ queue can be on
         * any host, but for simplicity it is on the same host that the Controller is running on.
         */
        RabbitMQhost = config.getString("RabbitMQ.host");
        /* The authentication token that will allow a worker to upload the quality report to the member node. */
        authToken = config.getString("dataone.authToken");

        /* This method is overridden from the RabbitMQ library and serves as the callback that is invoked whenenver
         * an entry added to the 'generatedReportChannel' and this particular instance of the Worker is selected for
         * delivery of the queue message.
         */
        final Consumer consumer = new DefaultConsumer(generateReportChannel) {
            @Override
            public void handleDelivery(String consumerTag, Envelope envelope, AMQP.BasicProperties properties, byte[] body) throws IOException {

                byte[] message;
                ByteArrayInputStream bis = new ByteArrayInputStream(body);
                ObjectInput in = new ObjectInputStream(bis);
                QueueEntry qEntry = null;
                try {
                    qEntry = (QueueEntry) in.readObject();
                } catch (java.lang.ClassNotFoundException e) {
                    System.out.println("Class 'QueueEntry' not found");
                }

                //String message = new String(body, "UTF-8");
                Worker wkr = new Worker();
                String runXML = null;

                //System.out.println(" [x] Received '" + message + "'");
                try {
                    runXML = wkr.processReport(qEntry);
                    qEntry.setRunXML(runXML);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                } catch (java.lang.Exception e) {
                    e.printStackTrace();
                } finally {
                    System.out.println(" [x] Done");
                    //generateReportChannel.basicAck(envelope.getDeliveryTag(), false);
                }

                /* Once the quality report has been created, it can be submitted to the member node to be
                   uploaded and indexed.
                */
                try {
                    wkr.submitReport(qEntry);
                    ByteArrayOutputStream bos = new ByteArrayOutputStream();
                    ObjectOutput out = new ObjectOutputStream(bos);
                    out.writeObject(qEntry);
                    message = bos.toByteArray();

                    wkr.writeReportCreatedQueue(message);
                    System.out.println(" [x] Sent completed report for pid: '" + qEntry.getMetadataPid() + "'");
                } catch (Exception e) {

                } finally {
                    System.out.println(" [x] Done");
                    /* Inform the controller that the report has been created and uploaded. */
                    /* TODO: include a status value and description so that when a response is sent for
                       a failed report creation, the controller can take the appropriate action.
                     */
                    generateReportChannel.basicAck(envelope.getDeliveryTag(), false);
                }

            }
        };

        generateReportChannel.basicConsume(GENERATE_REPORT_QUEUE_NAME, false, consumer);
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
        generateReportConnection = factory.newConnection();
        generateReportChannel = generateReportConnection.createChannel();

        generateReportChannel.queueDeclare(GENERATE_REPORT_QUEUE_NAME, false, false, false, null);
        System.out.println(" [*] Waiting for messages. To exit press CTRL+C");

        // Only send one request to each worker at a time.
        generateReportChannel.basicQos(1);

        // Queue to send generated reports back to controller
        factory = new ConnectionFactory();
        factory.setHost(RabbitMQhost);
        reportCreatedConnection = factory.newConnection();
        reportCreatedChannel = reportCreatedConnection.createChannel();
        reportCreatedChannel.queueDeclare(REPORT_CREATED_QUEUE_NAME, false, false, false, null);
    }

    /**
     * The processReport method runs the requested metadig-engine quality suite for the provided
     * metadata document. This method appends the generated quality report to the RabbitMQ
     * queue entry.
     *
     * @param message the RabbitMQ queue entry that contains the metadata document to score.
     * @return
     * @throws InterruptedException
     * @throws Exception
     */
    public String processReport(QueueEntry message) throws InterruptedException, Exception {

        String suiteId = message.getQualitySuiteId();
        System.out.println(" [x] Running suite '" + message.getQualitySuiteId() + "'" + " for metadata pid " + message.getMetadataPid());

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
     * Submit (upload) the newly generated quality report to the source member node of the metadata document that
     * the report was generated for.
     *
     * @param message the RabbitMQ message containing the metadata document and generated quality report.
     * @return pid the
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
            logger.error("Unable to read quality report for metadata pid: " + message.getMetadataPid());
            logger.error(ex);
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
        System.out.println(" Created session for subject: " + session.getSubject());

        memberNodeServiceUrl = message.getMemberNode();

        /* Upload the quality report to the source member node */
        MNode mn = null;
        try {
            mn = D1Client.getMN(memberNodeServiceUrl);
        } catch (ServiceFailure ex) {
            ex.printStackTrace();
            System.out.println("Error connecting to DataONE client at URL: " + memberNodeServiceUrl);
        }

        System.out.println(" Uploading quality report with pid: " + newId.getValue() + ", rightsHolder: " + sm.getRightsHolder().getValue());

        Identifier returnPid = null;
        try {
            returnPid = mn.create(session, newId, qualityReport, sm);
        } catch (InvalidRequest | NotAuthorized | InvalidToken | InvalidSystemMetadata | UnsupportedType | IdentifierNotUnique | InsufficientResources
                | NotImplemented | ServiceFailure ex) {
            logger.error(ex);
            System.out.println("Error uploading object with PID: " + returnPid.getValue());
        }

        System.out.println("Uploaded pid " + returnPid.getValue() + " to member node " + mn.getNodeId().getValue());

        return (returnPid.getValue());
    }

    /**
     *
     * @param message
     * @throws IOException
     */
    public void writeReportCreatedQueue (byte[] message) throws IOException {
        reportCreatedChannel.basicPublish("", REPORT_CREATED_QUEUE_NAME, MessageProperties.PERSISTENT_TEXT_PLAIN, message);
    }
}

