package edu.ucsb.nceas.mdqengine.store;

import edu.ucsb.nceas.mdqengine.exception.MetadigStoreException;
import edu.ucsb.nceas.mdqengine.model.*;
import edu.ucsb.nceas.mdqengine.serialize.XmlMarshaller;
import net.minidev.json.JSONArray;
import net.minidev.json.JSONObject;
import net.minidev.json.JSONValue;
import org.apache.commons.io.IOUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.dataone.client.auth.CertificateManager;
import org.dataone.client.types.AccessPolicyEditor;
import org.dataone.client.v2.MNode;
import org.dataone.client.v2.itk.D1Client;
import org.dataone.service.exceptions.ServiceFailure;
import org.dataone.service.types.v1.Identifier;
import org.dataone.service.types.v1.ObjectFormatIdentifier;
import org.dataone.service.types.v1.Session;
import org.dataone.service.types.v1.Subject;
import org.dataone.service.types.v1.util.ChecksumUtil;
import org.dataone.service.types.v2.Node;
import org.dataone.service.types.v2.SystemMetadata;

import javax.xml.bind.JAXBException;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.lang.reflect.InvocationTargetException;
import java.math.BigInteger;
import java.net.URLEncoder;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collection;
import java.util.Date;

import static org.dataone.configuration.Settings.getConfiguration;

public class MNStore implements MDQStore {

	public static final String MDQ_NS = "https://nceas.ucsb.edu/mdqe/v1";
	
	protected Log log = LogFactory.getLog(this.getClass());

	private MNode node = null;
	
	private Session session = null;

	private String mnURL = null;
	
	public MNStore() {
		this(null);
	}
	
	public MNStore(String baseUrl) {
		
		// use the desired MN
		if (baseUrl == null) {
			mnURL = getConfiguration().getString("mn.baseurl", "https://mn-demo-8.test.dataone.org/knb/d1/mn/");
		} else {
			mnURL = baseUrl;
		}
		try {
			node = D1Client.getMN(mnURL);
		} catch (ServiceFailure e) {
			log.error(e.getMessage(), e);
		}
		
		// initialize the session+cert+key for the default user
		session = new Session();
		X509Certificate cert = CertificateManager.getInstance().loadCertificate();
		PrivateKey key = CertificateManager.getInstance().loadKey();
		Subject subject = new Subject();
		subject.setValue(CertificateManager.getInstance().getSubjectDN(cert));
		CertificateManager.getInstance().registerCertificate(subject.getValue(), cert, key);
		session.setSubject(subject);

	}
	
	private SystemMetadata generateSystemMetadata(Object model)
			throws UnsupportedEncodingException, JAXBException,
			NoSuchMethodException, SecurityException, IllegalAccessException,
			IllegalArgumentException, InvocationTargetException, NoSuchAlgorithmException {

		SystemMetadata sysMeta = new SystemMetadata();

		// find the SID
		String id = (String) model.getClass().getMethod("getId", null).invoke(model, null);
		Identifier seriesId = new Identifier();
		seriesId.setValue(id);
		sysMeta.setSeriesId(seriesId);
		sysMeta.setSerialVersion(BigInteger.ONE);

		// use the simple classname added to the NS
		ObjectFormatIdentifier formatId = new ObjectFormatIdentifier();
		formatId.setValue(MDQ_NS + "#" + model.getClass().getSimpleName().toLowerCase());
		sysMeta.setFormatId(formatId);

		// roles
		sysMeta.setRightsHolder(session.getSubject());
		sysMeta.setSubmitter(session.getSubject());
		//sysMeta.setAuthoritativeMemberNode(node.getNodeId());
		//sysMeta.setOriginMemberNode(node.getNodeId());

		// for now, make them all public for easier debugging
		AccessPolicyEditor accessPolicyEditor = new AccessPolicyEditor(null);
		accessPolicyEditor.setPublicAccess();
		sysMeta.setAccessPolicy(accessPolicyEditor.getAccessPolicy());

		// size
		String obj = XmlMarshaller.toXml(model, true);
		sysMeta.setSize(BigInteger.valueOf(obj.getBytes("UTF-8").length));
		sysMeta.setChecksum(ChecksumUtil.checksum(obj.getBytes("UTF-8"), "MD5"));
		sysMeta.setFileName(model.getClass().getSimpleName().toLowerCase() + ".xml");

		// timestamps
		Date now = Calendar.getInstance().getTime();
		sysMeta.setDateSysMetadataModified(now);
		sysMeta.setDateUploaded(now);

		return sysMeta;
	}

	@Override
	public Collection<String> listSuites() {
		
		// use shared impl
		Collection<String> results = list(Suite.class);
		return results;
	}
	
