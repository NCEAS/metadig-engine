package edu.ucsb.nceas.mdqengine.collections;

import edu.ucsb.nceas.mdqengine.exception.MetadigException;
import edu.ucsb.nceas.mdqengine.model.Identifier;
import edu.ucsb.nceas.mdqengine.store.MDQStore;
import edu.ucsb.nceas.mdqengine.store.StoreFactory;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.joda.time.DateTime;

import java.util.*;

public class Identifiers {

    public static Log log = LogFactory.getLog(Identifiers.class);
    private HashMap<String, Identifier> identifiers = new HashMap<>();
    private String sequenceId = null;
    private Boolean foundFirstPid = false;
    private String firstPidInSequence = null;

    public Identifiers () {
    }

    /**
     * Determine the correct sequenceId for this run by finding the sequenceId assigned to previous pids
     * in this obsolescence chain.
     * <p>
     * Evaluate the sequenceId of all identifires in the DataONE obsolescence chain and determine the correct
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
     * @param stopWhenSIfound
     * @param store
     * @param forward
     *
     * @throws Exception
     */

    public void getNextIdentifier(String metadataId, Boolean stopWhenSIfound, MDQStore store, Boolean forward, Integer level) {

        Identifier identifier= null;
        String obsoletedBy = null;
        String obsoletes = null;
        String sequenceId = null;
        Boolean SIfound = false;

        level += 1;
        log.debug("Getting next identifier: metadataId: " + metadataId);
        log.debug("Recursion level: " + level);

        // First see if we have this run in the collection
        identifier = identifiers.get(metadataId);

        // If not in the collection, see if it is in the store
        if(identifier == null) {
            try {
                log.debug("Run for pid: " + metadataId + " not in collection, getting from store.");
                // the 'store.getRun' returns null if the run isn't found in the store. This will typically happen
                // if the next pid in the chain hasn't been cataloged.
                identifier = store.getIdentifier(metadataId);
            } catch (MetadigException me) {
                log.error("Error getting run: " + me.getCause());
                // Terminate recursion in the current direction
                return;
            }
        }

        // If a run was found for this pid in the chain, check if a sequence id was previously
        // defined for it. We want to use the same sequence id for all pids in the chain, right?
        if(identifier != null) {
            // get sequence id for this run
            sequenceId = identifier.getSequenceId();
            // End recursion if the sequence id for this chain has been found.
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

            if(stopWhenSIfound)
                if(SIfound) return;

            // The termination tests have passed, add this identifier to the collection
            this.addIdentifier(metadataId, identifier);

            // Moving in the forward direction, get the next pid in the chain
            if (forward) {
                log.debug("Checking for next forward pid (obsoletedBy)");
                obsoletedBy = identifier.getObsoletedBy();
                if(obsoletedBy != null) {
                    // Check for an invalid obsoletedBy - pid links to itself
                    if(obsoletedBy.compareToIgnoreCase(metadataId) == 0) {
                        log.debug("Stopping traversal at invalid obsoletedBy, pid " + metadataId + " obsoletes itself");
                        return;
                    }
                    log.debug("traversing forward to obsoletedBy: " + obsoletedBy);
                    getNextIdentifier(obsoletedBy, stopWhenSIfound, store, forward, level);
                } else {
                    log.debug("Reached end of forward (obsoletedBy) chain at pid: " + metadataId);
                }
            } else {
                // Moving in the backward direction, get the next pid in the chain
                log.debug("Checking for next backward pid (obsoletes)");
                obsoletes = identifier.getObsoletes();
                if(obsoletes != null) {
                    // Check for an invalid obsoletedBy - pid links to itself
                    if(obsoletes.compareToIgnoreCase(metadataId) == 0) {
                        log.debug("Stopping traversal at invalid obsoletes, pid " + metadataId + " is obsoleted by itself");
                        return;
                    }
                    log.debug("traversing backward to obsoletes: " + obsoletes);
                    getNextIdentifier(obsoletes, stopWhenSIfound, store, forward, level);
                } else {
                    // Have we reached the first run in the sequence (not obsoleted by any pid)?
                    if(identifier.getObsoletes() == null) {
                        this.foundFirstPid = true;
                        log.debug("Found first pid in sequence: " + identifier.getMetadataId());
                        firstPidInSequence = identifier.getMetadataId();
                    }
                    log.debug("Reached beginning of obsoletes chain at pid: " + metadataId);
                }
            }
        } else {
            // The run was null, recursion in the current direction ends.
            log.debug("Identifier not found in store: " + metadataId + ", terminating search in current direction.");
        }

        return;
    }

    /**
     * Get indentifier entries for all pids in a DataONE obsolescence chain from the DataStore
     * <p>
     * Successive versions of a metadata document are represented in DataONE as pids comprising an obsolecence chain (i.e. linked list) or sequence,
     * where new versions 'obsolete' older, outdated ones.
     * This method follows the obsolecence chain to retrieve all identifiers corresponding to pids in the obsolescence chain.
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
     * @param identifier The starting identifier - get all pids in this obsolesence chain
     * @throws Exception
     */

