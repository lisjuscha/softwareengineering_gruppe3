package com.flatmanager.model;

public class CleaningTask {
    private int id;
    private String task;
    private String assignedTo;
    private String dueDate;
    private boolean completed;

    public CleaningTask(int id, String task, String assignedTo, String dueDate, boolean completed) {
        this.id = id;
        this.task = task;
        this.assignedTo = assignedTo;
        this.dueDate = dueDate;
        this.completed = completed;
    }

    public int getId() { return id; }
    public String getTask() { return task; }
    public String getAssignedTo() { return assignedTo; }
    public String getDueDate() { return dueDate; }
    public boolean isCompleted() { return completed; }

    public void setId(int id) { this.id = id; }
    public void setTask(String task) { this.task = task; }
    public void setAssignedTo(String assignedTo) { this.assignedTo = assignedTo; }
    public void setDueDate(String dueDate) { this.dueDate = dueDate; }
    public void setCompleted(boolean completed) { this.completed = completed; }
}
