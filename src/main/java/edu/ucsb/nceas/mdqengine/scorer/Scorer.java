package edu.ucsb.nceas.mdqengine.scorer;

import com.rabbitmq.client.*;
import edu.ucsb.nceas.mdqengine.MDQconfig;
import edu.ucsb.nceas.mdqengine.exception.MetadigException;
import edu.ucsb.nceas.mdqengine.exception.MetadigProcessException;
import edu.ucsb.nceas.mdqengine.filestore.MetadigFile;
import edu.ucsb.nceas.mdqengine.filestore.MetadigFileStore;
import edu.ucsb.nceas.mdqengine.filestore.StorageType;
import edu.ucsb.nceas.mdqengine.solr.QualityScore;
import org.apache.commons.configuration2.ex.ConfigurationException;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVPrinter;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.solr.client.solrj.SolrClient;
import org.apache.solr.client.solrj.SolrQuery;
import org.apache.solr.client.solrj.SolrServerException;
import org.apache.solr.client.solrj.beans.BindingException;
import org.apache.solr.client.solrj.impl.HttpSolrClient;
import org.apache.solr.client.solrj.response.QueryResponse;
import org.dataone.client.auth.AuthTokenSession;
import org.dataone.client.rest.DefaultHttpMultipartRestClient;
import org.dataone.client.rest.MultipartRestClient;
import org.dataone.client.v2.impl.MultipartCNode;
import org.dataone.client.v2.impl.MultipartMNode;
import org.dataone.client.v2.impl.MultipartD1Node; // Don't include org.dataone.client.rest.MultipartD1Node (this is what IDEA selects)
import org.dataone.service.types.v1.Session;
import org.dataone.service.types.v1.Subject;
import org.dataone.service.types.v1.Group;
import org.dataone.service.types.v1.Identifier;
import org.dataone.service.types.v1.SubjectInfo;
import org.dataone.service.types.v1.SystemMetadata;
import org.joda.time.DateTime;
import org.joda.time.format.DateTimeFormatter;
import org.joda.time.format.ISODateTimeFormat;
import org.w3c.dom.Document;
import org.xml.sax.InputSource;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathExpression;
import javax.xml.xpath.XPathFactory;
import java.io.*;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The Scorer class contains methods that create graphs of aggregated quality scores.
 *
 * Peter Slaughter
 */
public class Scorer {

    private final static String EXCHANGE_NAME = "metadig";
    private final static String SCORER_QUEUE_NAME = "scorer";
    private final static String COMPLETED_QUEUE_NAME = "completed";
    private final static String SCORER_ROUTING_KEY = "scorer";
    private final static String COMPLETED_ROUTING_KEY = "completed";
    private final static String MESSAGE_TYPE_SCORER = "scorer";

    private static Connection inProcessConnection;
    private static Channel inProcessChannel;
    private static Connection completedConnection;
    private static Channel completedChannel;

    private static Log log = LogFactory.getLog(Scorer.class);
    private static String RabbitMQhost = null;
    private static int RabbitMQport = 0;
    private static String RabbitMQpassword = null;
    private static String RabbitMQusername = null;
    private static String CNauthToken = null;
    private static String CNsubjectId = null;
    private static String CNserviceUrl = null;
    private static SolrClient client = null;
    private static String solrLocation = null;
    private static String filestoreBase = null;

    private static long startTimeProcessing;
    private static long elapsedTimeSecondsProcessing;

