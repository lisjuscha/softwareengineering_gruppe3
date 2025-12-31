package com.flatmanager.dao;

import com.flatmanager.model.CleaningTask;
import com.flatmanager.storage.Database;

import java.sql.*;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

public class CleaningTaskDao {

    public void init() throws SQLException {
        // Ensure table exists
        try (Connection conn = Database.getConnection();
             Statement st = conn.createStatement()) {
            st.execute("CREATE TABLE IF NOT EXISTS cleaning_tasks (" +
                    "id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                    "title TEXT NOT NULL, " +
                    "due TEXT, " +
                    "assigned_to TEXT, " +
                    "recurrence TEXT, " +
                    "urgent INTEGER DEFAULT 0, " +
                    "completed INTEGER DEFAULT 0)");
        }
    }

    public List<CleaningTask> listAll() throws SQLException {
        List<CleaningTask> list = new ArrayList<>();
        String sql = "SELECT id, title, due, assigned_to, recurrence, urgent, completed FROM cleaning_tasks ORDER BY (due IS NULL), due ASC";
        try (Connection conn = Database.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                int id = rs.getInt("id");
                String title = rs.getString("title");
                String dueText = rs.getString("due");
                LocalDate due = (dueText != null && !dueText.isBlank()) ? LocalDate.parse(dueText) : null;
                String assignedTo = rs.getString("assigned_to");
                String recurrence = rs.getString("recurrence");
                boolean urgent = rs.getInt("urgent") != 0;
                boolean completed = rs.getInt("completed") != 0;

                CleaningTask t = new CleaningTask(title, due, (assignedTo != null && !assignedTo.trim().isEmpty()) ? assignedTo : null, recurrence, urgent);
                t.setId(id);
                t.setCompleted(completed);
                list.add(t);
            }
        }
        return list;
    }

    // Neue Methode: listet alle als erledigt markierten Aufgaben (wird vor dem Löschen aufgerufen)
    public List<CleaningTask> listCompleted() throws SQLException {
        List<CleaningTask> list = new ArrayList<>();
        String sql = "SELECT id, title, due, assigned_to, recurrence, urgent, completed FROM cleaning_tasks WHERE completed = 1 ORDER BY (due IS NULL), due ASC";
        try (Connection conn = Database.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                int id = rs.getInt("id");
                String title = rs.getString("title");
                String dueText = rs.getString("due");
                LocalDate due = (dueText != null && !dueText.isBlank()) ? LocalDate.parse(dueText) : null;
                String assignedTo = rs.getString("assigned_to");
                String recurrence = rs.getString("recurrence");
                boolean urgent = rs.getInt("urgent") != 0;
                boolean completed = rs.getInt("completed") != 0;

                CleaningTask t = new CleaningTask(title, due, (assignedTo != null && !assignedTo.trim().isEmpty()) ? assignedTo : null, recurrence, urgent);
                t.setId(id);
                t.setCompleted(completed);
                list.add(t);
            }
        }
        return list;
    }

    public void insert(CleaningTask task) throws SQLException {
        String sql = "INSERT INTO cleaning_tasks (title, due, assigned_to, recurrence, urgent, completed) VALUES (?, ?, ?, ?, ?, ?)";
        try (Connection conn = Database.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, task.getTitle());
            ps.setString(2, task.getDue() != null ? task.getDue().toString() : null);
            ps.setString(3, task.getAssignedTo());
            ps.setString(4, task.getRecurrence());
            ps.setInt(5, task.isUrgent() ? 1 : 0);
            ps.setInt(6, task.isCompleted() ? 1 : 0);
            ps.executeUpdate();

            // Try generated keys first; fall back to SQLite's last_insert_rowid() wenn nicht unterstützt
            try {
                try (ResultSet keys = ps.getGeneratedKeys()) {
                    if (keys != null && keys.next()) {
                        task.setId(keys.getInt(1));
                        return;
                    }
                }
            } catch (SQLFeatureNotSupportedException ex) {
                // Treiber unterstützt getGeneratedKeys nicht -> fallback unten
            }

            // Fallback: last_insert_rowid() auf derselben Connection
            try (Statement st = conn.createStatement();
                 ResultSet rs = st.executeQuery("SELECT last_insert_rowid()")) {
                if (rs.next()) {
                    task.setId(rs.getInt(1));
                }
            }
        }
    }

    public void update(CleaningTask task) throws SQLException {
        String sql = "UPDATE cleaning_tasks SET title = ?, due = ?, assigned_to = ?, recurrence = ?, urgent = ?, completed = ? WHERE id = ?";
        try (Connection conn = Database.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, task.getTitle());
            ps.setString(2, task.getDue() != null ? task.getDue().toString() : null);
            ps.setString(3, task.getAssignedTo());
            ps.setString(4, task.getRecurrence());
            ps.setInt(5, task.isUrgent() ? 1 : 0);
            ps.setInt(6, task.isCompleted() ? 1 : 0);
            ps.setInt(7, task.getId());
            ps.executeUpdate();
        }
    }

    public void deleteCompleted() throws SQLException {
        String sql = "DELETE FROM cleaning_tasks WHERE completed = 1";
        try (Connection conn = Database.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.executeUpdate();
        }
    }
}