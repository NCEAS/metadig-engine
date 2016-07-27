// define function to call the mdq cache
function get(url) {
	
	var file;
	
	// use Java implementation since we can
	var MDQCache = Java.type('edu.ucsb.nceas.mdqengine.dispatch.MDQCache');
	file = MDQCache.get(url);
	
	return file;
	
}
