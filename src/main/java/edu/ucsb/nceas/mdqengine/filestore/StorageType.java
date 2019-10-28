package edu.ucsb.nceas.mdqengine.filestore;

public enum StorageType {
    DATA("data"),
    GRAPH ("graph"),
    METADATA("metadata"),
    CODE("code"),
    TMP("tmp");

    private final String type;

    StorageType(String type) {
        this.type = type;
    }

    public String getStorageTyp() {
        return this.type.toLowerCase();
    }
}
