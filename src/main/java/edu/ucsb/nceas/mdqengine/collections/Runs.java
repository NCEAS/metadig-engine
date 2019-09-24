package edu.ucsb.nceas.mdqengine.collections;

import edu.ucsb.nceas.mdqengine.exception.MetadigException;
import edu.ucsb.nceas.mdqengine.model.Run;
import edu.ucsb.nceas.mdqengine.model.SysmetaModel;
import edu.ucsb.nceas.mdqengine.store.MDQStore;
import edu.ucsb.nceas.mdqengine.store.StoreFactory;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.joda.time.DateTime;

import java.util.*;

public class Runs {

    public static Log log = LogFactory.getLog(Run.class);
    private HashMap<String, Run> runs = new HashMap<>();
    private String sequenceId = null;
    private Boolean foundFirstPid = false;

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
     * @param metadataId The identifier of the metadata document associated with the report
     * @param suiteId The identifier for the suite used to score the metadata
     * @param stopWhenSIfound
     * @param store
     * @param forward
     *
     * @throws Exception
     */

    public void getNextRun(String metadataId, String suiteId, Boolean stopWhenSIfound, MDQStore store, Boolean forward) {

        Run run = null;
        String obsoletedBy = null;
        String obsoletes = null;
        String sequenceId = null;
        SysmetaModel sysmetaModel = null;
        Boolean SIfound = false;
        Boolean reachedDateBounds = false;

        log.debug("Getting next run: metadataId: " + metadataId + ", suiteId: " + suiteId);

        // First see if we have this run in the collection
        run = runs.get(metadataId);

        // If not in the collection, see if it is in the store
        if(run == null) {
            try {
                log.debug("Run for pid: " + metadataId + " not in collection, getting from store.");
                // the 'store.getRun' returns null if the run isn't found in the store. This will typically happen
                // if the next pid in the chain hasn't been cataloged.
                run = store.getRun(metadataId, suiteId);
            } catch (MetadigException me) {
                log.error("Error getting run: " + me.getCause());
                // Terminate recursion in the current direction
                return;
            }
        }

        // If a run was found for this pid in the chain, check if a sequence id was previously
        // defined for it. We want to use the same sequence id for all pids in the chain, right?
        if(run != null) {
            // get sequence id for this run
            sequenceId = run.getSequenceId();
            // End recursion if the minDate or maxDate have been passed, and possibly if the sequence id for
            // this chain has been found.
            if(sequenceId != null) {
                SIfound = true;
                // Has the sequence id for the collection been defined yet?
                if(this.sequenceId != null) {
                    if(! this.sequenceId.equals(sequenceId)) {
                        log.error("Warning, new sequenceId found for chain: " + sequenceId + " found at pid: " + metadataId);
                    }
                } else {
                    // We got the right sequence id for this chain
                    this.sequenceId = sequenceId;
                    log.debug("Found sequence id: " + sequenceId + " at pid: " + metadataId);
                }
            }


            // The termination tests have passed, add this run to the collection
            this.addRun(metadataId, run);

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
                    getNextRun(obsoletedBy, suiteId, stopWhenSIfound, store, forward);
                } else {
                    log.debug("Reached end of forward (obsoletedBy) chain at pid: " + metadataId);
                }
            } else {
                // Moving in the backward direction, get the next pid in the chain
                log.debug("Checking for next backward pid (obsoletes)");
                obsoletes = sysmetaModel.getObsoletes();
                if(obsoletes != null) {
                    log.debug("traversing backward to obsoletes: " + obsoletes);
                    getNextRun(obsoletes, suiteId, stopWhenSIfound, store, forward);
                } else {
                    // Have we reached the first run in the sequence (not obsoleted by any pid)?
                    if(run.getObsoletes() == null) {
                        this.foundFirstPid = true;
                        log.debug("Found first pid in sequence: " + run.getObjectIdentifier());
                    }
                    log.debug("Reached beginning of obsoletes chain at pid: " + metadataId);
                }
            }
        } else {
            // The run was null, recursion in the current direction ends.
            log.debug("Run not found in store for pid: " + metadataId + ", suiteId: " + suiteId + ", terminating search in current direction.");
        }

        return;
    }

    /**
     * Get runs for all pids in a DataONE obsolescence chain from the DataStore
     * <p>
     * Successive versions of a metadata document are represented in DataONE as pids comprising an obsolecence chain (i.e. linked list) or sequence,
     * where new versions 'obsolete' older, outdated ones.
     * This method follows the obsolecence chain to retrieve all runs corresponding to pids in the obsolescence chain.
     * The least number of pids to get will be for all versions that are within the current month of the starting pid.
     * If <b>stopWhenSIfound</b> is specified, the search will continue until the sequenceId is found, or the entire chain is
     * fetched.
     *
     * A new sequenceId can only be assinged to the first version in a chain. If pids are processed out of 'dateUploaded' order,
     * i.e. due to multi-processing, there may be 'breaks' in the chain and pids may temporarily be without sequenceIds. When a
     * new pid is added, a check will be made to re-link pids back to the chain by traversing backward until a sequenceId is
     * found, then previously saved pids with no sequenceId will have the correct one assinged.
     * </p>
     *
     * @param run The starting run - get all pids in this runs obsolesence chain
     * @param suiteId The metadig-engine suite id of the suite to match
     * @throws Exception
     */

    public void getRunSequence(Run run, String suiteId, Boolean stopWhenSIfound) throws MetadigException {

        boolean persist = true;
        MDQStore store = StoreFactory.getStore(persist);
        Boolean forward;
        String metadataId = run.getObjectIdentifier();
        this.sequenceId = null;

        // Convert input date string to JodaTime
        DateTime targetDate = new DateTime(run.getDateUploaded());
        // get target month start
        DateTime minDate = targetDate.withDayOfMonth(1);
        // get target month end
        DateTime maxDate = targetDate.plusMonths(1).withDayOfMonth(1).minusDays(1);

        // Start the traversal in the backward direction
        log.debug("Getting all runs (backward) for suiteId: " + suiteId + ", metadataId: " + metadataId + ", minDate: " + minDate + ", " + maxDate);
        forward = false;
        getNextRun(metadataId, suiteId, stopWhenSIfound, store, forward);

        // If the sequenceId has not been obtained when searching in the backward direction, then there
        // is no reason to search forward, as this means that the first pid in the series has not been found,
        // it will not be found searching forward.
        if(getSequenceId() == null && !foundFirstPid) {
            log.debug("Unable to find sequenceId for this sequence, will not search in forward direction.");
            return;
        }
        // Continue traversal in the forward direction, if necessary
        log.debug("Getting all runs (forward) for suiteId: " + suiteId + ", metadataId: " + metadataId);
        forward = true;
        getNextRun(metadataId, suiteId, stopWhenSIfound, store, forward);

        log.debug("Shutting down store");
        store.shutdown();
        log.debug("Done getting all runs (in DataONE obsolescence chain) for : metadata PID: " + metadataId  + ", suite id: " + suiteId);
    }

    /**
     * Update each run in an obsolescence sequence with a new sequenceId.
     * <p>
     * The runs in a sequence have already been fetched via <b>getRunsInSeq</b>, but some of them may not
     * have been assigned a sequenceId, i.e. possibly due to broken chains that have now been joined. Check
     * each pid in the sequence and assign it the sequenceId if it doesn't have it already.
     * </p>
     *
     * @param sequenceId a quality engine maintained sequence identifier, similiar in function to the DataONE series id.
     */

    public void updateSequenceId(String sequenceId) {

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
     * Add a run to the collection
     */

    public void addRun(String metadataPid, Run run) {
        if(! this.runs.containsKey(metadataPid)) {
            log.trace("Adding run for pid: " + metadataPid);
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
     * <p> Determine which run in this obsolecense sequence is the latest in the month, give a date.</p>
     *
     * @param date the date to use for comparison
     */
    public void setLatestRunInMonth (Date date) {

        Run run = null;

        // Convert input date string to JodaTime
        DateTime targetDate = new DateTime(date);
        // get target month start
        DateTime minDate = targetDate.withDayOfMonth(1);
        // get target month end
        DateTime maxDate = targetDate.plusMonths(1).withDayOfMonth(1).minusDays(1);

        // Get the run with the latest date in the month from the input date
        run = getLatestRun(targetDate, minDate, maxDate);
        String latestPid = run.getObjectIdentifier();
        Boolean latestSet = false;
        String thisPid = null;

        DateTime dateUploaded = null;
        // Check all other runs in this collection that are in the month and unmark them
        // as latest, if they had previously been marked.
        log.debug(this.runs.size() + " runs in collection");
        for (Map.Entry<String, Run> entry : this.runs.entrySet()) {
            thisPid = entry.getKey();
            run = entry.getValue();
            dateUploaded = new DateTime(run.getDateUploaded());
            latestSet = run.getIsLatest();
            log.debug("Checking run with pid: " + thisPid + ", dateUploaded: " + dateUploaded + ", isLatest: " + latestSet);
            // Don't consider this run if it is outside the target month
            if(dateUploaded.isBefore(minDate) || dateUploaded.isAfter(maxDate)) {
                log.debug("Skipping out of date range pid: " + run.getObjectIdentifier());
                continue;
            }
            // Update the run for the latest pid.
            if(thisPid.equalsIgnoreCase(latestPid)) {
                log.info("Setting latest run in month to pid: " + run.getObjectIdentifier() + " with date: " + run.getDateUploaded());
                run.setIsLatest(true);
                run.setModified(true);
                // Update the collection with this updated run
                this.runs.replace(thisPid, run);
            } else {
                // Not the latest pid, 'unmark' it if needed
                if(latestSet) {
                    log.info("Unsetting latest run in month to pid: " + run.getObjectIdentifier() + " with date: " + run.getDateUploaded());
                    run.setIsLatest(false);
                    run.setModified(true);
                    // Update the collection with this updated run
                    this.runs.replace(thisPid, run);
                }
            }
        }
        return;
    }

    /**
     * <p> Return the run from the collection that is the latest in the given month. The
     * field 'dateUploaded' is used for comparision. Since all runs in the collection are
     * in the same DateONE obsolecense chain, returning the 'latest' in a given month
     * should correspond to the pid with the highest metadata quality score.
     * Finding this run (pid) can be useful to aggregation and display routines to filter out
     * the 'best' runs in a month, to more accurately represent the progression of
     * quality scores over time.
     * </p>
     *
     * @param targetDate
     * @param minDate
     * @param maxDate
     *
     * @return Run - the latest run in the month
     */
    public Run getLatestRun(DateTime targetDate,  DateTime minDate, DateTime maxDate) {

        Run run = null;

        // Assume that the input pid is the latest
        DateTime latestDate = targetDate;
        String latestPid = null;
        DateTime thisDate = null;

        String thisPid = null;
        //DateTimeFormatter fmt = DateTimeFormat.forPattern("yyyy MM dd");
        log.debug("Getting latest pid in date range " + minDate.toString() + " to " + maxDate.toString());

        // Loop through each run, find latest in the month specified
        for (Map.Entry<String, Run> entry : this.runs.entrySet()) {
            thisPid = entry.getKey();
            run = entry.getValue();

            Date entryDate = run.getDateUploaded();
            // This run doesn't contain a sysmeta and so doesn't have a date. This probably
            // should never happen but just in case...
            if(entryDate == null) {
                log.debug("Run pid: " + thisPid + ", date is null");
                continue;
            } else {
                // Convert to JodaTime, so that we can easily check it
                thisDate = new DateTime(run.getDateUploaded());
                log.debug("Run pid: " + thisPid + ", date: " + thisDate.toString());
            }

            // Is this current month
            if(thisDate.isBefore(minDate)) {
                log.debug("Skipping pid: " + thisPid + " with date: " + entryDate + " (after end of month)");
                continue;
            }
            if(thisDate.isAfter(maxDate)) {
                log.debug("Skipping pid: " + thisPid + " with date: " + entryDate + " before start of month");
                continue;
            }

            // If the date of this entry is after the input date, then we have
            // a new 'leader'. This assumes that the newer pid in this sequence
            // is actually 'better' than the previous one.
            // Have to check for date equals in case this is the only pid in the sequence
            if(thisDate.isAfter(targetDate) || thisDate.isEqual(targetDate)) {
                log.trace("Setting latest pid: " + thisPid + ", date: " + thisDate);
                latestPid = thisPid;
                latestDate = thisDate;
            }
        }

        log.debug("Latest pid in month is: " + latestPid + " with date: " + latestDate.toString());

        return this.runs.get(latestPid);
    }


    /**
     * Set the sequence identifier for the run sequence.
     *
     * @param sequenceId
     *
     */

    public void setSequenceId(String sequenceId)  {
        this.sequenceId = sequenceId;
    }

    /**
     * Get the runs in this collection
     *
     * @return runs
     */

    public HashMap<String, Run> getRuns() {
        return this.runs;
    }

    /**
     * Get the runs in this collection that are marked as modified.
     *
     */

    public ArrayList<Run> getModifiedRuns() {

        ArrayList<Run> modRuns = new ArrayList<>();

        String thisPid = null;
        Run run = null;

        for (Map.Entry<String, Run> entry : this.runs.entrySet()) {
            thisPid = entry.getKey();
            run = entry.getValue();
            if(run.getModified()) {
                modRuns.add(run);
            }
        }
        return modRuns;
    }


    /**
     * Update all modified runs in the collection to the datastore.
     *
     */

    public void update() {

        String thisPid = null;
        Run run = null;
        Boolean modified = false;

        log.debug("Updating modified runs to datastore.");

        for (Map.Entry<String, Run> entry : this.runs.entrySet()) {
            thisPid = entry.getKey();
            run = entry.getValue();
            modified = run.getModified();
            if(modified) {
                try {
                    log.debug("Updating modified quality run for pid: " + run.getObjectIdentifier() + ", suite: "
                                    + run.getSuiteId() + ", dateUploaded: " + run.getDateUploaded() + ", sequenceId: "
                                    + run.getSequenceId() + ", isLatest: " + run.getIsLatest());
                    run.save();
                    // Keep modified setting for modified runs in case we need to do other operations on the runs (e.g. indexing)
                    //this.runs.replace(thisPid, run);
                } catch (Exception ex) {
                    log.error("Unable to save the quality report to database:" + ex.getMessage());
                }
            }
        }
    }

    /**
     * If pids are missing in the current obsolesence chain, then a traversal may not reach the starting pid, i.e.
     * the very first one in the chain. This must be determined, as we only assign a sequenceId when the first pids is found.
     *
     * @return foundFirstPid - was the starting pid in this obsolesense chain reached?
     */
    public Boolean getFoundFirstPid() {
        return this.foundFirstPid;
    }
}
