package edu.ucsb.nceas.mdqengine.dispatch;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;

import javax.script.ScriptException;

import org.apache.commons.codec.digest.DigestUtils;
import org.apache.commons.io.IOUtils;
import org.junit.Before;
import org.junit.Test;

import edu.ucsb.nceas.mdqengine.model.Result;
import edu.ucsb.nceas.mdqengine.model.Status;
import edu.ucsb.nceas.mdqengine.processor.XMLDialect;

public class PythonDispatcherTest {
	
	private Dispatcher dispatcher = null;
	
	private String dataUrl = "https://cn.dataone.org/cn/v2/resolve/doi:10.5063/AA/wolkovich.29.1";

	@Before
	public void init() {
		dispatcher = Dispatcher.getDispatcher("python");
	}
	
	@Test
	public void testTypes() {
		Map<String, Object> names = new HashMap<String, Object>();
		
		names.put("myInt", XMLDialect.retypeObject("2"));
		names.put("myFloat", XMLDialect.retypeObject("1.5"));
		names.put("myBool", XMLDialect.retypeObject("true"));
		names.put("myStr", XMLDialect.retypeObject("hello"));

		String code = "(type(myInt) is int) and (type(myFloat) is float) and (type(myBool) is bool)";
		Result result = null;
		try {
			result = dispatcher.dispatch(names, code);
		} catch (ScriptException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
			fail(e.getMessage());
		}
		assertEquals("true", result.getOutput());
	}
	
	@Test
	public void testResult() {
		Map<String, Object> names = new HashMap<String, Object>();
		names.put("x", 2);
		names.put("y", 2);
		String code = "def call(): \n"
				+ "  from edu.ucsb.nceas.mdqengine.model import Result \n"
				+ "  mdq_result = Result() \n"
				+ "  mdq_result.setOutput(\"Testing the result object, X equals Y\") \n"
				+ "  from edu.ucsb.nceas.mdqengine.model import Status \n"
				+ "  mdq_result.setStatus(Status.SUCCESS) \n"
				+ "  return (mdq_result) \n"
				;
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
		String code = "x == y";
		Result result = null;
		try {
			result = dispatcher.dispatch(names, code);
		} catch (ScriptException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
			fail(e.getMessage());
		}
		assertEquals("true", result.getOutput());
	}
	
	@Test
	public void testMethodReturn() {
		Map<String, Object> names = new HashMap<String, Object>();
		names.put("x", 2);
		names.put("y", 2);
		String code = 
				//"def call(a,b):    return (a == b)\n\n";
				"def call():    return (x == y)\n\n";
		Result result = null;
		try {
			result = dispatcher.dispatch(names, code);
		} catch (ScriptException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
			fail(e.getMessage());
		}
		assertEquals("true", result.getOutput());
	}
	@Test
	public void testNullArg() {
		Map<String, Object> names = new HashMap<String, Object>();
		names.put("x", null);
		String code = 
				"def call():    return (x == None)\n\n";
		Result result = null;
		try {
			result = dispatcher.dispatch(names, code);
		} catch (ScriptException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
			fail(e.getMessage());
		}
		assertEquals("true", result.getOutput());
	}
	
	@Test
	public void testCache() {
		Map<String, Object> names = new HashMap<String, Object>();
		names.put("x", 2);
		names.put("y", 2);
		InputStream library = this.getClass().getResourceAsStream("/code/mdq-cache.py");
		
		String code = 
				"def call(): \n"
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
		// make sure the file is named as expected
		assertTrue(result.getOutput().endsWith(DigestUtils.md5Hex(dataUrl)));
	}
}
