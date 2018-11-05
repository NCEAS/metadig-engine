package edu.ucsb.nceas.mdqengine.model;

import edu.ucsb.nceas.mdqengine.exception.MetadigException;
import edu.ucsb.nceas.mdqengine.exception.MetadigStoreException;
import edu.ucsb.nceas.mdqengine.store.MDQStore;
import edu.ucsb.nceas.mdqengine.store.StoreFactory;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.dataone.service.types.v2.SystemMetadata;

import javax.xml.bind.annotation.*;
import java.sql.SQLException;
import java.util.Date;
import java.util.List;

@XmlRootElement
@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(propOrder = {"id", "timestamp", "objectIdentifier", "suiteId", "status", "runStatus", "errorDescription", "result"})
public class Run {

	public static final String SUCCESS = "success";
	public static final String FAILURE = "failure";
	public static final String QUEUED = "queued";
	public static final String PROCESSING = "processing";
	private static MDQStore store = null;
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
	 * @param sysmeta The DataONE system metadata associated with the metadata document that was assessed.
	 * @throws Exception
	 */
	public void save(SystemMetadata sysmeta) throws MetadigStoreException {

	    boolean persist = true;
		store = StoreFactory.getStore(persist);

		if(!store.isAvailable()) {
			try {
				store.renew();
			} catch (MetadigStoreException e) {
				e.printStackTrace();
				throw(e);
			}
		}

		log.debug("Saving to persistent storage: metadata PID: " + this.getId()  + ", suite id: " + this.getSuiteId());

		try {
			store.saveRun(this, sysmeta);
		} catch (MetadigException me) {
			log.debug("Error saving run: " + me.getCause());
			if(me.getCause() instanceof SQLException) {
				log.debug("Retrying saveRun() due to error");
				store.saveRun(this, sysmeta);
			} else {
				throw(me);
			}
		}

		// TODO: shutdown connection when a Worker process/container ends. This may involve catching a SIGTERM
		// sent to the processing running the worker.
		// Note that when the connection pooler 'pgbouncer' is used, closing the connection actually just returns
		// the connection to the pool that pgbouncer maintains.
		store.shutdown();
		log.debug("Done saving to persistent storage: metadata PID: " + this.getId() + ", suite id: " + this.getSuiteId());
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
	public static Run getRun(String metadataId, String suiteId) throws MetadigStoreException {
		MDQStore store = null;
		boolean persist = true;
		store = StoreFactory.getStore(persist);

		if(!store.isAvailable()) {
			try {
				store.renew();
			} catch (MetadigStoreException e) {
				e.printStackTrace();
				throw(e);
			}
		}

		log.debug("Getting run for suiteId: " + suiteId + ", metadataId: " + metadataId);
		Run run = store.getRun(metadataId, suiteId);
		return run;
	}
}