    public static void main(String[] argv) throws Exception {

        Scorer gfr = new Scorer();
        MDQconfig cfg = new MDQconfig ();

        try {
            RabbitMQpassword = cfg.getString("RabbitMQ.password");
            RabbitMQusername = cfg.getString("RabbitMQ.username");
            RabbitMQhost = cfg.getString("RabbitMQ.host");
            RabbitMQport = cfg.getInt("RabbitMQ.port");
            solrLocation = cfg.getString("solr.location");
            filestoreBase = cfg.getString("metadig.store.directory");
            CNauthToken =  cfg.getString("CN.authToken");
            CNserviceUrl = cfg.getString("CN.serviceUrl");
            CNsubjectId = cfg.getString("CN.subjectId");
        } catch (ConfigurationException cex) {
            log.error("Unable to read configuration");
            MetadigException me = new MetadigException("Unable to read config properties");
            me.initCause(cex.getCause());
            throw me;
        }

        gfr.setupQueues();

        /* This method is overridden from the RabbitMQ library and serves as a callback. When a queue entry is submitted to the
         * 'graph' queue, RabbitMQ invokes this callback, passing in the queue entry and property information. The metadig-controller
         * program is the agent that sends the graph request, and will receive a message back when the request has been fulfilled.
         *
         */
        final Consumer consumer = new DefaultConsumer(inProcessChannel) {
            @Override
            public void handleDelivery(String consumerTag, Envelope envelope, AMQP.BasicProperties properties, byte[] body) throws IOException {

                // Read the queue entry
                ByteArrayInputStream bis = new ByteArrayInputStream(body);
                ObjectInput in = new ObjectInputStream(bis);
                ScorerQueueEntry qEntry = null;
                String graphFilename = null;
                MetadigException metadigException = null;

                //long startTime = System.nanoTime();
                startTimeProcessing = System.currentTimeMillis();
                elapsedTimeSecondsProcessing = 0L;

                // Read the queue entry passed to the callback from RabbitMQ
                try {
                    qEntry = (ScorerQueueEntry) in.readObject();
                } catch (java.lang.ClassNotFoundException e) {
                    log.error("Unable to process graph request");
                    e.printStackTrace();
                    return;
                }

                // The components of the graph queue request
                String collectionId = qEntry.getProjectId();
                String projectName = qEntry.getProjectName();
                String authToken = qEntry.getAuthToken();
                String nodeId = qEntry.getNodeId();
                String serviceUrl = qEntry.getServiceUrl();
                String formatFamily = qEntry.getFormatFamily();
                String suiteId = qEntry.getQualitySuiteId();

                // Requests coming from the controller via webapp will not have
                // authToken or serviceUrl set, so use the default.
                if(authToken == null || authToken.isEmpty())
                    authToken = CNauthToken;

                if(serviceUrl == null || serviceUrl.isEmpty())
                    serviceUrl = CNserviceUrl;

                if(nodeId == null)
                    nodeId = "";

                if(formatFamily == null)
                    formatFamily = "";

                if(suiteId == null)
                    suiteId = "";

                if(collectionId == null)
                    collectionId = "";

                // Pids associated with a collection, based on query results using 'collectionQuery' field in solr.
                ArrayList<String> collectionPids = null;

                String title = "Project " + projectName;
                HashMap<String, Object> variables = new HashMap<>();
                long difference;
                // Create the graph.
                // Two types of graphs are currently supported:
                // - a graph for all pids included in a DataONE collection (portal), and a specified suite id
                // - a graph for specified filters: member node, suite id, metadata format
                try {
                    MetadigFile mdFile = new MetadigFile();
                    Graph graph = new Graph();
                    Scorer gfr = new Scorer();
                    // If creating a graph for a collection, get the set of pids associated with the collection.
                    // Only scores for these pids will be included in the graph.
                    if(collectionId != null && ! collectionId.isEmpty()) {
                        log.info("Getting pids for collection " + collectionId);
                        // Always use the CN subject id and authentication token from the configuration file, as
                        // requests that this method uses need CN subject privs
                        collectionPids = gfr.getCollectionPids(collectionId, nodeId, serviceUrl, CNsubjectId, CNauthToken);
                    }

                    // Quality scores will now be obtained from the MetaDIG quality Solr index, using the list of pids obtained
                    // for the collection.
                    List<QualityScore> scores = gfr.getQualityScores(collectionId, suiteId, nodeId, formatFamily, collectionPids);
                    log.debug("# of quality scores returned: " + scores.size());
                    File scoreFile = gfr.createScoreFile(scores);
                    log.debug("Created score file: " + scoreFile.getPath());

                    // Create the graph and write to the filestore
                    MetadigFileStore filestore = new MetadigFileStore();

                    // TODO: get graph type from rabbitmq message
                    // Use 'variables' to pass info to the graph program

                    // Generate a temporary graph file based on the quality scores
                    log.debug("Creating graph");
                    String filePath = graph.create(GraphType.CUMULATIVE, title, scoreFile.getPath());
                    // Now save the graphics file to permanent storage
                    //String outfile = projectName + "-" + suiteId + ".png";
                    String outfile;

                    DateTime createDateTime = DateTime.now();

                    mdFile.setCreationDatetime(createDateTime);
                    mdFile.setCollectionId(collectionId);
                    mdFile.setSuiteId(suiteId);
                    mdFile.setNodeId(nodeId);
                    mdFile.setStorageType(StorageType.GRAPH.toString());
                    mdFile.setMediaType("image/jpeg");

                    Boolean replace = true;
                    // Save the generated graph file to the MetaDIG filestore
                    outfile = filestore.saveFile(mdFile, filePath, replace);
                    log.debug("Output graphics file " + outfile);

                    // Now save the score file, as this may be requested from a client
                    // This should have all the same info as the graphics file, except
                    // for fileid, storagetype, extension
                    mdFile = new MetadigFile();
                    mdFile.setCreationDatetime(createDateTime);
                    mdFile.setCollectionId(collectionId);
                    mdFile.setSuiteId(suiteId);
                    mdFile.setNodeId(nodeId);
                    mdFile.setStorageType(StorageType.DATA.toString());
                    mdFile.setMediaType("text/csv");
                    outfile = filestore.saveFile(mdFile, scoreFile.getPath(), replace);
                    log.debug("Output data file " + outfile);
                } catch (Exception e) {
                    log.error("Error creating graph: " + e.getMessage());
                    metadigException = new MetadigProcessException("Unable to create graph: " + e.getMessage());
                    metadigException.initCause(e);
                    qEntry.setException(metadigException);
                }

                // Send the report (completed or not) to the controller, with errors that were encountered.
                try {
                    difference = System.currentTimeMillis() - startTimeProcessing;
                    elapsedTimeSecondsProcessing = TimeUnit.MILLISECONDS.toSeconds(difference);
                    log.debug("Sending report info back to controller...");
                    qEntry.setProcessingElapsedTimeSeconds(elapsedTimeSecondsProcessing);
                    // Send a message to the controller for this job
                    gfr.returnGraphStatus(collectionId, projectName, qEntry);
                    log.debug("Sent report info back to controller...");
                } catch (IOException ioe) {
                    log.error("Unable to return quality report to controller.");
                    ioe.printStackTrace();
                }

                // Inform RabbitMQ that we are done with this task, and am ready for another.
                inProcessChannel.basicAck(envelope.getDeliveryTag(), false);
                log.info("Scorer completed task");
            }
        };

        inProcessChannel.basicConsume(SCORER_QUEUE_NAME, false, consumer);
    }

