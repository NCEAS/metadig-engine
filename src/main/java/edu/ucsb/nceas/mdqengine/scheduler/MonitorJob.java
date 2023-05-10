package edu.ucsb.nceas.mdqengine.scheduler;

import edu.ucsb.nceas.mdqengine.exception.MetadigStoreException;
import edu.ucsb.nceas.mdqengine.model.Run;
import edu.ucsb.nceas.mdqengine.store.DatabaseStore;
import edu.ucsb.nceas.mdqengine.store.MDQStore;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import org.quartz.*;

import java.util.ArrayList;
import java.util.List;

// the job to run
@DisallowConcurrentExecution
public class MonitorJob implements Job {

    private Log log = LogFactory.getLog(RequestReportJob.class);

    // Default constructor
    public MonitorJob() {
    }

    public void execute(JobExecutionContext context) throws JobExecutionException {

        log.info("job");
         MDQStore store = null;
         List<Run> processing = new ArrayList<Run>();
         // Get a connection to the database

         try {
             store = new DatabaseStore();
         } catch (MetadigStoreException e) {
             e.printStackTrace();
             throw new JobExecutionException("Cannot create store, unable to schedule job", e);
         }

         if (!store.isAvailable()) {
             try {
                 store.renew();
             } catch (MetadigStoreException e) {
                 e.printStackTrace();
                 throw new JobExecutionException("Cannot renew store, unable to schedule job", e);
             }
         }

        // query database
        try {
            processing = store.getProcessing();
        } catch (MetadigStoreException e) {
            e.printStackTrace();
        }

        // request job via rabbitMQ
        for (Run run : processing) {
            log.info(run.getId() + run.getStatus());
        }
     }

}
