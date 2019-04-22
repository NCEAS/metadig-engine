package edu.ucsb.nceas.mdqengine.model;

import edu.ucsb.nceas.mdqengine.exception.MetadigException;
import edu.ucsb.nceas.mdqengine.store.MDQStore;
import edu.ucsb.nceas.mdqengine.store.StoreFactory;
import org.apache.commons.configuration2.ex.ConfigurationException;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import javax.xml.bind.annotation.*;
import java.io.IOException;
import java.sql.SQLException;
import java.util.Date;
import java.util.List;

@XmlRootElement
@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(propOrder = {"id", "timestamp", "objectIdentifier", "suiteId", "status", "runStatus", "errorDescription", "sysmeta", "result"})
public class Run {

	public static final String SUCCESS = "success";
	public static final String FAILURE = "failure";
	public static final String QUEUED = "queued";
	public static final String PROCESSING = "processing";
	public static Log log = LogFactory.getLog(Run.class);

	/**
	 * The unique identifier for the QC run. This will likely be long and opaque
	 * (like a UUID) considering the quantity of runs that will likely be performed 
	 * over the content in a large repository.
	 */
	@XmlElement(required = true)
	private String id;

	/**
	 * The timestamp of the QC run
	 */
	@XmlElement(required = true)
	private Date timestamp;
	
	/**
	 * The identifier of the metadata document that was QCed.
	 * This is optional since in some cases, we will be performing 
	 * QC on objects that are being actively edited/created and 
	 * do not yet exist with persistent identifiers.
	 */
	@XmlElement(required = false)
	private String objectIdentifier;

	/**
	 * The list of results for this run. Results contain the check that was run and 
	 * the outcome of the check.
	 */
	@XmlElement(required = false)
	private List<Result> result;
	
	/**
	 * The identifier for the suite that was run
	 */
	@XmlElement(required = false)
	private String suiteId;

	/**
	 *  The error message for a check
	 */
	@XmlElement(required = true)
	private String status;

	/**
	 * The processing status of the run, i.e. was an error encountered generating the run
	 */
	@XmlElement(required = true)
	private String runStatus;

	/**
	 * The error message describing why a run (not a check) failed
	 */
	@XmlElement(required = false)
	private String errorDescription;

	/**
	 * SystemMetadata from DataONE
	 */
	@XmlElement(required = false)
	private SysmetaModel sysmeta;

	public String getId() {
		return id;
	}

	public void setId(String id) {
		this.id = id;
	}

	public Date getTimestamp() {
		return timestamp;
	}

	public void setTimestamp(Date timestamp) {
		this.timestamp = timestamp;
	}

	public SysmetaModel getSysmeta() {
		return sysmeta;
	}

	public void setSysmeta(SysmetaModel sysmeta) {
		this.sysmeta = sysmeta;
	}

	public List<Result> getResult() {
		return result;
	}

	public void setResult(List<Result> result) {
		this.result = result;
	}

	public String getObjectIdentifier() {
		return objectIdentifier;
	}

	public void setObjectIdentifier(String objectIdentifier) {
		this.objectIdentifier = objectIdentifier;
	}

	public String getSuiteId() {
		return suiteId;
	}

	public void setSuiteId(String suiteId) { this.suiteId = suiteId; }

	public String getStatus() { return status; }

	public void setStatus(String status) { this.status = status; }

	public String getRunStatus() { return runStatus; }

	public void setRunStatus(String status) { this.runStatus = status; }

	public String getErrorDescription() { return errorDescription; }

	public void setErrorDescription(String errorDescription) { this.errorDescription = errorDescription; }

	/**
	 * Save a quality report to a DatabaseStore.
	 * <p>
	 * The quality report is saved to a database instance.
	 * </p>
	 *
	 * @throws Exception
	 */
	public void save() throws MetadigException {

	    boolean persist = true;
		MDQStore store = StoreFactory.getStore(persist);

		log.debug("Saving to persistent storage: metadata PID: " + this.getObjectIdentifier()  + ", suite id: " + this.getSuiteId());

		try {
			store.saveRun(this);
		} catch (MetadigException me) {
			log.debug("Error saving run: " + me.getCause());
			if(me.getCause() instanceof SQLException) {
				log.debug("Retrying saveRun() due to error");
				store.renew();
				store.saveRun(this);
			} else {
				throw(me);
			}
		}

		// Note that when the connection pooler 'pgbouncer' is used, closing the connection actually just returns
		// the connection to the pool that pgbouncer maintains.
        log.debug("Shutting down store");
		store.shutdown();
		log.debug("Done saving to persistent storage: metadata PID: " + this.getObjectIdentifier() + ", suite id: " + this.getSuiteId());
	}

	/**
	 * Save a quality report to a DatabaseStore.
	 * <p>
	 * The quality report is saved to a database instance.
	 * </p>
	 *
	 * @param metadataId The DataONE identifier of the run to fetch
	 * @param suiteId The metadig-engine suite id of the suite to match
	 * @throws Exception
	 */
	public static Run getRun(String metadataId, String suiteId) throws MetadigException, IOException, ConfigurationException {
		boolean persist = true;
		MDQStore store = StoreFactory.getStore(persist);

		log.debug("Getting run for suiteId: " + suiteId + ", metadataId: " + metadataId);

		Run run = null;
		try {
			run = store.getRun(metadataId, suiteId);
		} catch (MetadigException me) {
			log.debug("Error getting run: " + me.getCause());
			if(me.getCause() instanceof SQLException) {
				log.debug("Retrying getRun() due to error");
				store.renew();
				store.getRun(metadataId, suiteId);
			} else {
				throw(me);
			}
		}
		log.debug("Shutting down store");
		store.shutdown();
		log.debug("Done getting from persistent storage: metadata PID: " + metadataId  + ", suite id: " + suiteId);
		return run;
	}
}
