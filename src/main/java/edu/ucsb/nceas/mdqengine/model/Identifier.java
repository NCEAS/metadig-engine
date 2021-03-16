package edu.ucsb.nceas.mdqengine.model;

import edu.ucsb.nceas.mdqengine.exception.MetadigException;
import edu.ucsb.nceas.mdqengine.store.MDQStore;
import edu.ucsb.nceas.mdqengine.store.StoreFactory;
import org.apache.commons.configuration2.ex.ConfigurationException;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.joda.time.DateTime;
import org.joda.time.format.DateTimeFormat;
import org.joda.time.format.DateTimeFormatter;

import java.io.IOException;
import java.sql.SQLException;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.TimeZone;

public class Identifier {

    public static Log log = LogFactory.getLog(Identifier.class);

    private String metadataId;
    private String dataSource;
    private String obsoletes;
    private String obsoletedBy;
    private String sequenceId;
    private String dateUploaded;
    private String dateSysMetaModified;
    private String formatId;
    private String rightsHolder;
    private List<String> groups;

    private Boolean modified = false;

    public void setMetadataId(String metadataId) {
        this.metadataId = metadataId;
    }
    public String getMetadataId() {
        return this.metadataId;
    };

    public void setDataSource(String dataSource) {
        this.dataSource = dataSource;
    }
    public String getDataSource() {
        return this.dataSource;
    };

    public void setObsoletes(String obsoletes) {
        this.obsoletes = obsoletes;
    }
    public String getObsoletes() {
        return this.obsoletes;
    };

    public void setObsoletedBy(String obsoletedBy) {
        this.obsoletedBy = obsoletedBy;
    }
    public String getObsoletedBy() {
        return this.obsoletedBy;
    };

    public void setSequenceId(String sequenceId) {
        this.sequenceId = sequenceId;
    }
    public String getSequenceId() {
        return this.sequenceId;
    };

    /**
        Set the system metadata 'dateUploaded' for this identifier.
        <p>
          Format the date according to the allowed format for Solr,
          which only accepts UTC (GMT) values
          (https://lucene.apache.org/solr/guide/6_6/working-with-dates.html).
        </p>
     */
    public void setDateUploaded(Date dateUploaded) {
        // Example DataONE 'dateUploaded': 2017-04-13T20:09:08.131+00:00
        DateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'");
        dateFormat.setTimeZone(TimeZone.getTimeZone("GMT"));
        this.dateUploaded = dateFormat.format(dateUploaded);
    }

    public void setDateUploaded(String dateUploaded) { this.dateUploaded = dateUploaded; }
    public String getDateUploaded() { return this.dateUploaded; }
    public DateTime getDateUploadedAsDateTime() {
        DateTimeFormatter formatter = DateTimeFormat.forPattern("yyyy-MM-dd'T'HH:mm:ss'Z'");
        return(formatter.parseDateTime(this.dateUploaded));
    }

    public void setDateSysMetaModified(Date dateUploaded) {
        // Example DataONE 'dateSysMetadataModified': 2017-04-13T20:09:08.131+00:00
        DateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'");
        dateFormat.setTimeZone(TimeZone.getTimeZone("GMT"));
        this.dateSysMetaModified = dateFormat.format(dateUploaded);
    }

    public void setDateSysMetaModified(String dateSysMetaModified) { this.dateSysMetaModified = dateSysMetaModified; }
    public String getDateSysMetaModified() { return this.dateSysMetaModified; }
    public DateTime getDateSysMetaModifiedAsDateTime() {
        DateTimeFormatter formatter = DateTimeFormat.forPattern("yyyy-MM-dd'T'HH:mm:ss'Z'");
        return(formatter.parseDateTime(this.dateSysMetaModified));
    }

    public void setModified(Boolean modified) { this.modified = modified; }
    public Boolean getModified() { return this.modified; }

    public void setFormatId(String formatId) { this.formatId = formatId; }
    public String getFormatId() { return this.formatId; };

    public void setRightsHolder(String rightsHolder) { this.rightsHolder = rightsHolder; }
    public String getRightsHolder() { return this.rightsHolder; };

    public List<String> getGroups() {
        return groups;
    }
    public void setGroups(List<String> groups) { this.groups = groups; }

        /**
         * Save an identifier to the DatabaseStore.
         *
         * @throws Exception
         */
    public Integer save() throws MetadigException {

        boolean persist = true;
        MDQStore store = StoreFactory.getStore(persist);

        Integer updateCount = 0;
        log.debug("Saving to persistent storage: metadata PID: " + this.getMetadataId() + ", sequenceId: " + this.getSequenceId());

        try {
            updateCount = store.saveIdentifier(this);
        } catch (MetadigException me) {
            log.error("Error saving identifier: " + me.getCause());
            if(me.getCause() instanceof SQLException) {
                log.debug("Retrying identifier.save() due to error");
                store.renew();
                updateCount = store.saveIdentifier(this);
            } else {
                throw(me);
            }
        }

        // Note that when the connection pooler 'pgbouncer' is used, closing the connection actually just returns
        // the connection to the pool that pgbouncer maintains.
        log.debug("Shutting down store");
        store.shutdown();
        log.debug("Done saving identifier to persistent storage: " + this.getMetadataId());
        return updateCount;
    }
    /**
     * Get a quality report from the the DatabaseStore.
     * <p>
     * The quality report is saved to a database instance.
     * </p>
     *
     * @param metadataId The DataONE identifier of the run to fetch
     * @param suiteId The metadig-engine suite id of the suite to match
     * @throws Exception
     */
    public static Identifier getIdentifier(String metadataId, String suiteId) throws MetadigException, IOException, ConfigurationException {
        boolean persist = true;
        MDQStore store = StoreFactory.getStore(persist);

        log.debug("Getting run for suiteId: " + suiteId + ", metadataId: " + metadataId);

        Identifier meIdentifier = null;

        try {
            meIdentifier = Identifier.getIdentifier(metadataId, suiteId);
        } catch (MetadigException me) {
            log.debug("Error getting run: " + me.getCause());
            if(me.getCause() instanceof SQLException) {
                log.debug("Retrying getRun() due to error");
                store.renew();
                Identifier.getIdentifier(metadataId, suiteId);
            } else {
                throw(me);
            }
        }
        log.debug("Shutting down store");
        store.shutdown();
        log.debug("Done getting from persistent storage: metadata PID: " + metadataId  + ", suite id: " + suiteId);
        return meIdentifier ;
    }
}
