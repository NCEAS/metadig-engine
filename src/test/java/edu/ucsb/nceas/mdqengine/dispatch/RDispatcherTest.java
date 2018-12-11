package edu.ucsb.nceas.mdqengine.dispatch;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.io.File;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.script.ScriptException;

import org.apache.commons.codec.digest.DigestUtils;
import org.apache.commons.io.IOUtils;
import org.junit.Before;
import org.junit.Test;

import edu.ucsb.nceas.mdqengine.model.Result;
import edu.ucsb.nceas.mdqengine.model.Status;

public class RDispatcherTest {
	
	private Dispatcher dispatcher = null;

	private String dataUrl = "https://knb.ecoinformatics.org/knb/d1/mn/v2/object/doi:10.5063/AA/wolkovich.29.1";

	@Before
	public void init() {
		dispatcher = new RDispatcher();
	}
	
	@Test
	public void testEquality() {
		Map<String, Object> names = new HashMap<String, Object>();
		names.put("x", 2);
		names.put("y", 2);
		//String code = "result = list(value = (x == y));";
		// notice that the result construct is optional and taken care of by the dispatcher if omitted
		String code = "(x == y);";

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
	public void testNull() {
		Map<String, Object> names = new HashMap<String, Object>();
		names.put("x", null);
		names.put("y", 2);
		String code = "(is.null(x));";

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
	public void testComplex() {
		Map<String, Object> names = new HashMap<String, Object>();
		List<List<String>> list = new ArrayList<List<String>>();
		List<String> sublist = new ArrayList<String>();
		sublist.add("one");
		sublist.add("two");
		List<String> sublist2 = new ArrayList<String>();
		sublist2.add("red");
		sublist2.add("blue");
		list.add(sublist);
		list.add(sublist2);
		names.put("x", list);
		names.put("length", 2);
		String code = 
				"mdq_result = list(output = list(list(value= (length(x) == length) )));";
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
				"call <- function() { return (x == y) } \n"
				+ "mdq_result = list(output=list(list(value=call())));";
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
		String code = ""
				+ "status <- \"FAILURE\" \n"
		        + "value <- \"The packageId value should match the pattern 'scope.identifier.revision'\" \n"
		        + "mdq_result <- list(status=status, output=list(list(value=value))) \n"
		        ;
		Result result = null;
		try {
			result = dispatcher.dispatch(names, code);
		} catch (ScriptException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
			fail(e.getMessage());
		}
		assertEquals(Status.FAILURE, result.getStatus());
	}
	
	@Test
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
	public void testNumOfRecords() {

		// will come from metadata record using xpath queries
		// see metadata here: https://knb.ecoinformatics.org/knb/d1/mn/v2/object/doi:10.5063/AA/tao.1.1
		Map<String, Object> names = new HashMap<String, Object>();
		names.put("dataUrl", "https://knb.ecoinformatics.org/knb/d1/mn/v2/object/doi:10.5063/AA/tao.2.1");
		names.put("header", true);
		names.put("sep", ",");
		names.put("expected", 100);

		// R code to check congruence between loaded data and the metadata
		String code =
				"library(httr); \n"
		        + "tf <- tempfile(fileext='.csv') \n"
		        + "GET(dataUrl, write_disk(tf, overwrite=TRUE)) \n"
				+ "df <- read.csv(tf, header=header, sep=sep); \n"
				+ "mdq_result = list(output=list(list(value = (nrow(df) == expected) )))";
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

		InputStream library = this.getClass().getResourceAsStream("/code/mdq-cache.R");

		String code = "path <- get('" + dataUrl + "') \n" +
				"mdq_result <- list(output = list(list(value = path)))\n";

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
