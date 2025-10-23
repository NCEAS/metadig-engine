package edu.ucsb.nceas.mdqengine.store;

import edu.ucsb.nceas.mdqengine.MDQconfig;
import edu.ucsb.nceas.mdqengine.exception.MetadigStoreException;
import edu.ucsb.nceas.mdqengine.model.*;
import edu.ucsb.nceas.mdqengine.serialize.XmlMarshaller;
import org.apache.commons.configuration2.ex.ConfigurationException;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.dataone.service.types.v1.*;
import org.dataone.service.types.v2.Node;
import org.dataone.service.util.TypeMarshaller;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.xml.sax.SAXException;

import javax.sql.DataSource;
import javax.xml.bind.JAXBException;
import javax.xml.parsers.ParserConfigurationException;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.sql.*;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.time.Instant;
import java.util.*;
import java.util.Date;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Persistent storage for quality runs.
 * 
 * @author slaughter
 *
 */
public class DatabaseStore implements MDQStore, AutoCloseable {

    protected Log log = LogFactory.getLog(this.getClass());

    private static String dbUrl = null;
    private static String dbUser = null;
    private static String dbPasswd = null;

    private Map<String, Suite> suites = new HashMap<String, Suite>();
    private Map<String, Check> checks = new HashMap<String, Check>();
    private Map<String, Run> runs = new HashMap<String, Run>();
    private Connection conn = null;
    private DataSource dataSource = null;

    public DatabaseStore() throws MetadigStoreException {
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

        } catch (ConfigurationException | IOException ex) {
            log.error(ex.getMessage());
            MetadigStoreException mse = new MetadigStoreException("Unable to create new Store");
            mse.initCause(ex.getCause());
            throw mse;
        }

        try {
            Properties props = new Properties();
            props.setProperty("user", dbUser);
            props.setProperty("password", dbPasswd);
            props.setProperty("prepareThreshold", "0");
            // props.setProperty("extra_float_digits", "2");
            Class.forName("org.postgresql.Driver");
            conn = DriverManager.getConnection(dbUrl, props);
            conn.setAutoCommit(false);
        } catch (Exception e) {
            e.printStackTrace();
            log.error(e.getClass().getName() + ": " + e.getMessage());
            MetadigStoreException mse = new MetadigStoreException("Unable to create the database store.");
            mse.initCause(e);
            throw (mse);
        }

        log.trace("Connection initialized");

        PathMatchingResourcePatternResolver resolver = new PathMatchingResourcePatternResolver();

