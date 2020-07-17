package edu.ucsb.nceas.mdqengine.filestore;

import edu.ucsb.nceas.mdqengine.MDQconfig;
import edu.ucsb.nceas.mdqengine.exception.MetadigEntryNotFound;
import edu.ucsb.nceas.mdqengine.exception.MetadigFilestoreException;
import org.apache.commons.configuration2.ex.ConfigurationException;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import edu.ucsb.nceas.mdqengine.model.*;
import org.joda.time.DateTime;
import sun.tools.tree.NewArrayExpression;

import java.io.IOException;
import java.sql.*;
import java.util.Properties;

import java.sql.Connection;
import java.sql.DriverManager;

public class FilestoreDB {

    private static String dbUrl = null;
    private static String dbUser = null;
    private static String dbPasswd = null;

    private Connection conn = null;

    protected Log log = LogFactory.getLog(this.getClass());

    /*
     * Get a connection to the database that contains the quality reports.
     */
    private void init() throws MetadigFilestoreException {

        log.debug("initializing connection");
        try {
            MDQconfig cfg = new MDQconfig();
            dbUrl = cfg.getString("jdbc.url");
            dbUser = cfg.getString("postgres.user");
            dbPasswd = cfg.getString("postgres.passwd");

        } catch (ConfigurationException | IOException ex) {
            log.error(ex.getMessage());
            MetadigFilestoreException mse = new MetadigFilestoreException("Unable to create new Store");
            mse.initCause(ex.getCause());
            throw mse;
        }

        try {
            Properties props = new Properties();
            props.setProperty("user", dbUser);
            props.setProperty("password", dbPasswd);
            props.setProperty("prepareThreshold", "0");
            //props.setProperty("extra_float_digits", "2");
            Class.forName("org.postgresql.Driver");
            conn = DriverManager.getConnection(dbUrl, props);
            conn.setAutoCommit(false);
        } catch (Exception e) {
            e.printStackTrace();
            log.error(e.getClass().getName() + ": " + e.getMessage());
            MetadigFilestoreException mse = new MetadigFilestoreException("Unable to create the database store.");
            mse.initCause(e);
            throw (mse);
        }
    }

    public FilestoreDB () throws MetadigFilestoreException {
        try {
            this.init();
        } catch (MetadigFilestoreException mse) {
            log.error("Error initializing filestore database: " + mse.getMessage());
            throw(mse);
        }
    }

    public MetadigFile getFileEntry(MetadigFile mdFile) throws MetadigFilestoreException {

        Result result = new Result();
        PreparedStatement stmt = null;

        String pid = mdFile.getPid();
        String suiteId = mdFile.getSuiteId();
        String nodeId = mdFile.getNodeId();
        String mdFormatFilter = mdFile.getMetadataFormatFilter();
        String storageType = mdFile.getStorageType();
        if(storageType != null)
            storageType = storageType.toString();
        else
            storageType = "";
        String mediaType = mdFile.getMediaType();
        String altFilename = mdFile.getAltFilename();

        // Hope for the best, prepare for the worst!
        MetadigFilestoreException me = new MetadigFilestoreException("Unable get file from filestore.");
        // Select records from the 'filestore' table

        MetadigFile resultMdFile = new MetadigFile();
        String sql = null;
        try {
            // If the alternate filename is specified, then the query will need to just include the
            // storageType and the alternate filename, as this combination should be unique.
            if(altFilename != null && ! altFilename.isEmpty()) {
                sql = "select * from filestore where storage_type = ? and alt_filename= ?";
                stmt = conn.prepareStatement(sql);
                stmt.setString(1, storageType);
                stmt.setString(2, altFilename);
            } else {
                sql = "select * from filestore where pid = ? and storage_type = ? and media_type = ?";
                stmt = conn.prepareStatement(sql);
                stmt.setString(1, pid);
                stmt.setString(2, suiteId);
                stmt.setString(3, nodeId);
                stmt.setString(4, mdFormatFilter);
                stmt.setString(5, storageType);
                stmt.setString(6, mediaType);
            }

            log.debug("issuing query: " + sql);
            ResultSet rs = stmt.executeQuery();
            // TODO: The resultset should contain only one row
            if(rs.next()) {
                resultMdFile.setFileId(rs.getString("file_id"));
                resultMdFile.setPid(rs.getString("pid"));
                resultMdFile.setSuiteId(rs.getString("suite_id"));
                resultMdFile.setNodeId(rs.getString("node_id"));
                resultMdFile.setMetadataFormatFilter(rs.getString("format_filter"));
                // Convert the sql datetime to Java timestamp to Joda DateTime!
                Timestamp dateTime = rs.getTimestamp("creation_datetime");
                DateTime ct = new DateTime(dateTime);
                resultMdFile.setCreationDatetime(ct);
                resultMdFile.setStorageType(rs.getString("storage_type"));
                resultMdFile.setMediaType(rs.getString("media_type"));
                resultMdFile.setAltFilename(rs.getString("alt_filename"));

                rs.close();
                stmt.close();
                log.debug("Retrieved filestore successfully for file id: " + resultMdFile.getFileId());
            } else {
                log.debug("Filestore entry not found for pid: " + pid + ", suiteId: " + suiteId +
                        ", nodeId" + nodeId + ", formatFilter: " + mdFormatFilter + ", storageType: " + storageType + ", mediaType: "  + mediaType +
                        ", alt filename: " + altFilename);
                me.initCause(new MetadigEntryNotFound("Filestore db entry not found"));
                throw me;
            }
        } catch ( MetadigFilestoreException e ) {
            // Catch the exception we just threw.
            throw me;
        } catch ( Exception e ) {
            // Catch some other exception
            log.error( e.getClass().getName()+": "+ e.getMessage());
            me.initCause(e);
            throw(me);
        }

        return resultMdFile;
    }

