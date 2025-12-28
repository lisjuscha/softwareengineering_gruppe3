package com.flatmanager.model;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;

/**
 * Modell für eine Reinigungs-/Aufgaben-Entität im Putzplan.
 *
 * Enthält Informationen über Titel, Fälligkeitsdatum, Zuweisung, Wiederholung sowie Flags
 * für Erledigt und Dringend. Die Methode {@link #fromResultSet(ResultSet)} erzeugt eine
 * Instanz aus einer DB-Abfrage (ResultSet).
 */
public class CleaningTask {
    private Integer id;
    private String title;
    private LocalDate due;
    private String assignedTo;
    private String recurrence;
    private boolean completed;
    private boolean urgent;

    public CleaningTask(Integer id, String title, LocalDate due, String assignedTo, String recurrence, boolean completed, boolean urgent) {
        this.id = id;
        this.title = title;
        this.due = due;
        this.assignedTo = assignedTo;
        this.recurrence = recurrence == null ? "Einmalig" : recurrence;
        this.completed = completed;
        this.urgent = urgent;
    }

    public CleaningTask(String title, LocalDate due, String assignedTo, String recurrence, boolean urgent) {
        this(null, title, due, assignedTo, recurrence, false, urgent);
    }

    public Integer getId() { return id; }
    public void setId(Integer id) { this.id = id; }
    public String getTitle() { return title; }
    public LocalDate getDue() { return due; }
    public String getAssignedTo() { return assignedTo; }
    public String getRecurrence() { return recurrence; }
    public boolean isCompleted() { return completed; }
    public boolean isUrgent() { return urgent; }

    public void setTitle(String t) { this.title = t; }
    public void setDue(LocalDate d) { this.due = d; }
    public void setAssignedTo(String a) { this.assignedTo = a; }
    public void setRecurrence(String r) { this.recurrence = r; }
    public void setCompleted(boolean c) { this.completed = c; }
    public void setUrgent(boolean u) { this.urgent = u; }

    public boolean hasAssignee() { return assignedTo != null && !assignedTo.trim().isEmpty(); }

    /**
     * Erzeugt ein {@link CleaningTask} aus dem aktuellen ResultSet-Zeiger.
     * @param rs ResultSet mit Spalten (id, title, due, assignedTo, recurrence, completed, urgent)
     * @return neues CleaningTask-Objekt
     * @throws SQLException falls ein Feld nicht gelesen werden kann
     */
    public static CleaningTask fromResultSet(ResultSet rs) throws SQLException {
        Integer id = rs.getInt("id");
        String title = rs.getString("title");
        String dueStr = rs.getString("due");
        java.time.LocalDate due = dueStr == null ? null : java.time.LocalDate.parse(dueStr);
        String assignedTo = rs.getString("assignedTo");
        String recurrence = rs.getString("recurrence");
        boolean completed = rs.getInt("completed") == 1;
        boolean urgent = rs.getInt("urgent") == 1;
        return new CleaningTask(id, title, due, assignedTo, recurrence, completed, urgent);
    }
}
