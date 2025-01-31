package edu.ucsb.nceas.mdqengine.scheduler;

import edu.ucsb.nceas.mdqengine.MDQconfig;
import org.dataone.client.v2.impl.MultipartCNode;
import org.dataone.client.v2.impl.MultipartMNode;
import org.dataone.hashstore.HashStore;
import org.dataone.hashstore.HashStoreFactory;

import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Iterator;
import java.util.Map;
import java.lang.reflect.Field;
import java.util.Properties;

import org.dataone.service.exceptions.NotAuthorized;
import org.dataone.service.types.v1.Identifier;
import org.dataone.service.types.v1.Session;
import org.dataone.service.types.v2.SystemMetadata;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.fail;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Test class for RequestReportJob
 */
public class RequestReportJobTest {

    /**
     * Temporary folder for tests to run in
     */
    @TempDir
    public static Path tempFolder;

    private static String hashStoreRootDirectory;

    private static HashStore hashStore;
    private static final String testPid = "dou.test.eml.1";

    /**
     * Create a HashStore before all junit tests run inside a temp folder with a data object and
     * sysmeta object to work with.
     */
    @BeforeAll
    public static void prepareJunitHashStore()
        throws IOException, NoSuchFieldException, IllegalAccessException {
        // Create a hashstore in a tmp folder
        hashStoreRootDirectory = tempFolder.resolve("hashstore").toString();
        createHashStoreAndTestObjects(hashStoreRootDirectory);

        // Load metadig.properties from test-docs
        String fullPathToMetadigProps = getPathToDocInResources("metadig.properties");

        Properties properties = new Properties();
        try (
            FileInputStream inputStream = new FileInputStream(fullPathToMetadigProps)) {
            properties.load(inputStream);
        }

        // Modify the key to be the temp hashstore folder
        properties.setProperty("store.store_path", hashStoreRootDirectory);

        // Re-write the updated properties to the temp folder
        Path modifiedMetadigProperties = tempFolder.resolve("modified_metadig.properties");
        // Save the modified props with the revised 'store_path' to the specified tmp file location
        try (FileOutputStream outputStream = new FileOutputStream(
            modifiedMetadigProperties.toFile())) {
            properties.store(outputStream, "store_path has been changed to tmp folder");
        }
        overrideConfigFilePathInMDQConfig(modifiedMetadigProperties.toString());
    }

    /**
     * Get the path in resources/test-docs/ for the given fileName
     */
    public static String getPathToDocInResources(String fileName) {
        String docToRetrieve = "test-docs/" + fileName;
        ClassLoader classLoader = RequestReportJobTest.class.getClassLoader();
        URL resourceUrlToMetadigProps = classLoader.getResource(docToRetrieve);
        if (resourceUrlToMetadigProps == null) {
            fail("Unable to retrieve file from:" + docToRetrieve);
        }

        return resourceUrlToMetadigProps.getPath();
    }

    /**
     * Create a HashStore in the given path and store a data object and sysmeta object
     */
    public static void createHashStoreAndTestObjects(String storePath) {
        Properties storeProperties = new Properties();
        storeProperties.setProperty("storePath", storePath);
        storeProperties.setProperty("storeDepth", "3");
        storeProperties.setProperty("storeWidth", "2");
        storeProperties.setProperty("storeAlgorithm", "SHA-256");
        storeProperties.setProperty(
            "storeMetadataNamespace", "https://ns.dataone.org/service/types/v2.0#SystemMetadata");
        String hashstoreClassName = "org.dataone.hashstore.filehashstore.FileHashStore";

        try {
            hashStore = HashStoreFactory.getHashStore(hashstoreClassName, storeProperties);

            // Store data object
            String pathToEmlDoc = getPathToDocInResources("eml.1.1.xml");
            try (InputStream dataStream = Files.newInputStream(Paths.get(pathToEmlDoc))) {
                hashStore.storeObject(dataStream, testPid, null, null, null, -1);
            }

            // Store sysmeta object
            String pathToSysmeta = getPathToDocInResources("eml.1.1.sysMeta.xml");
            try (InputStream metadataStream = Files.newInputStream(Paths.get(pathToSysmeta))) {
                hashStore.storeMetadata(metadataStream, testPid);
            }

        } catch (Exception e) {
            e.printStackTrace();
            fail("Unexpected exception encountered: " + e.getMessage());

        }
    }

