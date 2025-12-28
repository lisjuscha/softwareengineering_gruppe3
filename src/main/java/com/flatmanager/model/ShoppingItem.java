package com.flatmanager.model;

import javafx.beans.property.*;

/**
 * Modellklasse für einen Eintrag in der Einkaufsliste.
 *
 * <p>Die Klasse verwendet JavaFX-Properties, damit Views einfach an Daten gebunden werden können.
 * Sie enthält Aliase (z. B. getName/setName, isBought/setBought) für Kompatibilität mit älteren DAOs.</p>
 */
public class ShoppingItem {

    private final IntegerProperty id;
    private final StringProperty itemName;
    private final StringProperty quantity;
    private final StringProperty addedBy;
    private final StringProperty category;
    private final StringProperty purchasedFor;

    private final BooleanProperty purchased;
    private final BooleanProperty selected;

    /**
     * Vollständiger Konstruktor mit allen Feldern.
     */
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

    // No-arg constructor for reflection-based DAO fallback
    /**
     * Parameterloser Konstruktor für Reflection/DAO-Fallback.
     */
    public ShoppingItem() {
        this.id = new SimpleIntegerProperty(0);
        this.itemName = new SimpleStringProperty("");
        this.quantity = new SimpleStringProperty("1");
        this.addedBy = new SimpleStringProperty("");
        this.category = new SimpleStringProperty("");
        this.purchasedFor = new SimpleStringProperty("");

        this.purchased = new SimpleBooleanProperty(false);
        this.selected = new SimpleBooleanProperty(false);
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

    // Compatibility alias for DAO which expects getName()/setName()
    public String getName() {
        return getItemName();
    }

    public void setName(String name) {
        this.itemName.set(name);
    }

    public String getQuantity() {
        return quantity.get();
    }

    public StringProperty quantityProperty() {
        return quantity;
    }

    public void setQuantity(String q) {
        this.quantity.set(q);
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

    // Compatibility for DAO expecting 'bought' boolean accessor
    public boolean isBought() {
        return isPurchased();
    }

    public void setBought(boolean b) {
        setPurchased(b);
    }

    // Compatibility: allow DAO to set id via setId(int)
    public void setId(int newId) {
        this.id.set(newId);
    }

    // Compatibility: note field accessors (DAO may query getNote)
    public String getNote() {
        return null; // ShoppingItem does not persist note currently
    }

    public void setNote(String note) {
        // no-op placeholder to satisfy reflection-based DAO
    }
}