        Resource[] suiteResources = null;
        // load all metadig quality suites and checks from local files, if available
        try {
            suiteResources = resolver.getResources("classpath*:/suites/*.xml");
            // do we have an additional location for these?
            if (additionalDir != null) {
                Resource[] additionalSuiteResources = resolver
                        .getResources("file://" + additionalDir + "/suites/*.xml");
                suiteResources = (Resource[]) ArrayUtils.addAll(suiteResources, additionalSuiteResources);
            }
        } catch (IOException e) {
            log.error("Could not read local suite resources: " + e.getMessage(), e);
        }
        if (suiteResources != null) {
            for (Resource resource : suiteResources) {
                Suite suite = null;
                try {
                    URL url = resource.getURL();
                    String xml = IOUtils.toString(url.openStream(), "UTF-8");
                    suite = (Suite) XmlMarshaller.fromXml(xml, Suite.class);
                } catch (ParserConfigurationException | JAXBException | IOException | SAXException e) {
                    log.error("Could not load suite.");
                    continue;
                }
                this.createSuite(suite);

            }
        }
        if (this.isAvailable()) {
            log.trace("Initialized database store: opened database successfully");
        } else {
            throw new MetadigStoreException("Error initializing database, connection not available");
        }
    }

    @Override
    public Collection<String> listRuns() {
        return runs.keySet();
    }

    /*
     * Get a single run from the 'runs' table.
     */
    @Override
    public Run getRun(String metadataId, String suiteId) throws MetadigStoreException {
        // return runs.get(id);
        Run run = null;
        Result result = new Result();
        PreparedStatement stmt = null;
        String seqId = null;
        boolean isLatest;
        Integer runCount = null;
        String resultStr = null;

        // Hope for the best, prepare for the worst!
        MetadigStoreException me = new MetadigStoreException("Unable get quality report to the database.");
        // Select records from the 'runs' table
        try {
            log.trace("preparing statement for query");
            String sql = "select * from runs where metadata_id = ? and suite_id = ?";
            stmt = conn.prepareStatement(sql);
            stmt.setString(1, metadataId);
            stmt.setString(2, suiteId);

            log.trace("issuing query: " + sql);
            ResultSet rs = stmt.executeQuery();
            if (rs.next()) {
                seqId = rs.getString("sequence_id");
                isLatest = rs.getBoolean("is_latest");
                runCount = rs.getInt("run_count");
                resultStr = rs.getString("results");
                rs.close();
                stmt.close();

                // Convert the returned run xml document to a 'run' object.
                try {
                    // try to use the schema
                    run = (Run) XmlMarshaller.fromXml(resultStr, Run.class);
                } catch (Exception e) {
                    // a bunch of old docs don't conform to any schema, so if above fails
                    // just unmarshall according to the class definition
                    // Convert the returned run xml document to a 'run' object.

                    // first migrate the schema forward
                    String ns = XmlMarshaller.getRootNamespace(resultStr);

                    if ("https://nceas.ucsb.edu/mdqe/v1".equals(ns) || "https://nceas.ucsb.edu/mdqe/v1.1".equals(ns)) {
                        // Replace known older namespaces with v1.2
                        resultStr = resultStr.replace("https://nceas.ucsb.edu/mdqe/v1",
                                "https://nceas.ucsb.edu/mdqe/v1.2");
                        resultStr = resultStr.replace("https://nceas.ucsb.edu/mdqe/v1.1",
                                "https://nceas.ucsb.edu/mdqe/v1.2");
                    }

                    InputStream is = new ByteArrayInputStream(resultStr.getBytes());
                    run = TypeMarshaller.unmarshalTypeFromStream(Run.class, is);
                }

                //
                // Note: These fields are in the Solr index, but don't need to be in the run
                // XML, so have to be manually added after the JAXB marshalling has created the
                // run object.
                run.setObjectIdentifier(metadataId);
                run.setSuiteId(suiteId);
                run.setRunCount(runCount);
                run.setSequenceId(seqId);
                run.setIsLatest(isLatest);
                log.trace("Retrieved run successfully for metadata id: " + run.getObjectIdentifier());
            } else {
                log.trace("Run not found for metadata id: " + metadataId + ", suiteId: " + suiteId);
            }
        } catch (Exception e) {
            log.error(e.getClass().getName() + ": " + e.getMessage());
            e.printStackTrace();
            me.initCause(e);
            throw (me);
        }

        return (run);
    }

    /**
     * Get a list of runs that are stuck with the processing status for more than
     * the processing time, configurable in metadig properties. These stuck runs
     * could occur if the Worker dies before finishing a run
     *
     * @return a List of Run objects
     * @throws MetadigStoreException if unable to query the DB
     */
    @Override
    public List<Run> listInProcessRuns() throws MetadigStoreException {

        List<Run> runs = new ArrayList<Run>();
        PreparedStatement stmt = null;
        String metadataId = null;
        String suiteId = null;
        String sequenceId = null;
        String status = null;
        String nodeId = null;
        Integer runCount = null;
        boolean isLatest;

        String processingTime = null; // configurable in metadig.properties, the number of hours to wait before
                                      // requeueing a run stuck in processing (eg: 24)

        try {
            MDQconfig cfg = new MDQconfig();
            processingTime = cfg.getString("quartz.monitor.processing.time");
        } catch (IOException | ConfigurationException e) {
            log.error("Could not read configuration.");
            return runs;
        }

        // Hope for the best, prepare for the worst!
        MetadigStoreException me = new MetadigStoreException("Unable get runs from the database.");
        // Select records from the 'runs' table
        try {
            log.trace("preparing statement for query");
            String sql = "SELECT * FROM runs JOIN identifiers ON runs.metadata_id = identifiers.metadata_id WHERE runs.status = 'processing' AND AGE(CURRENT_TIMESTAMP, runs.timestamp) > INTERVAL '"
                    + processingTime + " hours'";
            stmt = conn.prepareStatement(sql);
            log.trace("issuing query: " + sql);
            ResultSet rs = stmt.executeQuery();
            if (rs.next()) {
                do {
                    Run run = new Run();
                    metadataId = rs.getString("metadata_id");
                    suiteId = rs.getString("suite_id");
                    sequenceId = rs.getString("sequence_id");
                    isLatest = rs.getBoolean("is_latest");
                    status = rs.getString("status");
                    nodeId = rs.getString("data_source");
                    runCount = rs.getInt("run_count");

                    // populate the run object
                    run.setSequenceId(sequenceId);
                    run.setIsLatest(isLatest);
                    run.setObjectIdentifier(metadataId);
                    run.setSuiteId(suiteId);
                    run.setStatus(status);
                    run.setNodeId(nodeId);
                    run.setRunCount(runCount);

                    runs.add(run); // add a run to the list
                } while (rs.next());

                rs.close();
                stmt.close();
            } else {
                log.trace("No processing runs found.");
            }
        } catch (SQLException e) {
            log.error(e.getClass().getName() + ": " + e.getMessage());
            e.printStackTrace();
            me.initCause(e);
            throw (me);
        }

        return (runs);
    }

    /*
     * Save a single run, first populating the 'pids' table, then the 'runs' table.
     */
    public void saveRun(Run run) throws MetadigStoreException {
        // runs.put(run.getId(), run);

        PreparedStatement stmt = null;
        String datasource = null;
        SysmetaModel sysmeta = run.getSysmeta();
        if (sysmeta != null) {
            datasource = sysmeta.getOriginMemberNode();
        } else {
            datasource = run.getNodeId();
        }
        String metadataId = run.getObjectIdentifier();
        String suiteId = run.getSuiteId();
        String status = run.getRunStatus();
        String error = run.getErrorDescription();
        String sequenceId = run.getSequenceId();
        Boolean isLatest = run.getIsLatest();
        Integer runCount = run.getRunCount();
        String resultStr = null;
        Timestamp dateTime = Timestamp.from(Instant.now());
        run.setTimestamp(dateTime);

        String runStr = null;

        MetadigStoreException me = new MetadigStoreException("Unable save quality report to the database.");
        try {
            // JAXB annotations (e.g. in Check.java) are unable to prevent marshalling from
            // performing
            // XML special character encoding (i.e. '"' to '&quot', so we have to unescape
            // these character for the entire report here.
            runStr = XmlMarshaller.toXml(run, true);
        } catch (JAXBException e) {
            e.printStackTrace();
            me.initCause(e);
            throw (me);
        } catch (UnsupportedEncodingException e) {
            e.printStackTrace();
            me.initCause(e);
            throw (me);
        }

        // First, insert a record into the main table ('pids')
        try {
            // Insert a pid into the 'identifiers' table and if it is already there, update
            // it
            // with any new info, e.g. the 'datasource' may have changed.
            String sql = "INSERT INTO identifiers (metadata_id, data_source) VALUES (?, ?)"
                    + " ON CONFLICT (metadata_id) DO UPDATE SET data_source = ?";
            stmt = conn.prepareStatement(sql);
            stmt.setString(1, metadataId);
            stmt.setString(2, datasource);
            // For the 'conflict' clause
            stmt.setString(3, datasource);
            stmt.executeUpdate();
            stmt.close();
            conn.commit();
            // conn.close();
        } catch (SQLException e) {
            log.error(e.getClass().getName() + ": " + e.getMessage());
            me.initCause(e);
            throw (me);
        }

        // Perform an 'upsert' on the 'runs' table - if a record exists for the
        // 'metadata_id, suite_id' already,
        // then update the record with the incoming data.
        try {
            String sql = "INSERT INTO runs (metadata_id, suite_id, timestamp, results, status, error, sequence_id, is_latest) VALUES (?, ?, ?, ?, ?, ?, ?, ?)"
                    + " ON CONFLICT ON CONSTRAINT metadata_id_suite_id_fk "
                    + " DO UPDATE SET (timestamp, results, status, error, sequence_id, is_latest, run_count) = (?, ?, ?, ?, ?, ?, ?);";

            stmt = conn.prepareStatement(sql);
            stmt.setString(1, metadataId);
            stmt.setString(2, suiteId);
            stmt.setTimestamp(3, dateTime);
            stmt.setString(4, runStr);
            stmt.setString(5, status);
            stmt.setString(6, error);
            stmt.setString(7, sequenceId);
            stmt.setBoolean(8, isLatest);
            // For 'on conflict'
            stmt.setTimestamp(9, dateTime);
            stmt.setString(10, runStr);
            stmt.setString(11, status);
            stmt.setString(12, error);
            stmt.setString(13, sequenceId);
            stmt.setBoolean(14, isLatest);
            stmt.setInt(15, runCount);
            stmt.executeUpdate();
            stmt.close();
            conn.commit();
            // conn.close();
        } catch (SQLException e) {
            log.error(e.getClass().getName() + ": " + e.getMessage());
            me.initCause(e);
            throw (me);
        }

        // Next, insert a record into the child table ('runs')
        log.trace("Records created successfully");
    }

    /*
     * Check if the connection to the database server is usable.
     */
    public boolean isAvailable() {
        boolean reachable = false;
        log.trace("Checking if store (i.e. sql connection) is available.");
        try {
            reachable = conn.isValid(10);
        } catch (Exception e) {
            log.error("Error checking database connection: " + e.getMessage());
        }
        return reachable;
    }

    /*
     * Reset the store, i.e. renew the database connection.
     */
    public void renew() throws MetadigStoreException {
        if (!this.isAvailable()) {
            log.trace("Renewing connection to database");
            this.init();
        }
    }

    public void close() {

        try {
            conn.close();
            log.trace("Successfully closed database");
        } catch (java.sql.SQLException e) {
            log.error("Error closing database: " + e.getMessage());
        }
    }

    public void saveTask(Task task, String nodeId) throws MetadigStoreException {

        PreparedStatement stmt = null;

        // Perform an 'upsert' on the 'nodes' table - if a record exists for the
        // 'metadata_id, suite_id' already,
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
            // conn.close();
        } catch (SQLException e) {
            log.error(e.getClass().getName() + ": " + e.getMessage());
            MetadigStoreException me = new MetadigStoreException("Unable save last harvest date to the database.");
            me.initCause(e);
            throw (me);
        }

        // Next, insert a record into the child table ('runs')
        log.trace("Records created successfully");
    }

    public Task getTask(String taskName, String taskType, String nodeId) {

        // return runs.get(id);
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
            if (rs.next()) {
                task.setTaskName(rs.getString("task_name"));
                task.setTaskType(rs.getString("task_type"));
                rs.close();
                stmt.close();
            } else {
                log.trace("No results returned from query");
            }

            task.setLastHarvestDatetimes(getNodeHarvestDatetimes(task.getTaskName(), task.getTaskType(), nodeId));
        } catch (Exception e) {
            log.error(e.getClass().getName() + ": " + e.getMessage());
        }

        return (task);
    }

    public HashMap<String, String> getNodeHarvestDatetimes(String taskName, String taskType, String nodeId) {

        // return runs.get(id);
        Result result = new Result();
        PreparedStatement stmt = null;
        String lastDT = null;
        Task task = new Task();

        HashMap<String, String> nodeHarvestDates = new HashMap<>();
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
        } catch (Exception e) {
            log.error(e.getClass().getName() + ": " + e.getMessage());
        }

        return (nodeHarvestDates);
    }

    public void saveNodeHarvest(Task task, String nodeId) throws MetadigStoreException {

        PreparedStatement stmt = null;

        // Perform an 'upsert' on the 'nodes' table - if a record exists for the
        // 'metadata_id, suite_id' already,
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
            // conn.close();
        } catch (SQLException e) {
            log.error(e.getClass().getName() + ": " + e.getMessage());
            MetadigStoreException me = new MetadigStoreException("Unable save last harvest date to the database.");
            me.initCause(e);
            throw (me);
        }

        // Next, insert a record into the child table ('runs')
        log.trace("Records created successfully");
    }

    public void saveNode(Node node) throws MetadigStoreException {

        PreparedStatement stmt = null;

        // Perform an 'upsert' on the 'nodes' table - if a record exists for the
        // 'metadata_id, suite_id' already,
        // then update the record with the incoming data.
        try {
            String sql = "INSERT INTO nodes " +
                    " (identifier, name, type, state, synchronize, last_harvest, baseURL) VALUES (?, ?, ?, ?, ?, ?, ?) "
                    +
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
            log.error(e.getClass().getName() + ": " + e.getMessage());
            MetadigStoreException me = new MetadigStoreException(
                    "Unable to save node " + node.getIdentifier().getValue() + " to database.");
            me.initCause(e);
            throw (me);
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
            if (rs.next()) {
                node = extractNodeFields(rs);
                rs.close();
                stmt.close();
            } else {
                log.trace("No results returned for nodeId: " + nodeId);
            }
        } catch (Exception e) {
            log.error(e.getClass().getName() + ": " + e.getMessage());
        }

        return (node);
    }

    public ArrayList<Node> getNodes() {

        Result result = new Result();
        PreparedStatement stmt = null;

        ArrayList<Node> nodes = new ArrayList<>();
        ResultSet rs = null;
        Node node;
        // Select records from the 'nodes' table
        try {
            log.trace("preparing statement for query");
            String sql = "select * from nodes; ";
            stmt = conn.prepareStatement(sql);

            log.trace("issuing query: " + sql);
            rs = stmt.executeQuery();
            while (rs.next()) {
                node = extractNodeFields(rs);
                nodes.add(node);
            }
        } catch (Exception e) {
            log.error(e.getClass().getName() + ": " + e.getMessage());
        }

        try {
            rs.close();
            stmt.close();
        } catch (Exception e) {
            log.error("Error closing node database: " + e.getMessage());
        }

        log.trace(nodes.size() + " nodes found in node table.");

        return (nodes);
    }

    public Node extractNodeFields(ResultSet resultSet) {

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
