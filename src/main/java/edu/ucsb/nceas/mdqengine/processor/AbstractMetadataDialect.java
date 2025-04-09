package edu.ucsb.nceas.mdqengine.processor;

import edu.ucsb.nceas.mdqengine.model.Result;
import edu.ucsb.nceas.mdqengine.model.Output;
import edu.ucsb.nceas.mdqengine.model.Namespace;

import java.io.IOException;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;

import org.apache.commons.codec.binary.Base64;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang.BooleanUtils;
import org.apache.commons.lang.math.NumberUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.dataone.service.types.v2.SystemMetadata;

public abstract class AbstractMetadataDialect implements MetadataDialect {

    protected Map<String, Object> params;
    protected String directory;
    protected SystemMetadata systemMetadata;
	protected Map<String, Namespace> namespaces = new HashMap<String, Namespace>();

	public static Log log = LogFactory.getLog(XMLDialect.class);

    @Override 
    public Result postProcess(Result result) {
		// Return the result as-is if there are no outputs to post-process
		if (result.getOutput() == null) {
			log.debug("Skipping postProcess step because this result's output is null.");
			return (result);
		}

		// Post-process each output (if needed)
		for (Output output : result.getOutput()) {
			if (output == null) {
				log.debug("Output was null.");
				continue;
			}

			String value = output.getValue();
			if (value != null) {
				Path path = null;
				try {
					path = Paths.get(value);
				} catch (InvalidPathException e) {
					// NOPE
					return result;
				}

				if (path.toFile().exists()) {
					// encode it
					String encoded = null;
					try {
						encoded = Base64.encodeBase64String(IOUtils.toByteArray(path.toUri()));
						output.setValue(encoded);
						// TODO: set mime-type when we have support for that, or assume they did it
						// already?
					} catch (IOException e) {
						log.error(e.getMessage());
					}
				}
			}
		}

		return result;
	}

    	/*
	 * Retype an object based on a few simple assumptions. A "String" value is
	 * typically passed in. If only numeric characters are present in the String,
	 * then the object is caste to type "Number". If the string value appears to
	 * be an "affirmative" or "negative" value (e.g. "Y", "Yes", "N", "No", ...)
	 * then the value is caste to "Boolean".
	 */
    @Override
	public Object retypeObject(Object value) {
		Object result = value;

		if (value instanceof String stringValue) {
			// try to type the value correctly
			if (NumberUtils.isNumber(stringValue) && !stringValue.matches("^0\\d*$")) {
				// If it's a valid number and doesn't start with zeros, create a Number object
				result = NumberUtils.createNumber(stringValue);
			} else {
				// try to convert to bool
				Boolean bool = BooleanUtils.toBooleanObject((String) value);
				// if it worked, return the boolean, otherwise the original result is returned
				if (bool != null) {
					result = bool;
				}
			}

		}

		return result;
	}

    @Override
    public Map<String, Object> getParams() {
		return params;
	}

    @Override
	public void setParams(Map<String, Object> params) {
		this.params = params;
	}

    @Override
	public void setDirectory(String dir) {
		this.directory = dir;
	}

    @Override
	public SystemMetadata getSystemMetadata() {
		return systemMetadata;
	}

    @Override
	public void setSystemMetadata(SystemMetadata systemMetadata) {
		this.systemMetadata = systemMetadata;
	}
}

