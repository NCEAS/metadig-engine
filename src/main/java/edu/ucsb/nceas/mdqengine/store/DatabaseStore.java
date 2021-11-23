package edu.ucsb.nceas.mdqengine.store;

import edu.ucsb.nceas.mdqengine.MDQconfig;
import edu.ucsb.nceas.mdqengine.exception.MetadigStoreException;
import edu.ucsb.nceas.mdqengine.model.*;
import edu.ucsb.nceas.mdqengine.model.Identifier;
import edu.ucsb.nceas.mdqengine.serialize.XmlMarshaller;
import edu.ucsb.nceas.mdqengine.solr.QualityScore;
import org.apache.commons.configuration2.ex.ConfigurationException;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVPrinter;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.dataone.service.types.v1.*;
import org.dataone.service.types.v2.Node;
import org.dataone.service.util.TypeMarshaller;
import org.joda.time.DateTime;
import org.joda.time.format.DateTimeFormatter;
import org.joda.time.format.ISODateTimeFormat;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.xml.sax.SAXException;

import javax.sql.DataSource;
import javax.xml.bind.JAXBException;
import java.io.*;
import java.net.URL;
import java.sql.*;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.time.Instant;
import java.util.Date;
import java.util.*;

/**
 * Persistent storage for metadata assessment runs.
 * @author slaughter
 *
 */
public class DatabaseStore implements MDQStore {

    protected Log log = LogFactory.getLog(this.getClass());

    private static String dbUrl = null;
    private static String dbUser = null;
    private static String dbPasswd = null;

    private Map<String, Suite> suites = new HashMap<String, Suite>();
    private Map<String, Check> checks = new HashMap<String, Check>();
    private Map<String, Run> runs = new HashMap<String, Run>();
    private Connection conn = null;
    private DataSource dataSource = null;

    public DatabaseStore () throws MetadigStoreException {
        log.trace("Initializing a new DatabaseStore to " + dbUrl + ".");
        this.init();
    }

    /*
     * Get a connection to the database that contains the quality reports.
     */
    private void init() throws MetadigStoreException {

        log.trace("initializing connection");
        String additionalDir = null;
        try {
            MDQconfig cfg = new MDQconfig();
            dbUrl = cfg.getString("jdbc.url");
            dbUser = cfg.getString("postgres.user");
            dbPasswd = cfg.getString("postgres.passwd");
            additionalDir = cfg.getString("mdq.store.directory");

        } catch (ConfigurationException | IOException ex ) {
            log.error(ex.getMessage());
            MetadigStoreException mse = new MetadigStoreException("Unable to create new Store");
            mse.initCause(ex.getCause());
            throw mse;
        }

        try {
            Properties props = new Properties();
            props.setProperty("user",dbUser);
            props.setProperty("password",dbPasswd);
            props.setProperty("prepareThreshold", "0");
            //props.setProperty("extra_float_digits", "2");
            Class.forName("org.postgresql.Driver");
            conn = DriverManager.getConnection(dbUrl, props);
            conn.setAutoCommit(false);
        } catch (Exception e) {
            e.printStackTrace();
            log.error(e.getClass().getName()+": "+e.getMessage());
            MetadigStoreException mse = new MetadigStoreException("Unable to create the database store.");
            mse.initCause(e);
            throw(mse);
        }

        log.trace("Connection initialized");

        PathMatchingResourcePatternResolver resolver = new PathMatchingResourcePatternResolver();

        Resource[] suiteResources = null;
        // load all metadig quality suites and checks from local files, if available
        try {
            suiteResources  = resolver.getResources("classpath*:/suites/*.xml");
            // do we have an additional location for these?
            if (additionalDir != null) {
                Resource[] additionalSuiteResources = resolver.getResources("file://" + additionalDir + "/suites/*.xml");
                suiteResources = (Resource[]) ArrayUtils.addAll(suiteResources, additionalSuiteResources);
            }
        } catch (IOException e) {
            log.error("Could not read local suite resources: " + e.getMessage(), e);
        }
        if (suiteResources != null) {
            for (Resource resource: suiteResources) {
                Suite suite = null;
                try {
                    URL url = resource.getURL();
                    String xml = IOUtils.toString(url.openStream(), "UTF-8");
                    suite = (Suite) XmlMarshaller.fromXml(xml, Suite.class);
                } catch (JAXBException | IOException | SAXException e) {
                    //log.warn("Could not load suite '" + resource.getFilename() + "' due to an error: " + e.getMessage() + ".");
                    continue;
                }
                this.createSuite(suite);

            }
        }
        if(this.isAvailable()) {
            log.trace("Initialized database store: opened database successfully");
        } else {
            throw new MetadigStoreException("Error initializing database, connection not available");
        }
    }