    public void saveFileEntry(MetadigFile mdFile) throws MetadigFilestoreException {

        PreparedStatement stmt = null;

        String fileId = mdFile.getFileId();
        String pid = mdFile.getPid();
        String suiteId = mdFile.getSuiteId();
        String nodeId = mdFile.getNodeId();
        String metadataFormatFilter = mdFile.getMetadataFormatFilter();
        String storageType = mdFile.getStorageType();
        String altFilename = mdFile.getAltFilename();
        DateTime creationDatetime = mdFile.getCreationDateTime();
        Timestamp timestamp = new Timestamp(creationDatetime.getMillis());
        String mediaType = mdFile.getMediaType();

        MetadigFilestoreException me = new MetadigFilestoreException("Unable save metadig file info to the datdabase.");

        // Attempt to insert a new record. If the unique constraint is violated (updating an existing record), perform an 'upsert', replacing
        // the original record.
        try {
            String sql = "INSERT INTO filestore (file_id, pid, suite_id, node_id, format_filter, storage_type," +
                    " creation_datetime, media_type, alt_filename) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)"
                    + " ON CONFLICT ON CONSTRAINT all_properties_fk"
                    + " DO UPDATE SET (file_id, pid, suite_id, node_id, format_filter, storage_type, " +
                    "creation_datetime, media_type, alt_filename) = (?, ?, ?, ?, ?, ?, ?, ?, ?)";

            stmt = conn.prepareStatement(sql);
            stmt.setString(1, fileId);
            stmt.setString(2, pid);
            stmt.setString(3, suiteId);
            stmt.setString(4, nodeId);
            stmt.setString(5, metadataFormatFilter);
            stmt.setString(6, storageType);
            stmt.setTimestamp(7, timestamp);
            stmt.setString(8, mediaType);
            stmt.setString(9, altFilename);
            stmt.setString(10, fileId);
            stmt.setString(11, pid);
            stmt.setString(12, suiteId);
            stmt.setString(13, nodeId);
            stmt.setString(14, metadataFormatFilter);
            stmt.setString(15, storageType);
            stmt.setTimestamp(16, timestamp);
            stmt.setString(17, mediaType);
            stmt.setString(18, altFilename);

            stmt.executeUpdate();
            stmt.close();
            conn.commit();
            //conn.close();
        } catch (SQLException e) {
            log.error( e.getClass().getName()+": "+ e.getMessage());
            me.initCause(e);
            throw(me);
        }

        log.debug("Filestore record created successfully");
    }

    public void deleteFileEntry(MetadigFile mdFile) throws MetadigFilestoreException {

        PreparedStatement stmt = null;
        String fileId = mdFile.getFileId();
        MetadigFilestoreException me = new MetadigFilestoreException("Unable to remove entry for fileId: " + fileId + " from the datdabase.");

        try {
            String sql = "DELETE from filestore where file_id=?";

            stmt = conn.prepareStatement(sql);
            stmt.setString(1, fileId);
            stmt.executeUpdate();
            stmt.close();
            conn.commit();
        } catch (SQLException e) {
            log.error( e.getClass().getName()+": "+ e.getMessage());
            me.initCause(e);
            throw(me);
        }

        log.debug("Filestore record removed successfully");
    }

    /*
     * Check if the connection to the database server is usable.
     */
    public boolean isAvailable() {
        boolean reachable = false;
        log.debug("Checking if store (i.e. sql connection) is available.");
        try {
            reachable = conn.isValid(10);
        } catch (Exception e ) {
            log.error("Error checking database connection: " + e.getMessage());
        }
        return reachable;
    }

    /*
     * Reset the store, i.e. renew the database connection.
     */
    public void renew() throws MetadigFilestoreException {
        if(!this.isAvailable()) {
            log.debug("Renewing connection to database");
            this.init();
        }
    }

    public void shutdown() {

        try {
            conn.close();
            log.debug("Successfully closed database");
        } catch ( java.sql.SQLException e) {
            log.error("Error closing database: " + e.getMessage());
        }
    }
}
