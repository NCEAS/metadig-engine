package edu.ucsb.nceas.mdqengine.processor;

import java.io.IOException;
import java.util.concurrent.Callable;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.validation.Schema;
import javax.xml.validation.SchemaFactory;

import org.apache.commons.io.IOUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.w3c.dom.Document;
import org.xml.sax.ErrorHandler;
import org.xml.sax.SAXException;
import org.xml.sax.SAXParseException;

import edu.ucsb.nceas.mdqengine.model.Result;
import edu.ucsb.nceas.mdqengine.model.Status;

/**
 * Check to ensure document is schema-valid.
 * Currently, the document must direct us to the schema location in order to validate
 * @author leinfelder
 *
 */
public class SchemaCheck implements Callable<Result> {

	private String document;
	
	public Log log = LogFactory.getLog(this.getClass());
	
	@Override
	public Result call() {
		Result result = new Result();
		
		try {		
			
			DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
			
			SchemaFactory sf = SchemaFactory.newInstance(XMLConstants.W3C_XML_SCHEMA_NS_URI);
			Schema schema = sf.newSchema();
			factory.setSchema(schema);
			factory.setNamespaceAware(true);
			
			DocumentBuilder builder = factory.newDocumentBuilder();
			ErrorHandler eh = new ErrorHandler() {

				@Override
				public void warning(SAXParseException exception)
						throws SAXException {
					throw exception;
					
				}

				@Override
				public void error(SAXParseException exception)
						throws SAXException {
					throw exception;
					
				}

				@Override
				public void fatalError(SAXParseException exception)
						throws SAXException {
					throw exception;
					
				}
				
			};
			builder.setErrorHandler(eh);
			
			Document doc = builder.parse(IOUtils.toInputStream(document, "UTF-8"));

		
		} catch (IOException | ParserConfigurationException e) {
			result.setStatus(Status.ERROR);
			result.setMessage(e.getMessage());
			return result;
		} catch (SAXParseException e) {
			result.setStatus(Status.FAILURE);
			result.setMessage(e.getMessage());
			return result;
		} catch (Exception e) {
			result.setStatus(Status.FAILURE);
			result.setMessage(e.getMessage());
			return result;
		} 
		
		result.setStatus(Status.SUCCESS);
		result.setMessage("Document is schema valid");
		return result;
		
	}

	public String getDocument() {
		return document;
	}

	public void setDocument(String document) {
		this.document = document;
	}
	

}
