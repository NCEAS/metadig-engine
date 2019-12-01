package edu.ucsb.nceas.mdqengine.filestore;

public enum StorageType {
    DATA("data"),
    GRAPH ("graph"),
    METADATA("metadata"),
    CODE("code"),
    TMP("tmp");

    private String type;

    StorageType(String type) {
        this.type = type;
    }

    public String toString(){
        switch(this){
            case DATA:
                return "data";
            case GRAPH:
                return "graph";
            case METADATA:
                return "metadata";
            case CODE:
                return "code";
            case TMP:
                return "tmp";
        }
        return null;
    }

    public static StorageType getValue(String value) {
        if(value.equalsIgnoreCase(DATA.toString()))
            return StorageType.DATA;
        else if(value.equalsIgnoreCase(GRAPH.toString()))
            return StorageType.GRAPH;
        else if(value.equalsIgnoreCase(METADATA.toString()))
            return StorageType.METADATA;
        else if(value.equalsIgnoreCase(CODE.toString()))
            return StorageType.CODE;
        else if(value.equalsIgnoreCase(TMP.toString()))
            return StorageType.TMP;
        else
            return null;
    }

//    public String getStorageType() {
//        return this.toString();
//    }
}
