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
 * @author      Peter Slaughter
 * @version     %I%, %G%
 * @since       1.0
 */
public class JobScheduler {

    public static Log log = LogFactory.getLog(Controller.class);

    public static void main(String[] argv) throws Exception {
        JobScheduler js = new JobScheduler();

        String taskType = null;
        String taskName = null;
        String taskGroup = null;
        String authToken = null;
        String authTokenParamName = null;
        String cronSchedule = null;
        String params = null;

        String pidFilter = null;
        String suiteId = null;
        String nodeId = null;
        String nodeServiceUrl = null;
        String startHarvestDatetime = null;
        int countRequested = 1000;
        int harvestDatetimeInc = 1;

        // Filestore variables
        String dirIncludeMatch = null;
        String dirExcludeMatch = null;
        String fileIncludeMatch = null;
        String fileExcludeMatch = null;
        String logFile = null;

        String taskListFilename = js.readConfig("task.file");
        log.debug("task list filename: " + taskListFilename);

        // Read the task list
        Reader in = new FileReader(taskListFilename);
        //Iterable<CSVRecord> records = CSVFormat.RFC4180.withHeader("query","task-type","job-name","job-group","cron-schedule","params").parse(in);
        //Iterable<CSVRecord> records = CSVFormat.RFC4180.withHeader("query","task-type","job-name","job-group","cron-schedule","suite-id","node-id","node-service-url").parse(in);

        System.out.println("creating scheduler");
        SchedulerFactory sf = new StdSchedulerFactory();
        Scheduler scheduler = sf.getScheduler();

        // The tasklist.csv file contains entries of jobs to be scheduled. The header line and typical "task" line look line like:
        //      query,task-type,job-name,job-group,cron-schedule,params
        //      ?q=formatId:eml*, quality, quality-knb, metadig, 0/10 * * * * ?, "knb.suite.1;urn:node:KNB;https://knb.ecoinformatics.org/knb/d1/mn;2018-10-10T00:00:00.00Z,1"
        //
        System.out.println("Scheduler name is: " + scheduler.getSchedulerName());
        System.out.println("Scheduler instance ID is: " + scheduler.getSchedulerInstanceId());
        System.out.println("Scheduler context's value for key QuartzTopic is " + scheduler.getContext().getString("QuartzTopic"));
        scheduler.start();

        Iterable<CSVRecord> records = CSVFormat.RFC4180.withFirstRecordAsHeader().withQuote('"').withCommentMarker('#').parse(in);
        for (CSVRecord record : records) {
            taskType       = record.get("task-type").trim();
            taskName        = record.get("task-name").trim();
            taskGroup       = record.get("task-group").trim();
            authTokenParamName = record.get("auth-token").trim();
            cronSchedule   = record.get("cron-schedule").trim();
            params         = record.get("params").trim();
            System.out.println("Task type: " + taskType);
            System.out.println("cronSchedule: " + cronSchedule);
            params = params.startsWith("\"") ? params.substring(1) : params;
            params = params.endsWith("\"") ? params.substring(0, params.length()-1) : params;

            authToken = js.readConfig(authTokenParamName);
            //System.out.println("authToken: " + authToken);
            System.out.println("params: " + params);
            if(taskType.equals("quality")) {
                System.out.println("Scheduling harvest for task name: " + taskName + ", task group: " + taskGroup);
                String[] splitted = Arrays.stream(params.split(";"))
                        .map(String::trim)
                        .toArray(String[]::new);

                int icnt = -1;
                System.out.println("Split length: " + splitted.length);
                // filter to use for removing unneeded pids from harvest list
                pidFilter      = splitted[++icnt].trim();
                System.out.println("pidFilter: " + pidFilter);
                // Suite identifier
                suiteId        = splitted[++icnt].trim();
                System.out.println("suiteId: " + suiteId);
                // DataOne Node identifier
                nodeId         = splitted[++icnt].trim();
                System.out.println("nodeId: " + nodeId);
                // Dataone base service URL
                nodeServiceUrl = splitted[++icnt].trim();
                System.out.println("nodeServiceUrl: " + nodeServiceUrl);
                // Start harvest datetime. This is the beginning of the date range for the first harvest.
                // After the first harvest, the end of the harvest date range will be used as the beginning
                // for the next harvest.
                startHarvestDatetime = splitted[++icnt].trim();
                System.out.println("startHarvestDatetime: " + startHarvestDatetime);
                // Harvest datetime increment. This value will be added to the beginning of the harvest datetime
                // range to determine the end of the range. This is specified in number of days.
                harvestDatetimeInc = Integer.parseInt(splitted[++icnt].trim());
                System.out.println("harvestDatetimeInc: " + harvestDatetimeInc);
                // The number of results to return from the DataONE 'listObjects' service
                countRequested = Integer.parseInt(splitted[++icnt].trim());
                System.out.println("countRequested: " + countRequested);
            } else if(taskType.equals("score")) {
                System.out.println("Scheduling harvest for task name: " + taskName + ", task group: " + taskGroup);
                String[] splitted = Arrays.stream(params.split(";"))
                        .map(String::trim)
                        .toArray(String[]::new);

                int icnt = -1;
                System.out.println("Split length: " + splitted.length);
                // filter to use for removing unneeded pids from harvest list
                pidFilter      = splitted[++icnt].trim();
                System.out.println("pidFilter: " + pidFilter);
                // Suite identifier
                suiteId        = splitted[++icnt].trim();
                System.out.println("suiteId: " + suiteId);
                // DataOne Node identifier
                nodeId         = splitted[++icnt].trim();
                System.out.println("nodeId: " + nodeId);
                // Dataone base service URL
                nodeServiceUrl = splitted[++icnt].trim();
                System.out.println("nodeServiceUrl: " + nodeServiceUrl);
                // Start harvest datetime. This is the beginning of the date range for the first harvest.
                // After the first harvest, the end of the harvest date range will be used as the beginning
                // for the next harvest.
                startHarvestDatetime = splitted[++icnt].trim();
                System.out.println("startHarvestDatetime: " + startHarvestDatetime);
                // Harvest datetime increment. This value will be added to the beginning of the harvest datetime
                // range to determine the end of the range. This is specified in number of days.
                harvestDatetimeInc = Integer.parseInt(splitted[++icnt].trim());
                System.out.println("harvestDatetimeInc: " + harvestDatetimeInc);
                // The number of results to return from the DataONE 'listObjects' service
                countRequested = Integer.parseInt(splitted[++icnt].trim());
                System.out.println("countRequested: " + countRequested);
            } else if(taskType.equals("filestore")) {
                System.out.println("Scheduling filestore ingest task name: " + taskName + ", task group: " + taskGroup);
                String[] splitted = Arrays.stream(params.split(";"))
                        .map(String::trim)
                        .toArray(String[]::new);

                int icnt = -1;
                System.out.println("Split length: " + splitted.length);
                // Filestore staging directories include in the ingest
                dirIncludeMatch = splitted[++icnt].trim();
                System.out.println("dirIncludeMatch: " + dirIncludeMatch);
                // Filestore staging directories to include in the ingest
                dirExcludeMatch = splitted[++icnt].trim();
                System.out.println("dirExcludeMatch: " + dirExcludeMatch);
                // Filestore staging files to include in the ingest
                fileIncludeMatch = splitted[++icnt].trim();
                System.out.println("fileIncludeMatch: " + fileIncludeMatch);
                // Filestore staging files to exclude from the ingest
                fileExcludeMatch = splitted[++icnt].trim();
                System.out.println("fileExcludeMatch: " + fileExcludeMatch);
                logFile = splitted[++icnt].trim();
                System.out.println("log file: " + logFile);
            }

            try {
                System.out.println("Setting task");
                // Currently there is only taskType="quality", but there could be more in the future!
                JobDetail job = null;
                if(taskType.equals("quality")) {
                    job = newJob(RequestReportJob.class)
                            .withIdentity(taskName, taskGroup)
                            .usingJobData("taskName", taskName)
                            .usingJobData("taskType", taskType)
                            .usingJobData("authToken", authToken)
                            .usingJobData("pidFilter", pidFilter)
                            .usingJobData("suiteId", suiteId)
                            .usingJobData("nodeId", nodeId)
                            .usingJobData("nodeServiceUrl", nodeServiceUrl)
                            .usingJobData("startHarvestDatetime", startHarvestDatetime)
                            .usingJobData("harvestDatetimeInc", harvestDatetimeInc)
                            .usingJobData("countRequested", countRequested)
                            .build();
                } else if (taskType.equalsIgnoreCase("score")) {
                    job = newJob(RequestScorerJob.class)
                            .withIdentity(taskName, taskGroup)
                            .usingJobData("taskName", taskName)
                            .usingJobData("taskType", taskType)
                            .usingJobData("authToken", authToken)
                            .usingJobData("pidFilter", pidFilter)
                            .usingJobData("suiteId", suiteId)
                            .usingJobData("nodeId", nodeId)
                            .usingJobData("nodeServiceUrl", nodeServiceUrl)
                            .usingJobData("startHarvestDatetime", startHarvestDatetime)
                            .usingJobData("harvestDatetimeInc", harvestDatetimeInc)
                            .usingJobData("countRequested", countRequested)
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
                }

                System.out.println("Setting trigger");
                CronTrigger trigger = newTrigger()
                    .withIdentity(taskName + "-trigger", taskGroup)
                    .withSchedule(cronSchedule(cronSchedule))
                    .build();

                System.out.println("Scheduling task");
                scheduler.scheduleJob(job, trigger);

            } catch (SchedulerException se) {
                se.printStackTrace();
            }
        }
        Thread.sleep(300L * 100000000L);
        scheduler.shutdown();
    }

    public JobScheduler () {
    }

    public String readConfig (String paramName) throws ConfigurationException, IOException {
        String paramValue = null;
        try {
            MDQconfig cfg = new MDQconfig();
            paramValue = cfg.getString(paramName);
        } catch (Exception e) {
            log.error("Could not read configuration for param: " + paramName + ": " + e.getMessage());
            throw e;
        }
        return paramValue;
    }
}


