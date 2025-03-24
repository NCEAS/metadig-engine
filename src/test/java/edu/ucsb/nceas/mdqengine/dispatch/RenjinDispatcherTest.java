package edu.ucsb.nceas.mdqengine.dispatch;

import edu.ucsb.nceas.mdqengine.model.Result;
import edu.ucsb.nceas.mdqengine.model.Status;

import javax.script.ScriptException;
import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.fail;

public class RenjinDispatcherTest {
	
	private Dispatcher dispatcher = null;
	
	@BeforeEach
	@Disabled("ignoring Renjin init")
	public void init() {
		dispatcher = Dispatcher.getDispatcher("Renjin");
	}
	
	@Test
	@Disabled("ignoring Renjin testEquality")

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
		assertEquals("TRUE", result.getOutput().get(0).getValue());
	}
	
	@Test
	@Disabled("ignoring testMethodReturn")
	public void testMethodReturn() {
		Map<String, Object> names = new HashMap<String, Object>();
		names.put("x", 2);
		names.put("y", 2);
		String code = 
				"call <- function() { return (x == y) }";
		Result result = null;
		try {
			result = dispatcher.dispatch(names, code);
		} catch (ScriptException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
			fail(e.getMessage());
		}
		assertEquals("TRUE", result.getOutput().get(0).getValue());
	}
	
	@Test
	@Disabled("ignoring testError")
	public void testError() {
		Map<String, Object> names = new HashMap<String, Object>();
		names.put("x", 1);
		names.put("y", 2);
		String code = "stopifnot(x==y)";
		Result result = null;
		try {
			result = dispatcher.dispatch(names, code);
		} catch (ScriptException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
			fail(e.getMessage());
		}
		assertEquals(Status.ERROR, result.getStatus());
	}
	
	@Test
	@Disabled("ignoring testNumOfRecords")
	public void testNumOfRecords() {
		
		// will come from metadata record using xpath queries
		// see metadata here: https://knb.ecoinformatics.org/knb/d1/mn/v2/object/doi:10.5063/AA/tao.1.1
		Map<String, Object> names = new HashMap<String, Object>();
		names.put("dataUrl", "https://knb.ecoinformatics.org/knb/d1/mn/v2/object/doi:10.5063/AA/tao.2.1");
		names.put("header", true);
		names.put("sep", ",");
		names.put("expected", 100);
		
		// R code to check congruence between loaded data and the metadata
		String code = "df <- read.csv(dataUrl, header=header, sep=sep); nrow(df) == expected";
		Result result = null;
		try {
			result = dispatcher.dispatch(names, code);
		} catch (ScriptException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
			fail(e.getMessage());
		}
		assertEquals("TRUE", result.getOutput().get(0).getValue());
	}

}