    public void getIdentifierSequence(Identifier identifier, Boolean stopWhenSIfound) throws MetadigException {

        boolean persist = true;
        MDQStore store = StoreFactory.getStore(persist);
        Boolean forward;
        String metadataId = identifier.getMetadataId();
        this.sequenceId = null;

        // Keep track of the current recursion level
        Integer level = 0;

        // Convert input date string to JodaTime
        DateTime targetDate = new DateTime(identifier.getDateUploaded());
        // get target month start
        DateTime minDate = targetDate.withDayOfMonth(1);
        // get target month end
        DateTime maxDate = targetDate.plusMonths(1).withDayOfMonth(1).minusDays(1);

        // Start the traversal in the backward direction
        log.debug("Getting all identifier entries (backward) for metadataId: " + metadataId + ", minDate: " + minDate + ", " + maxDate);
        forward = false;
        getNextIdentifier(metadataId, stopWhenSIfound, store, forward, level);

        // If the sequenceId has not been obtained when searching in the backward direction, then there
        // is no reason to search forward, as this means that the first pid in the series has not been found,
        // it will not be found searching forward.
        if(getSequenceId() == null && !foundFirstPid) {
            log.debug("Unable to find sequenceId for this sequence, will not search in forward direction.");
            return;
        }
        // Continue traversal in the forward direction, if necessary
        log.debug("Getting all identifier entries (forward) for metadataId: " + metadataId);
        forward = true;
        getNextIdentifier(metadataId, stopWhenSIfound, store, forward, level);

        log.debug("Shutting down store");
        store.shutdown();
        log.debug("Done getting all identifier entries (in DataONE obsolescence chain) for : metadata PID: " + metadataId);
    }

    /**
     * Update each identifier entry in an obsolescence sequence with a new sequenceId.
     * <p>
     * The identifiers in a sequence have already been fetched via <b>getIdentifiersInSequence</b>, but some of them may not
     * have been assigned a sequenceId, i.e. possibly due to broken chains that have now been joined. Check
     * each pid in the sequence and assign it the sequenceId if it doesn't have it already.
     * </p>
     *
     * @param sequenceId a quality engine maintained sequence identifier, similiar in function to the DataONE series id.
     */

    public void updateSequenceId(String sequenceId) {

        log.debug("Updating identifier entries with new sequence id: " + sequenceId);

        Identifier identifier = null;
        String pid = null;
        Boolean modified = false;
        HashMap<String, Exception> errors = new HashMap<>();

        // Update identifier entries that don't already have the sequence id set
        String thisSeqId = null;
        for (Map.Entry<String, Identifier> entry : identifiers.entrySet()) {
            pid = (String) entry.getKey();
            identifier = (Identifier) entry.getValue();
            thisSeqId = identifier.getSequenceId();
//            if(thisSeqId != null && thisSeqId.equals(sequenceId)) {
//                log.error("Will not update sequence id for pid: " + identifier.getMetadataId() + " to id: " + sequenceId);
//                continue;
//            }

            if(thisSeqId != null && ! thisSeqId.equals(sequenceId)) {
                log.error("Multiple sequence ids found in one sequence chain for pid: " + identifier.getMetadataId());
            }
            log.debug("Updating sequence id for pid: " + identifier.getMetadataId() + " to id: " + sequenceId);
            identifier.setSequenceId(sequenceId);
            identifier.setModified(true);
        }
    }

    /**
     * Add a run to the collection
     */

    public void addIdentifier(String metadataPid, Identifier identifier) {
        if(! this.identifiers.containsKey(metadataPid)) {
            log.trace("Adding identifier entry for pid: " + metadataPid);
            this.identifiers.put(metadataPid, identifier);
        }
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
     * Get the identifier entries in this collection
     *
     * @return identifiers
     */

    public HashMap<String, Identifier> getIdentifiers() {
        return this.identifiers;
    }

    /**
     * Get the identifiers in this collection that are marked as modified.
     *
     */

    public ArrayList<Identifier> getModifiedIdentifiers() {

        ArrayList<Identifier> modIdentifiers = new ArrayList<>();

        String thisPid = null;
        Identifier identifier = null;

        for (Map.Entry<String, Identifier> entry : this.identifiers.entrySet()) {
            thisPid = entry.getKey();
            identifier = entry.getValue();
            if(identifier.getModified()) {
                modIdentifiers.add(identifier);
            }
        }
        return modIdentifiers;
    }


    /**
     * Update all modified identifier entries in the collection to the datastore.
     *
     */

    public void update() {

        String thisPid = null;
        Identifier identifier= null;
        Boolean modified = false;

        log.debug("Updating modified identifier entries to datastore.");

        for (Map.Entry<String, Identifier> entry : this.identifiers.entrySet()) {
            identifier = entry.getValue();
            modified = identifier.getModified();
            if(modified) {
                try {
                    log.debug("Updating modified identifier entry for pid: " + identifier.getMetadataId()
                            + ", dateUploaded: " + identifier.getDateUploaded() + ", sequenceId: "
                            + identifier.getSequenceId());
                    identifier.save();
                    // Keep modified setting for modified identifier entries in case we need to do other operations on the run entry (e.g. indexing)
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

    /**
     * Return the first pid in the sequence.
     *
     * @return firstPid - the starting pid in this obsolesense chain
     */
    public String getFirstPidInSequence() {
        return firstPidInSequence;
    }
}
