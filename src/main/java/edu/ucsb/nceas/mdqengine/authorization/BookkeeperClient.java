package edu.ucsb.nceas.mdqengine.authorization;

import edu.ucsb.nceas.mdqengine.MDQconfig;
import edu.ucsb.nceas.mdqengine.authentication.DataONE;
import edu.ucsb.nceas.mdqengine.exception.MetadigException;
import org.apache.commons.configuration2.ex.ConfigurationException;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.http.HttpStatus;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.dataone.bookkeeper.api.Usage;
import org.dataone.bookkeeper.api.UsageList;

import java.io.*;
import java.io.IOException;
import java.util.List;

public class BookkeeperClient {

    private static BookkeeperClient instance;
    public static Log log = LogFactory.getLog(DataONE.class);
    private String bookkeeperURL = null;
    private String bookkeeperAuthToken = null;

    private BookkeeperClient () {
    }

    /**
     * Get the singleton instance of the BookKeeplerClient class
     * @return  the instance of the class
     */
    public static BookkeeperClient getInstance() throws MetadigException {
        if (instance == null) {
            synchronized (BookkeeperClient.class) {
                if (instance == null) {
                    instance = new BookkeeperClient();
                    instance.init();
                }
            }
        }
        return instance;
    }

    /**
     * Initialize a bookkeeper client and get metadig config parameters needed for interacting with
     * DataONE bookkeeper service
     *
     * @throws MetadigException
     */
    protected void init () throws MetadigException {
        // Get metadig config parameter for the bookkeeper URL

        try {
            bookkeeperURL = MDQconfig.readConfigParam("bookkeeper.url");
            bookkeeperAuthToken  = MDQconfig.readConfigParam("bookkeeper.authToken");
        } catch (ConfigurationException | IOException e) {
            throw new MetadigException("Unable to initialize DataONE bookkeeper client: " + e.getMessage());
        }
    }

    /**
     * Retrieve a bookkeeper quota usage usage
     * @param id the usage database sequence identifier
     * @param instanceId the usage instance identifier
     * @param quotaType the usage quota type ("portal" | "storage" | ...)
     * @param status the usage status ("active" | "inactive")
     * @return
     * @throws MetadigException
     */
    public List<Usage> listUsages(int id, String instanceId, String quotaType, String status, List<String> subjects) throws MetadigException {
        // Check the portal quota with DataONE bookkeeper
        String serviceURL  = this.bookkeeperURL;
        ObjectMapper objectMapper = new ObjectMapper();
        CloseableHttpClient httpClient = HttpClients.createDefault();
        String idStr = String.valueOf(id);

        if (id > 0) {
            log.debug("Getting bookkeeper portal Usage for id: " + idStr);
            serviceURL += "/usages?id=" + idStr;
        } else {
            log.debug("Getting bookkeeper portal Usage for quotaType, instanceId, status: " +
                    quotaType + ", " +
                    instanceId + ", " +
                    status);
            if(status != null) {
                serviceURL += "/usages?quotaType=" + quotaType + "&instanceId=" + String.valueOf(instanceId) + "&status=" + status;
            } else {
                serviceURL += "/usages?quotaType=" + quotaType + "&instanceId=" + String.valueOf(instanceId);
            }
        }

        log.debug("Using serviceURL: " + serviceURL);
        HttpGet httpGet = new HttpGet(serviceURL);

        String msg = null;
        // Send a request to the bookkeeper service for the quota related to this portal
        try {
            httpGet.addHeader("Authorization", "Bearer " + bookkeeperAuthToken);
            // Ask for JSON reponse
            httpGet.addHeader("Accept", "application/json");

            log.debug("Submitting request to DataONE bookkeeper: " + serviceURL);
            // send the request to bookkeeper
            CloseableHttpResponse httpResponse = httpClient.execute(httpGet);
            // Delete the token

            // Read the response from bookkeeper
            StringBuffer response = new StringBuffer();
            int statusCode = httpResponse.getStatusLine().getStatusCode();

            // If the HTTP request returned without an error, convert the result to a JSON string,
            // then deserialize to a Java object so that we can easily inspect it.
            if(statusCode == HttpStatus.SC_OK) {
                BufferedReader reader = new BufferedReader(new InputStreamReader(httpResponse.getEntity().getContent()));
                String inputLine;
                response = new StringBuffer();

                while ((inputLine = reader.readLine()) != null) {
                    response.append(inputLine);
                }

                UsageList usageList = objectMapper.readValue(response.toString(), UsageList.class);
                List<Usage> usages = usageList.getUsages();
                if (usages.size() == 0) {
                    msg =  "No usages returned.";
                    log.error(msg);
                    throw(new MetadigException(msg));
                }
                log.debug("Bookkeeper Usage status found for portal " + idStr + ": " + usages.get(0).getStatus());
                return(usages);
            } else {
                log.debug("Getting bookkeeper portal Usage for quotaType, instanceId, status: " +
                        quotaType + ", " +
                        instanceId + ", " +
                        status);
                msg =  "HTTP error status getting bookkeeper usage for id, quotaType, instanceId, status: " + idStr + ": " +
                        "," + quotaType +
                        "," + instanceId +
                        "," + status +
                        httpResponse.getStatusLine().getReasonPhrase();
                log.error(msg);
                throw(new MetadigException(msg));
            }
        } catch (IOException ioe) {
            msg =  "Error getting bookkeeper usage: " + ioe.getMessage();
            log.error(msg);
            throw(new MetadigException(msg));
        } finally {
            try {
                httpClient.close();
            } catch (IOException e) {
                log.warn("Error closing connection to bookkeeper client: " + e.getMessage());
            }
        }
    }
}
