package edu.ucsb.nceas.mdqengine.scheduler;

import edu.ucsb.nceas.mdqengine.MDQconfig;

import java.util.Map;
import java.lang.reflect.Field;
import java.net.URL;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Test class for RequestReportJob
 */
public class RequestReportJobTest {

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
