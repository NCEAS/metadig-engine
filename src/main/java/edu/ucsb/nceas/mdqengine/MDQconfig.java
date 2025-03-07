package edu.ucsb.nceas.mdqengine;

import org.apache.commons.configuration2.Configuration;
import org.apache.commons.configuration2.builder.fluent.Configurations;
import org.apache.commons.configuration2.ex.ConfigurationException;
import org.apache.commons.io.IOUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Iterator;

public class MDQconfig {

    private static String configFilePath = "/opt/local/metadig/metadig.properties";
    private static Log log = LogFactory.getLog(MDQconfig.class);

    public static Configuration config;

    public MDQconfig() throws ConfigurationException, IOException {
        boolean inServlet = false;

        // If running in a servlet, have to get the config info from the webapp context,
        // as we can't
        // read from external dirs on disk.
        Configurations configs = new Configurations();
        if (inServlet) {
            InputStream inputStream = this.getClass().getClassLoader().getResourceAsStream("/metadig.properties");
            String TMP_DIR = System.getProperty("java.io.tmpdir");
            File tempFile = new File(TMP_DIR + "/metadig.properties");
            log.trace("Reading config properties in servlet from: " + tempFile);
            FileOutputStream out = new FileOutputStream(tempFile);
            IOUtils.copy(inputStream, out);
            config = configs.properties(tempFile);
        } else {
            log.trace("Reading config properties from: " + configFilePath);
            config = configs.properties(new File(configFilePath));
        }
    }

    /**
     * Read a configuration file for a String parameter values.
     */
    public String getString(String paramName) throws ConfigurationException {
        return (config.getString(paramName));
    }

    /**
     * Read a configuration file for a String parameter values.
     */
    public int getInt(String paramName) throws ConfigurationException {
        return (config.getInt(paramName));
    }

    /**
     * Read a configuration file and return all the keys.
     */
    public Iterator<String> getKeys() {
        return (config.getKeys());
    }

    public static String readConfigParam(String paramName) throws ConfigurationException, IOException {
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
