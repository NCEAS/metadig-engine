package edu.ucsb.nceas.mdqengine.scheduler;

import edu.ucsb.nceas.mdqengine.MDQconfig;
import edu.ucsb.nceas.mdqengine.exception.MetadigFilestoreException;
import edu.ucsb.nceas.mdqengine.exception.MetadigStoreException;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVRecord;
import org.apache.commons.configuration2.ex.ConfigurationException;
import org.apache.commons.io.FilenameUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.quartz.*;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.channels.Channels;
import java.nio.channels.ReadableByteChannel;

import java.io.IOException;
import java.util.Arrays;

/**
 * <p>
 * Run a MetaDIG Quality Engine Scheduler task.
 * This task will read from the "download list file" and attempt
 * to download web resources to the metadig local file system.
 * Typically, these are data files that provide information to
 * metadata assessment checks. The location of the downloads file
 * is specified in the metadig.properties file property "downloadList".
 * </p>
 *
 * @author Peter Slaughter
 */
@PersistJobDataAfterExecution
@DisallowConcurrentExecution
public class AcquireWebResourcesJob implements Job {

    private Log log = LogFactory.getLog(AcquireWebResourcesJob.class);

    // Since Quartz will re-instantiate a class every time it
    // gets executed, members non-static member variables can
    // not be used to maintain state!

    /**
     * <p>
     * Empty constructor for job initialization
     * </p>
     * <p>
     * Quartz requires a public empty constructor so that the
     * scheduler can instantiate the class whenever it needs.
     * </p>
     */
    public AcquireWebResourcesJob() {
    }

    /**
     * <p>
     * Called by the <code>{@link org.quartz.Scheduler}</code> when a
     * <code>{@link org.quartz.Trigger}</code> fires that is associated with
     * the <code>Job</code>.
     * </p>
     *
     * @throws JobExecutionException if there is an exception while executing the
     *                               job.
     */
    public void execute(JobExecutionContext context) throws JobExecutionException {

        String downloadListFilepath = null;

        try {
            MDQconfig cfg = new MDQconfig();
            downloadListFilepath = cfg.getString("downloadsList");
            log.debug("downloadListFilepath: " + downloadListFilepath);
            if (downloadListFilepath == null) {
                String errMsg = "Value retrieved for 'downloadsList' file path from config "
                        + "(properties) is null. Please check 'metadig.properties'.";
                throw new IllegalArgumentException(errMsg);
            }
        } catch (ConfigurationException | IOException ce) {
            JobExecutionException jee = new JobExecutionException("Error executing task.");
            jee.initCause(ce);
            throw jee;
        }

        JobKey key = context.getJobDetail().getKey();
        JobDataMap dataMap = context.getJobDetail().getJobDataMap();

        String taskName = dataMap.getString("taskName");
        String taskType = dataMap.getString("taskType");

        String source = null;
        String destination = null;
        String params = null;
        String newer = null;
        Boolean replaceIfNewer = false;
        URL thisURL = null;
        String mediaType = null;
        Reader in = null;
        Iterable<CSVRecord> records = null;

        try {
            in = new FileReader(downloadListFilepath);
            records = CSVFormat.RFC4180.withFirstRecordAsHeader().withQuote('"').withCommentMarker('#').parse(in);
        } catch (FileNotFoundException fnf) {
            log.error("Download file list " + "\"" + downloadListFilepath + "\"" + "not found: " + fnf.getMessage());
            return;
        } catch (IOException ioe) {
            log.error("Error reading download file list " + "\"" + downloadListFilepath + "\": " + ioe.getMessage());
        }

        // Loop through all files listed in the downloads file, and attempt to download
        // the "source" URL to the local
        // "destination" file. This method assumes that single files are being
        // downloaded from the web.
        for (CSVRecord record : records) {
            source = record.get("source").trim();
            destination = record.get("destination").trim();
            params = record.get("params").trim();
            log.debug("URL to acquire: " + source);

            String[] splitted = Arrays.stream(params.split(";"))
                    .map(String::trim)
                    .toArray(String[]::new);

            int icnt = -1;
            log.debug("Param count: " + splitted.length);
            if (splitted.length > 0) {
                String tmpStr = splitted[++icnt].trim();
                if (tmpStr.equalsIgnoreCase("newer")) {
                    replaceIfNewer = true;
                }
                log.debug("replace newer: " + replaceIfNewer);
                if (splitted.length > 1) {
                    mediaType = splitted[++icnt].trim();
                    log.debug("mediaType: " + mediaType);
                }
            }

            try {
                thisURL = new URL(source);
                downloadWebResource(thisURL, destination, mediaType);
            } catch (MalformedURLException mue) {
                log.error("Invalid URL specified for download source resource " + "\"" + source + "\""
                        + mue.getMessage());
            }
        }
    }

    /**
     * Download a single web resource to a local file.
     * <p>
     * The file downloaded will be accessible to metadig services.
     * </p>
     *
     * @param url            The web resource to download.
     * @param outputFileName The local file to download the resource to.
     * @param mediaType      The mediaType to request from the remote server.
     */
    public void downloadWebResource(URL url, String outputFileName, String mediaType) {

        HttpURLConnection urlConnection = null;
        try {
            urlConnection = (HttpURLConnection) url.openConnection();
            if (!mediaType.isEmpty()) {
                log.debug("Setting Accept header to: " + mediaType);
                urlConnection.setRequestProperty("Accept", mediaType);
            } else {
                log.debug("Setting Accept header to: */*");
                urlConnection.setRequestProperty("Accept", "*/*");
            }
            urlConnection.setRequestProperty("User-Agent", "curl/7.65.3");
            InputStream in = new BufferedInputStream(urlConnection.getInputStream());
            ReadableByteChannel rbc = Channels.newChannel(in);
            FileOutputStream fos = new FileOutputStream(outputFileName);
            fos.getChannel().transferFrom(rbc, 0, Long.MAX_VALUE);
            log.debug("Successfully downloaded " + url.getFile() + " to " + outputFileName);
        } catch (MalformedURLException mue) {
            log.error("Invalid URL specified for download source resource " + "\"" + url.getFile() + "\""
                    + mue.getMessage());
        } catch (IOException ioe) {
            log.error("Unable to download web resource " + "\"" + url.getFile() + "\": " + ioe.getMessage());
        } finally {
            urlConnection.disconnect();
        }

        return;
    }
}