    /*
     * Get a single run from the 'runs' table.
     */
    @Override
    public Identifier getIdentifier(String metadataId) throws MetadigStoreException  {

        PreparedStatement stmt = null;

        Identifier identifier = new Identifier();

        // Hope for the best, prepare for the worst!
        MetadigStoreException me = new MetadigStoreException("Unable get identifier " + metadataId + " from the datdabase.");
        // Select records from the 'runs' table
        try {
            log.trace("preparing statement for query");
            String sql = "select * from identifiers where metadata_id = ? ";
            stmt = conn.prepareStatement(sql);
            stmt.setString(1, metadataId);

            Array arr = null;
            log.trace("issuing query: " + sql);
            ResultSet rs = stmt.executeQuery();
            if(rs.next()) {
                identifier.setMetadataId(rs.getString("metadata_id"));
                identifier.setDataSource(rs.getString("data_source"));
                identifier.setObsoletes(rs.getString("obsoletes"));
                identifier.setObsoletedBy(rs.getString("obsoleted_by"));
                identifier.setDateUploaded(rs.getTimestamp("date_uploaded"));
                identifier.setDateSysMetaModified(rs.getTimestamp("date_sysmeta_modified"));
                identifier.setSequenceId(rs.getString("sequence_id"));
                identifier.setFormatId(rs.getString("format_id"));
                identifier.setRightsHolder(rs.getString("rights_holder"));
                arr = rs.getArray("groups");
                String[] groups = (String[])arr.getArray();
                identifier.setGroups(Arrays.asList(groups));
                rs.close();
                stmt.close();
                log.trace("Retrieved identifier successfully for metadata id: " + identifier.getMetadataId());
            } else {
                log.trace("Identifier not found: " + metadataId);
                return null;
            }
        } catch ( Exception e ) {
            log.error( e.getClass().getName()+": "+ e.getMessage());
            e.printStackTrace();
            me.initCause(e);
            throw(me);
        }

        return(identifier);
    }

