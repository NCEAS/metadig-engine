package edu.ucsb.nceas.mdqengine.model;

import edu.ucsb.nceas.mdqengine.exception.MetadigException;
import edu.ucsb.nceas.mdqengine.store.MDQStore;
import edu.ucsb.nceas.mdqengine.store.StoreFactory;
import org.apache.commons.configuration2.ex.ConfigurationException;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import java.io.IOException;
import java.sql.SQLException;
import java.util.Arrays;
import java.util.Date;
import java.util.List;

@XmlAccessorType(XmlAccessType.FIELD)
public class Result {
	
	public static Log log = LogFactory.getLog(Result.class);

	/* The metadata id the check was run for */
	private String metadataId;

	/* The suite that was run */
	private String suiteId;

	/**
	 * The check that was run
	 */
	private Check check;
		
	/**
	 * The timestamp of the check execution
	 */
	private Date timestamp;
		
	/**
	 * The list of output value[s] from the check execution
	 */
	private List<Output> output;
	
	/**
	 * The status of the check run, constrained to the Status enum
	 */
	private Status status;

	public List<Output> getOutput() {
		return output;
	}

	public void setOutput(Output... output) {
		this.output = Arrays.asList(output);
	}
	
	public void setOutput(List<Output> output) {
		this.output = output;
	}

	public Status getStatus() {
		return status;
	}

	public void setStatus(Status status) {
		this.status = status;
	}

	public Check getCheck() {
		return check;
	}

	public void setCheck(Check check) {
		this.check = check;
	}

	public Date getTimestamp() {
		return timestamp;
	}

	public void setTimestamp(Date timestamp) {
		this.timestamp = timestamp;
	}

	/**
	 * Save a quality run result to a DatabaseStore.
	 * <p>
	 * The quality run result is saved to a database instance.
	 * </p>
	 *
	 * @throws Exception
	 */
	public void save(String metadataId, String suiteId) throws MetadigException {

		boolean persist = true;
		MDQStore store = StoreFactory.getStore(persist);

		log.trace("Saving check result to persistent storage: metadata PID: " + metadataId  + ", suite id: " + suiteId);

		try {
			store.saveResult(this, metadataId, suiteId);
		} catch (MetadigException me) {
			log.trace("Error saving result: " + me.getCause());
			if(me.getCause() instanceof SQLException) {
				log.trace("Retrying saveResult() due to error");
				store.renew();
				store.saveResult(this, metadataId, suiteId);
			} else {
				throw(me);
			}
		}

		// Note that when the connection pooler 'pgbouncer' is used, closing the connection actually just returns
		// the connection to the pool that pgbouncer maintains.
		log.trace("Shutting down store");
		store.shutdown();
		log.trace("Done saving result to persistent storage: metadata PID: " + metadataId + ", suite id: " + suiteId);
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
	public static Result getResult(String metadataId, String suiteId, String checkId) throws MetadigException, IOException, ConfigurationException {
		boolean persist = true;
		Result result = null;
		MDQStore store = StoreFactory.getStore(persist);

		log.trace("Getting run result for suiteId: " + suiteId + ", metadataId: " + metadataId);

		try {
			result = store.getResult(metadataId, suiteId, checkId);
		} catch (MetadigException me) {
			log.trace("Error getting run: " + me.getCause());
			if(me.getCause() instanceof SQLException) {
				log.trace("Retrying getRun() due to error");
				store.renew();
				store.getRun(metadataId, suiteId);
			} else {
				throw(me);
			}
		}
		log.trace("Shutting down store");
		store.shutdown();
		log.trace("Done getting from persistent storage: metadata PID: " + metadataId  + ", suite id: " + suiteId);
		return result;
	}
}

