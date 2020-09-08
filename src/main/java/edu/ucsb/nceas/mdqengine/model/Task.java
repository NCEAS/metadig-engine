package edu.ucsb.nceas.mdqengine.model;

import java.util.HashMap;

public class Task {

    private String taskName;
    private String taskType;
    private HashMap<String, String> lastHarvestDatetimes = new HashMap<>();

    public void setTaskName(String name) {
        this.taskName = name;
    }

    public String getTaskName() {
        return taskName;
    }

    public void setTaskType(String type) { this.taskType = type; }

    public String getTaskType() { return taskType; }

    public void setLastHarvestDatetimes(HashMap<String, String> lastHarvestDatetimes) {
        this.lastHarvestDatetimes = lastHarvestDatetimes;
    }

    public void setLastHarvestDatetime(String lastHarvestDatetime, String nodeId) {
        this.lastHarvestDatetimes.put(nodeId, lastHarvestDatetime);
    }

    public String getLastHarvestDatetime(String nodeId) {
        return this.lastHarvestDatetimes.get(nodeId);
    }

}
