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
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.dataone.service.types.v2.SystemMetadata;

/**
 * An abstract base class for implementations of the {@link MetadataDialect}
 * interface.
 * 
 * Provides common functionality and utility methods that are shared across
 * different metadata dialects. This class is intended to reduce duplication and
 * enforce consistency across dialect implementations.
 * 
 * @author clark
 */

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
