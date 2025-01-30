package edu.ucsb.nceas.mdqengine.scheduler;

import edu.ucsb.nceas.mdqengine.MDQconfig;
import org.dataone.hashstore.HashStore;
import org.dataone.hashstore.HashStoreFactory;

import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.nio.file.Path;
import java.util.Iterator;
import java.util.Map;
import java.lang.reflect.Field;
import java.net.URL;
import java.util.Properties;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * Test class for RequestReportJob
 */
public class RequestReportJobTest {

    /**
     * Temporary folder for tests to run in
     */
    @TempDir
    public Path tempFolder;

    /**
     * Create a HashStore in the given path and store a data object and sysmeta object
     */
    public void createHashStoreAndTestObjects(String storePath) {
        Properties storeProperties = new Properties();
        storeProperties.setProperty("storePath", storePath);
        storeProperties.setProperty("storeDepth", "3");
        storeProperties.setProperty("storeWidth", "2");
        storeProperties.setProperty("storeAlgorithm", "SHA-256");
        storeProperties.setProperty(
            "storeMetadataNamespace", "https://ns.dataone.org/service/types/v2.0#SystemMetadata");
        String hashstoreClassName = "org.dataone.hashstore.filehashstore.FileHashStore";

        try {
            HashStore hashStore = HashStoreFactory.getHashStore(hashstoreClassName,
                                                                storeProperties);
            // TODO: Store data object
            // TODO: Store sysmeta object

        } catch (Exception e) {
            e.printStackTrace();
            fail("Unexpected exception encountered: " + e.getMessage());

        }
    }

    /**
     * Check that we get a hashstore successfully, no exceptions should be thrown
     */
    @Test
    public void testGetHashStoreFromMetadigProps() throws Exception {
        // TODO: Refactor this junit test when adding remaining hashstore checks to be efficient
        // Create a hashstore in a tmp folder
        Path root = tempFolder;
        String hashStoreRootDirectory = root.resolve("hashstore").toString();
        createHashStoreAndTestObjects(hashStoreRootDirectory);

        // Load metadig.properties from test-docs
        URL resourceUrl = this.getClass().getResource("/test-docs/metadig.properties");
        String fullPathToMetadigProps = resourceUrl.getPath();
        Properties properties = new Properties();
        try (FileInputStream inputStream = new FileInputStream(fullPathToMetadigProps)) {
            properties.load(inputStream);
        }

        // Modify the key to be the temp hashstore folder
        properties.setProperty("store.store_path", hashStoreRootDirectory);

        // Re-write the updated properties to the temp folder
        Path modifiedMetadigProperties = tempFolder.resolve("modified_metadig.properties");

        // Save the modified properties to the specified temp folder
        try (FileOutputStream outputStream = new FileOutputStream(
            modifiedMetadigProperties.toFile())) {
            properties.store(outputStream, "store_path has been changed to tmp folder");
        }

        // Now override MDQconfig, so it pulls properties from the modified metadig properties
        // found in the temp folder, which contains a revised store_path
        Field field = MDQconfig.class.getDeclaredField("configFilePath");
        field.setAccessible(true);
        field.set(null, modifiedMetadigProperties.toString());

        // Confirm that the 'store_path' key has been modified
        MDQconfig cfg = new MDQconfig();
        Iterator<String> keys = cfg.getKeys();
        while (keys.hasNext()) {
            String key = keys.next();
            if (key.equals("store.store_path")) {
                String value = cfg.getString(key);
                assertEquals(value, hashStoreRootDirectory);
            }
        }

        // Confirm a HashStore was retrieved successfully
        HashStore retrievedHashStore = RequestReportJob.getHashStoreFromMetadigProps();
        assertNotNull(retrievedHashStore, "The object should not be null");

    }

    /**
     * Check that we are able to retrieve store properties from a metadig.properties file
     */
    @Test
    public void testGetStorePropsFromMetadigProps() throws Exception {
        // Override the static field in MDQconfig that looks for metadig.properties in
        // '/opt/local/metadig/metadig.properties' and instead look for the properties file in
        // the test folder resources 'test-docs'
        Field field = MDQconfig.class.getDeclaredField("configFilePath");
        field.setAccessible(true);
        // Retrieve the absolute path to the metadig.properties in 'test-docs'
        URL resourceUrl = this.getClass().getResource("/test-docs/metadig.properties");
        String fullPathToMetadigProps = resourceUrl.getPath();
        // Override MDQconfig class' private static variable
        field.set(null, fullPathToMetadigProps);

        Map<String, Object> storeConfig = RequestReportJob.getStorePropsFromMetadigProps();

        String storePath = (String) storeConfig.get("store_path");
        String storeDepth = (String) storeConfig.get("store_depth");
        String storeWidth = (String) storeConfig.get("store_width");
        String storeAlgo = (String) storeConfig.get("store_algorithm");
        String sysmetaNamespace = (String) storeConfig.get("store_metadata_namespace");

        assertEquals("/junit/test/hashstore", storePath);
        assertEquals("3", storeDepth);
        assertEquals("2", storeWidth);
        assertEquals("SHA-256", storeAlgo);
        assertEquals("https://ns.dataone.org/service/types/v2.0#SystemMetadata", sysmetaNamespace);
    }
}
