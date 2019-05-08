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

    private String solrLocation = null;
    public static Log log = LogFactory.getLog(Controller.class);

    public static void main(String[] argv) throws Exception {
        JobScheduler js = new JobScheduler();

        String taskType = null;
        String jobName = null;
        String jobGroup = null;
        String authToken = null;
        String authTokenParamName = null;
        String cronSchedule = null;
        String params = null;

        String pidFilter = null;
        String suiteId = null;
        String nodeId = null;
        String nodeServiceUrl = null;
        String startHarvestDatetime = null;
        int harvestDatetimeInc = 1;

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
            jobName        = record.get("job-name").trim();
            jobGroup       = record.get("job-group").trim();
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
                System.out.println("Scheduling harvest for job name: " + jobName + ", job group: " + jobGroup);
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
            }

            try {
                System.out.println("Setting job");
                // Currently there is only taskType="quality", but there could be more in the future!
                JobDetail job = null;
                if(taskType.equals("quality")) {
                    job = newJob(RequestReportJob.class)
                            .withIdentity(jobName, jobGroup)
                            .usingJobData("authToken", authToken)
                            .usingJobData("pidFilter", pidFilter)
                            .usingJobData("suiteId", suiteId)
                            .usingJobData("nodeId", nodeId)
                            .usingJobData("nodeServiceUrl", nodeServiceUrl)
                            .usingJobData("startHarvestDatetime", startHarvestDatetime)
                            .usingJobData("harvestDatetimeInc", harvestDatetimeInc)
                            .build();
                }

                System.out.println("Setting trigger");
                CronTrigger trigger = newTrigger()
                    .withIdentity(jobName + "-trigger", jobGroup)
                    .withSchedule(cronSchedule(cronSchedule))
                    .build();

                System.out.println("Scheduling job");
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
            //solrLocation = cfg.getString("solr.location");
            //SolrClient solrClient = new HttpSolrClient.Builder(solrLocation).build();
            //log.info("Created Solr client");
            paramValue = cfg.getString(paramName);
        } catch (Exception e) {
            log.error("Could not create Solr client", e);
            throw e;
        }
        return paramValue;
    }
}


