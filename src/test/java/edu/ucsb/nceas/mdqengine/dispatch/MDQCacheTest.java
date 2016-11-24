package edu.ucsb.nceas.mdqengine.dispatch;

import static org.junit.Assert.*;

import java.io.File;

import org.apache.commons.codec.digest.DigestUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.junit.Test;

public class MDQCacheTest {
	
	private Log log = LogFactory.getLog(this.getClass());
	
	private String dataUrl = "https://knb.ecoinformatics.org/knb/d1/mn/v2/object/doi:10.5063/AA/wolkovich.29.1";
	
	@Test
	public void testCache() {
		
		String path = null;
		File file = null;

		try {
			path = MDQCache.get(dataUrl);
			file = new File(path);
		} catch (Exception e) {
			fail(e.getMessage());
		}
		assertTrue(file.exists());
		assertEquals(DigestUtils.md5Hex(dataUrl), file.getName());

		log.debug("fetched URL: " + dataUrl + " and cached file: " + file.getAbsolutePath());
		
	}

}
