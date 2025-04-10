package edu.ucsb.nceas.mdqengine.processor;

import com.fasterxml.jackson.databind.ObjectMapper;

import org.apache.commons.lang.BooleanUtils;
import org.apache.commons.lang.math.NumberUtils;
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

    /*
     * Retype an object based on a few simple assumptions. A "String" value is
     * typically passed in. If only numeric characters are present in the String,
     * then the object is caste to type "Number". If the string value appears to
     * be an "affirmative" or "negative" value (e.g. "Y", "Yes", "N", "No", ...)
     * then the value is caste to "Boolean".
     */
    public static Object retypeObject(Object value) {
        Object result = value;

        if (value instanceof String stringValue) {
            // try to type the value correctly
            if (NumberUtils.isNumber(stringValue) && !stringValue.matches("^0\\d*$")) {
                // If it's a valid number and doesn't start with zeros, create a Number object
                result = NumberUtils.createNumber(stringValue);
            } else {
                // try to convert to bool
                Boolean bool = BooleanUtils.toBooleanObject((String) value);
                // if it worked, return the boolean, otherwise the original result is returned
                if (bool != null) {
                    result = bool;
                }
            }

        }

        return result;
    }
}
