package com.flatmanager.model;

import javafx.beans.property.*;

public class ShoppingItem {

    private final IntegerProperty id;
    private final StringProperty itemName;
    private final StringProperty quantity;
    private final StringProperty addedBy;
    private final StringProperty category;
    private final StringProperty purchasedFor;

    private final BooleanProperty purchased;
    private final BooleanProperty selected;

    public ShoppingItem(int id, String itemName, String quantity, String addedBy, String category, String purchasedFor, boolean purchased) {
        this.id = new SimpleIntegerProperty(id);
        this.itemName = new SimpleStringProperty(itemName);
        this.quantity = new SimpleStringProperty(quantity);
        this.addedBy = new SimpleStringProperty(addedBy);
        this.category = new SimpleStringProperty(category);
        this.purchasedFor = new SimpleStringProperty(purchasedFor);

        this.purchased = new SimpleBooleanProperty(purchased);
        this.selected = new SimpleBooleanProperty(purchased);
    }

    public int getId() {
        return id.get();
    }

    public IntegerProperty idProperty() {
        return id;
    }

    public String getItemName() {
        return itemName.get();
    }

    public StringProperty itemNameProperty() {
        return itemName;
    }

    public String getQuantity() {
        return quantity.get();
    }

    public StringProperty quantityProperty() {
        return quantity;
    }

    public String getAddedBy() {
        return addedBy.get();
    }

    public StringProperty addedByProperty() {
        return addedBy;
    }

    public String getCategory() {
        return category.get();
    }

    public StringProperty categoryProperty() {
        return category;
    }

    public String getPurchasedFor() {
        return purchasedFor.get();
    }

    public StringProperty purchasedForProperty() {
        return purchasedFor;
    }

    public void setPurchasedFor(String value) {
        purchasedFor.set(value);
    }

    public BooleanProperty selectedProperty() {
        return selected;
    }

    public boolean isSelected() {
        return selected.get();
    }

    public void setSelected(boolean value) {
        selected.set(value);
    }

    public BooleanProperty purchasedProperty() {
        return purchased;
    }

    public boolean isPurchased() {
        return purchased.get();
    }

    public void setPurchased(boolean value) {
        purchased.set(value);
    }
}