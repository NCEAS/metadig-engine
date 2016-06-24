package edu.ucsb.nceas.mdqengine.dispatch;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

import java.util.HashMap;
import java.util.Map;

import javax.script.ScriptException;

import org.junit.Before;
import org.junit.Test;

import edu.ucsb.nceas.mdqengine.model.Result;

public class JavaScriptDispatcherTest {
	
	private Dispatcher dispatcher = null;
	
	@Before
	public void init() {
		dispatcher = Dispatcher.getDispatcher("JavaScript");
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
				"function call() { return (x == y) }";
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
