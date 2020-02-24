package edu.ucsb.nceas.mdqengine.authentication;

import org.dataone.client.auth.AuthTokenSession;
import org.dataone.service.types.v1.Session;
import org.dataone.service.types.v1.Subject;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

public class DataONE {

public static Log log = LogFactory.getLog(DataONE.class);

    /**
     * Get a DataONE authenticated session
     * <p>
     *     If no subject or authentication token are provided, a public session is returned
     * </p>
     * @param authToken the authentication token
     * @return the DataONE session
     */
    public static Session getSession(String subjectId, String authToken) {

        Session session;

        // query Solr - either the member node or cn, for the project 'solrquery' field
        if (authToken == null || authToken.isEmpty()) {
            log.debug("Creating public sessioni");
            session = new Session();
        } else {
            log.debug("Creating authentication session for subjectId: " + subjectId + ", token: " + authToken.substring(0, 5) + "...");
            session = new AuthTokenSession(authToken);
        }

        if (subjectId != null && !subjectId.isEmpty()) {
            Subject subject = new Subject();
            subject.setValue(subjectId);
            session.setSubject(subject);
        }

        return session;
    }
}
