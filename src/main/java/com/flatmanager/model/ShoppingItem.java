package com.flatmanager.model;

import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;

public class ShoppingItem {
    private int id;
    private String itemName;
    private String quantity;
    private String addedBy;
    private String category;

    // Neue Property für Checkbox
    private BooleanProperty selected = new SimpleBooleanProperty(false);

    public ShoppingItem(int id, String itemName, String quantity, String addedBy, String category) {
        this.id = id;
        this.itemName = itemName;
        this.quantity = quantity;
        this.addedBy = addedBy;
        this.category = category;
    }

    // Getter & Setter
    public int getId() { return id; }
    public String getItemName() { return itemName; }
    public String getQuantity() { return quantity; }
    public String getAddedBy() { return addedBy; }
    public String getCategory() { return category; }

    public void setId(int id) { this.id = id; }
    public void setItemName(String itemName) { this.itemName = itemName; }
    public void setQuantity(String quantity) { this.quantity = quantity; }
    public void setAddedBy(String addedBy) { this.addedBy = addedBy; }
    public void setCategory(String category) { this.category = category; }

    // Property für Checkbox
    public BooleanProperty selectedProperty() { return selected; }
    public boolean isSelected() { return selected.get(); }
    public void setSelected(boolean selected) { this.selected.set(selected); }
}
