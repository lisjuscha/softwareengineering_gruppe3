package com.flatmanager.model;

public class BudgetTransaction {
    private int id;
    private String description;
    private double amount;
    private String paidBy;
    private String date;
    private String category;

    public BudgetTransaction(int id, String description, double amount, String paidBy, String date, String category) {
        this.id = id;
        this.description = description;
        this.amount = amount;
        this.paidBy = paidBy;
        this.date = date;
        this.category = category;
    }

    public int getId() { return id; }
    public String getDescription() { return description; }
    public double getAmount() { return amount; }
    public String getPaidBy() { return paidBy; }
    public String getDate() { return date; }
    public String getCategory() { return category; }

    public void setId(int id) { this.id = id; }
    public void setDescription(String description) { this.description = description; }
    public void setAmount(double amount) { this.amount = amount; }
    public void setPaidBy(String paidBy) { this.paidBy = paidBy; }
    public void setDate(String date) { this.date = date; }
    public void setCategory(String category) { this.category = category; }
}
