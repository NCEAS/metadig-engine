package edu.ucsb.nceas.mdqengine.scheduler;

import edu.ucsb.nceas.mdqengine.Controller;
import edu.ucsb.nceas.mdqengine.MDQconfig;
import org.apache.commons.configuration2.ex.ConfigurationException;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVRecord;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.quartz.*;
import org.quartz.impl.StdSchedulerFactory;

import java.io.FileReader;
import java.io.IOException;
import java.io.Reader;
import java.util.Arrays;

import static org.quartz.CronScheduleBuilder.cronSchedule;
import static org.quartz.JobBuilder.newJob;
import static org.quartz.TriggerBuilder.newTrigger;

/**
 * The Scheduler class manages tasks that harvest metadata from DataONE and
 * submits requets to the metadig-engine to geneate quality reports from the
 * acquired metadata.
 *
 * @author Peter Slaughter
 * @version %I%, %G%
 * @since 1.0
 */
public class JobScheduler {

    public static Log log = LogFactory.getLog(Controller.class);

    public static void main(String[] argv) throws Exception {
        JobScheduler js = new JobScheduler();

        String taskType = null;
        String taskName = null;
        String taskGroup = null;
        String cronSchedule = null;
        String params = null;

        String pidFilter = null;
        String suiteId = null;
        String nodeId = null;
        String startHarvestDatetime = null;
        int countRequested = 1000;
        int harvestDatetimeInc = 1;
        String requestType = null;

        // Filestore variables
        String dirIncludeMatch = null;
        String dirExcludeMatch = null;
        String fileIncludeMatch = null;
        String fileExcludeMatch = null;
        String logFile = null;
        Boolean replaceIfNewer = false; // replace a local data file if the associated web resoruce is newer
        String comments = null;

        String taskListFilename = js.readConfig("task.file");
        log.debug("task list filename: " + taskListFilename);

        // Read the task list
        Reader in = new FileReader(taskListFilename);
        // Iterable<CSVRecord> records =
        // CSVFormat.RFC4180.withHeader("query","task-type","job-name","job-group","cron-schedule","params").parse(in);
        // Iterable<CSVRecord> records =
        // CSVFormat.RFC4180.withHeader("query","task-type","job-name","job-group","cron-schedule","suite-id","node-id","node-service-url").parse(in);

        log.debug("creating scheduler");
        SchedulerFactory sf = new StdSchedulerFactory();
        Scheduler scheduler = sf.getScheduler();

        // The tasklist.csv file contains entries of jobs to be scheduled. The header
        // line and typical "task" line look line like:
        // query,task-type,job-name,job-group,cron-schedule,params
        // ?q=formatId:eml*, quality, quality-knb, metadig, 0/10 * * * * ?,
        // "knb.suite.1;urn:node:KNB;https://knb.ecoinformatics.org/knb/d1/mn;2018-10-10T00:00:00.00Z,1"
        //
        log.debug("Scheduler name is: " + scheduler.getSchedulerName());
        log.debug("Scheduler instance ID is: " + scheduler.getSchedulerInstanceId());
        log.debug(
                "Scheduler context's value for key QuartzTopic is " + scheduler.getContext().getString("QuartzTopic"));
        scheduler.start();

        Iterable<CSVRecord> records = CSVFormat.RFC4180.withFirstRecordAsHeader().withQuote('"').withCommentMarker('#')
                .parse(in);
        for (CSVRecord record : records) {
            taskType = record.get("task-type").trim();
            taskName = record.get("task-name").trim();
            taskGroup = record.get("task-group").trim();
            cronSchedule = record.get("cron-schedule").trim();
            params = record.get("params").trim();
            log.debug("Task type: " + taskType);
            log.debug("Task name: " + taskName);
            log.debug("cronSchedule: " + cronSchedule);
            params = params.startsWith("\"") ? params.substring(1) : params;
            params = params.endsWith("\"") ? params.substring(0, params.length() - 1) : params;

            log.debug("params: " + params);
            if (taskType.equals("quality")) {
                // Example taskList.csv entry:
                // quality,quality-arctic,metadig,20 0/1 * * *
                // ?,"^eml.*|^http.*eml.*;arctic.data.center.suite.1;urn:node:ARCTIC;2019-12-01T14:30:00.00Z;1;100"
                log.debug("Scheduling harvest for task name: " + taskName + ", task group: " + taskGroup);
                String[] splitted = Arrays.stream(params.split(";"))
                        .map(String::trim)
                        .toArray(String[]::new);

                int icnt = -1;
                log.debug("Split length: " + splitted.length);
                // filter to use for removing unneeded pids from harvest list
                pidFilter = splitted[++icnt].trim();
                log.debug("pidFilter: " + pidFilter);
                // Suite identifier
                suiteId = splitted[++icnt].trim();
                log.debug("suiteId: " + suiteId);
                // DataOne Node identifier
                nodeId = splitted[++icnt].trim();
                log.debug("nodeId: " + nodeId);
                // Start harvest datetime. This is the beginning of the date range for the first
                // harvest.
                // After the first harvest, the end of the harvest date range will be used as
                // the beginning
                // for the next harvest.
                startHarvestDatetime = splitted[++icnt].trim();
                log.debug("startHarvestDatetime: " + startHarvestDatetime);
                // Harvest datetime increment. This value will be added to the beginning of the
                // harvest datetime
                // range to determine the end of the range. This is specified in number of days.
                harvestDatetimeInc = Integer.parseInt(splitted[++icnt].trim());
                log.debug("harvestDatetimeInc: " + harvestDatetimeInc);
                // The number of results to return from the DataONE 'listObjects' service
                countRequested = Integer.parseInt(splitted[++icnt].trim());
                log.debug("countRequested: " + countRequested);
            } else if (taskType.equals("score")) {
                // Example taskList.csv entry:
                // score,CN-fair,metadig,35 0/1 * * *
                // ?,".*portal.*;FAIR.suite.1;urn:node:CN;2019-12-01T00:00:00.00Z;1;100"
                log.debug("Scheduling harvest for task name: " + taskName + ", task group: " + taskGroup);
                String[] splitted = Arrays.stream(params.split(";"))
                        .map(String::trim)
                        .toArray(String[]::new);
                log.debug("Split length: " + splitted.length);
                int icnt = -1;
                // filter to use for removing unneeded pids from harvest list
                pidFilter = splitted[++icnt].trim();
                // Suite identifier
                suiteId = splitted[++icnt].trim();
                // DataOne Node identifier
                nodeId = splitted[++icnt].trim();
                // Start harvest datetime. This is the beginning of the date range for the first
                // harvest.
                // After the first harvest, the end of the harvest date range will be used as
                // the beginning
                // for the next harvest.
                startHarvestDatetime = splitted[++icnt].trim();
                // Harvest datetime increment. This value will be added to the beginning of the
                // harvest datetime
                // range to determine the end of the range. This is specified in number of days.
                harvestDatetimeInc = Integer.parseInt(splitted[++icnt].trim());
                // The number of results to return from the DataONE 'listObjects' service
                countRequested = Integer.parseInt(splitted[++icnt].trim());
                // Is this scores request for a portal or an entire member node?
                requestType = splitted[++icnt].trim();

                log.debug("pidFilter: " + pidFilter);
                log.debug("suiteId: " + suiteId);
                log.debug("nodeId: " + nodeId);
                log.debug("startHarvestDatetime: " + startHarvestDatetime);
                log.debug("harvestDatetimeInc: " + harvestDatetimeInc);
                log.debug("countRequested: " + countRequested);
                log.debug("requestType: " + requestType);
            } else if (taskType.equals("filestore")) {
                // Example taskList.csv entry:
                // filestore,ingest,metadig,,,0 0/30 * * *
                // ?,"stage;;*.*;README.txt;filestore-ingest.log"
                log.debug("Scheduling filestore ingest task name: " + taskName + ", task group: " + taskGroup);
                String[] splitted = Arrays.stream(params.split(";"))
                        .map(String::trim)
                        .toArray(String[]::new);

                int icnt = -1;
                log.debug("Split length: " + splitted.length);
                // Filestore staging directories include in the ingest
                dirIncludeMatch = splitted[++icnt].trim();
                log.debug("dirIncludeMatch: " + dirIncludeMatch);
                // Filestore staging directories to include in the ingest
                dirExcludeMatch = splitted[++icnt].trim();
                log.debug("dirExcludeMatch: " + dirExcludeMatch);
                // Filestore staging files to include in the ingest
                fileIncludeMatch = splitted[++icnt].trim();
                log.debug("fileIncludeMatch: " + fileIncludeMatch);
                // Filestore staging files to exclude from the ingest
                fileExcludeMatch = splitted[++icnt].trim();
                log.debug("fileExcludeMatch: " + fileExcludeMatch);
                logFile = splitted[++icnt].trim();
                log.debug("log file: " + logFile);
            } else if (taskType.equals("nodelist")) {
                log.debug("Scheduling nodelist update from DataONE, task name: " + taskName + ", task group: "
                        + taskGroup);
                String[] splitted = Arrays.stream(params.split(";"))
                        .map(String::trim)
                        .toArray(String[]::new);

                int icnt = -1;
                log.debug("Split length: " + splitted.length);
                nodeId = splitted[++icnt].trim();
                log.debug("nodeId: " + nodeId);
            } else if (taskType.equals("downloads")) {
                // Note that currently there are no parameters specified for this task.
                log.debug("Scheduling download of web resources for assessment checks, task name: " + taskName
                        + ", task group: " + taskGroup);
            }

            try {
                // Currently there is only taskType="quality", but there could be more in the
                // future!
                JobDetail job = null;
                if (taskType.equals("quality")) {
                    job = newJob(RequestReportJob.class)
                            .withIdentity(taskName, taskGroup)
                            .usingJobData("taskName", taskName)
                            .usingJobData("taskType", taskType)
                            .usingJobData("pidFilter", pidFilter)
                            .usingJobData("suiteId", suiteId)
                            .usingJobData("nodeId", nodeId)
                            .usingJobData("startHarvestDatetime", startHarvestDatetime)
                            .usingJobData("harvestDatetimeInc", harvestDatetimeInc)
                            .usingJobData("countRequested", countRequested)
                            .build();
                } else if (taskType.equalsIgnoreCase("score")) {
                    job = newJob(RequestScorerJob.class)
                            .withIdentity(taskName, taskGroup)
                            .usingJobData("taskName", taskName)
                            .usingJobData("taskType", taskType)
                            .usingJobData("pidFilter", pidFilter)
                            .usingJobData("suiteId", suiteId)
                            .usingJobData("nodeId", nodeId)
                            .usingJobData("startHarvestDatetime", startHarvestDatetime)
                            .usingJobData("harvestDatetimeInc", harvestDatetimeInc)
                            .usingJobData("countRequested", countRequested)
                            .usingJobData("requestType", requestType)
                            .build();
                } else if (taskType.equalsIgnoreCase("filestore")) {
                    job = newJob(FilestoreIngestJob.class)
                            .withIdentity(taskName, taskGroup)
                            .usingJobData("taskName", taskName)
                            .usingJobData("taskType", taskType)
                            .usingJobData("dirIncludeMatch", dirIncludeMatch)
                            .usingJobData("dirExcludeMatch", dirExcludeMatch)
                            .usingJobData("fileIncludeMatch", fileIncludeMatch)
                            .usingJobData("fileExcludeMatch", fileExcludeMatch)
                            .usingJobData("logFile", logFile)
                            .build();
                } else if (taskType.equalsIgnoreCase("nodelist")) {
                    job = newJob(NodeList.class)
                            .withIdentity(taskName, taskGroup)
                            .usingJobData("taskName", taskName)
                            .usingJobData("taskType", taskType)
                            .usingJobData("nodeId", nodeId)
                            .build();
                } else if (taskType.equalsIgnoreCase("downloads")) {
                    log.debug("Scheduling task type: " + taskType);
                    job = newJob(AcquireWebResourcesJob.class)
                            .withIdentity(taskName, taskGroup)
                            .usingJobData("taskName", taskName)
                            .usingJobData("taskType", taskType)
                            .build();
                }

                CronTrigger trigger = newTrigger()
                        .withIdentity(taskName + "-trigger", taskGroup)
                        .withSchedule(
                                cronSchedule(cronSchedule)
                                        .withMisfireHandlingInstructionDoNothing())
                        .build();

                scheduler.scheduleJob(job, trigger);

            } catch (SchedulerException se) {
                se.printStackTrace();
            }
        }
        Thread.sleep(300L * 100000000L);
        scheduler.shutdown();
    }

    public JobScheduler() {
    }

    /**
     * Read a single parameter from the quality engine parameter file
     * 
     * @param paramName the parameter to read from the config file
     * @throws ConfigurationException if there is an exception while reading the
     *                                config file
     * @throws IOException            if there is an exception while reading the
     *                                config file
     */
    public String readConfig(String paramName) throws ConfigurationException, IOException {
        String paramValue = null;
        try {
            MDQconfig cfg = new MDQconfig();
            paramValue = cfg.getString(paramName);
        } catch (ConfigurationException | IOException e) {
            log.error("Could not read configuration for param: " + paramName + ": " + e.getMessage());
            throw e;
        }
        return paramValue;
    }
}
