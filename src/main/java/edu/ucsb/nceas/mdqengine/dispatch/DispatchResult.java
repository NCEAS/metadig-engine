package edu.ucsb.nceas.mdqengine.dispatch;

public class DispatchResult {
	
	private String value;
	
	private String message;
	
	private String status;

	public String getValue() {
		return value;
	}

	public void setValue(String value) {
		this.value = value;
	}

	public String getMessage() {
		return message;
	}

	public void setMessage(String message) {
		this.message = message;
	}

	public String getStatus() {
		return status;
	}

	public void setStatus(String status) {
		this.status = status;
	}

}
