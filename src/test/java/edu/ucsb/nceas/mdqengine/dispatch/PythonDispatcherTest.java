package edu.ucsb.nceas.mdqengine.dispatch;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

import java.util.HashMap;
import java.util.Map;

import javax.script.ScriptException;

import org.junit.Before;
import org.junit.Test;

import edu.ucsb.nceas.mdqengine.model.Result;
import edu.ucsb.nceas.mdqengine.processor.XMLDialect;

public class PythonDispatcherTest {
	
	private Dispatcher dispatcher = null;
	
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
		assertEquals("true", result.getValue());
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
		assertEquals("true", result.getValue());
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
		assertEquals("true", result.getValue());
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
		assertEquals("true", result.getValue());
	}
}
