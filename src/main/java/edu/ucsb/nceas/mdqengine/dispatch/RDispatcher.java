package edu.ucsb.nceas.mdqengine.dispatch;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.util.Calendar;
import java.util.Map;

import javax.script.ScriptException;

import org.apache.commons.io.IOUtils;

import edu.ucsb.nceas.mdqengine.model.Result;
import edu.ucsb.nceas.mdqengine.model.Status;
import edu.ucsb.nceas.mdqengine.serialize.JsonMarshaller;

public class RDispatcher extends Dispatcher {
		
	@Override
	public Result dispatch(Map<String, Object> names, String code) throws ScriptException {

		Result result = null;
		File script = null;
		File input = null;
		File output = null;
		
		String preCode = 
				"library('jsonlite', quietly=TRUE); \n"
				+ "args = commandArgs(trailingOnly=TRUE); \n"
				+ "input = args[1]; \n"
				+ "output = args[2]; \n"
				+ "vars <- fromJSON(readLines(input, warn=FALSE)); \n"
				+ "for (i in seq_along(vars)) { \n"
				+ "	assign(names(vars)[i], vars[[i]]); \n"
				+ "} \n";
		
		String postCode =
				"\n"
				+ "jsonResult <- toJSON(result, auto_unbox=TRUE); \n"
				+ "writeLines(jsonResult, con = output); \n";
		
		try {
			
			script = File.createTempFile("mdqe_script", ".R");
			input = File.createTempFile("mdqe_input", ".json");
			output = File.createTempFile("mdqe_output", ".json");
			
			log.debug("script: \n" + script.getAbsolutePath());
			log.debug("input: \n" + input.getAbsolutePath());
			log.debug("output: \n" + output.getAbsolutePath());
			
			String combinedCode = preCode + code + postCode;
			log.debug("Running scripted code: \n" + combinedCode);
			
			// write code to script file
			IOUtils.write(combinedCode, new FileOutputStream(script), "UTF-8");

			// write input variables to json
			String inputJson = JsonMarshaller.toJson(names);
			IOUtils.write(inputJson, new FileOutputStream(input), "UTF-8");
			
			// run the process
			ProcessBuilder pb = new ProcessBuilder(
					"Rscript", 
					"--vanilla", 
					script.getAbsolutePath(), 
					input.getAbsolutePath(), 
					output.getAbsolutePath());
			Process p = pb.start();
			int ret = p.waitFor();
			
			String stdOut = IOUtils.toString(p.getInputStream(), "UTF-8");
			String stdErr = IOUtils.toString(p.getErrorStream(), "UTF-8");
			log.debug("stdOut:" + stdOut);
			log.debug("stdErr:" + stdErr);
			
			if (ret > 0) {
				// report an error
				result = new Result();
				result.setStatus(Status.ERROR);
				result.setMessage(stdErr);
			} else {
				// read result from output
				String jsonOutput = IOUtils.toString(new FileInputStream(output), "UTF-8");
				result = (Result) JsonMarshaller.fromJson(jsonOutput, Result.class);
			}
						
		} catch (Exception e) {
			throw new ScriptException(e);
		} finally {
			// clean up
			script.delete();
			input.delete();
			output.delete();
		}
		
		result.setTimestamp(Calendar.getInstance().getTime());
		
		log.debug("Result value: " + result.getValue());
		log.debug("Result status: " + result.getStatus());
		log.debug("Result message: " + result.getMessage());

		return result;
	}

}
