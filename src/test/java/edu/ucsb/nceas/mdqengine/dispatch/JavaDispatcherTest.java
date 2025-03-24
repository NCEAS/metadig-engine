package edu.ucsb.nceas.mdqengine.dispatch;

import java.util.HashMap;
import java.util.Map;

import javax.script.ScriptException;

import edu.ucsb.nceas.mdqengine.model.Result;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.fail;

public class JavaDispatcherTest {
	
	private Dispatcher dispatcher = null;
	
	@BeforeEach
	public void init() {
		dispatcher = Dispatcher.getDispatcher("Java");
	}
	
	@Test
	public void testEquality() {
		Map<String, Object> names = new HashMap<String, Object>();
		names.put("x", 2);
		names.put("y", 2);
		String className = MockJavaEqualityCheck.class.getName();
		Result result = null;
		try {
			result = dispatcher.dispatch(names, className);
		} catch (ScriptException e) {
			e.printStackTrace();
			fail(e.getMessage());
		}
		assertEquals("true", result.getOutput().get(0).getValue());
	}
}
