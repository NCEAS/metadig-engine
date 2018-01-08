package edu.ucsb.nceas.mdqengine;

import com.rabbitmq.client.*;

import java.io.*;
import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeoutException;
import org.apache.commons.configuration2.Configuration;
import org.apache.commons.configuration2.builder.fluent.Configurations;
import org.apache.commons.configuration2.ex.ConfigurationException;

import edu.ucsb.nceas.mdqengine.MDQEngine;
import edu.ucsb.nceas.mdqengine.MDQStore;
import edu.ucsb.nceas.mdqengine.model.Result;
import edu.ucsb.nceas.mdqengine.model.Run;
import edu.ucsb.nceas.mdqengine.model.Suite;
import edu.ucsb.nceas.mdqengine.store.InMemoryStore;
import edu.ucsb.nceas.mdqengine.serialize.XmlMarshaller;
import org.dataone.service.types.v2.SystemMetadata;


public class Worker {

    private final static String GENERATE_REPORT_QUEUE_NAME = "generateReport";
    private final static String REPORT_CREATED_QUEUE_NAME = "reportCreated";

    private static Connection generateReportConnection;
    private static Channel generateReportChannel;
    private static Connection reportCreatedConnection;
    private static Channel reportCreatedChannel;

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

                    ByteArrayOutputStream bos = new ByteArrayOutputStream();
                    ObjectOutput out = new ObjectOutputStream(bos);
                    out.writeObject(qEntry);
                    message = bos.toByteArray();

                    wkr.writeReportCreatedQueue(message);
                    System.out.println(" [x] Sent completed report for pid: '" + qEntry.getMetadataPid() + "'");
                } catch (InterruptedException e) {
                    e.printStackTrace();
                } catch (java.lang.Exception e) {
                    e.printStackTrace();
                } finally {
                    System.out.println(" [x] Done");
                    generateReportChannel.basicAck(envelope.getDeliveryTag(), false);
                }
            }
        };

        generateReportChannel.basicConsume(GENERATE_REPORT_QUEUE_NAME, false, consumer);
    }

    public void setupQueues () throws IOException, TimeoutException {
        ConnectionFactory factory = new ConnectionFactory();
        factory.setHost("localhost");
        generateReportConnection = factory.newConnection();
        generateReportChannel = generateReportConnection.createChannel();

        generateReportChannel.queueDeclare(GENERATE_REPORT_QUEUE_NAME, false, false, false, null);
        System.out.println(" [*] Waiting for messages. To exit press CTRL+C");

        // Only send one request to each worker at a time.
        generateReportChannel.basicQos(1);

        // Queue to send generated reports back to controller
        factory = new ConnectionFactory();
        factory.setHost("localhost");
        reportCreatedConnection = factory.newConnection();
        reportCreatedChannel = reportCreatedConnection.createChannel();
        reportCreatedChannel.queueDeclare(REPORT_CREATED_QUEUE_NAME, false, false, false, null);
    }

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

    public void writeReportCreatedQueue (byte[] message) throws IOException {
        reportCreatedChannel.basicPublish("", REPORT_CREATED_QUEUE_NAME, MessageProperties.PERSISTENT_TEXT_PLAIN, message);
    }
}

