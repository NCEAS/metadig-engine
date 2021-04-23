package edu.ucsb.nceas.mdqengine.processor;

import org.apache.commons.io.IOUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.dataone.client.v2.itk.D1Client;
import org.dataone.service.types.v1.Person;
import org.dataone.service.types.v1.Subject;
import org.dataone.service.types.v1.SubjectInfo;
import org.dataone.service.types.v2.SystemMetadata;
import org.dataone.service.util.TypeMarshaller;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;

/**
 * Looks up group membership of given subject
 * @author leinfelder
 *
 */
public class GroupLookupCheck implements Callable<List<String>> {

	// the XML serialization of SystemMetadata
	private String systemMetadata;
	private String rightsHolder = null;

	public Log log = LogFactory.getLog(this.getClass());

	@Override
	public List<String> call() {
		List<String> groups = new ArrayList<String>();
		Subject subject = null;
		SubjectInfo subjectInfo = null;

			try {
                if (rightsHolder == null) {
                    if(this.systemMetadata != null) {
						SystemMetadata sm = TypeMarshaller.unmarshalTypeFromStream(SystemMetadata.class, IOUtils.toInputStream(systemMetadata, "UTF-8"));
						subject = sm.getRightsHolder();
					} else {
						log.warn("No SystemMetadata.rightsHolder given, cannot look up group membership");
						return groups;
					}
				} else {
                	log.debug("Setting subject to rightsHolder: " + this.rightsHolder);
                	subject = new Subject();
                	subject.setValue(this.rightsHolder);
				}

				log.debug("Looking up SubjectInfo for: " + subject.getValue());
                try {
					subjectInfo = D1Client.getCN().getSubjectInfo(null, subject);
					log.debug("subjectInfo: " + subjectInfo.getPersonList().toString());
				} catch (org.dataone.service.exceptions.NotFound fn ){
                	log.debug("Subject: " + subject.getValue() + "not found");
                	return(groups);
				}
				if (subjectInfo != null && subjectInfo.getPersonList() != null && subjectInfo.getPersonList().size() > 0) {
					Person person = subjectInfo.getPerson(0);
					log.debug("Checking person: " + person.getSubject().getValue());
					// get if we are looking up a person or a group
					if (person.getSubject().equals(subject)) {
						log.debug("Persons are equal: " + person.getSubject().getValue());
						if (person.getIsMemberOfList() != null && person.getIsMemberOfList().size() > 0) {
							for (Subject group: person.getIsMemberOfList()) {
								String groupSubject = group.getValue();
								log.debug("Found group: " + groupSubject);
								groups.add(groupSubject);
							}
						}
					} else {
						// it must be a group already
						groups.add(subject.getValue());
					}
				} else {
					log.error("Didn't find subject info or personList for subject: " + subject);
				}

			} catch (Exception e) {
				log.error("Could not look up SubjectInfo", e);
			}

		return groups;
	}

	public String getSystemMetadata() {
		return systemMetadata;
	}

	public void setSystemMetadata(String sm) {
		this.systemMetadata = sm;
	}

	public String getRightsHolder() {
		return rightsHolder;
	}

	public void setRightsHolder(String rightsHolder) {
		this.rightsHolder = rightsHolder;
    }
}