    /**
     * Retrieve pids associated with a DataONE collection.
     *
     * <p>First the 'collectionQuery' field is retrieved from DataONE Solr for the collection</p>
     * <p>Next, a query is issued with the query from collectionQuery field, to retrieve all Solr docs for the collection ids./p>
     *
     * @param collectionId a DataONE project id to fetch scores for, e.g. urn:uuid:f137095e-4266-4474-aa5f-1e1fcaa5e2dc
     * @param nodeId a DataONE node identifier, e.g. "urn:node:KNB"
     * @param
     * @return a List of quality scores fetched from Solr
     */
    private ArrayList<String> getCollectionPids(String collectionId, String nodeId, String serviceUrl, String subjectId, String authToken) throws Exception {

        Document xmldoc = null;
        String queryStr = null;
        // Page though the results, requesting a certain amount of pids at each request
        int startPos = 0;
        int countRequested = 1000;

        /* If we are creating a graph for a DataONE project, then we have to first retrieve the Solr field 'collectionQuery' from the DataONE Solr engine
           which will be used to query DataONE Solr for all the pids associated with that project (that's 2 queries!)
         */
        ArrayList<String> pids = new ArrayList<>();
        queryStr = "?q=id:" + escapeSpecialChars(collectionId) + "&fl=collectionQuery,label&q.op=AND";

        startPos = 0;
        countRequested = 10000;
        xmldoc = queryD1Solr(queryStr, serviceUrl, startPos, countRequested, subjectId, authToken);

        if(xmldoc == null) {
            throw new MetadigException("No result returned from Solr query: " + queryStr);
        }

        // Extract the ids from the Solr result XML
        XPathFactory xPathfactory = XPathFactory.newInstance();
        XPath xpath = xPathfactory.newXPath();
        XPathExpression fieldXpath = null;
        // TODO: replace this test query with the live one
        fieldXpath = xpath.compile("//result/doc/str[@name='collectionQuery']/text()");

        // extract the 'collectionQuery' field from the Solr result
        org.w3c.dom.NodeList xpathResult = (org.w3c.dom.NodeList) fieldXpath.evaluate(xmldoc, XPathConstants.NODESET);
        org.w3c.dom.Node node = xpathResult.item(0);
        String collectionQuery = node.getTextContent();

        if(collectionQuery == null) {
            log.error("Unable to fetch collectionQuery field for collection id: " + collectionId);
            throw new MetadigException("Unable to fetch collectionQuery field for collection id: " + collectionId);
        }

        // Extract the portal 'label' (title)
        fieldXpath = xpath.compile("//result/doc/str[@name='label']/text()");
        xpathResult = (org.w3c.dom.NodeList) fieldXpath.evaluate(xmldoc, XPathConstants.NODESET);
        node = xpathResult.item(0);
        String projectName = node.getTextContent();

        // Get account information for the collection owner. The account info will be used when the 'collectionQuery'
        // query is made, which will use the owner's identity and group memberships, so that the pids that are returned
        // from the query are the ones that the user would see when viewing their portal page.
        // First get the sysmeta from the collection pid, in order to determine the owner. Next, get the account info
        // from the CN. Then add those groups into the query. Each group will be included in the filter query in this format:
        //     "(readPermission:"http://orcid.org/0000-0002-2192-403X")
        //      OR (rightsHolder:"http://orcid.org/0000-0002-2192-403X")"
        SystemMetadata sysmeta = null;
        try {
            sysmeta = getSystemMetadata(collectionId, serviceUrl, subjectId, authToken);
        } catch (MetadigProcessException mpe) {
            log.error("Unable to get system metadata for collection: " + collectionId);
            throw(mpe);
        }

        Subject rightsHolder = sysmeta.getRightsHolder();
        SubjectInfo subjectInfo = getSubjectInfo(rightsHolder, serviceUrl, subjectId, authToken);
        String groupStr = null;

        groupStr = "(readPermission:" + "\"" + rightsHolder.getValue()
                + "\")" + " OR (rightsHolder:\"" + rightsHolder.getValue() + "\"" + ")"
                + " OR (readPermission:\"public\")";

        // Assemble the
        for(Group group : subjectInfo.getGroupList()) {
            log.debug("Adding group to query: " + group.getSubject().getValue());
            if(groupStr == null) {
                groupStr = "(readPermission:" + "\"" + group.getSubject().getValue()
                        + "\")" + " OR (rightsHolder:\"" + group.getSubject().getValue() + "\"" + ")";
            } else {
                groupStr += " OR (readPermission:" + "\"" + group.getSubject().getValue()
                        + "\")" + " OR (rightsHolder:\"" + group.getSubject().getValue() + "\"" + ")";
            }
        }

        //groupStr = "+AND+" + "(" + groupStr + ")";
        //groupStr = "&fq=" + encodeValue("rightsHolder:\"CN=PASTA-GMN,O=LTER,ST=New Mexico,C=US\"");
        groupStr = "&fq=" + encodeValue(groupStr);

        // Send the collectionQuery string to Solr to get the pids associated with the collection
        // The 'collectionQuery' Solr field may have backslashes that are used to escape special characters (i.e. ":") that are not
        // intended to be interpreted by Solr. These backslashes however, have to be encoded in the URL sent to Solr. Re have
        // to be selective in what is encoded, as encoded other chars causes problems.
        queryStr = "?q=" + encodeValue(collectionQuery) + groupStr + "&fl=id&q.op=AND";

        int resultCount = 0;
        startPos = 0;
        countRequested = 1000;
        // Now get the pids associated with the project
        // One query can return many documents, so use the paging mechanism to make sure we retrieve them all.
        // Keep paging through query results until all pids have been fetched. The last 'page' of query
        // results is indicated by the number of items returned being less than the number requested.
        int thisResultLength;
        // Now setup the xpath to retrieve the ids returned from the collection query.
        fieldXpath = xpath.compile("//result/doc/str[@name='id']/text()");
        // Loop through the Solr result. As the result may be large, page through the results, accumulating
        // the pids returned

        log.debug("query string: " + queryStr);
        do {
            //TODO: check that a result was returned
            xmldoc = queryD1Solr(queryStr, serviceUrl, startPos, countRequested, subjectId, authToken);
            if(xmldoc == null) {
                log.info("no values returned from query");
                break;
            }
            xpathResult = (org.w3c.dom.NodeList) fieldXpath.evaluate(xmldoc, XPathConstants.NODESET);
            String currentPid = null;
            thisResultLength = xpathResult.getLength();
            log.trace("Got " + thisResultLength + " pids this query");
            if(thisResultLength == 0) break;
            for (int index = 0; index < xpathResult.getLength(); index++) {
                node = xpathResult.item(index);
                currentPid = node.getTextContent();
                pids.add(currentPid);
            }

            startPos += thisResultLength;
        } while (thisResultLength > 0);

        return pids;
    }

