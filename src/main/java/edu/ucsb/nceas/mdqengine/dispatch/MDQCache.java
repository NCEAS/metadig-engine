package edu.ucsb.nceas.mdqengine.dispatch;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;

import org.apache.commons.codec.digest.DigestUtils;
import org.apache.commons.io.IOUtils;

public class MDQCache {
		
	private String cacheDir = null;
	
	public MDQCache() {
		cacheDir = System.getProperty("java.io.tmpdir");
		if (!cacheDir.endsWith(File.separator)) {
			cacheDir += File.separator;
		}
		cacheDir += "mdq_cache" + File.separator;
	}
	
	public String get(String url) throws IOException {
		File f = null;
		
		// get MD5 of the URL string as the standard filename in the cache dir
		String key = url.toString();
		key = DigestUtils.md5Hex(key);
		
		// make sure we have the cache directory
		File dir = new File(cacheDir);
		if (!dir.exists()) {
			dir.mkdir();
		}
		
		// look for the file
		String path = cacheDir + key;
		f = new File(path);

		// if we don't find it, then download and store it
		if (!f.exists()) {
			InputStream input = new URL(url).openStream();
			IOUtils.copyLarge(input, new FileOutputStream(f));
		}
		
		// done!
		return f.getAbsolutePath();
		
	}
	
	public void clearCache() {
		File dir = new File(cacheDir);
		if (dir.exists()) {
			dir.delete();
		}
	}

}
