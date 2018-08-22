package edu.ucsb.nceas.mdqengine;

import org.apache.commons.configuration2.Configuration;
import org.apache.commons.configuration2.builder.fluent.Configurations;
import org.apache.commons.configuration2.ex.ConfigurationException;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import java.io.File;

public class MDQconfig {

    private static String configFilePath = "/etc/metadig/metadig.properties";
    public static Log log = LogFactory.getLog(MDQconfig.class);

    /**
     * Read a configuration file for a String parameter values.
     */
    public String getString (String paramName) throws ConfigurationException {

        Configurations configs = new Configurations();
        Configuration config = configs.properties(new File(configFilePath));
        return(config.getString(paramName));
    }

    /**
     * Read a configuration file for a String parameter values.
     */
    public Integer getInt(String paramName) throws ConfigurationException {

        Configurations configs = new Configurations();
        Configuration config = configs.properties(new File(configFilePath));
        return(config.getInt(paramName));
    }
}
