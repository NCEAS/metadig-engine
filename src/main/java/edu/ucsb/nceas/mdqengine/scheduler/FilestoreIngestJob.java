package edu.ucsb.nceas.mdqengine.scheduler;

import edu.ucsb.nceas.mdqengine.MDQconfig;
import edu.ucsb.nceas.mdqengine.exception.MetadigFilestoreException;
import edu.ucsb.nceas.mdqengine.exception.MetadigStoreException;
import edu.ucsb.nceas.mdqengine.filestore.MediaTypes;
import edu.ucsb.nceas.mdqengine.filestore.MetadigFile;
import edu.ucsb.nceas.mdqengine.filestore.MetadigFileStore;
import edu.ucsb.nceas.mdqengine.filestore.StorageType;
import org.apache.commons.configuration2.ex.ConfigurationException;
import org.apache.commons.io.FilenameUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.quartz.*;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;

/**
 * Ingest files that have been placed in the staging area into the MetaDIG filestore.
 * <p>
 * A scheduler task is run so that periodically the FilestoreIngestJob will be
 * executed. This program looks in the staging area (e.g. /opt/local/metadig/store)
 * and first copies them to their permanent location in the store, i.e.
 *     ./store/staging/code/somefile.R -> ./store/code/somefile.R
 * and then creates an entry in the MetaDIG 'filestore' database, so that the
 * file can be quickly located and accessed when needed.
 * Typically files such as R code (for creating graphis) will be ingested this way.
 * </p>
 *
 * @author Peter Slaughter
 */
@PersistJobDataAfterExecution
@DisallowConcurrentExecution
public class FilestoreIngestJob implements Job {

    private Log log = LogFactory.getLog(FilestoreIngestJob.class);

    // Since Quartz will re-instantiate a class every time it
    // gets executed, members non-static member variables can
    // not be used to maintain state!

    /**
     * <p>
     * Empty constructor for job initialization
     * </p>
     * <p>
     * Quartz requires a public empty constructor so that the
     * scheduler can instantiate the class whenever it needs.
     * </p>
     */
    public FilestoreIngestJob() {
    }

    /**
     * <p>
     * Called by the <code>{@link org.quartz.Scheduler}</code> when a
     * <code>{@link org.quartz.Trigger}</code> fires that is associated with
     * the <code>Job</code>.
     * </p>
     *
     * @throws JobExecutionException if there is an exception while executing the job.
     */
    public void execute(JobExecutionContext context) throws JobExecutionException {

        String filestoreDir = null;
        HashSet<String> dirIncludes = new HashSet<String>();

        try {
            MDQconfig cfg = new MDQconfig();
            filestoreDir = cfg.getString("metadig.store.directory");
        } catch (ConfigurationException | IOException ce) {
            JobExecutionException jee = new JobExecutionException("Error executing task.");
            jee.initCause(ce);
            throw jee;
        }

        JobKey key = context.getJobDetail().getKey();
        JobDataMap dataMap = context.getJobDetail().getJobDataMap();

        String dirIncludeMatch = dataMap.getString("dirIncludeMatch");
        String dirExcludeMatch = dataMap.getString("dirExcludeMatch");
        String fileIncludeMatch = dataMap.getString("fileIncludeMatch");
        String fileExcludeMatch = dataMap.getString("fileExcludeMatch");
        String logFile = dataMap.getString("logFile");

        List<String> inDirs = Arrays.asList(dirIncludeMatch.split(","));

        File fileName = new File(filestoreDir);
        File[] fileList = fileName.listFiles();
        for (File file: fileList) {
            if(file.isDirectory() && inDirs.contains(file.getName())) {
                try {
                    log.info("Searching for files in " + file.getAbsolutePath() + " to ingest into filestore");
                    searchDir(file, dirIncludeMatch, dirExcludeMatch, fileIncludeMatch, fileExcludeMatch);
                } catch (MetadigFilestoreException mse) {
                    JobExecutionException jee = new JobExecutionException("Error executing task.");
                    jee.initCause(mse);
                    throw jee;
                }
            }
        }
    }

    private void searchDir(File file, String dirIncludeMatch, String dirExcludeMatch, String fileIncludeMatch,
                                 String fileExcludeMatch) throws MetadigFilestoreException {

        List<String> inDirs = Arrays.asList(dirIncludeMatch.split(","));
        File[] fileList = file.listFiles();
        for (File thisFile : fileList) {
            if(thisFile.isDirectory() && inDirs.contains(file.getName())) {
                try {
                    searchDir(thisFile, dirIncludeMatch, dirExcludeMatch, fileIncludeMatch, fileExcludeMatch);
                } catch (MetadigFilestoreException mse) {
                    log.error("Error searching directory " + thisFile.getName() + ": " + mse.getMessage());
                    throw mse;
                }
            } else if (thisFile.isFile()) {
                log.info("Ingesting file: " + thisFile.getAbsolutePath());
                ingestFile(thisFile, thisFile);
            }
        }
    }

    private File ingestFile(File dir, File file) throws MetadigFilestoreException {

        //MetadigFile mf = MetadigFile(collectionId, metadataId, suiteId, nodeId, metadataFormatFilter, storageType, relativePath, createtionDate, fileExt);
        MetadigFileStore metadigFileStore = null;
        MetadigFile metadigFile = null;

        try {
            metadigFileStore = new MetadigFileStore();
            metadigFile = new MetadigFile();
        } catch (MetadigFilestoreException mse) {
            log.error("Unable to ingest file, cannot intialize MetadigFileStore: " + mse.getMessage());
            throw mse;
        }

        // The directory name should be the Metadig filestore storage type, i.e. "code" or "graph", etc
        StorageType storageType = StorageType.getValue(dir.getName());
        metadigFile.setStorageType(StorageType.CODE.toString());
        log.debug("setting storage type to " + metadigFile.getStorageType());
        metadigFile.setAltFilename(file.getName());

        Boolean replace = true;
        try {
            // Get the mediaType from the file extension
            MediaTypes mediaTypes = new MediaTypes();
            String mediaTypeName = mediaTypes.getMediaTypeName(file);
            metadigFile.setMediaType(mediaTypeName);
            log.debug("Setting mediaType to: " + mediaTypeName);
            String newFile = metadigFileStore.saveFile(metadigFile, file.getAbsolutePath(), replace);
            // Now that the file has been saved to the filestore, remove the file from the staging area
            file.delete();
        } catch (Exception e) {
            log.error("Unable to save file " + "\"" + file.getName() + "\" to filestore: " + e.getMessage());
            MetadigFilestoreException mse = new MetadigFilestoreException("Unable to save file " + "\"" + file.getName() + "\" to filestore");
            mse.initCause(e.getCause());
            throw mse;
        }

        return file;
    }
}