    /*
     * Save a single run, first populating the 'pids' table, then the 'runs' table.
     */
    public Integer saveIdentifier(Identifier identifier) throws MetadigStoreException {

        PreparedStatement stmt = null;

        String metadataId = identifier.getMetadataId();
        String dataSource = identifier.getDataSource();
        String obsoletes = identifier.getObsoletes();
        String obsoletedBy = identifier.getObsoletedBy();
        Timestamp dateUploaded = new Timestamp(identifier.getDateUploadedAsDateTime().getMillis());
        Timestamp dateSysMetaModified = new Timestamp(identifier.getDateSysMetaModifiedAsDateTime().getMillis());
        String sequenceId = identifier.getSequenceId();
        String formatId = identifier.getFormatId();
        String rightsHolder = identifier.getRightsHolder();
        List<String> groups = identifier.getGroups();
        Integer updateCount = 0;

        MetadigStoreException me = new MetadigStoreException("Unable save identifier " + metadataId + " to the datdabase.");
        // First, insert a record into the main table ('pids')
        try {
            String[] groupsList = groups.toArray(new String[0]);
            Array groupsArray = conn.createArrayOf("text", groupsList);
            // Insert a pid into the 'identifiers' table and if it is already their, update it
            // with any new info, e.g. the 'datasource' may have changed. There are two cases where
            // an update an occur - if new system metadata is being updated, or if the sequenceId is
            // being assigned for this pid. Avoid updating an identifier entry with older sysmeta info.
            String sql = "INSERT INTO identifiers AS i (metadata_id, data_source, obsoletes, obsoleted_by, "
                    + "date_uploaded, date_sysmeta_modified, sequence_id, format_id, rights_holder, groups) "
                    + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?) "
                    + "ON CONFLICT (metadata_id) DO UPDATE SET (data_source, obsoletes, obsoleted_by, "
                    + "date_uploaded, date_sysmeta_modified, sequence_id, format_id, rights_holder, groups) "
                    + " = (?, ?, ?, ?, ?, ?, ?, ?, ?) "
                    + "WHERE EXCLUDED.date_sysmeta_modified > i.date_sysmeta_modified "
                    + " OR (EXCLUDED.sequence_id IS NOT NULL AND i.sequence_id IS NULL);";

            stmt = conn.prepareStatement(sql);
            stmt.setString(1, metadataId);
            stmt.setString(2, dataSource);
            stmt.setString(3, obsoletes);
            stmt.setString(4, obsoletedBy);
            stmt.setTimestamp(5, dateUploaded);
            stmt.setTimestamp(6, dateSysMetaModified);
            stmt.setString(7, sequenceId);
            stmt.setString(8, formatId);
            stmt.setString(9, rightsHolder);
            stmt.setArray(10, groupsArray);
            stmt.setString(11, dataSource);
            stmt.setString(12, obsoletes);
            stmt.setString(13, obsoletedBy);
            stmt.setTimestamp(14, dateUploaded);
            stmt.setTimestamp(15, dateSysMetaModified);
            stmt.setString(16, sequenceId);
            stmt.setString(17, formatId);
            stmt.setString(18, rightsHolder);
            stmt.setArray(19, groupsArray);
            updateCount = stmt.executeUpdate();
            stmt.close();
            conn.commit();
        } catch (SQLException e) {
            log.error( e.getClass().getName()+": "+ e.getMessage());
            me.initCause(e);
            throw(me);
        }