	/**
	 * generic method for looking up model classes from the store
	 * @param clazz
	 * @return
	 */
	private Collection<String> list(Class clazz) {
		
		Collection<String> results = new ArrayList<String>();
		try {
			// query system for object
			String formatId = MDQ_NS + "#" + clazz.getSimpleName().toLowerCase();

			String solrQuery = "q=" + URLEncoder.encode("formatId:\"" + formatId + "\"", "UTF-8");
			solrQuery += URLEncoder.encode("-obsoletedBy:*", "UTF-8");
			solrQuery += "&fl=id,seriesId&wt=json&rows=10000";
			log.debug("solrQuery = " + solrQuery);

			// search the index
			InputStream solrResultStream = node.query(session, "solr", solrQuery);

			// parse results to find the ids
			JSONObject solrResults = (JSONObject) JSONValue.parse(solrResultStream);
			log.debug("solrResults = " + solrResults.toJSONString());
			
			if (solrResults != null && solrResults.containsKey("response")) {
				JSONArray solrDocs = (JSONArray)((JSONObject) solrResults.get("response")).get("docs");
				log.debug("solrDocs = " + solrDocs.toJSONString());
				
				for (Object solrDoc: solrDocs) {
					log.debug("solrDoc = " + solrDoc.toString());
					
					String id = ((JSONObject) solrDoc).get("id").toString();
					log.debug("id = " + id);
					String seriesId = ((JSONObject) solrDoc).get("seriesId").toString();
					log.debug("seriesId = " + seriesId);
					
					// don't fetch the object - just the id
					results.add(seriesId);
				}
			}
			
		} catch (Exception e) {
			log.error(e.getMessage(), e);
		}

		return results;
	}
	
	/**
	 * Shared method for getting a model from store
	 * @param id
	 * @param clazz
	 * @return
	 */
	private Object get(String id, Class clazz) {
		
		Object model = null;
		try {
			Identifier identifier = new Identifier();
			identifier.setValue(id);
			InputStream is = node.get(session, identifier);
			model = XmlMarshaller.fromXml(IOUtils.toString(is, "UTF-8"), clazz);
		} catch (Exception e) {
			log.error("Could not get model: " + id + ": " + e.getMessage(), e);
		}
		
		return model;
	}
	
	private boolean create(Object model, String id) {
		try {
			Identifier identifier = node.generateIdentifier(session, "UUID", null);
			Identifier sid = null;
			if (id != null) {
				sid = new Identifier();
				sid.setValue(id);
			}
			String obj = XmlMarshaller.toXml(model, true);
			SystemMetadata sysMeta = this.generateSystemMetadata(model);
			sysMeta.setIdentifier(identifier);
			sysMeta.setSeriesId(sid);
			node.create(session, identifier, IOUtils.toInputStream(obj, "UTF-8"), sysMeta );
		} catch (Exception e) {
			log.error(e.getMessage(), e);
			return false;
		}
		return true;
	}
	
	private boolean update(Object model, String id) {
		try {
			// identified by SID, but need PIDs for update action
			Identifier sid = new Identifier();
			sid.setValue(id);
			SystemMetadata oldSysMeta = node.getSystemMetadata(session, sid);
			Identifier oldId = oldSysMeta.getIdentifier();
			Identifier newId = node.generateIdentifier(session, "UUID", null);
			
			SystemMetadata sysMeta = generateSystemMetadata(model);
			sysMeta.setIdentifier(newId);
			sysMeta.setSeriesId(sid);
			sysMeta.setObsoletes(oldId);

			String obj = XmlMarshaller.toXml(model, true);
			node.update(session, oldId, IOUtils.toInputStream(obj, "UTF-8"), newId , sysMeta );
		} catch (Exception e) {
			log.error(e.getMessage(), e);
			return false;
		}
		return true;
		
	}
	
	private boolean delete(String id) {
		try {
			Identifier identifier = new Identifier();
			identifier.setValue(id);
			node.archive(session, identifier);
		} catch (Exception e) {
			log.error(e.getMessage(), e);
			return false;
		}
		return true;
		
	}

	@Override
	public Suite getSuite(String id) {
		Suite model = (Suite) get(id, Suite.class);
		return model;
	}

	@Override
	public void createSuite(Suite model) {
		create(model, model.getId());
	}

	@Override
	public void updateSuite(Suite model) {
		update(model, model.getId());	
	}

	@Override
	public void deleteSuite(Suite model) {
		delete(model.getId());	
	}

	@Override
	public Collection<String> listChecks() {
		return list(Check.class);
	}

	@Override
	public Check getCheck(String id) {
		return (Check) get(id, Check.class);
	}

	@Override
	public void createCheck(Check check) {
		create(check, check.getId());
	}

	@Override
	public void updateCheck(Check check) {
		update(check, check.getId());
	}

	@Override
	public void deleteCheck(Check check) {
		delete(check.getId());
	}

	@Override
	public Collection<String> listRuns() {
		return list(Run.class);
	}

	@Override
	public Run getRun(String suite, String id) {
		return (Run) get(id, Run.class);
	}

	@Override
	public void createRun(Run run) {
		create(run, run.getId());		
	}

	@Override
	public void deleteRun(Run run) {
		delete(run.getId());
	}

	@Override
	public boolean isAvailable() { return true; }

	@Override
	public void renew() {}

	@Override
	public Task getTask(String taskName, String taskType, String nodeId) { return new Task(); }

	@Override
	public void saveTask(Task task, String nodeId) throws MetadigStoreException { }

	@Override
	public void shutdown() {};

	@Override
	public void saveRun(Run run) {}

	@Override
	public Node getNode (String nodeId) { return new Node(); };

	@Override
	public void saveNode(Node node) throws MetadigStoreException {};

	@Override
	public ArrayList<Node> getNodes() { return new ArrayList<> (); };


}
