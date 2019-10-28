package edu.ucsb.nceas.mdqengine.filestore;

import edu.ucsb.nceas.mdqengine.MDQconfig;
import edu.ucsb.nceas.mdqengine.exception.MetadigStoreException;
import org.apache.commons.configuration2.ex.ConfigurationException;
import org.apache.commons.io.FileUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;

public class MetadigFileStore {


    protected Log log = LogFactory.getLog(this.getClass());
    private String file;
    private String filestoreBase = null;

    public MetadigFileStore() throws MetadigStoreException {
        this.init();
    }

    /*
     * Get a connection to the database that contains the quality reports.
     */
    private void init() throws MetadigStoreException {

        try {
            MDQconfig cfg = new MDQconfig();
            this.filestoreBase = cfg.getString("metadig.store.directory");
        } catch (ConfigurationException | IOException ex) {
            log.error(ex.getMessage());
            MetadigStoreException mse = new MetadigStoreException("Unable to create new Store");
            mse.initCause(ex.getCause());
            throw mse;
        }

        log.debug("Filestore initialized, top directory: " + this.filestoreBase);

        return;
    }

    public File getFile(MetadigFile mdFile) throws MetadigStoreException {
        String path = null;

        // First query the database to find a match based on the data in the MetadigFile entry. In this version of
        // the filestore, only one file should match.
        MetadigFile resultFile = null;
        FilestoreDB fsdb = new FilestoreDB();

        try {
            resultFile = fsdb.getFileEntry(mdFile);
        } catch (MetadigStoreException mse) {
            log.error("Unable to get file: " + mse.getMessage());
            throw mse;
        }

        //path = this.getFilePath(resultFile);
        path = this.filestoreBase + "/" + mdFile.getRelativePath();

        File storeFile = new File(path);

        if (!storeFile.exists()) {
            MetadigStoreException metadigStoreException = new MetadigStoreException("File " + path + " doesn't exist");
            throw metadigStoreException;
        }

        if (!storeFile.canRead()) {
            MetadigStoreException metadigStoreException = new MetadigStoreException("File " + path + " is not readable");
            throw metadigStoreException;
        }

        fsdb.shutdown();
        return storeFile;
    }

    /*
     * Copy the input file to the specified filename in the MetaDIG filestore
     */
    public String saveFile(MetadigFile mdFile, String inputFile, Boolean replace) throws IOException, MetadigStoreException {
        String path = null;

        File infile = new File(inputFile);
        FileInputStream fis = new FileInputStream(infile);
        path = saveFile(mdFile, fis, replace);
        fis.close();

        return path;
    }

    /*
     * Write the input stream to a file in the MetaDIG filestore using filename. The
     * absolute path will be "/filestore base/storage type/filename", for example
     * "/data/metadig/store/graph/testproj-urn:node:mnTestKNB-FAIR.suite.1.jpg"
     */
    public String saveFile(MetadigFile mdFile, FileInputStream fis, Boolean replace) throws MetadigStoreException {
        String path = null;
        FilestoreDB fsdb;

        // First we have to insert this file into the database, as this is the only way to
        // check if a file with the same properties exists. If the file already exists, then
        // we need to obtain the path in the filestore (including the fileId), update the
        // creation time in the db and write the replacement file out to disk.

        try {
            fsdb = new FilestoreDB();
        } catch (MetadigStoreException mse) {
            log.error("Unable to connect to filestore database");
            throw (mse);
        }

        Boolean duplicate = false;
        try {
            fsdb.saveFileEntry(mdFile);
        } catch (MetadigStoreException mse) {
            duplicate = true;
        }

        MetadigFile existingFile;
        // If the file is a duplicate, we have to get the file_id to replace it.
        if (duplicate) {
            existingFile = fsdb.getFileEntry(mdFile);
            path = filestoreBase + "/" + existingFile.getRelativePath();
        } else {
            path = filestoreBase + "/" + mdFile.getRelativePath();
        }

        // Copy the input file to the path inside the filestore.
        // copyInputStrreamToFile automatically create any needed directories!
        File targetFile = new File(path);
        try {
            FileUtils.copyInputStreamToFile(fis, targetFile);
            log.debug("Wrote file to path: " + path);
        } catch (IOException ioe) {
            log.error("Error writing to path: " + path);
        }

        fsdb.shutdown();
        //TODO: make sure file is readable
        return path;
    }

    public boolean deleteFile(MetadigFile mdFile) throws MetadigStoreException {

        String path = null;
        FilestoreDB fsdb;

        try {
            fsdb = new FilestoreDB();
            fsdb.deleteFileEntry(mdFile);
        } catch (MetadigStoreException mse) {
            log.error("Unable to connect to filestore database");
            throw (mse);
        }

        File fileToDelete = FileUtils.getFile(getFilePath(mdFile));
        boolean success = FileUtils.deleteQuietly(fileToDelete);
        return success;
    }

    public String getFilePath(MetadigFile mdFile) {
        return this.filestoreBase + "/" + mdFile.getRelativePath();
    }
}