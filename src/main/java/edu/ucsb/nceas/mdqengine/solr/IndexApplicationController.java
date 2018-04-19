package edu.ucsb.nceas.mdqengine.solr;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.solr.client.solrj.SolrClient;
import org.dataone.service.types.v1.Identifier;
import org.dataone.service.types.v2.SystemMetadata;
import org.springframework.context.support.ClassPathXmlApplicationContext;

import java.io.*;
import java.util.List;

import static org.apache.solr.client.solrj.impl.HttpSolrClient.Builder;

//import org.springframework.context.ApplicationContext;

public class IndexApplicationController {

    private static String SOLRINDEXES = "solrIndexes";
    private static String solrLocation = "http://localhost:8983/solr/quality";
    //private final static String defaultSpringConfigFileURL = "/index-processor-context.xml";

    private List<SolrIndex> solrIndexes = null;
    private ClassPathXmlApplicationContext context = null;
    private String springConfigFileURL = null;
    private SolrClient solrClient = null;
    private SolrIndex solrIndex = null;
    Log log = LogFactory.getLog(IndexApplicationController.class);

    /**
     * Set the Spring configuration file url and metacat.properties file
     */
    public IndexApplicationController() throws Exception {
    }

    /**
     * Initialize the list of the SolrIndex objects from the configuration file.
     * Set the SolrServer implementation using the factory.
     * @param configFile the path of the Spring configuration file
     */
    public void initialize(String configFile) throws Exception {
        try {
            solrClient = new Builder(solrLocation).build();
            log.info("Created Solr client");
        } catch (Exception e) {
            log.error("Could not create Solr client", e);
            e.printStackTrace();
        }

        springConfigFileURL = configFile;
        context = getContext(configFile);
        solrIndexes = (List<SolrIndex>) context.getBean(SOLRINDEXES);

        for (SolrIndex solrIndex: solrIndexes) {
            // set the solr client to use
            solrIndex.setSolrClient(solrClient);
            log.info("Set solr client for solrIndex");
        }
        log.info("ApplicationController initialized");
    }

    /**
     * Get the ApplicaionContext of Spring.
     */
    private ClassPathXmlApplicationContext getContext(String configFile) {
        log.info("Getting context...");
        if (context == null) {
            log.info("Creating new ClassPathXmlApplicationContext");
            context = new ClassPathXmlApplicationContext(configFile);
        }
        return context;
    }

    /**
     * Get the path of the Spring configuration file.
     * @return the path of the Spring configuration file.
     */
    public String getSpringConfigFile() {
        return springConfigFileURL;
    }

    /**
     * Get the list of the solr index.
     * @return the list of the solr index.
     */
    public List<SolrIndex> getSolrIndexes() {
        return solrIndexes;
    }

    /**
     * Insert or update a document in the Solr index
     *
     */

    public void insertSolrDoc(Identifier pid, SystemMetadata sysmeta, InputStream is) {

        /* The DataONE indexer reads the input doc as a disk file, so write out the
           data file contents to disk.
         */
        File tFile = null;
        try{
            byte[] buffer = new byte[is.available()];
            is.read(buffer);

            tFile = File.createTempFile("tempfile", ".xml");
            OutputStream outStream = new FileOutputStream(tFile);
            outStream.write(buffer);
        } catch(IOException e){
            e.printStackTrace();
        }

        if (sysmeta != null) {
            log.info("'solrIndexes' size: " + solrIndexes.size());
            try {
                for (SolrIndex solrIndex: solrIndexes) {
                    log.info("calling solrIndex.insert()...");
                    solrIndex.insert(pid, sysmeta, tFile.getAbsolutePath());
                }
            } catch (Exception e) {
                log.error("Unable to insert Solr document for PID: " + pid.getValue());
                e.printStackTrace();
            }
        }
    }
}