    /**
       * Retrieve quality scores from the MetaDIG Quality Solr Server.
       *
       * @param collectionId a DataONE project id to fetch scores for, e.g. urn:uuid:f137095e-4266-4474-aa5f-1e1fcaa5e2dc
       * @param suiteId a MetaDIG quality suite id, e.g. "FAIR.suite.1"
       * @param nodeId a DataONE node identifier, e.g. "urn:node:KNB"
       * @param formatFamily list of MetaDIG metadata format "families", e.g. "iso19115,eml"
       * @param
       * @return a List of quality scores fetched from Solr
       */
    private List<QualityScore> getQualityScores(String collectionId, String suiteId, String nodeId, String formatFamily, ArrayList<String> collectionPids) throws Exception {
        // Now that we have all the pids, query the Quality Solr server for the scores for each pid associate with the project.
        // These scores will be written out to a file that will be used by the graphing routine to create a plot of the aggregated statistics.
        // If a project wasn't specified, then we are not building a special query for a list of pids, so try to get the max amount
        // of pids per query.
        List<QualityScore> resultList = null;
        ArrayList<QualityScore> allResults = new ArrayList<>();

        String pidStr;
        String queryStr = null;
        int countRequested;
        String listString;
        ArrayList<String> tmpList;
        String formatFamilySearchTerm = null;

        // The metadata format family can be specified to filter the quality scores that will be included
        // in the graph./
        if (formatFamily != null) {
            if(formatFamily.split(",").length == 1) {
                formatFamilySearchTerm = "*" + formatFamily + "*";
            } else {
                String terms[] = formatFamilySearchTerm.split(",");
                for (int iterm = 0; iterm < terms.length; iterm++) {
                    if(iterm > 0) {
                        formatFamilySearchTerm += "," + "*" + terms[iterm] + "*";
                    } else {
                        formatFamilySearchTerm += "*" + terms[iterm] + "*";
                    }
                }
                formatFamilySearchTerm = "(" + formatFamilySearchTerm + ")";
            }
        }

        int startPosInResult = 0;
        int startPosInQuery = 0; // this will always be zero - we are listing the pids to retrieve, so will always want to start at the first result

        // Now accumulate the Quality Solr document results for the list of pids for the project.
        if (collectionId != null && ! collectionId.isEmpty()) {
            log.info("Getting quality scores for collection: " + collectionId);
            int pidCntToRequest = 25;
            int totalPidCnt = collectionPids.size();
            int pidsLeft = totalPidCnt;
            do {
                // On the last run, the pids to retrieve may be less that the 'desired' amount
                pidCntToRequest = Math.min(pidsLeft, pidCntToRequest);
                tmpList = new ArrayList(collectionPids.subList(startPosInResult, startPosInResult+pidCntToRequest));
                startPosInResult += pidCntToRequest;
                //pidStr = "metadataId:(" + "\"" + listString + "\"" + ")";
                pidStr = "(";
                for (int i = 0; i <  tmpList.size(); i++) {
                    //String id = escapeSpecialChars(tmpList.get(i));
                    String id = tmpList.get(i);
                    pidStr += "\"" + id + "\"";
                    if(i < tmpList.size() - 1)
                        pidStr += " OR ";
                }

                pidStr += ")";
                queryStr = "metadataId:" + pidStr;
                // TODO: make sure the wildcards will work for the desired formats
                if (formatFamilySearchTerm != null) {
                    queryStr += " AND metadataFormatId:" + formatFamilySearchTerm;
                }

                if (suiteId != null) {
                    queryStr += " AND suiteId:" + suiteId;
                }
                log.trace("query to quality Solr server: " + queryStr);
                // Send query to Quality Solr Server
                // Get all the pids in this pid string
                resultList = queryQualitySolr(queryStr, startPosInQuery, pidCntToRequest);
                // It's possible that none of the pids from the collection have quality scores
                // This should not happen but check just in case.
                if(resultList.size() > 0) {
                    // Add results from this pid range to the accumulator of all results.
                    allResults.addAll(resultList);
                }
                pidsLeft -= pidCntToRequest;
            } while (pidsLeft > 0);
        } else {
            log.info("Getting quality scores for suiteId: " + suiteId + ", datasource: " + nodeId + " formats: " + formatFamily);
            countRequested = 1000;
            formatFamilySearchTerm = null;
            queryStr = "metadataId:*";
            if(suiteId != null) {
                queryStr += " AND suiteId:" + "\"" + suiteId + "\"";
            }
            if(nodeId != null) {
                queryStr += " AND datasource:" + "\"" + nodeId + "\"";
            }
            if (formatFamilySearchTerm != null) {
                queryStr += " AND metadataFormatId:" + "\"" + formatFamilySearchTerm + "\"";
            }
            log.trace("query to quality Solr server: " + queryStr);
            do {
                resultList = queryQualitySolr(queryStr, startPosInQuery, countRequested);
                // If no more results, break
                if(resultList.size() == 0) break;
                // Add results from this pid range to the accumulator of all results.
                allResults.addAll(resultList);
                //startPosInQuery += resultList.size();
                startPosInQuery += countRequested;
            } while (resultList.size() > 0);
        }
        log.debug("Got " + allResults.size() + " scores from Quality Solr server");
        return allResults;
    }

