package edu.ucsb.nceas.mdqengine.dispatch;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

import java.util.HashMap;
import java.util.Map;

import javax.script.ScriptException;

import org.junit.Before;
import org.junit.Test;

public class JavaDispatcherTest {
	
	private Dispatcher dispatcher = null;
	
	@Before
	public void init() {
		dispatcher = Dispatcher.getDispatcher("Java");
	}
	
	@Test
	public void testEquality() {
		Map<String, Object> names = new HashMap<String, Object>();
		names.put("x", 2);
		names.put("y", 2);
		String className = MockJavaEqualityCheck.class.getName();
		DispatchResult result = null;
		try {
			result = dispatcher.dispatch(names, className);
		} catch (ScriptException e) {
			e.printStackTrace();
			fail(e.getMessage());
		}
		assertEquals("true", result.getValue());
	}
}
