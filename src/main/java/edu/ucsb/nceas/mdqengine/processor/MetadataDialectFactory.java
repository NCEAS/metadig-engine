package edu.ucsb.nceas.mdqengine.processor;

import java.io.IOException;
import java.io.InputStream;

import javax.xml.parsers.ParserConfigurationException;

import org.xml.sax.SAXException;

public class MetadataDialectFactory {
    public static MetadataDialect createDialect(String contentType, InputStream metadataContent)
            throws IllegalArgumentException, SAXException, IOException, ParserConfigurationException {
        switch (contentType.toLowerCase()) {
            case "xml":
                return new XMLDialect(metadataContent);
            case "json":
                return new JSONDialect(metadataContent);
            default:
                throw new IllegalArgumentException("Unsupported content type: " + contentType);
        }
    }

}
