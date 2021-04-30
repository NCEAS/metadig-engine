package edu.ucsb.nceas.mdqengine.model;

public enum Status {
	SUCCESS("success"),
	FAILURE("failure"),
	ERROR("error"),
	SKIP("skip");

	private final String type;

	Status(String type) {
		this.type = type;
	}

	public String getType() {
		return this.type.toLowerCase();
	}
}
