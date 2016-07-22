package edu.ucsb.nceas.mdqengine.model;

/**
 * Captures additional information about the document being QCed.
 * Intended to be used in conjunction with a Run  to more easily 
 * analyze the results
 * @author leinfelder
 *
 */
public class Metadata {
	
	private String formatId;
	
	private String datasource;
	
	private String dataUrl;
	
	private String rightsHolder;

	public String getFormatId() {
		return formatId;
	}

	public void setFormatId(String formatId) {
		this.formatId = formatId;
	}

	public String getDatasource() {
		return datasource;
	}

	public void setDatasource(String datasource) {
		this.datasource = datasource;
	}

	public String getDataUrl() {
		return dataUrl;
	}

	public void setDataUrl(String dataUrl) {
		this.dataUrl = dataUrl;
	}

	public String getRightsHolder() {
		return rightsHolder;
	}

	public void setRightsHolder(String rightsHolder) {
		this.rightsHolder = rightsHolder;
	}

}
