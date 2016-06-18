package edu.ucsb.nceas.mdqengine.store;

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

import javax.xml.bind.JAXBException;

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
import org.dataone.configuration.Settings;
import org.dataone.service.exceptions.ServiceFailure;
import org.dataone.service.types.v1.Identifier;
import org.dataone.service.types.v1.ObjectFormatIdentifier;
import org.dataone.service.types.v1.Session;
import org.dataone.service.types.v1.Subject;
import org.dataone.service.types.v1.util.ChecksumUtil;
import org.dataone.service.types.v2.SystemMetadata;

import edu.ucsb.nceas.mdqengine.MDQStore;
import edu.ucsb.nceas.mdqengine.model.Check;
import edu.ucsb.nceas.mdqengine.model.Recommendation;
import edu.ucsb.nceas.mdqengine.model.Run;
import edu.ucsb.nceas.mdqengine.serialize.XmlMarshaller;

public class MNStore implements MDQStore {

	protected Log log = LogFactory.getLog(this.getClass());

	private MNode node = null;
	
	private Session session = null;

	private String mnURL = null;
	
	public MNStore() {
		
		// use the desired MN
		mnURL = Settings.getConfiguration().getString("mn.baseurl", "https://mn-demo-8.test.dataone.org/knb/d1/mn/");
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
		
		// for now, just use the classname
		ObjectFormatIdentifier formatId = new ObjectFormatIdentifier();
		formatId.setValue(model.getClass().getCanonicalName());
		sysMeta.setFormatId(formatId);
		
		// roles
		sysMeta.setRightsHolder(session.getSubject());
		sysMeta.setSubmitter(session.getSubject());
		sysMeta.setAuthoritativeMemberNode(node.getNodeId());
		sysMeta.setOriginMemberNode(node.getNodeId());
		
		// for now, make them all public for easier debugging
		AccessPolicyEditor accessPolicyEditor = new AccessPolicyEditor(null);
		accessPolicyEditor.setPublicAccess();
		sysMeta.setAccessPolicy(accessPolicyEditor.getAccessPolicy());
				
		// size
		String obj = XmlMarshaller.toXml(model);
		sysMeta.setSize(BigInteger.valueOf(obj.getBytes("UTF-8").length));
		sysMeta.setChecksum(ChecksumUtil.checksum(obj.getBytes("UTF-8"), "MD5"));
		sysMeta.setFileName(model.getClass().getSimpleName() + ".xml");

		// timestamps
		Date now = Calendar.getInstance().getTime();
		sysMeta.setDateSysMetadataModified(now);
		sysMeta.setDateUploaded(now);
		
		return sysMeta;
	}

	@Override
	public Collection<String> listRecommendations() {
		
		Collection<String> recommendations = new ArrayList<String>();
		try {
			// query system for recommendations
			String solrQuery = "q=" + URLEncoder.encode("formatId:\"" + Recommendation.class.getCanonicalName() + "\"", "UTF-8");
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
					
					// don't fetch the recommendation - just the id
					//Recommendation r = this.getRecommendation(seriesId);
					recommendations.add(seriesId);
				}
			}
			
		} catch (Exception e) {
			log.error(e.getMessage(), e);
		}

		return recommendations;
	}

	@Override
	public Recommendation getRecommendation(String id) {
		
		Recommendation rec = null;
		try {
			Identifier identifier = new Identifier();
			identifier.setValue(id);
			InputStream is = node.get(session, identifier);
			rec = (Recommendation) XmlMarshaller.fromXml(IOUtils.toString(is, "UTF-8"), Recommendation.class);
		} catch (Exception e) {
			log.error("Could not get Recommendation: " + id + ": " + e.getMessage(), e);
		}
		
		return rec;
	}

	@Override
	public void createRecommendation(Recommendation rec) {
		try {
			Identifier identifier = node.generateIdentifier(session, "UUID", null);
			Identifier sid = null;
			String existingId = rec.getId();
			if (existingId != null) {
				sid = new Identifier();
				sid.setValue(rec.getId());
			}
			String obj = XmlMarshaller.toXml(rec);
			SystemMetadata sysMeta = this.generateSystemMetadata(rec);
			sysMeta.setIdentifier(identifier);
			sysMeta.setSeriesId(sid);
			node.create(session, identifier, IOUtils.toInputStream(obj, "UTF-8"), sysMeta );
		} catch (Exception e) {
			log.error(e.getMessage(), e);
		}
	}

	@Override
	public void updateRecommendation(Recommendation rec) {
		try {
			// identified by SID, but need PIDs for update action
			Identifier sid = new Identifier();
			sid.setValue(rec.getId());
			SystemMetadata oldSysMeta = node.getSystemMetadata(session, sid);
			Identifier oldId = oldSysMeta.getIdentifier();
			Identifier newId = node.generateIdentifier(session, "UUID", null);
			
			SystemMetadata sysMeta = generateSystemMetadata(rec);
			sysMeta.setIdentifier(newId);
			sysMeta.setSeriesId(sid);
			sysMeta.setObsoletes(oldId);

			String obj = XmlMarshaller.toXml(rec);
			node.update(session, oldId, IOUtils.toInputStream(obj, "UTF-8"), newId , sysMeta );
		} catch (Exception e) {
			log.error(e.getMessage(), e);
		}
		
	}

	@Override
	public void deleteRecommendation(Recommendation rec) {
		try {
			Identifier identifier = new Identifier();
			identifier.setValue(rec.getId());
			node.archive(session, identifier);
		} catch (Exception e) {
			log.error(e.getMessage(), e);
		}
		
	}

	@Override
	public Collection<String> listChecks() {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public Check getCheck(String id) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public void createCheck(Check check) {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void updateCheck(Check check) {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void deleteCheck(Check check) {
		// TODO Auto-generated method stub
		
	}

	@Override
	public Collection<String> listRuns() {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public Run getRun(String id) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public void createRun(Run run) {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void deleteRun(Run run) {
		// TODO Auto-generated method stub
		
	}

}
