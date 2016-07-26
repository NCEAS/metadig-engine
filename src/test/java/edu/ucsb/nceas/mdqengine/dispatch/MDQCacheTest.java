package edu.ucsb.nceas.mdqengine.dispatch;

import static org.junit.Assert.*;

import java.io.File;
import java.net.URL;

import org.apache.commons.codec.digest.DigestUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.junit.Test;

public class MDQCacheTest {
	
	private Log log = LogFactory.getLog(this.getClass());
	
	private String dataUrl = "https://cn.dataone.org/cn/v2/resolve/doi:10.5063/AA/wolkovich.29.1";
	
	@Test
	public void testCache() {
		MDQCache cache = new MDQCache();
		
		File file = null;
		URL url = null;

		try {
			url = new URL(dataUrl);
			file = cache.get(url);
		} catch (Exception e) {
			fail(e.getMessage());
		}
		assertTrue(file.exists());
		assertEquals(DigestUtils.md5Hex(url.toString()), file.getName());

		log.debug("fetched URL: " + url + " and cached file: " + file.getAbsolutePath());
		
	}

}
