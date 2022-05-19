package edu.ucsb.nceas.mdqengine.scorer;

import edu.ucsb.nceas.mdqengine.MDQconfig;
import edu.ucsb.nceas.mdqengine.dispatch.Dispatcher;
import edu.ucsb.nceas.mdqengine.exception.MetadigException;
import edu.ucsb.nceas.mdqengine.exception.MetadigFilestoreException;
import edu.ucsb.nceas.mdqengine.filestore.MetadigFile;
import edu.ucsb.nceas.mdqengine.filestore.MetadigFileStore;
import edu.ucsb.nceas.mdqengine.filestore.StorageType;
import edu.ucsb.nceas.mdqengine.model.Result;
import edu.ucsb.nceas.mdqengine.model.Status;
import org.apache.commons.configuration2.ex.ConfigurationException;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.joda.time.DateTime;

import javax.script.ScriptException;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.HashMap;


/**
 * Execute a graphing program using the Java scripting engine.
 *
 */
public class Graph {

    protected Log log = LogFactory.getLog(this.getClass());

    private String scoreFile = null;
    private String outFilename = null;
    private MetadigFileStore fileStore = null;
    private static String filestoreBase = null;


    public Graph() throws MetadigException {
        try {
            init();
        } catch (MetadigException me) {
            log.error("Error initializing Graph object: " + me.getMessage());
           throw me;
        }
    }

    public void init() throws MetadigException {

        Scorer gfr = new Scorer();

        try {
            MDQconfig cfg = new MDQconfig ();
            filestoreBase = cfg.getString("metadig.store.directory");
        } catch (ConfigurationException | IOException e) {
            log.error("Unable to read configuration");
            MetadigException me = new MetadigException("Unable to read config properties");
            me.initCause(e.getCause());
            throw me;
        }

        this.fileStore = new MetadigFileStore();

    }

    /**
     *
     * Create a graph of the specified type, with the provided input file
     *
     * <p>
     * The graphics file that is produced is placed in a temporary location, that should be
     * copied to a permanent location if desired by the calling program.
     * </p>
     *
     * @param type the type of graph to create, i.e. "cummulative, monthly"
     * @param title the title to display in the graph
     * @param inputFile the input data file to graph
     * @return the location of the created graphics file
     * @throws Exception
     */
    public String create(GraphType type, String title, String inputFile) throws Exception {

        try {
            this.fileStore = new MetadigFileStore();
        } catch (MetadigFilestoreException mse) {
            throw (mse);
        }

        HashMap<String, Object> variables = new HashMap<>();
        //File script = File.createTempFile("mdqe_script", ".R");
        Object res = null;
        // Retrieve code for different graph types
        MetadigFileStore fileStore = new MetadigFileStore();
        File codeFile = null;
        String dispatcherType = null;

        MetadigFile mdFile = new MetadigFile();
        mdFile.setCreationDatetime(DateTime.now());
        mdFile.setStorageType(StorageType.CODE.toString());

        switch(type) {
            case CUMULATIVE:
                mdFile.setMediaType("text/x-rsrc");
                mdFile.setAltFilename("graph_" + GraphType.CUMULATIVE.toString().toLowerCase() + "_quality_scores.R");
                log.debug("Creating a " + GraphType.CUMULATIVE.toString().toLowerCase() + " graph with " + mdFile.getAltFilename());

                codeFile = fileStore.getFile(mdFile);
                dispatcherType = "r";
                break;
            case MONTHLY:
                mdFile.setMediaType("text/x-rsrc");
                mdFile.setAltFilename("graph_" + GraphType.MONTHLY.toString().toLowerCase() + "_quality_scores.R");
                log.debug("Creating a " + GraphType.MONTHLY.toString().toLowerCase() + " graph with " + mdFile.getAltFilename());

                codeFile = fileStore.getFile(mdFile);
                dispatcherType = "r";
                break;
        }

        log.debug("Graph program length: " + codeFile.length());

        // The the graph program the title of the graph
        // Currently we aren't putting titles on the graphs
        //variables.put("title", title);
        variables.put("title", "");
        // Set the input data file to read
        variables.put("inFile", inputFile);
        File tmpfile = File.createTempFile("accumulated-graph", ".png");
        log.debug("generating tmp output graphics file to: " + tmpfile.getCanonicalPath());
        variables.put("outFile", tmpfile.getCanonicalPath());

        String code = new String(Files.readAllBytes(codeFile.toPath()));
        Dispatcher dispatcher = Dispatcher.getDispatcher(dispatcherType);
        Result result = null;

        try {
            log.debug("dispatching graph program " + codeFile.toPath());
            result = dispatcher.dispatch(variables, code);
        } catch (ScriptException e) {
            log.error("Error executing script");
        }

        if(result.getStatus() != Status.SUCCESS) {
            log.error("Error running graphics program" + result.getOutput().toString() + ", status: " + result.getStatus());
        }

        return tmpfile.getCanonicalPath();
    }

    // TODO: future functions that would be useful
    //public void load(String storageType, String id, String aggregationName) {}
    //public FileInputStream getFileInputStream() {}
}