    /**
      * Create a CSV (Comma Separated Values) file that contains metadata quality scores from
      * the Quality Solr Server. This file is in a format that is needed by the Quality Scorer
      *
      * @param allResults the quality scores returned from the MetaDIG Quality Solr Server
      * @return a File for the generated score file.
      */
    private File createScoreFile(List<QualityScore> allResults) throws Exception {

        // score file format
        // pid,formatId,dateUploaded,datasource,scoreOverall,scoreFindable,scoreAccessible,scoreInteroperable,scoreReusable,obsoletes,obsoletedBy,seriesId,version
        // i.e.
        // aekos.org.au/collection/nsw.gov.au/nsw_atlas/vis_flora_module/JTH_MC.20180628,eml://ecoinformatics.org/eml-2.1.1,2018-08-03T02:09:25.091Z,urn:node:TERN,0.59,0.73,1.0,0.0,0.4,aekos.org.au/collection/nsw.gov.au/nsw_atlas/vis_flora_module/JTH_MC.20170515,,199d5db7-0181-48ba-bec7-8933fb78c694,9
        File tmpfile = File.createTempFile("scorefile-", ".csv");
        log.debug("Creating score file: " + tmpfile);
        Boolean append = true;
        FileWriter fileWriter = new FileWriter(tmpfile, append);
        // TODO: Pass param or detect suite, so we know what 'scoreByType' fields to create header columns for
        CSVPrinter csvPrinter = new CSVPrinter(fileWriter, CSVFormat.RFC4180.withHeader(
                  "pid", "formatId", "dateUploaded", "datasource", "scoreOverall",
                  "scoreFindable", "scoreAccessible", "scoreInteroperable", "scoreReusable",
                  "obsoletes", "obsoletedBy", "sequenceId"));

        log.debug("# score results to write to CSV: " + allResults.size());
        QualityScore oneScore = (QualityScore) allResults.get(0);

        // Print out the date in ISO 8601 format
        DateTime dt = new DateTime();
        //DateTimeFormatter fmt = DateTimeFormat.forPattern("yyyy-MM-dd hh:mm:ss zzz");
        DateTimeFormatter fmt = ISODateTimeFormat.dateTime();

        try {
            for (QualityScore result : new ArrayList<QualityScore>(allResults)) {
                // TODO: Pass param or detect suite, so we know what 'scoreByType' fields to write out
                csvPrinter.printRecord(
                        result.metadataId,
                        result.metadataFormatId,
                        fmt.print(new DateTime(result.dateUploaded)),
                        result.datasource,
                        result.scoreOverall,
                        result.scores_by_type.get("scoreByType_Findable_f"),
                        result.scores_by_type.get("scoreByType_Accessible_f"),
                        result.scores_by_type.get("scoreByType_Interoperable_f"),
                        result.scores_by_type.get("scoreByType_Reusable_f"),
                        result.obsoletes,
                        result.obsoletedBy,
                        result.sequenceId);
            }
        } catch (Exception e) {
            log.debug("Error: " + e.getMessage());
            throw e;
        }

        csvPrinter.flush();
        fileWriter.close();
        return tmpfile;
    }

