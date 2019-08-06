package edu.ucsb.nceas.mdqengine.collections;

import edu.ucsb.nceas.mdqengine.exception.MetadigException;
import edu.ucsb.nceas.mdqengine.model.Run;
import edu.ucsb.nceas.mdqengine.model.SysmetaModel;
import edu.ucsb.nceas.mdqengine.store.MDQStore;
import edu.ucsb.nceas.mdqengine.store.StoreFactory;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class Runs {

    public static final String SUCCESS = "success";
    public static final String FAILURE = "failure";
    public static final String QUEUED = "queued";
    public static final String PROCESSING = "processing";
    public static Log log = LogFactory.getLog(Run.class);

    private HashMap<String, Run> runs = new HashMap<>();
    private Boolean completeChain = false;
    private String sequenceId = null;

    public Runs () {
    }


    /**
     * Determine the correct sequenceId for this run by finding the sequenceId assigned to previous pids
     * in this obsolescence chain.
     * <p>
     * Evaluate the sequenceId of all runs in the DataONE obsolescence chain and determine the correct
     * seriedId to use for the current pid. If one doesn't exist, then create a new one.
     * </p>
     *
     * @throws Exception
     */

    public String getSequenceId() {
        return this.sequenceId;
    }

    /**
     * Get the next run in a series, recursively
     * <p>
     * Recursion ends when either the next run is not available (not in store) or the pointer to the
     * next pid in the chain isn't specified (we are at the end of the chain).
     * </p>
     *
     * @throws Exception
     */

    public void getNextRun(String metadataId, String suiteId, Boolean stopIfSIfound, MDQStore store, Boolean forward) {
        Run run = null;
        String obsoletedBy = null;
        String obsoletes = null;
        String sequenceId = null;
        SysmetaModel sysmetaModel = null;

        log.debug("Getting next run: metadataId: " + metadataId + ", suiteId: " + suiteId);

        // First see if we have this run in the collection
        run = runs.get(metadataId);

        // If not in the collection, see if it is in the store
        if(run == null) {
            try {
                run = store.getRun(metadataId, suiteId);
            } catch (MetadigException me) {
                log.debug("Error getting run: " + me.getCause());
                return;
            }
        }

        // If a run was found for this pid in the chain, check if a sequence id was previously
        // defined for it. We want to use the same sequence id for all pids in the chain, right?
        if(run != null) {
            this.addRun(metadataId, run);
            // get sequence id for this run
            sequenceId = run.getSequenceId();
            // End recursion if the sequence id is found and termination is requested
            if(sequenceId != null) {
                // Has the sequence id for the collection been defined yet and is it different
                // than the one for the current pid? This can happen if different, separate segments
                // of the chain were previously processed and now the chain is connected.
                if(this.sequenceId != null) {
                    if(! this.sequenceId.equals(sequenceId)) {
                        log.error("Warning, new sequenceId found for chain: " + sequenceId + " found at pid: " + metadataId);
                    }
                } else {
                    // We got the right sequence id for this chain
                    this.sequenceId = sequenceId;
                    log.debug("Found sequence id: " + sequenceId + " at pid: " + metadataId);
                    if(stopIfSIfound) {
                        log.debug("Terminating traversal as stop (when sequenceId is first found) is specified.");
                        return;
                    }
                }
            }

            // Get the sysmeta object within the run, to retrieve the 'obsoletes' or 'obsoletedBy' pid
            sysmetaModel = run.getSysmeta();
            if(sysmetaModel == null) {
                log.error("Missing sysmeta model for run with id: " + run.getObjectIdentifier());
                return;
            }
            // Moving in the forward direction, get the next pid in the chain
            if (forward) {
                log.debug("Checking for next forward pid (obsoletedBy)");
                obsoletedBy = sysmetaModel.getObsoletedBy();
                if(obsoletedBy != null) {
                    log.debug("traversing forward to obsoletedBy: " + obsoletedBy);
                    getNextRun(obsoletedBy, suiteId, stopIfSIfound, store, forward);
                } else {
                    log.debug("Reached end of forward (obsoletedBy) chain at pid: " + metadataId);
                }
            } else {
                // Moving in the backward direction, get the next pid in the chain
                log.debug("Checking for next backward pid (obsoletes)");
                obsoletes = sysmetaModel.getObsoletes();
                if(obsoletes != null) {
                    log.debug("traversing backward to obsoletes: " + obsoletes);
                    getNextRun(obsoletes, suiteId, stopIfSIfound, store, forward);
                } else {
                    log.debug("Reached end of backward (obsoletes) chain at pid: " + metadataId);
                }
            }
        } else {
            log.debug("Run not found for pid: " + metadataId + ", suiteId: " + suiteId);
        }

        return;
    }

    /**
     * Get runs for all pids in a DataONE obsolescence chain from the DataStore
     * <p>
     * Different versions of a metadata document are represented in DataONE as pids comprising an obsolecence chain (i.e. linked list) or sequence,
     * where new versions 'obsolete' older, outdated ones.
     * This method follows the obsolecence chain to retrieve all runs corresponding to pids in the obsolescence chain.
     * For efficiency, the search can be terminated early, before the entire chain is received, by specifing <b>stopIfSIfound</b>
     * </p>
     *
     * @param metadataId The DataONE identifier of the run to fetch
     * @param suiteId The metadig-engine suite id of the suite to match
     * @throws Exception
     */

    public void getRunSequence(String metadataId, String suiteId, Boolean stopIfSIfound) throws MetadigException {

        boolean persist = true;
        MDQStore store = StoreFactory.getStore(persist);
        Boolean forward = false;
        this.sequenceId = null;

        // Start the traversal in the backward direction
        log.debug("Getting all runs (backward) for suiteId: " + suiteId + ", metadataId: " + metadataId);
        getNextRun(metadataId, suiteId, stopIfSIfound, store, forward);

        // If the sequence id is not found, continue traversal in the forward direction
        if(this.sequenceId == null) {
            log.debug("Getting all runs (forward) for suiteId: " + suiteId + ", metadataId: " + metadataId);
            forward = true;
            getNextRun(metadataId, suiteId, stopIfSIfound, store, forward);
        }

        log.debug("Shutting down store");
        store.shutdown();
        log.debug("Done getting all runs (in DataONE obsolescence chain) for : metadata PID: " + metadataId  + ", suite id: " + suiteId);
    }

    /**
     * Update each run in an obsolescence sequence with a new sequenceId.
     * <p>
     * The runs in a sequence have already been fetched via <b>getRunsInSeq</b>. This method is used when the sequence hasn't
     * already been assigned a sequence id, as it will assign the sequence id to each run.
     * </p>
     *
     * @param sequenceId a quality engine maintained sequence identifier, similiar in function to the DataONE series id.
     */

    public void update(String sequenceId) {

        log.debug("Updating runs with new sequence id: " + sequenceId);

        Run run = null;
        String pid = null;
        Boolean modified = false;
        HashMap<String, Exception> errors = new HashMap<>();

        // Update runs that don't already have the sequence id set
        String thisSeqId = null;
        for (Map.Entry<String, Run> entry : runs.entrySet()) {
            pid = (String) entry.getKey();
            run = (Run) entry.getValue();
            thisSeqId = run.getSequenceId();
            if(thisSeqId != null && thisSeqId.equals(sequenceId)) {
                continue;
            }

            if(thisSeqId != null && ! thisSeqId.equals(sequenceId)) {
                log.error("Multiple sequence ids found in one sequence chain for pid: " + run.getObjectIdentifier());
            }
            log.debug("Updating sequence id for pid: " + run.getObjectIdentifier() + " to id: " + sequenceId);
            run.setSequenceId(sequenceId);
            run.setModified(true);
        }
    }

    /**
     * Save eech modified run to the data store.
     *
     * @throws Exception
     */

    public void save() throws MetadigException {

        boolean persist = true;
        MDQStore store = StoreFactory.getStore(persist);

        log.debug("Saving a set of runs...");

        Run run = null;
        String pid = null;

        for (Map.Entry<String, Run> entry : runs.entrySet()) {
            pid = (String) entry.getKey();
            run = (Run) entry.getValue();

            // If this run has been modified, save it to the DataStore
            if(run.getModified()) {
                log.debug("Saving modified run for pid: " + run.getObjectIdentifier() + ", suite id: " + run.getSuiteId());
                run.save();

                try {
                    log.debug("Saving quality run...");
                    // convert String into InputStream
                    run.save();
                    log.debug("Saved quality run ");
                } catch (Exception ex) {
                    log.error("Unable to save quality run for pid: " + pid + ", suiteId: " + run.getSuiteId());
                    continue;
                }
            }
        }
    }

    /**
     * Add a run to the collection
     */

    public void addRun(String metadataPid, Run run) {
        if(! this.runs.containsKey(metadataPid)) {
            this.runs.put(metadataPid, run);
        }
    }

    /**
     * Generate a unique identifier
     *
     */

    public String generateId() {
        String uuid = "urn:uuid:" + (UUID.randomUUID().toString());
        return uuid;
    }

    /**
     * Set the sequence identifier for the run sequence.
     *
     */

    public void setSequenceId(String sequenceId)  {
        this.sequenceId = sequenceId;
    }

    /**
     * Get the runs in this collection
     *
     */

    public HashMap<String, Run> getRuns() {
        return this.runs;
    }
}
