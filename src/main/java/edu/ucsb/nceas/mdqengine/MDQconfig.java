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

public class MDQconfig {

    private static String configFilePath = "/etc/metadig/metadig.properties";
    private static Log log = LogFactory.getLog(MDQconfig.class);

    public static Configuration config;

    public MDQconfig () throws ConfigurationException, IOException {
        // Check if we are running in a servlet
        boolean inServlet = false;
        try {
            Class servletClass = Class.forName("javax.servlet.http.HttpServlet");
            inServlet = true;
            log.debug("Loaded javax.servlet.http.HttpServlet - running in servlet environment.");
        //} catch (ClassNotFoundException ex) {
        } catch (Exception e) {
            log.debug("Unable to load javax.servlet.http.HttpServlet - not running in servlet environment.");
            inServlet = false;
        }

        // If running in a servlet, have to get the config info from the webapp context, as we can't
        // read from external dirs on disk.
        Configurations configs = new Configurations();
        if (inServlet) {
            InputStream inputStream = this.getClass().getClassLoader().getResourceAsStream("/metadig.properties");
            String TMP_DIR = System.getProperty("java.io.tmpdir");
            File tempFile = new File(TMP_DIR + "/metadig.properties");
            log.debug("Reading config properties in servlet from: " + tempFile);
            FileOutputStream out = new FileOutputStream(tempFile);
            IOUtils.copy(inputStream, out);
            config = configs.properties(tempFile);
            log.debug("Successfully read properties from: " + tempFile);
        } else {
            log.debug("Reading config properties from: " + configFilePath);
            config = configs.properties(new File(configFilePath));
            log.debug("Successfully read properties from: " + configFilePath);
        }
    }

    /**
     * Read a configuration file for a String parameter values.
     */
    public String getString (String paramName) throws ConfigurationException {
        return(config.getString(paramName));
    }

    /**
     * Read a configuration file for a String parameter values.
     */
    public int getInt(String paramName) throws ConfigurationException {
        return(config.getInt(paramName));
    }
}
