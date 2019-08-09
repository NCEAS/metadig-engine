package edu.ucsb.nceas.mdqengine.dispatch;

import edu.ucsb.nceas.mdqengine.model.Result;
import edu.ucsb.nceas.mdqengine.model.Status;
import org.apache.commons.codec.digest.DigestUtils;
import org.apache.commons.io.IOUtils;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;

import javax.script.ScriptException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;

import static org.junit.Assert.*;

public class JavaScriptDispatcherTest {
	
	private Dispatcher dispatcher = null;
	
	private String dataUrl = "https://knb.ecoinformatics.org/knb/d1/mn/v2/object/doi:10.5063/AA/wolkovich.29.1";

	
	@Before
	@Ignore("ignoring init")
	public void init() {
		dispatcher = Dispatcher.getDispatcher("JavaScript");
	}
	
	@Test
	@Ignore("ignoring testEquality")
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
		assertEquals("true", result.getOutput().get(0).getValue());
	}
	
	@Test
	@Ignore("ignoring testResult")
	public void testResult() {
		Map<String, Object> names = new HashMap<String, Object>();
		names.put("x", 2);
		names.put("y", 2);
		String code = ""
				+ "var Result = Java.type('edu.ucsb.nceas.mdqengine.model.Result');"
				+ "var Output = Java.type('edu.ucsb.nceas.mdqengine.model.Output');"
				+ "mdq_result = new Result();"
				+ "mdq_result.output = new Output('Testing the result object, x equals y');"
				+ "mdq_result.status = Java.type('edu.ucsb.nceas.mdqengine.model.Status').SUCCESS;"
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
	@Ignore("ignoring testMethodReturn")
	public void testMethodReturn() {
		Map<String, Object> names = new HashMap<String, Object>();
		names.put("x", 2);
		names.put("y", 2);
		String code = 
				"function call() { return (x == y) }";
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
	@Ignore("ignoring testCache")
	public void testCache() {
		Map<String, Object> names = new HashMap<String, Object>();
		names.put("x", 2);
		names.put("y", 2);
		InputStream library = this.getClass().getResourceAsStream("/code/mdq-cache.js");
		
		String code = "get('" + dataUrl + "')";
		
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
		assertTrue(result.getOutput().get(0).getValue().endsWith(DigestUtils.md5Hex(dataUrl)));
	}
}
