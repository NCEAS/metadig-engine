package edu.ucsb.nceas.mdqengine.grapher;

public enum GraphType {
    CUMULATIVE("cumulative"),
    MONTHLY("monthly");

    private final String type;

    GraphType(String type) {
        this.type = type;
    }

    public String getTyp() {
        return this.type.toLowerCase();
    }
}

