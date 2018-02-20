package edu.ucsb.nceas.mdqengine;

import com.rabbitmq.client.*;

import java.io.*;
import java.util.concurrent.TimeoutException;

import org.apache.commons.configuration2.Configuration;
import org.apache.commons.configuration2.builder.fluent.Configurations;
import org.apache.commons.configuration2.ex.ConfigurationException;
import org.dataone.exceptions.MarshallingException;
import org.dataone.service.types.v2.SystemMetadata;
import org.dataone.service.util.TypeMarshaller;
import org.joda.time.DateTime;

/**
 * The Controller class accepts requests for generating quality documents for
 * for metadata documents. As the report generation process can take a significant
 * amount of time, the controller delegates report generation to worker processes
 * via a RabbitMQ queue that the worker processes read from.
 * @author      Peter Slaughter
 *
 * @version     %I%, %G%
 * @since       1.0
 */
public class Controller {

    private final static String GENERATE_REPORT_QUEUE_NAME = "generateReport";
    private final static String REPORT_CREATED_QUEUE_NAME = "reportCreated";

    private static com.rabbitmq.client.Connection generateReportConnection;
    private static com.rabbitmq.client.Channel generateReportChannel;
    private static com.rabbitmq.client.Connection reportCreatedConnection;
    private static com.rabbitmq.client.Channel reportCreatedChannel;

    private static String RabbitMQhost = null;
    private static Controller instance;

    public static void main(String[] argv) throws Exception {

        Configurations configs = new Configurations();
        Configuration config = null;
        try {
            config = configs.properties(new File("./config/metadig.properties"));
            // access configuration properties
        } catch (ConfigurationException cex) {
            // Something went wrong
        }

        RabbitMQhost = config.getString("RabbitMQ.host");

        DateTime requestDateTime = new DateTime();

        // TODO: move this test into the JUnit tests
        Controller metadigCtrl = Controller.getInstance();

        FileInputStream metadata = new FileInputStream("/Users/slaughter/Projects/Metadig/test/knb.1101.1.xml");
        FileInputStream sysmeta = new FileInputStream( "/Users/slaughter/Projects/Metadig/test/sysmeta.xml");

        metadigCtrl.start();
        metadigCtrl.processRequest("urn:node:mnTestKNB", "1234",
                metadata, "arctic.data.center.suite.1", "/tmp", requestDateTime, sysmeta);

        //metadigCtrl.processRequest("urn:node:mnTestKNB", "4567",
        //        metadata, "arctic.data.center.suite.1", "/tmp", requestDateTime, sysmeta);


        // Check if all queues have been purged, then shutdown
        // metadigCtrl.shutdown();
    }

    private Controller(){}

    /**
     * Implement a Singleton pattern using "double checked locking" pattern.
     * @return a singleton instance of the Controller class.
     */
    public static Controller getInstance(){
        if(instance == null){
            synchronized (Controller.class) {
                if(instance == null){
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

    public void start () {
        try {
            this.setupQueues();
        } catch (java.io.IOException | java.util.concurrent.TimeoutException e) {
            e.printStackTrace();
        }
    }

    /**
     *
     * @param memberNode the member node service URL to send the quality report to.
     * @param metadataPid the identifier of the metadata document.
     * @param metadata the metadata XML document.
     * @param qualitySuiteId the unique identifier of the metadig-engine quality suite, i.e. 'arctic.data.center.suite.1'
     * @param localFilePath the local directory path on the member node where data files can be located (not implemented yet)
     * @param requestDateTime the date and time of the initial report generation request
     * @param systemMetadata the DataONE system metadata for the metadata document.
     * @return
     * @throws java.io.IOException
     */
    public String processRequest( String memberNode,
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
        while(result != -1) {
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

        qEntry = new QueueEntry(memberNode, metadataPid, metadataDoc, qualitySuiteId, localFilePath, requestDateTime, sysmeta, runXML);

        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        ObjectOutput out = new ObjectOutputStream(bos);
        out.writeObject(qEntry);
        message = bos.toByteArray();

        this.writeGenerateQueue(message);
        System.out.println(" [x] Sent report request for pid: '" + qEntry.getMetadataPid() + "'");
        //metadigController.shutdown();

        return("sucess");
    }

    /**
     *
     * @throws IOException
     * @throws TimeoutException
     */
    public void setupQueues () throws IOException, TimeoutException {
        ConnectionFactory factory = new ConnectionFactory();
        factory.setHost("localhost");
        generateReportConnection = factory.newConnection();
        generateReportChannel = generateReportConnection.createChannel();
        generateReportChannel.queueDeclare(GENERATE_REPORT_QUEUE_NAME, false, false, false, null);

        factory = new ConnectionFactory();
        factory.setHost("localhost");
        reportCreatedConnection = factory.newConnection();
        reportCreatedChannel = reportCreatedConnection.createChannel();
        reportCreatedChannel.queueDeclare(REPORT_CREATED_QUEUE_NAME, false, false, false, null);

        /* This method overrides the RabbitMQ library and implements a callback that is invoked whenever an entry is added
         * to 'reportCreatedChannel'.
         */
        final Consumer consumer = new DefaultConsumer(reportCreatedChannel) {
            @Override
            public void handleDelivery(String consumerTag, Envelope envelope, AMQP.BasicProperties properties, byte[] body) throws IOException {

                ByteArrayInputStream bis = new ByteArrayInputStream(body);
                ObjectInput in = new ObjectInputStream(bis);
                QueueEntry qEntry = null;
                try {
                    qEntry = (QueueEntry) in.readObject();
                } catch (java.lang.ClassNotFoundException e) {
                    System.out.println("Class 'QueueEntry' not found");
                } finally {
                    reportCreatedChannel.basicAck(envelope.getDeliveryTag(), false);
                }

                System.out.println(" [x] Controller received completed report for pid: '" + qEntry.getMetadataPid() + "'");
                //System.out.println(qEntry.getRunXML());
            }
        };

        reportCreatedChannel.basicConsume(REPORT_CREATED_QUEUE_NAME, false, consumer);
    }

    /**
     *
     * @param message
     * @throws IOException
     */
    public void writeGenerateQueue (byte[] message) throws IOException {

        generateReportChannel.basicPublish("", GENERATE_REPORT_QUEUE_NAME, MessageProperties.PERSISTENT_TEXT_PLAIN, message);
    }

    /**
     *
     * @throws IOException
     * @throws TimeoutException
     */
    public void shutdown() throws IOException, TimeoutException {
        inProcessReportChannel.close();
        inProcessReportConnection.close();

        completedChannel.close();
        completedConnection.close();
    }

    /**
     * Read a file from a Java resources folder.
     * @param fileName the relative path of the file to read.
     * @return THe resources file.
     */
    private File getResource(String fileName) {

        StringBuilder result = new StringBuilder("");

        //Get file from resources folder
        ClassLoader classLoader = getClass().getClassLoader();
        File file = new File(classLoader.getResource(fileName).getFile());

        return file;
    }
}

