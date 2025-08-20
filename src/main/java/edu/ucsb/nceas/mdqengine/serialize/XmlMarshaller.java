package edu.ucsb.nceas.mdqengine.serialize;

import org.apache.commons.io.IOUtils;
import org.apache.commons.lang.StringEscapeUtils;
import org.xml.sax.SAXException;

import javax.xml.XMLConstants;
import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBException;
import javax.xml.bind.Marshaller;
import javax.xml.bind.Unmarshaller;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.stream.StreamSource;
import javax.xml.validation.Schema;
import javax.xml.validation.SchemaFactory;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.util.Map;

/**
 * Utility class for marshalling and unmarshalling Java objects to and from XML.
 *
 * This class provides methods for converting Java objects to XML
 * representations and vice versa, using JAXB.
 */
public class XmlMarshaller {

	/**
	 * Converts the given object to an XML string.
	 * 
	 * The unescapeXML flag determines whether XML escaping is applied to the
	 * output.
	 *
	 * @param obj         the object to be converted to XML
	 * @param unescapeXML flag indicating whether to unescape XML
	 * @return the XML string representation of the object
	 * @throws JAXBException                if an error occurs during XML binding
	 * @throws UnsupportedEncodingException if the character encoding is unsupported
	 */
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

	/**
	 * Converts an XML string to a Java object of the specified class.
	 * 
	 * @param xml   the XML string to be converted
	 * @param clazz the class type to which the XML should be mapped
	 * @return the Java object represented by the XML
	 * @throws JAXBException
	 */
	public static Object fromXml(String xml, Class clazz)
			throws ParserConfigurationException, JAXBException, IOException, SAXException {

		xml = normalizeNamespace(xml);

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

	/**
	 * Normalizes known outdated namespace URIs in XML namespace declarations
	 * to the most recent version.
	 * 
	 * This is necessary because maintaining two+ versions of the jaxb model, which
	 * doesn't match the schema anyway, was not working. New schemas are always
	 * backwards compatible with old ones, so an old document can always be
	 * unmarshalled against a newer version of the schema.
	 *
	 * 
	 * @param xml the XML string to normalize
	 * @return the XML string with known namespaces rewritten
	 */
	public static String normalizeNamespace(String xml) {
		if (xml == null)
			return null;

		Map<String, String> nsMap = Map.of(
				"https://nceas.ucsb.edu/mdqe/v1", "https://nceas.ucsb.edu/mdqe/v1.2",
				"https://nceas.ucsb.edu/mdqe/v1.1", "https://nceas.ucsb.edu/mdqe/v1.2");

		String result = xml;

		for (java.util.Map.Entry<String, String> entry : nsMap.entrySet()) {
			result = result.replace(
					"xmlns:mdq=\"" + entry.getKey() + "\"",
					"xmlns:mdq=\"" + entry.getValue() + "\"");
			result = result.replace(
					"xsi:schemaLocation=\"" + entry.getKey(),
					"xsi:schemaLocation=\"" + entry.getValue());
		}
		return result;
	}

}