        return updateCount;
    }

    @Override
    public Collection<String> listRuns() {
        return runs.keySet();
    }

    /*
     * Get a single result from the 'check_results' table.
     */
    @Override
    public Result getResult(String metadataId, String suiteId, String checkId) throws MetadigStoreException  {

        Result result = new Result();
        Check check = new Check();
        List<Output> outputList = null;
        Array arr = null;
        PreparedStatement stmt = null;

        // Hope for the best, prepare for the worst!
        MetadigStoreException me = new MetadigStoreException("Unable to get check result from the datdabase.");
        // Select records from the 'runs' table
        try {
            log.trace("preparing statement for query");
            String sql = "select * check_results where metadata_id = ? and suite_id = ? and check_id = ?";
            stmt = conn.prepareStatement(sql);
            stmt.setString(1, metadataId);
            stmt.setString(2, suiteId);
            stmt.setString(3, checkId);
            log.trace("issuing query: " + sql);
            ResultSet rs = stmt.executeQuery();

            if(rs.next()) {
                check.setId(rs.getString("check_id"));
                check.setName(rs.getString("check_name"));
                check.setLevel(Level.valueOf(rs.getString("check_level")));
                check.setType(rs.getString("check_type"));
                result.setStatus(Status.valueOf(rs.getString("status")));
                arr = rs.getArray("output");
                String[] output = (String[])arr.getArray();
                result.setOutput((Output) Arrays.asList(output));
                rs.close();
                stmt.close();
                result.setCheck(check);
                log.trace("Retrieved check result successfully for metadata id: " + metadataId
                    + ", suiteId: " + suiteId + ", checkId: " + checkId);
            } else {
                log.trace("Check result not found for metadata id: " + metadataId + ", suiteId: " + suiteId
                    + ", checkId: " + checkId);
            }
        } catch ( Exception e ) {
            log.error( e.getClass().getName()+": "+ e.getMessage());
            e.printStackTrace();
            me.initCause(e);
            throw(me);
        }
        return(result);
    }

    /*
     * Save a single check result. Note that a run must first be saved before saving the associated
     * check results for that run (due to foreign key constraint).
     */
    public void saveResult(Result result, String metadataId, String suiteId) throws MetadigStoreException {

        PreparedStatement stmt = null;
        Check check = result.getCheck();
        String checkId = check.getId();
        String checkName = check.getName();
        String checkType = check.getType();
        String checkLevel = check.getLevel().toString();
        String status = result.getStatus().toString();
        List<String> output = new ArrayList<>();
        // Convert the result 'output' values to a list of strings
        for(Output o: result.getOutput()) {
           output.add(o.getValue());
        }

        MetadigStoreException me = new MetadigStoreException("Unable save run result to the datdabase.");
        try {
            // Insert a result into the 'check_results' table and if it is already their, update it
            // with any new info, e.g. such as the 'status' that may have changed.

            String[] outputList = output.toArray(new String[0]);
            Array outputArray = conn.createArrayOf("text", outputList);

            String sql = "INSERT INTO check_results (metadata_id, suite_id, check_id,"
                    + " check_name, check_type, check_level, status, output) "
                    + " VALUES (?, ?, ?, ?, ?, ?, ?, ?)"
                    + " ON CONFLICT ON CONSTRAINT checks_metadata_id_suite_id_check_id_pk "
                    + " DO UPDATE SET (check_name, check_type, check_level, status, output)"
                    + " = (?, ?, ?, ?, ?);";

            stmt = conn.prepareStatement(sql);
            stmt.setString(1, metadataId);
            stmt.setString(2, suiteId);
            stmt.setString(3, checkId);
            stmt.setString(4, checkName);
            stmt.setString(5, checkType);
            stmt.setString(6, checkLevel);
            stmt.setString(7, status);
            stmt.setArray(8, outputArray);
            // For the 'conflict' clause
            stmt.setString(9, checkName);
            stmt.setString(10, checkType);
            stmt.setString(11, checkLevel);
            stmt.setString(12, status);
            stmt.setArray(13, outputArray);
            stmt.executeUpdate();
            stmt.close();
            conn.commit();
            //conn.close();
        } catch (SQLException e) {
            log.error( e.getClass().getName()+": "+ e.getMessage());
            me.initCause(e);
            throw(me);
        }

        log.trace("Check result saved successfully");
    }

    /*
     * Get a single run from the 'runs' table.
     */
    @Override
    public Run getRun(String metadataId, String suiteId) throws MetadigStoreException  {
        //return runs.get(id);
        Run run = null;
        Result result = new Result();
        PreparedStatement stmt = null;
        String mId = null;
        String sId = null;
        String seqId = null;
        Boolean isLatest = false;
        String resultStr = null;

        // Hope for the best, prepare for the worst!
        MetadigStoreException me = new MetadigStoreException("Unable get quality report to the datdabase.");
        // Select records from the 'runs' table
        try {
            log.trace("preparing statement for query");
            String sql = "select * from runs where metadata_id = ? and suite_id = ?";
            stmt = conn.prepareStatement(sql);
            stmt.setString(1, metadataId);
            stmt.setString(2, suiteId);

            log.trace("issuing query: " + sql);
            ResultSet rs = stmt.executeQuery();
            if(rs.next()) {
                mId = rs.getString("metadata_id");
                sId = rs.getString("suite_id");
                //seqId = rs.getString("sequence_id");
                //isLatest = rs.getBoolean("is_latest");
                resultStr = rs.getString("results");
                rs.close();
                stmt.close();
                // Convert the returned run xml document to a 'run' object.
                InputStream is = new ByteArrayInputStream(resultStr.getBytes());
                run = TypeMarshaller.unmarshalTypeFromStream(Run.class, is);
                // Note: These fields are in the Solr index, but don't need to be in the run XML, so
                // have to be manually added after the JAXB marshalling has created the run object.
                //run.setSequenceId(seqId);
                //run.setIsLatest(isLatest);
                log.trace("Retrieved run successfully for metadata id: " + run.getObjectIdentifier());
            } else {
                log.trace("Run not found for metadata id: " + metadataId + ", suiteId: " + suiteId);
            }
        } catch ( Exception e ) {
            log.error( e.getClass().getName()+": "+ e.getMessage());
            e.printStackTrace();
            me.initCause(e);
            throw(me);
        }

        return(run);
    }

    /*
     * Save a single run, first populating the 'pids' table, then the 'runs' table.
     */
    public void saveRun(Run run) throws MetadigStoreException {
        //runs.put(run.getId(), run);

        log.debug("Saving run to db for pid: " + run.getObjectIdentifier());
        String datasource = null;
        PreparedStatement stmt = null;
        //SysmetaModel sysmeta = run.getSysmeta();
        //if(sysmeta != null) {
        //    datasource = sysmeta.getOriginMemberNode();
        //}
        String metadataId = run.getObjectIdentifier();
        String suiteId = run.getSuiteId();
        String status = run.getRunStatus();
        String error = run.getErrorDescription();
        //String sequenceId = run.getSequenceId();
        //Boolean isLatest = run.getIsLatest();
        String resultStr = null;
        Timestamp dateTime = Timestamp.from(Instant.now());
        run.setTimestamp(dateTime);

        String runStr = null;

        MetadigStoreException me = new MetadigStoreException("Unable save quality report to the datdabase.");
        try {
            // JAXB annotations (e.g. in Check.java) are unable to prevent marshalling from performing
            // XML special character encoding (i.e. '"' to '&quot', so we have to unescape
            // these character for the entire report here.
            runStr = XmlMarshaller.toXml(run, true);
            log.debug("run marshalled to xml");
        } catch (JAXBException e) {
            e.printStackTrace();
            me.initCause(e);
            throw(me);
        } catch (UnsupportedEncodingException e) {
            e.printStackTrace();
            me.initCause(e);
            throw(me);
        }

        // Perform an 'upsert' on the 'runs' table - if a record exists for the 'metadata_id, suite_id' already,
        // then update the record with the incoming data.
        try {
            String sql = "INSERT INTO runs (metadata_id, suite_id, timestamp, results, status, error) VALUES (?, ?, ?, ?, ?, ?)"
                    + " ON CONFLICT ON CONSTRAINT runs_metadata_id_suite_id_pk "
                    + " DO UPDATE SET (timestamp, results, status, error) = (?, ?, ?, ?);";

            stmt = conn.prepareStatement(sql);
            stmt.setString(1, metadataId);
            stmt.setString(2, suiteId);
            stmt.setTimestamp(3, dateTime);
            stmt.setString(4, runStr);
            stmt.setString(5, status);
            stmt.setString(6, error);
            //stmt.setString(7, sequenceId);
            //stmt.setBoolean(8, isLatest);
            // For 'on conflict'
            stmt.setTimestamp(7, dateTime);
            stmt.setString(8, runStr);
            stmt.setString(9, status);
            stmt.setString(10, error);
            //stmt.setString(11, sequenceId);
            //stmt.setBoolean(14, isLatest);
            stmt.executeUpdate();
            stmt.close();
            conn.commit();
            //conn.close();
            log.debug("run committed to db");
        } catch (SQLException e) {
            log.error( e.getClass().getName()+": "+ e.getMessage());
            me.initCause(e);
            throw(me);
        }

        // Next, insert a record into the child table ('runs')
        log.debug("Records created successfully");
    }

    /*
     * Check if the connection to the database server is usable.
     */
    public boolean isAvailable() {
        boolean reachable = false;
        log.trace("Checking if store (i.e. sql connection) is available.");
        try {
            reachable = conn.isValid(10);
        } catch (Exception e ) {
            log.error("Error checking database connection: " + e.getMessage());
        }
        return reachable;
    }

    /*
     * Reset the store, i.e. renew the database connection.
     */
    public void renew() throws MetadigStoreException {
        if(!this.isAvailable()) {
            log.trace("Renewing connection to database");
            this.init();
        }
    }

    public void shutdown() {

        try {
            conn.close();
            log.trace("Successfully closed database");
        } catch ( java.sql.SQLException e) {
            log.error("Error closing database: " + e.getMessage());
        }
    }

    public void saveTask(Task task, String nodeId) throws MetadigStoreException {

        PreparedStatement stmt = null;

        // Perform an 'upsert' on the 'nodes' table - if a record exists for the 'metadata_id, suite_id' already,
        // then update the record with the incoming data.
        try {
            String sql = "INSERT INTO tasks (task_name, task_type) VALUES (?, ?)"
                    + " ON CONFLICT ON CONSTRAINT task_name_task_type"
                    + " DO NOTHING";

            stmt = conn.prepareStatement(sql);
            stmt.setString(1, task.getTaskName());
            stmt.setString(2, task.getTaskType());
            stmt.executeUpdate();
            stmt.close();
            conn.commit();
            saveNodeHarvest(task, nodeId);
            //conn.close();
        } catch (SQLException e) {
            log.error( e.getClass().getName()+": "+ e.getMessage());
            MetadigStoreException me = new MetadigStoreException("Unable save last harvest date to the datdabase.");
            me.initCause(e);
            throw(me);
        }

        // Next, insert a record into the child table ('runs')
        log.trace("Records created successfully");
    }

    public Task getTask(String taskName, String taskType, String nodeId) {

        //return runs.get(id);
        Result result = new Result();
        PreparedStatement stmt = null;
        String lastDT = null;
        Task task = new Task();

        // Select records from the 'nodes' table
        try {
            log.trace("preparing statement for query");
            String sql = "select * from tasks where task_name = ? and task_type = ?";
            stmt = conn.prepareStatement(sql);
            stmt.setString(1, taskName);
            stmt.setString(2, taskType);

            log.trace("issuing query: " + sql);
            ResultSet rs = stmt.executeQuery();
            if(rs.next()) {
                task.setTaskName(rs.getString("task_name"));
                task.setTaskType(rs.getString("task_type"));
                rs.close();
                stmt.close();
            } else {
                log.trace("No results returned from query");
            }

            task.setLastHarvestDatetimes(getNodeHarvestDatetimes(task.getTaskName(), task.getTaskType(), nodeId));
        } catch ( Exception e ) {
            log.error( e.getClass().getName()+": "+ e.getMessage());
        }

        return(task);
    }

    public HashMap<String,String> getNodeHarvestDatetimes(String taskName, String taskType, String nodeId) {

        //return runs.get(id);
        Result result = new Result();
        PreparedStatement stmt = null;
        String lastDT = null;
        Task task = new Task();

        HashMap<String, String> nodeHarvestDates  = new HashMap<>();
        // Select records from the 'nodes' table
        try {
            String sql = "select * from node_harvest where task_name = ? and task_type = ? and node_id = ?";
            stmt = conn.prepareStatement(sql);
            stmt.setString(1, taskName);
            stmt.setString(2, taskType);
            stmt.setString(3, nodeId);

            log.trace("issuing query: " + sql);
            ResultSet rs = stmt.executeQuery();
            while (rs.next()) {
                nodeHarvestDates.put(nodeId, rs.getString("last_harvest_datetime"));
            }
            rs.close();
            stmt.close();
        } catch ( Exception e ) {
            log.error( e.getClass().getName()+": "+ e.getMessage());
        }

        return(nodeHarvestDates);
    }


    public void saveNodeHarvest(Task task, String nodeId) throws MetadigStoreException {

        PreparedStatement stmt = null;

        // Perform an 'upsert' on the 'nodes' table - if a record exists for the 'metadata_id, suite_id' already,
        // then update the record with the incoming data.
        try {
            String sql = "INSERT INTO node_harvest (task_name, task_type, node_id, last_harvest_datetime) VALUES (?, ?, ?, ?)"
                    + " ON CONFLICT ON CONSTRAINT node_harvest_task_name_task_type_node_id_uc"
                    + " DO UPDATE SET (task_name, task_type, node_id, last_harvest_datetime) = (?, ?, ?, ?);";

            stmt = conn.prepareStatement(sql);
            stmt.setString(1, task.getTaskName());
            stmt.setString(2, task.getTaskType());
            stmt.setString(3, nodeId);
            stmt.setString(4, task.getLastHarvestDatetime(nodeId));
            stmt.setString(5, task.getTaskName());
            stmt.setString(6, task.getTaskType());
            stmt.setString(7, nodeId);
            stmt.setString(8, task.getLastHarvestDatetime(nodeId));
            stmt.executeUpdate();
            stmt.close();
            conn.commit();
            //conn.close();
        } catch (SQLException e) {
            log.error( e.getClass().getName()+": "+ e.getMessage());
            MetadigStoreException me = new MetadigStoreException("Unable save last harvest date to the datdabase.");
            me.initCause(e);
            throw(me);
        }

        // Next, insert a record into the child table ('runs')
        log.trace("Records created successfully");
    }

    public void saveNode(Node node) throws MetadigStoreException {

        PreparedStatement stmt = null;

        // Perform an 'upsert' on the 'nodes' table - if a record exists for the 'metadata_id, suite_id' already,
        // then update the record with the incoming data.
        try {
            String sql = "INSERT INTO nodes " +
                    " (identifier, name, type, state, synchronize, last_harvest, baseURL) VALUES (?, ?, ?, ?, ?, ?, ?) " +
                    " ON CONFLICT ON CONSTRAINT node_id_pk DO UPDATE SET " +
                    " (identifier, name, type, state, synchronize, last_harvest, baseURL) = (?, ?, ?, ?, ?, ?, ?);";

            DateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'");
            dateFormat.setTimeZone(TimeZone.getTimeZone("GMT"));
            String lastHarvestDatetimeStr = dateFormat.format(node.getSynchronization().getLastHarvested());

            stmt = conn.prepareStatement(sql);
            stmt.setString(1, node.getIdentifier().getValue());
            stmt.setString(2, node.getName());
            stmt.setString(3, node.getType().toString());
            stmt.setString(4, node.getState().toString());
            stmt.setBoolean(5, node.isSynchronize());
            stmt.setString(6, lastHarvestDatetimeStr);
            stmt.setString(7, node.getBaseURL());
            stmt.setString(8, node.getIdentifier().getValue());
            stmt.setString(9, node.getName());
            stmt.setString(10, node.getType().toString());
            stmt.setString(11, node.getState().toString());
            stmt.setBoolean(12, node.isSynchronize());
            stmt.setString(13, lastHarvestDatetimeStr);
            stmt.setString(14, node.getBaseURL());
            stmt.executeUpdate();
            stmt.close();
            conn.commit();
        } catch (SQLException e) {
            log.error( e.getClass().getName()+": "+ e.getMessage());
            MetadigStoreException me = new MetadigStoreException("Unable to save node " + node.getIdentifier().getValue() + " to database.");
            me.initCause(e);
            throw(me);
        }

        // Next, insert a record into the child table ('runs')
        log.trace("Records created successfully");
    }

      public Node getNode(String nodeId) {

        Result result = new Result();
        PreparedStatement stmt = null;
        Node node = new Node();

        // Select records from the 'nodes' table
        try {
            log.trace("preparing statement for query");
            String sql = "select * from nodes where identifier = ? ";
            stmt = conn.prepareStatement(sql);
            stmt.setString(1, nodeId);

            log.trace("issuing query: " + sql);
            ResultSet rs = stmt.executeQuery();
            if(rs.next()) {
                node = extractNodeFields(rs);
                rs.close();
                stmt.close();
            } else {
                log.trace("No results returned for nodeId: " + nodeId);
            }
        } catch ( Exception e ) {
            log.error( e.getClass().getName()+": "+ e.getMessage());
        }

        return(node);
    }

    public ArrayList<Node> getNodes() {

        Result result = new Result();
        PreparedStatement stmt = null;

        ArrayList<Node> nodes = new ArrayList<> ();
        ResultSet rs = null;
        Node node;
        // Select records from the 'nodes' table
        try {
            log.trace("preparing statement for query");
            String sql = "select * from nodes; ";
            stmt = conn.prepareStatement(sql);

            log.trace("issuing query: " + sql);
            rs = stmt.executeQuery();
            while(rs.next()) {
                node = extractNodeFields(rs);
                nodes.add(node);
            }
        } catch ( Exception e ) {
            log.error(e.getClass().getName() + ": " + e.getMessage());
        }

        try {
            rs.close();
            stmt.close();
        } catch (Exception e) {
            log.error("Error closing node database: " +  e.getMessage());
        }

        log.trace(nodes.size() + " nodes found in node table.");

        return(nodes);
    }

    /**
     * Export a suite run result to an external file
     * @param args  arguments for the app
     * @throws Exception  all application exceptions
     */
    public ArrayList<String> exportResults (String suiteId, String nodeId, String formats, String outputFilePath) {
        File tmpfile = File.createTempFile("scorefile-", ".csv");
        log.debug("Creating score file: " + tmpfile);
        Boolean append = true;
        PreparedStatement stmt = null;
        FileWriter fileWriter = new FileWriter(tmpfile, append);
        // TODO: Pass param or detect suite, so we know what 'scoreByType' fields to create header columns for
        CSVPrinter csvPrinter = new CSVPrinter(fileWriter, CSVFormat.RFC4180.withHeader(
                "pid", "formatId", "dateUploaded", "datasource", "scoreOverall",
                "scoreFindable", "scoreAccessible", "scoreInteroperable", "scoreReusable",
                "obsoletes", "obsoletedBy", "sequenceId"));

        String query = "select c.check_id,c.check_name,c.check_type,c.check_level,c.status," +
                "i.data_source,i.metadata_id,i.obsoleted_by from identifiers i left " +
                "join check_results c on i.metadata_id = c.metadata_id AND c.suite_id=? AND data_source=?";

        // Select records from the 'nodes' table
        try {
            stmt = conn.prepareStatement(query);
            stmt.setString(1, suiteId);
            stmt.setString(2, nodeId);

            // Set number of result rows to return per fetch.
            stmt.setFetchSize(50000);

            log.trace("issuing query: " + query);
            ResultSet rs = stmt.executeQuery();

            while (rs.next()) {
                csvPrinter.printRecord(
                    rs.getString("c.check_id");
                    rs.getString("c.check_name");
                    rs.getString("c.check_type");
                    rs.getString("c.check_level");
                    rs.getString("c.status");
                    rs.getString("i.data_source");
                    rs.getString("i.metadata_id");
                    rs.getString("i.obsoleted_by"));
            }
            rs.close();
            stmt.close();
        } catch ( Exception e ) {
            log.error( e.getClass().getName()+": "+ e.getMessage());
        }
    }

    public Node extractNodeFields (ResultSet resultSet) {

        Node node = new Node();
        try {
            NodeReference nodeReference = new NodeReference();
            nodeReference.setValue(resultSet.getString("identifier"));
            node.setIdentifier(nodeReference);
            node.setName(resultSet.getString("name"));
            switch (resultSet.getString("type")) {
                case "CN":
                    node.setType(NodeType.CN);
                    break;
                case "MN":
                    node.setType(NodeType.MN);
                    break;
                case "MONITOR":
                    node.setType(NodeType.MONITOR);
                    break;
            }

            switch (resultSet.getString("state")) {
                case "UP":
                    node.setState(NodeState.UP);
                    break;
                case "DOWN":
                    node.setState(NodeState.DOWN);
                    break;
                default:
                    node.setState(NodeState.UNKNOWN);
                    break;
            }

            node.setSynchronize(resultSet.getBoolean("synchronize"));

            Synchronization synchronization = new Synchronization();
            SimpleDateFormat formatter = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'");
            formatter.setTimeZone(TimeZone.getTimeZone("GMT"));
            Date lastHarvestDate = formatter.parse(resultSet.getString("last_harvest"));
            synchronization.setLastHarvested(lastHarvestDate);
            node.setSynchronization(synchronization);

            node.setBaseURL(resultSet.getString("baseURL"));
        } catch (java.sql.SQLException | java.text.ParseException e) {
            log.error("Error retrieving node from database: " + e);
        }

        return node;
    }

    @Override
    public void createRun(Run run) {
        runs.put(run.getId(), run);
    }

    @Override
    public void deleteRun(Run run) {
        runs.remove(run.getId());
    }

    @Override
    public Collection<String> listSuites() {
        return suites.keySet();
    }

    @Override
    public Suite getSuite(String id) {
        return suites.get(id);
    }

    @Override
    public void createSuite(Suite rec) {
        suites.put(rec.getId(), rec);
    }

    @Override
    public void updateSuite(Suite rec) {
        suites.put(rec.getId(), rec);
    }

    @Override
    public void deleteSuite(Suite rec) {
        suites.remove(rec.getId());
    }

    @Override
    public Collection<String> listChecks() {
        return checks.keySet();
    }

    @Override
    public Check getCheck(String id) {
        return checks.get(id);
    }

    @Override
    public void createCheck(Check check) {
        checks.put(check.getId(), check);
    }

    @Override
    public void updateCheck(Check check) {
        checks.put(check.getId(), check);
    }

    @Override
    public void deleteCheck(Check check) {
        checks.remove(check.getId());
    }
}
