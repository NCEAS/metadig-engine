package edu.ucsb.nceas.mdqengine.scheduler;

import edu.ucsb.nceas.mdqengine.MDQconfig;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.dataone.client.v2.impl.MultipartCNode;
import org.dataone.client.v2.impl.MultipartMNode;
import org.dataone.hashstore.HashStore;
import org.dataone.hashstore.HashStoreFactory;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
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

import org.dataone.service.types.v1.Identifier;
import org.dataone.service.types.v1.Session;
import org.dataone.service.types.v2.SystemMetadata;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.MockedStatic;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
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
    private static final String hashStoreDepth = "3";
    private static final String hashStoreWidth = "2";
    private static final String hashStoreAlgorithm = "SHA-256";
    private static final String hashStoreSysmetaNamespace =
        "https://ns.dataone.org/service/types/v2.0#SystemMetadata";

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

        // Load test metadig.properties from helm/metadig-controller
        Path projectRoot = Paths.get("..").toAbsolutePath().normalize();
        Path projectRootWithMetadigEngine = projectRoot.resolve("metadig-engine");
        Path helmMetadigPropsFilePath = projectRootWithMetadigEngine.resolve(
            "helm/metadig-controller/config.dev/metadig.properties");
        Properties properties = new Properties();
        try (FileInputStream inputStream = new FileInputStream(helmMetadigPropsFilePath.toFile())) {
            properties.load(inputStream);
        }

        // Set hashstore 'store.' keys just in case they are missing
        properties.setProperty("store.store_path", hashStoreRootDirectory);
        properties.setProperty("storeDepth", hashStoreDepth);
        properties.setProperty("storeWidth", hashStoreWidth);
        properties.setProperty("storeAlgorithm", hashStoreAlgorithm);
        properties.setProperty("storeMetadataNamespace", hashStoreSysmetaNamespace);

        // Re-write the updated properties to the temp folder
        Path modifiedMetadigProperties = tempFolder.resolve("modified_metadig.properties");
        // Save the modified props with the revised 'store_path' to the specified tmp file location
        try (FileOutputStream outputStream = new FileOutputStream(
            modifiedMetadigProperties.toFile())) {
            properties.store(outputStream, "Store properties have been written");
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
        storeProperties.setProperty("storeDepth", hashStoreDepth);
        storeProperties.setProperty("storeWidth", hashStoreWidth);
        storeProperties.setProperty("storeAlgorithm", hashStoreAlgorithm);
        storeProperties.setProperty("storeMetadataNamespace", hashStoreSysmetaNamespace);
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
     * Confirm that a http post request is executed when 'submitReportRequest' is called for a
     * pid with a data object and sysmeta that is available in hashstore
     */
    @Test
    public void testSubmitReportRequest_pidFoundInHashStore() throws Exception {
        MultipartCNode cnNode = mock(MultipartCNode.class);
        MultipartMNode mnNode = mock(MultipartMNode.class);
        boolean isCN = false;
        Session session = mock(Session.class);
        String qualityServiceUrl = "http://metadig-controller.metadig.svc:8080/quality";
        String pidStr = testPid;
        String suiteId = "mockSuite";

        // Mock HTTP client and response
        CloseableHttpClient mockHttpClient = mock(CloseableHttpClient.class);
        CloseableHttpResponse mockResponse = mock(CloseableHttpResponse.class);

        // Ensure execute() returns a mock response
        when(mockHttpClient.execute(any(HttpPost.class))).thenReturn(mockResponse);

        // When 'submitReportRequest' is called, it uses the mocked client that we can keep track of
        try (MockedStatic<HttpClients> mockedHttpClients = mockStatic(HttpClients.class)) {
            mockedHttpClients.when(HttpClients::createDefault).thenReturn(mockHttpClient);

            RequestReportJob job = new RequestReportJob();
            job.submitReportRequest(cnNode, mnNode, isCN, session, qualityServiceUrl, pidStr, suiteId);

            // Verify that the HTTP POST request was executed
            verify(mockHttpClient, times(1)).execute(any(HttpPost.class));
        }
    }

    /**
     * Confirm that a sysmeta object is returned. No exception should be thrown.
     */
    @Test
    public void testGetSystemMetadataFromHashStore() throws Exception {
        RequestReportJob job = new RequestReportJob();
        Identifier pid = new Identifier();
        pid.setValue(testPid);

        SystemMetadata sysmeta =
            job.getSystemMetadataFromHashStore(pid, hashStore);
        assertInstanceOf(SystemMetadata.class, sysmeta, "This should be a sysmeta object");
    }

    /**
     * Confirm that an IOException is thrown when a sysmeta is not available
     */
    @Test
    public void testGetSystemMetadataFromHashStore_metadataNotFound() throws Exception {
        RequestReportJob job = new RequestReportJob();
        Identifier pid = new Identifier();
        pid.setValue("pid.not.found");

        assertThrows(
            IOException.class,
            () -> job.getSystemMetadataFromHashStore(pid, hashStore));

    }

    /**
     * Confirm that an input stream to a data object is returned. No exception should be thrown.
     */
    @Test
    public void testGetObjectFromHashStore() throws Exception {
        RequestReportJob job = new RequestReportJob();
        Identifier pid = new Identifier();
        pid.setValue(testPid);

        InputStream objectIS =
            job.getObjectFromHashStore(pid, hashStore);
        assertInstanceOf(InputStream.class, objectIS, "This should be an InputStream");
    }

    /**
     * Confirm that a FileNotFoundException is thrown when supplied with a pid that has no
     * data object.
     */
    @Test
    public void testGetObjectFromHashStore_objectNotFound() {
        RequestReportJob job = new RequestReportJob();
        Identifier pid = new Identifier();
        pid.setValue("file.wont.be.found");

        assertThrows(
            FileNotFoundException.class,
            () -> job.getObjectFromHashStore(pid, hashStore));
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
        RequestReportJob job = new RequestReportJob();
        HashStore retrievedHashStore = job.getHashStoreFromMetadigProps();
        assertInstanceOf(HashStore.class, retrievedHashStore, "This should be an InputStream");
        assertNotNull(retrievedHashStore, "The object should not be null");

    }

    /**
     * Check that 'getHashStorePropsFromMetadigProps' returns null for an IOException thrown
     */
    @Test
    public void testGetHashStorePropsFromMetadigProps_IOException() throws Exception {
        RequestReportJob job = new RequestReportJob();

        try (MockedStatic<HashStoreFactory> mockedStatic = mockStatic(HashStoreFactory.class)) {
            mockedStatic
                .when(() -> HashStoreFactory.getHashStore(anyString(), any(Properties.class)))
                .thenThrow(new IOException("Mocked IOException"));

            HashStore hashstore = job.getHashStoreFromMetadigProps();

            assertNull(
                hashstore,
                "HashStore should return as null when any exception is thrown ");
        }
    }

    /**
     * Check that 'getHashStorePropsFromMetadigProps' returns null for an IllegalArgumentException
     * thrown
     */
    @Test
    public void testGetHashStorePropsFromMetadigProps_IllegalArgumentException() throws Exception {
        RequestReportJob job = new RequestReportJob();

        try (MockedStatic<HashStoreFactory> mockedStatic = mockStatic(HashStoreFactory.class)) {
            mockedStatic
                .when(() -> HashStoreFactory.getHashStore(anyString(), any(Properties.class)))
                .thenThrow(new IllegalArgumentException("Mocked IOException"));

            HashStore hashstore = job.getHashStoreFromMetadigProps();

            assertNull(
                hashstore,
                "HashStore should return as null when any exception is thrown ");
        }
    }

    /**
     * Check that we are able to retrieve store properties from a metadig.properties file
     */
    @Test
    public void testGetStorePropsFromMetadigProps() {
        RequestReportJob job = new RequestReportJob();
        Map<String, Object> storeConfig = job.getStorePropsFromMetadigProps();

        String storePath = (String) storeConfig.get("store_path");
        String storeDepth = (String) storeConfig.get("store_depth");
        String storeWidth = (String) storeConfig.get("store_width");
        String storeAlgo = (String) storeConfig.get("store_algorithm");
        String sysmetaNamespace = (String) storeConfig.get("store_metadata_namespace");

        assertEquals(hashStoreRootDirectory, storePath);
        assertEquals(hashStoreDepth, storeDepth);
        assertEquals(hashStoreWidth, storeWidth);
        assertEquals(hashStoreAlgorithm, storeAlgo);
        assertEquals(hashStoreSysmetaNamespace, sysmetaNamespace);
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