    /**
     * Override the static field in MDQconfig that looks for metadig.properties in
     * '/opt/local/metadig/metadig.properties' and instead look for the properties file in the
     * 'src/test/resources/test-docs' folder.
     *
     * The MCQconfig config file path defaults to a private static variable if it is not running in
     * a servlet where we'd need to get the info from the webapp context.
     */
    public static void overrideConfigFilePathInMDQConfig(String fullPathToMetadigProps)
        throws NoSuchFieldException, IllegalAccessException {
        Field field = MDQconfig.class.getDeclaredField("configFilePath");
        field.setAccessible(true);
        field.set(null, fullPathToMetadigProps);
    }

    // Junit Tests

    /**
     * Confirm that a sysmeta object is returned. No exception should be thrown.
     */
    @Test
    public void testGetSystemMetadataFromHashStoreOrNode() throws Exception {
        RequestReportJob job = new RequestReportJob();
        MultipartCNode cnNode = null;
        MultipartMNode mnNode = null;
        Session session = null;
        Identifier pid = new Identifier();
        pid.setValue(testPid);

        SystemMetadata sysmeta =
            job.getSystemMetadataFromHashStoreOrNode(pid, hashStore, cnNode, mnNode, false,
                                                     session);
        assertInstanceOf(SystemMetadata.class, sysmeta, "This should be a sysmeta object");
    }

    /**
     * Confirm that when a NotAuthorized is thrown after calling the MN API, that this test method
     * does not bubble up the exception and simply returns
     */
    @Test
    public void testGetSystemMetadataFromHashStoreOrNode_NotAuthorized() throws Exception {
        RequestReportJob job = new RequestReportJob();
        MultipartCNode cnNode = null;
        // Mock MN
        MultipartMNode mnNode = mock(MultipartMNode.class);
        Session session = mock(Session.class);
        Identifier pid = new Identifier();
        pid.setValue("pid.not.found");

        when(mnNode.getSystemMetadata(session, pid)).thenThrow(
            new NotAuthorized("8000", "User is not authorized"));

        job.getSystemMetadataFromHashStoreOrNode(pid, hashStore, cnNode, mnNode, false, session);
    }

    /**
     * Confirm that an input stream to a data object is returned. No exception should be thrown.
     */
    @Test
    public void testGetEMLMetadataDocInputStream() throws Exception {
        RequestReportJob job = new RequestReportJob();
        MultipartCNode cnNode = null;
        MultipartMNode mnNode = null;
        Session session = null;
        Identifier pid = new Identifier();
        pid.setValue(testPid);

        InputStream objectIS =
            job.getEMLMetadataDocInputStream(pid, hashStore, cnNode, mnNode, false, session);
        assertInstanceOf(InputStream.class, objectIS, "This should be an InputStream");
    }

    /**
     * Check that we get a hashstore successfully, no exceptions should be thrown.
     */
    @Test
    public void testGetHashStoreFromMetadigProps() throws Exception {
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
    public void testGetStorePropsFromMetadigProps() {
        Map<String, Object> storeConfig = RequestReportJob.getStorePropsFromMetadigProps();

        String storePath = (String) storeConfig.get("store_path");
        String storeDepth = (String) storeConfig.get("store_depth");
        String storeWidth = (String) storeConfig.get("store_width");
        String storeAlgo = (String) storeConfig.get("store_algorithm");
        String sysmetaNamespace = (String) storeConfig.get("store_metadata_namespace");

        assertEquals(hashStoreRootDirectory, storePath);
        assertEquals("3", storeDepth);
        assertEquals("2", storeWidth);
        assertEquals("SHA-256", storeAlgo);
        assertEquals("https://ns.dataone.org/service/types/v2.0#SystemMetadata", sysmetaNamespace);
    }

    /**
     * Revert the 'configFilePath' private static variable to be what it was to be safe
     */
    @AfterAll
    static void tearDown() throws Exception {
        Field field = MDQconfig.class.getDeclaredField("configFilePath");
        field.setAccessible(true);
        field.set(null, "/opt/local/metadig/metadig.properties");
    }
}