    /**
     * Send a message to the controller describing the status of the graphing job.
     *
     * @param metadataPid the metadata id associated with the graph request
     * @param suiteId the quality suite specified for the graph
     * @param qEntry the queue entry containing status and other information about the graph request
     * @throws IOException
     */
    private void returnGraphStatus(String metadataPid, String suiteId, ScorerQueueEntry qEntry) throws IOException {
        byte[] message = null;
        try {
            log.info("Elapsed time processing (seconds): "
                    + String.format("%d", elapsedTimeSecondsProcessing)
                    + " for metadataPid: " + metadataPid
                    + ", suiteId: " + suiteId
                    + "\n");

            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            ObjectOutput out = new ObjectOutputStream(bos);
            out.writeObject(qEntry);
            message = bos.toByteArray();

            log.info(" [x] Done");
            this.writeCompletedQueue(message);
            log.info(" [x] Sent completed report for project id: '" + qEntry.getProjectId() + "'");
        } catch (Exception e) {
            // If we couldn't prepare the message, then there is nothing left to do
            log.error(" Unable to return report to controller");
            e.printStackTrace();
            throw e;
        }
    }

    /**
     * Send a query to the DataONE Query Service , using the DataONE CN or MN API
     *
     * @param queryStr the query string to pass to the Solr server
     * @param serviceUrl the service URL for the DataONE CN or MN
     * @param startPos the start of the query result to return, if query pagination is being used
     * @param countRequested the number of results to return
     * @return an XML document containing the query result
     * @throws Exception
     */
    private Document queryD1Solr(String queryStr, String serviceUrl, int startPos, int countRequested, String subjectId, String authToken) throws Exception {

        MultipartRestClient mrc = null;
        // Polymorphism doesn't work with D1 node classes, so have to use the derived classes
        MultipartD1Node d1Node = null;

        Subject subject = new Subject();
        if(subjectId != null && !subjectId.isEmpty()) {
            subject.setValue(subjectId);
        }

        Session session = getSession(subjectId, authToken);

        // Add the start and count, if pagination is being used
        queryStr = queryStr + "&start=" + startPos + "&rows=" + countRequested;
        // Query the MN or CN Solr engine to get the query associated with this project that will return all project related pids.
        InputStream qis = null;
        MetadigException metadigException = null;

        try {
            d1Node = getMultipartD1Node(session, serviceUrl);
        } catch (Exception ex) {
            log.error("Unable to create MultipartD1Node for Solr query");
            metadigException = new MetadigProcessException("Unable to create multipart node client to query DataONE solr: " + ex.getMessage());
            metadigException.initCause(ex);
            throw metadigException;
        }

        // Send a query to a CN or MN
        try {
            qis = d1Node.query(session, "solr", queryStr);
        } catch (Exception e) {
            log.error("Error retrieving pids: " + e.getMessage());
            throw e;
        }

        Document xmldoc = null;
        DocumentBuilder builder = null;

        // If results were returned, create an XML document from them
        if (qis.available() == 1) {
            try {
                DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
                builder = factory.newDocumentBuilder();
                xmldoc = builder.parse(new InputSource(qis));
            } catch (Exception e) {
                log.error("Unable to create w3c Document from input stream", e);
                e.printStackTrace();
            } finally {
                qis.close();
            }
        } else {
            log.info("No results returned from D1 Solr query");
            qis.close();
        }

        return xmldoc;
    }

    /**
     * Send a query to the Quality Solr Server.
     * @param queryStr the query to send to Solr
     * @param startPos the starting position in query paginatio
     * @param countRequested the number of query results to return
     * @return a list of 'QualityScore' POJOs that contain the quality scores that were retrieved
     * @throws Exception
     */
    private List<QualityScore> queryQualitySolr(String queryStr, int startPos, int countRequested) throws SolrServerException, IOException {

        SolrClient solrClient = new HttpSolrClient.Builder(solrLocation).build();
        SolrQuery query = new SolrQuery(queryStr);
        query.setStart(startPos);
        query.setRows(countRequested);
        query.setParam("q.op", "AND");

        List<QualityScore> scores = null;
        QueryResponse response = null;

        try {
            response = solrClient.query("quality", query);
            scores = response.getBeans(QualityScore.class);
            solrClient.close();
        } catch (SolrServerException | IOException oe) {
            log.error("response status: " + response.getStatus());
            log.error("Error sending query to Solr: " + oe.getMessage());
            throw oe;
        } catch (BindingException be) {
            log.error("Error binding Solr result to class QualityScore:  " + be.getMessage());
            be.printStackTrace();
            throw be;
        } catch (Exception e) {
            log.error("Error querying solr: " + e.getMessage());
            log.error("query: " + query.toString());
            throw e;
        }

        return scores;
    }

