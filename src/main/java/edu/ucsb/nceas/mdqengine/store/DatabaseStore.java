package edu.ucsb.nceas.mdqengine.store;

import edu.ucsb.nceas.mdqengine.MDQStore;
import edu.ucsb.nceas.mdqengine.MDQconfig;
import edu.ucsb.nceas.mdqengine.exception.MetadigStoreException;
import edu.ucsb.nceas.mdqengine.model.Check;
import edu.ucsb.nceas.mdqengine.model.Result;
import edu.ucsb.nceas.mdqengine.model.Run;
import edu.ucsb.nceas.mdqengine.model.Suite;
import edu.ucsb.nceas.mdqengine.serialize.XmlMarshaller;
import org.apache.commons.configuration2.ex.ConfigurationException;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.dataone.configuration.Settings;
import org.dataone.service.types.v2.SystemMetadata;
import org.dataone.service.util.TypeMarshaller;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.xml.sax.SAXException;

import javax.sql.DataSource;
import javax.xml.bind.JAXBException;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.net.URL;
import java.sql.*;
import java.time.Instant;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

/**
 * Persistent storage for quality runs.
 * @author slaughter
 *
 */
public class DatabaseStore implements MDQStore {

    protected Log log = LogFactory.getLog(this.getClass());

    //String dbUrl = "jdbc:postgresql://localhost:5432/metadig";
    private static String dbUrl = null;

    Map<String, Suite> suites = new HashMap<String, Suite>();

    Map<String, Check> checks = new HashMap<String, Check>();

    Map<String, Run> runs = new HashMap<String, Run>();

    Connection conn = null;

    DataSource dataSource = null;

    public DatabaseStore () throws MetadigStoreException {
        log.debug("Initializing a new DatabaseStore to " + dbUrl + ".");
        this.init();
    }

