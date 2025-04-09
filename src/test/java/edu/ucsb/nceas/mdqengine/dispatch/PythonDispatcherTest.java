package edu.ucsb.nceas.mdqengine.dispatch;

import edu.ucsb.nceas.mdqengine.exception.MetadigException;

import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;

import javax.script.ScriptException;
import javax.xml.parsers.ParserConfigurationException;

import org.apache.commons.codec.digest.DigestUtils;
import org.apache.commons.io.IOUtils;

import edu.ucsb.nceas.mdqengine.model.Result;
import edu.ucsb.nceas.mdqengine.model.Status;
import edu.ucsb.nceas.mdqengine.processor.MetadataDialect;
import edu.ucsb.nceas.mdqengine.processor.MetadataDialectFactory;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.xml.sax.SAXException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

public class PythonDispatcherTest {

	private Dispatcher dispatcher = null;

	private String dataUrl = "https://knb.ecoinformatics.org/knb/d1/mn/v2/object/doi:10.5063/AA/wolkovich.29.1";

	@BeforeAll
	public static void setupOnce() {
		try {
			Dispatcher.setupJep();
		} catch (MetadigException me) {
			fail("Setup failed with MetadigException: " + me.getMessage());
		}
	}

	@BeforeEach
	public void init() {
		dispatcher = Dispatcher.getDispatcher("python");
	}

	@Test
	public void testTypes() throws IllegalArgumentException, SAXException, IOException, ParserConfigurationException {
		Map<String, Object> names = new HashMap<String, Object>();

		MetadataDialect md = MetadataDialectFactory.createDialect("xml", IOUtils.toInputStream("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" + //
						"<root></root>", "UTF-8"));

		names.put("myInt", md.retypeObject("2"));
		names.put("myFloat", md.retypeObject("1.5"));
		names.put("myBool", md.retypeObject("true"));
		names.put("myStr", md.retypeObject("hello"));

		String code = "output = (type(myInt) is int) and (type(myFloat) is float) and (type(myBool) is bool)";
		Result result = null;
		try {
			result = dispatcher.dispatch(names, code);
		} catch (ScriptException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
			fail(e.getMessage());
		}
		assertEquals("true", result.getOutput().get(0).getValue());
	}

	@Test
	public void testResult() {
		Map<String, Object> names = new HashMap<String, Object>();
		names.put("x", 2);
		names.put("y", 2);
		String code = "def call(): \n"
				+ "  global status \n"
				+ "  global result \n"
				+ "  result = x == y \n"
				+ "  if (result == True):\n"
				+ "    status = 'SUCCESS' \n"
				+ "  else:\n"
				+ "    status = 'FAILURE'";
		Result result = null;
		try {
			result = dispatcher.dispatch(names, code);
		} catch (ScriptException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
			fail(e.getMessage());
		}
		assertEquals(Status.SUCCESS, result.getStatus());
	}

	@Test
	public void testEquality() {
		Map<String, Object> names = new HashMap<String, Object>();
		names.put("x", 2);
		names.put("y", 2);
		String code = "def call():    return(x == y)\n";
		Result result = null;
		try {
			result = dispatcher.dispatch(names, code);
		} catch (ScriptException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
			fail(e.getMessage());
		}
		assertEquals("true", result.getOutput().get(0).getValue());
	}

	@Test
	public void testMethodReturn() {
		Map<String, Object> names = new HashMap<String, Object>();
		names.put("x", 2);
		names.put("y", 2);
		String code =
				// "def call(a,b): return (a == b)\n\n";
				"def call():    return (x == y)\n\n";
		Result result = null;
		try {
			result = dispatcher.dispatch(names, code);
		} catch (ScriptException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
			fail(e.getMessage());
		}
		assertEquals("true", result.getOutput().get(0).getValue());
	}

	@Test
	public void testNullArg() {
		Map<String, Object> names = new HashMap<String, Object>();
		names.put("x", null);
		String code = "def call():    return (x == None)\n\n";
		Result result = null;
		try {
			result = dispatcher.dispatch(names, code);
		} catch (ScriptException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
			fail(e.getMessage());
		}
		assertEquals("true", result.getOutput().get(0).getValue());
	}

	@Test
	public void testCache() {
		Map<String, Object> names = new HashMap<String, Object>();
		names.put("x", 2);
		names.put("y", 2);
		InputStream library = this.getClass().getResourceAsStream("/code/mdq-cache.py");

		String code = "global output \n"
				+ "def call(): \n"
				+ "  return get('" + dataUrl + "') \n";

		Result result = null;
		try {
			code = IOUtils.toString(library, "UTF-8") + code;
			result = dispatcher.dispatch(names, code);
		} catch (Exception e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
			fail(e.getMessage());
		}
		result.getOutput();
		// make sure the file is named as expected
		assertTrue(result.getOutput().get(0).getValue().endsWith(DigestUtils.md5Hex(dataUrl)));
	}
}
