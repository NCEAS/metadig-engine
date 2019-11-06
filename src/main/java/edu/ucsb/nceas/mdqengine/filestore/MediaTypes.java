package edu.ucsb.nceas.mdqengine.filestore;

import java.io.InputStream;
import java.io.Reader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.File;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVRecord;
import java.util.HashMap;

public class MediaTypes {

    private HashMap<String, String> mediaTypes = new HashMap<>();
    private Log log = LogFactory.getLog(MetadigFile.class);

    public MediaTypes () throws IOException {

        InputStream mediaTypesStream = this.getResourceFile("data/MediaTypes.csv");
        Reader mediaTypeReader = new InputStreamReader(mediaTypesStream);
        Iterable<CSVRecord> records = null;

        try {
            records = (Iterable<CSVRecord>) CSVFormat.RFC4180.withFirstRecordAsHeader().withQuote('"').withCommentMarker('#').parse(mediaTypeReader);
        } catch (java.io.IOException ioe) {
            log.error("Error reading MediaTypes.csv" + ioe.getMessage());
            throw ioe;
        }

        String mediaTypeName = null;
        String fileExt = null;

        for (CSVRecord record : records) {
            mediaTypeName = record.get("name").trim();
            fileExt = record.get("extension").trim();
            mediaTypes.put(mediaTypeName, fileExt);
        }
    }

    public String getFileExtension(String mediaTypeName) {
        String fileExt = null;

        if(mediaTypes.containsKey(mediaTypeName)) {
            fileExt = mediaTypes.get(mediaTypeName);
        } else {
            log.error("Unable to find media type " + "\"" + mediaTypeName + "\\" + " in list created from MediaTypes.csv");
        }

        return fileExt;
    }

    /**
     * Read a file from a Java resources folder.
     *
     * @param fileName the relative path of the file to read.
     * @return THe resources file as a stream.
     */
    private InputStream getResourceFile(String fileName) {

        //Get file from resources folder
        ClassLoader classLoader = getClass().getClassLoader();
        File file = new File(classLoader.getResource(fileName).getFile());
        log.info(file.getName());
        InputStream is = classLoader.getResourceAsStream(fileName);
        return is;
    }
}
