package edu.ucsb.nceas.mdqengine.scheduler;

import edu.ucsb.nceas.mdqengine.MDQconfig;
import edu.ucsb.nceas.mdqengine.exception.MetadigStoreException;
import edu.ucsb.nceas.mdqengine.filestore.MetadigFile;
import edu.ucsb.nceas.mdqengine.filestore.MetadigFileStore;
import edu.ucsb.nceas.mdqengine.filestore.StorageType;
import org.apache.commons.configuration2.ex.ConfigurationException;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.quartz.*;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;

/**
 * <p>
 * Run a MetaDIG Quality Engine Scheduler task, for example,
 * query a member node for new pids and request that a quality
 * report is created for each one.
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
                } catch (MetadigStoreException mse) {
                    JobExecutionException jee = new JobExecutionException("Error executing task.");
                    jee.initCause(mse);
                    throw jee;
                }
            }
        }
    }

    private void searchDir(File file, String dirIncludeMatch, String dirExcludeMatch, String fileIncludeMatch,
                                 String fileExcludeMatch) throws MetadigStoreException {

        List<String> inDirs = Arrays.asList(dirIncludeMatch.split(","));
        File[] fileList = file.listFiles();
        for (File thisFile : fileList) {
            if(thisFile.isDirectory() && inDirs.contains(file.getName())) {
                try {
                    searchDir(thisFile, dirIncludeMatch, dirExcludeMatch, fileIncludeMatch, fileExcludeMatch);
                } catch (MetadigStoreException mse) {
                    log.error("Error searching directory " + thisFile.getName() + ": " + mse.getMessage());
                    throw mse;
                }
            } else if (thisFile.isFile()) {
                log.info("Ingesting file: " + thisFile.getAbsolutePath());
                ingestFile(thisFile, thisFile);
            }
        }
    }

    private File ingestFile(File dir, File file) throws MetadigStoreException {

        //MetadigFile mf = MetadigFile(collectionId, metadataId, suiteId, nodeId, metadataFormatFilter, storageType, relativePath, createtionDate, fileExt);
        MetadigFileStore metadigFileStore = null;
        MetadigFile metadigFile = null;

        try {
            metadigFileStore = new MetadigFileStore();
            metadigFile = new MetadigFile();
        } catch (MetadigStoreException mse) {
            log.error("Unable to ingest file, cannot intialize MetadigFileStore: " + mse.getMessage());
            throw mse;
        }

        // The directory name should be the Metadig filestore storage type, i.e. "code" or "graph", etc
        //StorageType storageType = StorageType.getValue(dir.getName());
        //metadigFile.setStorageType(storageType.getStorageTyp());
        metadigFile.setStorageType(StorageType.CODE.toString());
        metadigFile.setAltFilename(file.getName());

        Boolean replace = true;
        try {
            String newFile = metadigFileStore.saveFile(metadigFile, file.getAbsolutePath(), replace);
            // Now that the file has been saved to the filestore, remove the file from the staging area
            file.delete();
        } catch (Exception e) {
            log.error("Unable to save file " + "\"" + file.getName() + "\" to filestore: " + e.getMessage());
            MetadigStoreException mse = new MetadigStoreException("Unable to save file " + "\"" + file.getName() + "\" to filestore");
            mse.initCause(e.getCause());
            throw mse;
        }

        return file;
    }
}
