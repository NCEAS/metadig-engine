package edu.ucsb.nceas.mdqengine.scorer;

import org.joda.time.DateTime;

import java.io.Serializable;

/**
 * The ScorerQueueEntry class holds information that is passed between metadig-controller and metadig-scorer
 * via RabbitMQ.
 */
public class ScorerQueueEntry implements Serializable {

    private static final long serialVersionUID = -2643076659001464940L;
    private String nodeId;              // the DataONE node identifier of the node that is the datasource
    private String qualitySuiteId;      // the MetaDIG quality suite to graph
    private String collectionId;        // the DataONE collection (portal) to graph data for
    private String formatFamily;        // the metadata dialect to include in the graph
    private DateTime requestDataTime;   // when the original graph request was made
    private long processingElapsedTimeSeconds;  // how long the graph request took to fulfill
    private Exception exception;

    // Hostsname of the machine that is running metadig engine
    private String hostname;

    public String getNodeId() {
        return nodeId;
    }

    public void setNodeId(String nodeId) {
        this.nodeId = nodeId;
    }

    public String getCollectionId() {
        return this.collectionId;
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

    public ScorerQueueEntry(String collectionId, String qualitySuiteId, String nodeId, String formatFamily, DateTime requestDataTime) {
        this.collectionId = collectionId;
        this.qualitySuiteId = qualitySuiteId;
        this.nodeId = nodeId;
        this.formatFamily = formatFamily;
        this.requestDataTime = requestDataTime;
    }
}

