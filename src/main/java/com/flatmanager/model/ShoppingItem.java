package com.flatmanager.model;

public class ShoppingItem {
    private int id;
    private String itemName;
    private String quantity;
    private String addedBy;
    private boolean purchased;

    public ShoppingItem(int id, String itemName, String quantity, String addedBy, boolean purchased) {
        this.id = id;
        this.itemName = itemName;
        this.quantity = quantity;
        this.addedBy = addedBy;
        this.purchased = purchased;
    }

    public int getId() { return id; }
    public String getItemName() { return itemName; }
    public String getQuantity() { return quantity; }
    public String getAddedBy() { return addedBy; }
    public boolean isPurchased() { return purchased; }

    public void setId(int id) { this.id = id; }
    public void setItemName(String itemName) { this.itemName = itemName; }
    public void setQuantity(String quantity) { this.quantity = quantity; }
    public void setAddedBy(String addedBy) { this.addedBy = addedBy; }
    public void setPurchased(boolean purchased) { this.purchased = purchased; }
}
