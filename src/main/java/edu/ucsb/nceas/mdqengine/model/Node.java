package edu.ucsb.nceas.mdqengine.model;

public class Node {

    private String nodeId;
    private String lastHarvestDatetime;

    public String getNodeId() {
        return nodeId;
    }

    public void setNodeId(String nodeId) {
        this.nodeId = nodeId;
    }

    public void setLastHarvestDatetime(String lastHarvestDatetime) {
        this.lastHarvestDatetime = lastHarvestDatetime;
    }

    public String getLastHarvestDatetime() {
        return lastHarvestDatetime;
    }
}

