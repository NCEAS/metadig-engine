package edu.ucsb.nceas.mdqengine.serialize;

import org.apache.commons.io.IOUtils;
import org.apache.commons.lang.StringEscapeUtils;
import org.xml.sax.SAXException;

import javax.xml.XMLConstants;
import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBException;
import javax.xml.bind.Marshaller;
import javax.xml.bind.Unmarshaller;
import javax.xml.transform.stream.StreamSource;
import javax.xml.validation.Schema;
import javax.xml.validation.SchemaFactory;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;

public class XmlMarshaller {

	public static String toXml(Object obj, Boolean unescapeXML) throws JAXBException, UnsupportedEncodingException {

		JAXBContext context = JAXBContext.newInstance(obj.getClass());

		Marshaller m = context.createMarshaller();
		m.setProperty(Marshaller.JAXB_FORMATTED_OUTPUT, true);

		ByteArrayOutputStream baos = new ByteArrayOutputStream();
		m.marshal(obj, baos);

		// JAXB annotations (e.g. in Check.java) are unable to prevent marshalling from
		// performing
		// XML special character encoding (i.e. '"' to '&quot', so we have to unescape
		// these character for the entire report here.
		if (unescapeXML) {
			return StringEscapeUtils.unescapeXml(baos.toString("UTF-8"));
		} else {
			return baos.toString("UTF-8");
		}

	}

	public static Object fromXml(String xml, Class clazz) throws JAXBException, IOException, SAXException {

		JAXBContext context = JAXBContext.newInstance(clazz);
		Unmarshaller u = context.createUnmarshaller();

		// TODO: include the schema in jar
		InputStream schemaStreamV1 = XmlMarshaller.class.getResourceAsStream("/schemas/schema1.xsd");
		InputStream schemaStreamV11 = XmlMarshaller.class.getResourceAsStream("/schemas/schema1.1.xsd");
		InputStream schemaStreamV12 = XmlMarshaller.class.getResourceAsStream("/schemas/schema1.2.xsd");
		
		if (schemaStreamV1 == null || schemaStreamV11 == null || schemaStreamV12 == null) {
			throw new IOException("One or more schema files not found");
		}

		SchemaFactory factory = SchemaFactory.newInstance(XMLConstants.W3C_XML_SCHEMA_NS_URI);
		Schema schema = factory.newSchema(new StreamSource[] {
				new StreamSource(schemaStreamV1),
				new StreamSource(schemaStreamV11),
				new StreamSource(schemaStreamV12)
		});
		u.setSchema(schema);

		Object obj = u.unmarshal(IOUtils.toInputStream(xml, "UTF-8"));
		return obj;

	}
}
