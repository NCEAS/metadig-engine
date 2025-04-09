package edu.ucsb.nceas.mdqengine.processor;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import java.io.InputStream;

import java.io.IOException;

public class ProcessorUtils {
    public static String detectContentType(InputStream metadataContent) {
        if (isValidXML(metadataContent)) {
            return "xml";
        } else if (isValidJSON(metadataContent)) {
            return "json";
        }
        throw new IllegalArgumentException("Unknown content format.");
    }

    private static boolean isValidXML(InputStream content) {
        try {
            DocumentBuilder builder = DocumentBuilderFactory.newInstance().newDocumentBuilder();
            builder.parse(new InputSource(content));
            return true;
        } catch (SAXException | ParserConfigurationException | IOException e) {
            return false;
        }
    }

    private static boolean isValidJSON(InputStream content) {
        ObjectMapper mapper = new ObjectMapper();
        try {
            mapper.readTree(content);
            return true;
        } catch (IOException e) {
            return false;
        }
    }
}
