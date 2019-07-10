package edu.ucsb.nceas.mdqengine.solr;

import edu.ucsb.nceas.mdqengine.MDQconfig;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.solr.client.solrj.impl.HttpSolrClient;
import org.dataone.service.types.v1.Identifier;
import org.dataone.service.types.v2.SystemMetadata;
import org.springframework.context.support.ClassPathXmlApplicationContext;

import java.io.*;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

//import org.apache.solr.client.solrj.impl.HttpSolrClient;

//import org.springframework.context.ApplicationContext;

public class IndexApplicationController {

    private static String SOLRINDEXES = "solrIndexes";
    private static String solrLocation = null;
    // TODO: configure Solr server (location, cloud vs standalone) via config parameters
    private static ArrayList<String> solrLocations = new ArrayList<String>(
            Arrays.asList("http://localhost:8983/solr", "http://localhost:7574/solr"));
    private List<SolrIndex> solrIndexes = null;
    private ClassPathXmlApplicationContext context = null;
    private String springConfigFileURL = null;
    private HttpSolrClient solrClient = null;
    //private CloudSolrClient solrClient = null;
    private SolrIndex solrIndex = null;
    Log log = LogFactory.getLog(IndexApplicationController.class);

    /**
     * Set the Spring configuration file url and metacat.properties file
     */
    public IndexApplicationController() throws Exception {
    }

    /**
     * Initialize the list of the SolrIndex objects from the configuration file.
     * Create a Solr client that will be used for indexing.
     * @param configFile the path of the Spring configuration file
     */
    public void initialize(String configFile, String solrLocation) throws Exception {
        try {
            MDQconfig cfg = new MDQconfig();
            // If not specified on command line, use default fallback
            if(solrLocation == null || solrLocation.equalsIgnoreCase("")) {
                solrLocation = cfg.getString("solr.location.fallback");
            }
            log.debug("Setting solr location to " + solrLocation);
            solrClient = new HttpSolrClient.Builder(solrLocation).build();
            //solrClient = new CloudSolrClient.Builder().withSolrUrl(solrLocations).build();
            //solrClient = new CloudSolrClient.Builder().withZkHost(solrLocation).build();
            //solrClient.setDefaultCollection("quality");
            log.info("Created Solr client at " + solrLocation);
        } catch (Exception e) {
            log.error("Could not create Solr client", e);
            throw e;
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

    public void insertSolrDoc(Identifier pid, SystemMetadata sysmeta, InputStream is) throws IOException, Exception {

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
            log.error("Unable to create output stream from metadata document.");
            throw e;
        }

        if (sysmeta != null) {
            try {
                for (SolrIndex solrIndex: solrIndexes) {
                    log.debug("calling solrIndex.insert()...");
                    solrIndex.insert(pid, sysmeta, tFile.getAbsolutePath());
                }
            } catch (Exception e) {
                log.error("Unable to insert Solr document for PID: " + pid.getValue());
                throw e;
            }
        }
    }
}

