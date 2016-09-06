package edu.ucsb.nceas.mdqengine.serialize;

import java.io.ByteArrayOutputStream;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;

import javax.xml.XMLConstants;
import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBException;
import javax.xml.bind.Marshaller;
import javax.xml.bind.Unmarshaller;
import javax.xml.transform.stream.StreamSource;
import javax.xml.validation.Schema;
import javax.xml.validation.SchemaFactory;

import org.apache.commons.io.IOUtils;
import org.xml.sax.SAXException;

public class XmlMarshaller {

	public static String toXml(Object obj) throws JAXBException, UnsupportedEncodingException {
		
	    JAXBContext context = JAXBContext.newInstance(obj.getClass());

	    Marshaller m = context.createMarshaller();
	    m.setProperty(Marshaller.JAXB_FORMATTED_OUTPUT, true);

	    ByteArrayOutputStream baos = new ByteArrayOutputStream();
	    m.marshal(obj, baos);
	    
	    return baos.toString("UTF-8");

	  }
	
	public static Object fromXml(String xml, Class clazz) throws JAXBException, IOException, SAXException {
		
	    JAXBContext context = JAXBContext.newInstance(clazz);
	    Unmarshaller u = context.createUnmarshaller();

	    // TODO: include the schema in jar
	    InputStream schemaStream = XmlMarshaller.class.getResourceAsStream("/schemas/schema1.xsd");
	    //InputStream schemaStream = new FileInputStream("/Users/leinfelder/git/mdqengine/target/schemas/schema1.xsd");
	    
	    SchemaFactory factory = SchemaFactory.newInstance(XMLConstants.W3C_XML_SCHEMA_NS_URI);
		Schema schema = factory.newSchema(new StreamSource(schemaStream));
		u.setSchema(schema);
		
	    Object obj = u.unmarshal(IOUtils.toInputStream(xml, "UTF-8"));
	    return obj;

	  }
}
