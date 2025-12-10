package com.flatmanager.dao;

import com.flatmanager.model.BudgetTransaction;
import com.flatmanager.storage.Database;

import java.sql.*;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

public class BudgetTransactionDao {

    public void init() throws SQLException {
        Connection conn = Database.getConnection();
        try (Statement st = conn.createStatement()) {
            st.execute("CREATE TABLE IF NOT EXISTS budget_transactions (" +
                    "id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                    "amount REAL NOT NULL, " +
                    "date TEXT NOT NULL, " +
                    "description TEXT, " +
                    "category TEXT)");
        }
    }

    public List<BudgetTransaction> listAll() throws SQLException {
        List<BudgetTransaction> list = new ArrayList<>();
        String sql = "SELECT id, amount, date, description, category FROM budget_transactions ORDER BY date DESC";
        Connection conn = Database.getConnection();
        try (PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                double amount = rs.getDouble("amount");
                String dateText = rs.getString("date");
                LocalDate date = (dateText != null && !dateText.isBlank()) ? LocalDate.parse(dateText) : null;

                BudgetTransaction t = new BudgetTransaction();
                t.setAmount(amount);
                t.setDate(date);
                t.setId(rs.getInt("id"));
                t.setDescription(rs.getString("description"));
                t.setCategory(rs.getString("category"));

                list.add(t);
            }
        }
        return list;
    }

    public void insert(BudgetTransaction t) throws SQLException {
        String sql = "INSERT INTO budget_transactions (amount, date, description, category) VALUES (?, ?, ?, ?)";
        Connection conn = Database.getConnection();
        try (PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setDouble(1, t.getAmount());
            ps.setString(2, t.getDate() != null ? t.getDate().toString() : null);
            ps.setString(3, t.getDescription());
            ps.setString(4, t.getCategory());
            ps.executeUpdate();

            try (ResultSet keys = ps.getGeneratedKeys()) {
                if (keys != null && keys.next()) {
                    t.setId(keys.getInt(1));
                    return;
                }
            } catch (SQLFeatureNotSupportedException ignored) {
            }

            try (Statement st = conn.createStatement();
                 ResultSet rs = st.executeQuery("SELECT last_insert_rowid()")) {
                if (rs.next()) t.setId(rs.getInt(1));
            }
        }
    }

    public void update(BudgetTransaction t) throws SQLException {
        String sql = "UPDATE budget_transactions SET amount = ?, date = ?, description = ?, category = ? WHERE id = ?";
        Connection conn = Database.getConnection();
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setDouble(1, t.getAmount());
            ps.setString(2, t.getDate() != null ? t.getDate().toString() : null);
            ps.setString(3, t.getDescription());
            ps.setString(4, t.getCategory());
            ps.setInt(5, t.getId());
            ps.executeUpdate();
        }
    }

    public void deleteById(int id) throws SQLException {
        String sql = "DELETE FROM budget_transactions WHERE id = ?";
        Connection conn = Database.getConnection();
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, id);
            ps.executeUpdate();
        }
    }
}