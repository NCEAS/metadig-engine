package edu.ucsb.nceas.mdqengine.model;

public class Task {

    private String taskName;
    private String taskType;
    private String lastHarvestDatetime;

    public void setTaskName(String name) {
        this.taskName = name;
    }

    public String getTaskName() {
        return taskName;
    }

    public void setTaskType(String type) { this.taskType = type; }

    public String getTaskType() { return taskType; }

    public void setLastHarvestDatetime(String lastHarvestDatetime) {
        this.lastHarvestDatetime = lastHarvestDatetime;
    }

    public String getLastHarvestDatetime() { return lastHarvestDatetime; }

}
