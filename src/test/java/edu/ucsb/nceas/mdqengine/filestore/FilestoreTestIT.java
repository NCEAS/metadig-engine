package edu.ucsb.nceas.mdqengine.filestore;

import edu.ucsb.nceas.mdqengine.exception.MetadigException;
import edu.ucsb.nceas.mdqengine.exception.MetadigFilestoreException;
import edu.ucsb.nceas.mdqengine.store.MDQStore;
import org.apache.commons.configuration2.ex.ConfigurationException;
import org.joda.time.DateTime;
import org.junit.BeforeClass;
import org.junit.Test;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

import static junit.framework.TestCase.assertTrue;
import static org.junit.Assert.assertEquals;

/**
 * Verify that the MetaDIG filestore works correctly, by saving, fetching and deleting a file from it.
 * <p>
 *     This test uses a fully configured MetaDIG PostgreSQL database, configuration file and designated
 *     directory for the filestore, so is therefor configured as an integration test.
 * </p>
 */
public class FilestoreTestIT {

    private static MDQStore store = null;
    private static Path tempDir;

    @BeforeClass
    public static void initStore() throws MetadigException, IOException, ConfigurationException {

        // use in-memory impl for now
        String prefix = null;
        tempDir = (Path) Files.createTempDirectory(prefix);
    }

    @Test
    public void saveFile() throws IOException, MetadigFilestoreException {

        MetadigFile mdFile = new MetadigFile();
        mdFile.setCreationDatetime(DateTime.now());
        mdFile.setPid("1234");
        mdFile.setSuiteId("FAIR.suite.1");
        mdFile.setNodeId("urn:node:KNB");
        mdFile.setStorageType(StorageType.TMP.toString());
        mdFile.setMediaType("image/jpeg");

        MetadigFileStore mfs;
        try {
            mfs = new MetadigFileStore();
        } catch (MetadigFilestoreException mse) {
            System.out.println("Error creating filestore: " + mse.getMessage());
            throw mse;
        }

        Boolean replace = true;
        String newPath = null;

        // Save a file entry
        try {
            File file = new File ( getClass().getClassLoader().getResource("data/sample-FAIR.suite.1.png").getFile());
            newPath = mfs.saveFile(mdFile, file.getAbsolutePath(), replace);
            System.out.println("Saved file to filestore: " + newPath);
        } catch (IOException ioe) {
            System.out.println("Error creating filestore: " + ioe.getMessage());
            throw ioe;
        }

        // Get a file entry
        File newFile = mfs.getFile(mdFile);
        if(newFile.exists()) {
            System.out.println("Retrived file from filestore: " + newFile.getAbsolutePath());
        } else {
            System.out.println("Unable to retrieve file from filestore: " + mfs.getFilePath(mdFile));
        }

        // Verify that the entry was saved to disk
        File f = new File(newPath);
        if(f.exists() && !f.isDirectory()) {
            System.out.println("Verified that a file was saved to the filestore: " + newPath);
        } else {
            System.out.println("Unable to save file to filestore: " + newPath);
        }

        // Delete the file entry
        boolean success = mfs.deleteFile(mdFile);
        assertTrue( "A file can be deleted from the MetaDIG filestore", success);
        System.out.println("Removed file from filestore: " + newPath);

        if(f.exists() && !f.isDirectory()) {
            System.out.println("The saved file still exists in the filestore: " + newPath);
        } else {
            System.out.println("Verified that the saved file has been removed from the filestore: " + newPath);
        }

        //
        // Save a file that we gian ve a specific filename to
        mdFile = new MetadigFile();
        mdFile.setCreationDatetime(DateTime.now());
        mdFile.setStorageType(StorageType.TMP.toString());
        mdFile.setMediaType("image/jpeg");
        mdFile.setAltFilename("sample-FAIR.suite.1.png");

        try {
            File file = new File ( getClass().getClassLoader().getResource("data/sample-FAIR.suite.1.png").getFile());
            newPath = mfs.saveFile(mdFile, file.getAbsolutePath(), replace);
            System.out.println("Saved file to filestore: " + newPath);
        } catch (IOException ioe) {
            System.out.println("Error creating filestore: " + ioe.getMessage());
            throw ioe;
        }

        // Get the file entry we just saved
        newFile = mfs.getFile(mdFile);
        if(newFile.exists()) {
            System.out.println("Retrived file from filestore: " + newFile.getAbsolutePath());
        } else {
            System.out.println("Unable to retrieve file from filestore: " + mfs.getFilePath(mdFile));
        }

        // Next delete the file
        success = mfs.deleteFile(mdFile);
        assertTrue( "A file can be deleted from the MetaDIG filestore", success);
        System.out.println("Removed file from filestore: " + newPath);

        // Verify that the file was deleted
        f = new File(newPath);
        if(f.exists() && !f.isDirectory()) {
            System.out.println("The saved file still exists in the filestore: " + newPath);
        } else {
            System.out.println("Verified that the saved file has been removed from the filestore: " + newPath);
        }

        //assertEquals(rec.getName(), r.getName());
    }

    public void getFile() {
        //assertEquals(rec.getName(), r.getName());
    }
}
