package com.flatmanager.model;

import java.time.LocalDate;
import java.util.Objects;

public class BudgetTransaction {

    private int id;
    private double amount;
    private String date; // gespeichertes Datum als ISO-String (yyyy-MM-dd)
    private String description;
    private String category;
    private String meta; // optionales 6. Feld zur Kompatibilität

    // Parameterloser Konstruktor (wird vom DAO benötigt)
    public BudgetTransaction() {
    }

    // Gängiger Konstruktor mit 6 Parametern (beibehalten für Kompatibilität)
    public BudgetTransaction(int id, double amount, String date, String description, String category, String meta) {
        this.id = id;
        this.amount = amount;
        this.date = date;
        this.description = description;
        this.category = category;
        this.meta = meta;
    }

    // Komfort-Konstruktor (optional)
    public BudgetTransaction(double amount, String date) {
        this.amount = amount;
        this.date = date;
    }

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public double getAmount() {
        return amount;
    }

    public void setAmount(double amount) {
        this.amount = amount;
    }

    // liefert Datum als String
    public String getDate() {
        return date;
    }

    // existierende Setter: akzeptiert String
    public void setDate(String date) {
        this.date = date;
    }

    // Überladung: akzeptiert LocalDate und konvertiert nach ISO-String
    public void setDate(LocalDate localDate) {
        this.date = (localDate != null) ? localDate.toString() : null;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getCategory() {
        return category;
    }

    public void setCategory(String category) {
        this.category = category;
    }

    public String getMeta() {
        return meta;
    }

    public void setMeta(String meta) {
        this.meta = meta;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        BudgetTransaction that = (BudgetTransaction) o;
        return id == that.id &&
                Double.compare(that.amount, amount) == 0 &&
                Objects.equals(date, that.date) &&
                Objects.equals(description, that.description) &&
                Objects.equals(category, that.category) &&
                Objects.equals(meta, that.meta);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, amount, date, description, category, meta);
    }

    @Override
    public String toString() {
        return "BudgetTransaction{" +
                "id=" + id +
                ", amount=" + amount +
                ", date='" + date + '\'' +
                ", description='" + description + '\'' +
                ", category='" + category + '\'' +
                ", meta='" + meta + '\'' +
                '}';
    }
}