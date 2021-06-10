package edu.ucsb.nceas.mdqengine;

import com.jayway.jsonpath.Configuration;
import com.jayway.jsonpath.DocumentContext;
import com.jayway.jsonpath.JsonPath;
import com.jayway.jsonpath.JsonPath;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.util.List;

public class JsonPathTest {

    public static void main(String[] argv) {
        File jsonFile = new File("/Users/slaughter/git/NCEAS/metadig-engine-assess-json/test/schema.org/SOSO-v1.2.0-full.jsonld");
        DocumentContext jsonContext = null;
        try {
            jsonContext = JsonPath.parse(jsonFile);
        } catch (IOException e) {
            e.printStackTrace();
        }
        //String path = "$['context']['vocab'] = 'https://schema.org/')]";
        // This works if the '@' are removed from the keys in the document
        //String path = "$['context']['vocab']";
        // This works, and "https://schema.org/" is returned as a string
        //String path = "$['@context']['@vocab']";
        // Error: net.minidev.json.JSONArray cannot be cast to java.lang.String
        //String path = "$['@context'][?(['@vocab'] == 'https://schema.org/')]";
        String path = "$['@context'][?(['@vocab'] == 'https://schema.org/')]";
        // Error: Invalid syntax at character 23
        //String path = "$['@context']['@vocab'] == \"https://schema.org/\"";
        //String value = jsonContext.read(path);
        List<String> values = jsonContext.read(path);
        System.out.println("# values: " + values.size());
        System.out.println("value: " + values.get(0));
    }
}
