package edu.ucsb.nceas.mdqengine.filestore;

import edu.ucsb.nceas.mdqengine.exception.MetadigStoreException;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.joda.time.DateTime;

import java.io.IOException;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;


/**
 * This is a data transfer object that contains information about a file entry in
 * the MetaDIG file store.
 */
public class MetadigFile {

    public static Log log = LogFactory.getLog(MetadigFile.class);

    private String fileId = null;
    private String collectionId = "";
    private String metadataId = "";
    private String suiteId = "";
    private String nodeId = "";
    private String metadataFormatFilter = "";
    private String storageType = null;
    private DateTime creationDatetime;
    private String mediaType = "";
    private String altFilename = ""; // Use this name for the file instead of a uuid
    private String path; // the complete path to the file
    private MediaTypes mediaTypes = null;

    public MetadigFile() throws MetadigStoreException {
        this.fileId = UUID.randomUUID().toString();
        this.creationDatetime = DateTime.now();
        this.init();
    };

    public MetadigFile (String collectionId, String metadataId, String suiteId, String nodeId, String metadataFormatFilter, String storageType,
                        String relativePath, DateTime createtionDate, String mediaType) throws MetadigStoreException {

        this.fileId = UUID.randomUUID().toString();
        this.collectionId = collectionId;
        this.metadataId = metadataId;
        this.suiteId = suiteId;
        this.nodeId = nodeId;
        this.metadataFormatFilter = metadataFormatFilter;
        this.storageType = storageType;
        this.creationDatetime = creationDatetime;
        this.mediaType = mediaType;
        this.init();
    }

    private void init() throws MetadigStoreException {
        try {
            mediaTypes = new MediaTypes();
        } catch (IOException ioe) {
            log.error("Unable to intialize Metadig fileStore: " + ioe.getMessage());
            MetadigStoreException mse = new MetadigStoreException("Unable to intialize Metadig filestore");
            mse.initCause(ioe.getCause());
            throw mse;
        }
    }

    public String getFileId() {
        return fileId;
    }

    public void setFileId(String id) {
        this.fileId = id;
    }

    public String getCollectionId() {
        return collectionId;
    }

    public void setCollectionId(String id) {
        this.collectionId = id;
    }

    public String getMetadataId() {
        return metadataId;
    }

    public void setMetadataId(String id) {
        this.metadataId = id;
    }

    public String getSuiteId() {
        return suiteId;
    }

    public void setSuiteId(String id) {
        this.suiteId = id;
    }

    public String getNodeId() {
        return nodeId;
    }

    public void setNodeId(String id) {
        this.nodeId = id;
    }

    public String getMetadataFormatFilter() {
        return metadataFormatFilter;
    }

    public void setMetadataFormatFilter(String filter) {
        this.metadataFormatFilter = filter;
    }

    public String getStorageType() {
        return storageType;
    }

    public void setStorageType(String type) {
        this.storageType = type.toLowerCase();
    }

    public DateTime getCreationDateTime() {
        return creationDatetime;
    }

    public void setCreationDatetime(DateTime time) {
        this.creationDatetime = time;
    }

    public String getMediaType() { return mediaType; }

    public void setMediaType(String type) { this.mediaType = type; }

    public String getAltFilename() {
        return altFilename;
    }

    public void setAltFilename(String filename) {
        this.altFilename = filename;
    }

    public String getRelativePath() {

        String pattern = "\\.";
        String filePath = null;
        String filename = null;
        String fileExt = null;

        if(altFilename != null && ! altFilename.isEmpty()) {
            filename = altFilename;
        } else {
            filename = fileId;
        }

        String newFileExt = null;
        fileExt = mediaTypes.getFileExtension(this.mediaType);
        // Don't include a '.' if it is already in the fileExt.
        Pattern r = Pattern.compile(pattern);
        Matcher m = r.matcher(fileExt);
        if (m.find()) {
            newFileExt = fileExt;
        } else {
            newFileExt = "." + fileExt;
        }

        // Don't include fileExt if already in file
        r = Pattern.compile(newFileExt);
        m = r.matcher(filename);
        if (m.find()) {
            filePath = storageType + "/" + filename;
        } else {
            if(fileExt != null) {
                filePath = storageType + "/" + filename + "." + fileExt;
            } else {
                filePath = storageType + "/" + filename;
            }
        }

        return filePath;
    }
}