    /*
     * Get a connection to the database that contains the quality reports.
     */
    private void init() throws MetadigStoreException {

        MDQconfig cfg = new MDQconfig();
        try {
            dbUrl = cfg.getString("jdbc.url");
        } catch (ConfigurationException ce) {
            log.error(ce.getMessage());
            MetadigStoreException mse = new MetadigStoreException("Unable to create new Store");
            mse.initCause(ce.getCause());
            throw mse;
        }

        try {
            Class.forName("org.postgresql.Driver");
            conn = DriverManager.getConnection(dbUrl,"metadig", "metadig");
            conn.setAutoCommit(false);
        } catch (Exception e) {
            e.printStackTrace();
            log.error(e.getClass().getName()+": "+e.getMessage());
            MetadigStoreException mse = new MetadigStoreException("Unable to create the database store.");
            mse.initCause(e);
            throw(mse);
        }

        // For now, load checks into memory from the distribution jar file
        String additionalDir = Settings.getConfiguration().getString("mdq.store.directory", null);

        PathMatchingResourcePatternResolver resolver = new PathMatchingResourcePatternResolver();

        Resource[] suiteResources = null;
        // load all the resources from local files
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
                    //log.debug("Loading suite found at: " + url.toString());
                    String xml = IOUtils.toString(url.openStream(), "UTF-8");
                    suite = (Suite) XmlMarshaller.fromXml(xml, Suite.class);
                } catch (JAXBException | IOException | SAXException e) {
                    //log.warn("Could not load suite '" + resource.getFilename() + "' due to an error: " + e.getMessage() + ".");
                    continue;
                }
                this.createSuite(suite);

            }
        }
        log.debug("Initialized databasestore: opened database successfully");
    }

    @Override
    public Collection<String> listRuns() {
        return runs.keySet();
    }

    /*
     * Get a single run from the 'runs' table.
     */
    @Override
    public Run getRun(String metadataId, String suiteId) {
        //return runs.get(id);
        Run run = null;
        Result result = new Result();
        PreparedStatement stmt = null;
        String mId = null;
        String sId = null;
        String resultStr = null;

        // Select records from the 'runs' table
        try {
            log.debug("preparing statement for query");
            String sql = "select * from runs where metadata_id = ? and suite_id = ?";
            stmt = conn.prepareStatement(sql);
            stmt.setString(1, metadataId);
            stmt.setString(2, suiteId);

            log.debug("issuing query: " + sql);
            ResultSet rs = stmt.executeQuery();
            if(rs.next()) {
                mId = rs.getString("metadata_id");
                sId = rs.getString("suite_id");
                resultStr = rs.getString("results");
                rs.close();
                stmt.close();
                // Convert the returned run xml document to a 'run' object.
                InputStream is = new ByteArrayInputStream(resultStr.getBytes());
                run = TypeMarshaller.unmarshalTypeFromStream(Run.class, is);
                log.debug("Retrieved run successfully, id from run object: " + run.getId());
            } else {
                log.debug("No results returned from query");
            }
        } catch ( Exception e ) {
            log.error( e.getClass().getName()+": "+ e.getMessage());
        }

        return(run);
    }

    /*
     * Save a single run, first populating the 'pids' table, then the 'runs' table.
     */
    public void saveRun(Run run, SystemMetadata sysmeta) throws MetadigStoreException {
        //runs.put(run.getId(), run);

        PreparedStatement stmt = null;
        String metadataId = sysmeta.getIdentifier().getValue().toString();
        String suiteId = run.getSuiteId();
        String datasource = sysmeta.getOriginMemberNode().getValue().toString();
        String resultStr = null;
        //DateTime now = new DateTime();
        //OffsetDateTime dateTime = OffsetDateTime.now();
        Timestamp dateTime = Timestamp.from(Instant.now());

        String runStr = null;

        MetadigStoreException me = new MetadigStoreException("Unable save quality report to the datdabase.");
        try {
            runStr = XmlMarshaller.toXml(run);
        } catch (JAXBException e) {
            e.printStackTrace();
            me.initCause(e);
            throw(me);
        } catch (UnsupportedEncodingException e) {
            e.printStackTrace();
            me.initCause(e);
            throw(me);
        }

        // First, insert a record into the main table ('pids')
        try {
            // Insert a pid into the 'identifiers' table and if it is already their, update it
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
            //conn.close();
        } catch (SQLException e) {
            log.error( e.getClass().getName()+": "+ e.getMessage());
            me.initCause(e);
            throw(me);
        }

        // Perform an 'upsert' on the 'runs' table - if a record exists for the 'metadata_id, suite_id' already,
        // then update the record with the incoming data.
        try {
            String sql = "INSERT INTO runs (metadata_id, suite_id, timestamp, results) VALUES (?, ?, ?, ?)"
                    + " ON CONFLICT ON CONSTRAINT metadata_id_suite_id_fk "
                    + " DO UPDATE SET (timestamp, results) = (?, ?);";

            stmt = conn.prepareStatement(sql);
            stmt.setString(1, metadataId);
            stmt.setString(2, suiteId);
            stmt.setTimestamp(3, dateTime);
            stmt.setString(4, runStr);
            // For 'on conflict'
            stmt.setTimestamp(5, dateTime);
            stmt.setString(6, runStr);
            stmt.executeUpdate();
            stmt.close();
            conn.commit();
            //conn.close();
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
        try {
            reachable = conn.isValid(10);
        } catch (java.sql.SQLException jse) {
            log.error("Error checking database connection: " + jse.getMessage());
        }
        return reachable;
    }

    /*
     * Reset the store, i.e. renew the database connection.
     */
    public void renew() throws MetadigStoreException {
        if(!this.isAvailable()) {
            log.debug("Renewing connection to database");
            this.init();
        }
    }

    public void shutdown() {

        try {
            conn.close();
            log.debug("Successfully closed database");
        } catch ( java.sql.SQLException e) {
            log.error("Error closing database: " + e.getMessage());
        }
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
