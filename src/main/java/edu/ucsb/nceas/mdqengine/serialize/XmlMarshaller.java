package edu.ucsb.nceas.mdqengine.serialize;

import java.io.ByteArrayOutputStream;

import javax.xml.bind.JAXBContext;
import javax.xml.bind.Marshaller;
import javax.xml.bind.Unmarshaller;

import org.apache.commons.io.IOUtils;

public class XmlMarshaller {

	public static String toXml(Object obj) throws Exception {
		
	    JAXBContext context = JAXBContext.newInstance(obj.getClass());

	    Marshaller m = context.createMarshaller();
	    m.setProperty(Marshaller.JAXB_FORMATTED_OUTPUT, true);

	    ByteArrayOutputStream baos = new ByteArrayOutputStream();
	    m.marshal(obj, baos);
	    
	    return baos.toString("UTF-8");

	  }
	
	public static Object fromXml(String xml, Class clazz) throws Exception {
		
	    JAXBContext context = JAXBContext.newInstance(clazz);
	    Unmarshaller u = context.createUnmarshaller();
	    Object obj = u.unmarshal(IOUtils.toInputStream(xml, "UTF-8"));
	    return obj;

	  }
}
