package edu.ucsb.nceas.mdqengine.processor;

import java.io.IOException;
import java.io.InputStream;

import javax.xml.parsers.ParserConfigurationException;

import org.dataone.service.types.v2.SystemMetadata;
import org.xml.sax.SAXException;

/**
 * A factory class for creating instances of {@link MetadataDialect} based on
 * content type.
 *
 * Supports creation of dialects for both XML and JSON formats.
 */
public class MetadataDialectFactory {
    /**
     * Creates a MetadataDialect implementation based on the provided content type.
     *
     * @param contentType     the format identifier of the metadata (e.g., "xml" or
     *                        "json")
     * @param metadataContent an InputStream of the metadata content to be parsed
     * @return The appropriate concrete implementation of the MetadataDialect
     *         according to content type.
     * @throws IllegalArgumentException
     * @throws SAXException
     * @throws IOException
     * @throws ParserConfigurationException
     */
    public static MetadataDialect createDialect(SystemMetadata sysMeta, InputStream metadataContent)
            throws IllegalArgumentException, SAXException, IOException, ParserConfigurationException {

        // detect either json or xml, default xml
        String formatId = sysMeta.getFormatId().getValue();

        if (formatId.contains("json")) {
            return new JSONDialect(metadataContent);
        } else {
            return new XMLDialect(metadataContent);
        }

    }

}
