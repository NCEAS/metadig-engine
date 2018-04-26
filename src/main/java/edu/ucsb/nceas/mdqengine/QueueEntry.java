package edu.ucsb.nceas.mdqengine;

import org.dataone.service.types.v2.SystemMetadata;
import org.joda.time.DateTime;

import java.io.Serializable;

public class QueueEntry implements Serializable {

    private static final long serialVersionUID = 6229001259619429676L;

    private String memberNode;

    private String metadataPid;

    private String metadataDoc;

    private String qualitySuiteId;

    private String localFilePath;

    private DateTime requestDataTime;

    private SystemMetadata systemMetadata;

    private String runXML;

    private long elapsedTimeSeconds;

    private Exception exception;

    // Hostsname of the machine that is running metadig engine
    private String hostname;

    public String getMemberNode() {
        return memberNode;
    }

    public void setMemberNode(String memberNode) {
        this.memberNode = memberNode;
    }

    public String getMetadataPid() {
        return metadataPid;
    }

    public void setMetadataPid(String pid) {
        this.metadataPid = pid;
    }

    public String getMetadataDoc() {
        return metadataDoc;
    }

    public void setMetadataDoc(String metadataDoc) {
        this.metadataDoc = metadataDoc;
    }

    public String getLocalFilePath() {
        return localFilePath;
    }

    public String getQualitySuiteId() {
        return qualitySuiteId;
    }

    public void setQualitySuiteId(String id) {
        this.qualitySuiteId = id;
    }

    public SystemMetadata getSystemMetadata() {
        return systemMetadata;
    }

    public void setSystemMetadata(SystemMetadata systemMetadata) {
        this.systemMetadata = systemMetadata;
    }

    public void setLocalFilePath(String filePath) {
        this.localFilePath = filePath;
    }

    public DateTime getRequestDataTime() {
        return requestDataTime;
    }

    public void setRequestDataTime(DateTime dataTime) {
        this.requestDataTime = dataTime;
    }

    public String getRunXML() {
        return runXML;
    }

    public void setRunXML(String runXML) {
        this.runXML = runXML;
    }

    public void setElapsedTimeSeconds (long seconds) {
        this.elapsedTimeSeconds = seconds;
    };

    public long getElapsedTimeSeconds() {
        return elapsedTimeSeconds;
    }

    public void setException(Exception exception) { this.exception = exception; };

    public Exception getException() { return exception; }

    public void setHostname(String hostname) { this.hostname = hostname; };

    public String getHostname() { return hostname; }

    public QueueEntry (String memberNode, String metadataPid, String metadataDoc, String qualitySuiteId, String localFilePath,
                       DateTime requestDataTime, SystemMetadata systemMetadata, String runXML, String hostname) {
        this.memberNode = memberNode;
        this.metadataPid = metadataPid;
        this.metadataDoc = metadataDoc;
        this.qualitySuiteId = qualitySuiteId;
        this.systemMetadata = systemMetadata;
        this.localFilePath = localFilePath;
        this.requestDataTime = requestDataTime;
        this.runXML = runXML;
        this.hostname = hostname;
    }
}

