package edu.ucsb.nceas.mdqengine.r;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

import java.util.HashMap;
import java.util.Map;

import javax.script.ScriptException;

import org.junit.Before;
import org.junit.Test;

public class RDispatcherTest {
	
	private RDispatcher dispatcher = null;
	
	@Before
	public void init() {
		dispatcher = new RDispatcher();
	}
	
	@Test
	public void testEquality() {
		Map<String, Object> names = new HashMap<String, Object>();
		names.put("x", 2);
		names.put("y", 2);
		String code = "x == y";
		String result = null;
		try {
			result = dispatcher.dispatch(names, code);
		} catch (ScriptException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
			fail(e.getMessage());
		}
		assertEquals("TRUE", result);
	}

}
