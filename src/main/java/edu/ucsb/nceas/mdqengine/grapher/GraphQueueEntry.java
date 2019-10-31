package edu.ucsb.nceas.mdqengine.grapher;

import org.joda.time.DateTime;

import java.io.Serializable;

/**
 * The GraphQueueEntry class holds information that is passed between metadig-controller and metadig-grapher
 * via RabbitMQ.
 */
public class GraphQueueEntry implements Serializable {

    private static final long serialVersionUID = -2643076659001464940L;
    private String nodeId;              // the DataONE node identifier of the node that is the datasource
    private String serviceUrl;          // the DataONE CN or MN serviceUrl
    private String qualitySuiteId;      // the MetaDIG quality suite to graph
    private String collectionid;        // the DataONE collection (portal) to graph data for
    private String projectName;         // the name of the portal or project
    private String formatFamily;        // the metadata dialect to include in the graph
    private DateTime requestDataTime;   // when the original graph request was made
    private long processingElapsedTimeSeconds;  // how long the graph request took to fulfill
    private Exception exception;

    // Hostsname of the machine that is running metadig engine
    private String hostname;

    public String getServiceUrl() {
        return serviceUrl;
    }

    public void setServiceUrl(String serviceUrl) {
        this.serviceUrl = serviceUrl;
    }

    public String getNodeId() {
        return nodeId;
    }

    public void setNodeId(String nodeId) {
        this.nodeId = nodeId;
    }

    public String getProjectId() {
        return this.collectionid;
    }

    public void setProjectId(String collectionid) {
        this.collectionid = collectionid;
    }

    public String getProjectName() {
        return this.projectName;
    }

    public void setProjectName(String projectName) {
        this.projectName = projectName;
    }

    public String getFormatFamily() {
        return this.formatFamily;
    }

    public void setFormatFamily(String formatFamily) {
        this.formatFamily = formatFamily;
    }

    public String getQualitySuiteId() {
        return qualitySuiteId;
    }

    public void setQualitySuiteId(String id) {
        this.qualitySuiteId = id;
    }

    public DateTime getRequestDataTime() {
        return requestDataTime;
    }

    public void setRequestDataTime(DateTime dataTime) {
        this.requestDataTime = dataTime;
    }

    public void setProcessingElapsedTimeSeconds (long seconds) {
        this.processingElapsedTimeSeconds = seconds;
    };

    public long getProcessingElapsedTimeSeconds() {
        return processingElapsedTimeSeconds;
    }

    public void setException(Exception exception) { this.exception = exception; };

    public Exception getException() { return exception; }

    public void setHostname(String hostname) { this.hostname = hostname; };

    public String getHostname() { return hostname; }

    public GraphQueueEntry (String collectionid, String projectName, String qualitySuiteId,
                            String nodeId, String formatFamily, DateTime requestDataTime) {
        this.collectionid = collectionid;
        this.projectName = projectName;
        this.qualitySuiteId = qualitySuiteId;
        this.nodeId = nodeId;
        this.formatFamily = formatFamily;
        this.requestDataTime = requestDataTime;
    }
}

