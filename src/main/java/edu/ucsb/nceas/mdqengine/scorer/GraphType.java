package edu.ucsb.nceas.mdqengine.scorer;

public enum GraphType {
    CUMULATIVE("cumulative"),
    MONTHLY("monthly");

    private final String type;

    GraphType(String type) {
        this.type = type;
    }

    public String getType() {
        return this.type.toLowerCase();
    }
}