    /**
     * Declare and connect to the RabbitMQ queues that are used to read and send requests from/to metadig-controller.
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

        try {
            inProcessConnection = factory.newConnection();
            inProcessChannel = inProcessConnection.createChannel();
            inProcessChannel.queueDeclare(SCORER_QUEUE_NAME, false, false, false, null);
            inProcessChannel.queueBind(SCORER_QUEUE_NAME, EXCHANGE_NAME, SCORER_ROUTING_KEY);
            // Channel will only send one request for each worker at a time.
            inProcessChannel.basicQos(1);
            log.info("Connected to RabbitMQ queue " + SCORER_QUEUE_NAME);
            log.info(" [*] Waiting for messages. To exit press CTRL+C");
        } catch (Exception e) {
            log.error("Error connecting to RabbitMQ queue " + SCORER_QUEUE_NAME);
            log.error(e.getMessage());
        }

        try {
            completedConnection = factory.newConnection();
            completedChannel = completedConnection.createChannel();
            completedChannel.exchangeDeclare(EXCHANGE_NAME, "direct", false);
            completedChannel.queueDeclare(COMPLETED_QUEUE_NAME, false, false, false, null);
            completedChannel.queueBind(COMPLETED_QUEUE_NAME, EXCHANGE_NAME, COMPLETED_ROUTING_KEY);
            log.info("Connected to RabbitMQ queue " + COMPLETED_QUEUE_NAME);
        } catch (Exception e) {
            log.error("Error connecting to RabbitMQ queue " + COMPLETED_QUEUE_NAME);
            log.error(e.getMessage());
        }
    }

    /**
     *
     * <p>
     * Write to the RabbitMQ 'completed' channel, which will be read by metadig-controller in the 'completed' queue, signalling that
     * this graph request has completed.
     * </p>
     *
     * @param message the message to send via the channel.
     * @throws IOException
     */
    public void writeCompletedQueue (byte[] message) throws IOException {
        // The completed queue doesn't use an exchange
        AMQP.BasicProperties basicProperties = new AMQP.BasicProperties.Builder()
                .contentType("text/plain")
                .type(MESSAGE_TYPE_SCORER)
                .build();
        completedChannel.basicPublish(EXCHANGE_NAME, COMPLETED_ROUTING_KEY, basicProperties, message);
    }

    /**
     * Get a DataONE system metadata object
     * @param pid the pid to get the system metadata for
     * @param serviceUrl the service URL of the DataONE node to request the sysmeta
     * @param authToken the authorization token to use for the request
     * @return a DataONE system metadata object
     * @throws MetadigProcessException
     */
    private SystemMetadata getSystemMetadata(String pid, String serviceUrl, String subjectId, String authToken) throws MetadigProcessException {

        SystemMetadata sysmeta = null;
        MultipartRestClient mrc = null;
        MultipartD1Node d1Node = null;
        MetadigProcessException metadigException = null;

        Subject subject = new Subject();
        if(subjectId != null && ! subjectId.isEmpty()) {
            subject.setValue(subjectId);
        }

        Session session = getSession(subjectId, authToken);
        Identifier identifier = new Identifier();
        identifier.setValue(pid);

        try {
            d1Node = getMultipartD1Node(session, serviceUrl);
        } catch (Exception ex) {
            metadigException = new MetadigProcessException("Unable to get subject info for subject: " + subject.getValue());
            metadigException.initCause(ex);
            throw metadigException;
        }

        try {
            sysmeta = d1Node.getSystemMetadata(session, identifier);
            log.debug("retrieved sysmeta for pid: " + sysmeta.getIdentifier().getValue());
        } catch (Exception ex) {
            log.error("Unable to retrieve sysmte for pid: " + pid);
            metadigException = new MetadigProcessException("Unable to get collection pids");
            metadigException.initCause(ex);
            throw metadigException;
        }

        return sysmeta;
    }

    /**
     * Get a DataONE subject information object
     * @param subject the DataONE idenotity to get info for
     * @param serviceUrl the service URL of the DataONE node to request the subject info from
     * @param authToken the authorization token to use for the request
     * @return a DataONE subject information object
     * @throws MetadigProcessException
     */
    private SubjectInfo getSubjectInfo(Subject rightsHolder, String serviceUrl, String subjectId, String authToken) throws MetadigProcessException {

        log.debug("Getting subject info for: " + rightsHolder);
        MultipartCNode cnNode = null;
        MetadigProcessException metadigException = null;

        SubjectInfo subjectInfo = null;
        Subject requestingSubject = new Subject();
        if(subjectId != null && ! subjectId.isEmpty()) {
            requestingSubject.setValue(subjectId);
        }

        Session session = getSession(subjectId, authToken);

        // Identity node as either a CN or MN based on the serviceUrl
        String pattern = "https*://cn.*?\\.dataone\\.org|https*://cn.*?\\.test\\.dataone\\.org";
        Pattern r = Pattern.compile(pattern);
        Matcher m = r.matcher(serviceUrl);
        if (!m.find()) {
            log.error("Must call a CN to get subject information");
            metadigException = new MetadigProcessException("Must call a CN to get subject information.");
            throw metadigException;
        }

        // Only CNs can call the 'subjectInfo' service (aka accounts), so we have to use
        // a MultipartCNode instance here.
        try {
            cnNode = (MultipartCNode) getMultipartD1Node(session, serviceUrl);
        } catch (Exception ex) {
            metadigException = new MetadigProcessException("Unable to create multipart D1 node: " + requestingSubject.getValue() + ": " + ex.getMessage());
            metadigException.initCause(ex);
            throw metadigException;
        }

        try {
            subjectInfo = cnNode.getSubjectInfo(session, rightsHolder);
        } catch (Exception ex) {
            metadigException = new MetadigProcessException("Unable to get subject information." + ex.getMessage());
            metadigException.initCause(ex);
            throw metadigException;
        }

        return subjectInfo;
    }

    /**
     * Get a DataONE authenticated session
     * <p>
     *     If no subject or authentication token are provided, a public session is returned
     * </p>
     * @param authToken the authentication token
     * @return the DataONE session
     */
    Session getSession(String subjectId, String authToken) {

        Session session;

        // query Solr - either the member node or cn, for the project 'solrquery' field
        if (authToken == null || authToken.isEmpty()) {
            log.debug("Creating public session");
            session = new Session();
        } else {
            log.debug("Creating authentication session");
            session = new AuthTokenSession(authToken);
        }

        if (subjectId != null && !subjectId.isEmpty()) {
            Subject subject = new Subject();
            subject.setValue(subjectId);
            session.setSubject(subject);
        }

        return session;
    }

    /**
     * Get a DataONE MultipartCNode object, which will be used to communication with a CN
     *
     * @param session a DataONE authentication session
     * @param serviceUrl the service URL for the node we are connecting to
     * @return a DataONE MultipartCNode object
     * @throws MetadigException
     */
    MultipartD1Node getMultipartD1Node(Session session, String serviceUrl) throws MetadigException {

        MultipartRestClient mrc = null;
        MultipartD1Node d1Node = null;
        MetadigProcessException metadigException = null;

        // First create an HTTP client
        try {
            mrc = new DefaultHttpMultipartRestClient();
        } catch (Exception ex) {
            log.error("Error creating rest client: " + ex.getMessage());
            metadigException = new MetadigProcessException("Unable to get collection pids");
            metadigException.initCause(ex);
            throw metadigException;
        }

        Boolean isCN = isCN(serviceUrl);

        // Now create a DataONE object that uses the rest client
        if (isCN) {
            log.debug("creating cn MultipartMNode" + ", subjectId: " + session.getSubject().getValue());
            d1Node = new MultipartCNode(mrc, serviceUrl, session);
        } else {
            log.debug("creating mn MultipartMNode" + " , subjectId: " + session.getSubject().getValue());
            d1Node = new MultipartMNode(mrc, serviceUrl, session);
        }
        return d1Node;
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
        log.info(file.getPath());

        InputStream is = classLoader.getResourceAsStream(fileName);

        return is;
    }

    /**
     * URL encode a limited set of values in an input string.
     * @param value The string to modify
     * @param target The string that will be modified
     * @return
     */
    private String URLencodeChars(String value, String target) {

        log.debug("target chars: " + target + ", length: " + target.length());
        for (int i=0; i < target.length(  ); i++) {
            String s = Character.toString(target.charAt(i));
            try {
                String encodedChar = URLEncoder.encode(s, "UTF-8");
                log.debug("replacement string: " + encodedChar);
                value = value.replace(s, encodedChar);
            } catch (java.io.UnsupportedEncodingException e) {
                log.error("Unable to URLencode string" + target + " into string " + value);
            }
        }
        return value;
    }

    /**
     * Escape characters that have a reserved meaning in Solr.
     * @param value the value to add escape characters to
     * @return the escaped value
     */
    private String escapeSpecialChars(String value) {
        // {
        value = value.replace("%7B", "\\%7B");
        // }
        value = value.replace("%7D", "\\%7D");
        // :
        //value = value.replace("%3A", "\\%3A");
        value = value.replace(":", "%5C:");

        //value = value.replace("(", "\\(");
        //value = value.replace(")", "\\)");
        //value = value.replace("?", "\\?");
        //value = value.replace("%3F", "\\%3F");
        //value = value.replace("\"", "\\\"");
        //value = value.replace("'", "\\'");

        return value;
    }

    private static String encodeValue(String value) {
        try {
            return URLEncoder.encode(value, StandardCharsets.UTF_8.toString());
        } catch (UnsupportedEncodingException ex) {
            throw new RuntimeException(ex.getCause());
        }
    }

    private Boolean isCN(String serviceUrl) {

        Boolean isCN = false;
        // Identity node as either a CN or MN based on the serviceUrl
        String pattern = "https*://cn.*?\\.dataone\\.org|https*://cn.*?\\.test\\.dataone\\.org";
        Pattern r = Pattern.compile(pattern);
        Matcher m = r.matcher(serviceUrl);
        if (m.find()) {
            isCN = true;
            log.debug("service URL is for a CN: " + serviceUrl);
        } else {
            log.debug("service URL is not for a CN: " + serviceUrl);
            isCN = false;
        }
        return isCN;
    }
}
