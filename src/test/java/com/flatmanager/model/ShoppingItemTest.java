package com.flatmanager.model;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ShoppingItemTest {

    @Test
    void testFullConstructorAndGetters() {
        ShoppingItem it = new ShoppingItem(42, "Milk", "2", "alice", "Dairy", "bob", true);
        assertEquals(42, it.getId());
        assertEquals("Milk", it.getItemName());
        assertEquals("Milk", it.getName());
        assertEquals("2", it.getQuantity());
        assertEquals("alice", it.getAddedBy());
        assertEquals("Dairy", it.getCategory());
        assertEquals("bob", it.getPurchasedFor());
        assertTrue(it.isPurchased());
        assertTrue(it.isBought());
        assertTrue(it.selectedProperty() != null);
    }

    @Test
    void testNoArgConstructorDefaultsAndSetters() {
        ShoppingItem it = new ShoppingItem();
        // defaults
        assertEquals(0, it.getId());
        assertEquals("", it.getItemName());
        assertEquals("1", it.getQuantity());
        assertEquals("", it.getAddedBy());
        assertEquals("", it.getCategory());
        assertEquals("", it.getPurchasedFor());
        assertFalse(it.isPurchased());

        // setters / aliases
        it.setName("Eggs");
        assertEquals("Eggs", it.getItemName());
        it.setQuantity("12");
        assertEquals("12", it.getQuantity());
        it.setPurchasedFor("charlie");
        assertEquals("charlie", it.getPurchasedFor());

        it.setBought(true);
        assertTrue(it.isBought());
        it.setPurchased(false);
        assertFalse(it.isPurchased());

        it.setId(99);
        assertEquals(99, it.getId());

        // note getters setNote are no-op but must not throw
        it.setNote("some note");
        assertNull(it.getNote());
    }

    @Test
    void testPropertiesAreLive() {
        ShoppingItem it = new ShoppingItem(1, "A", "1", "u", "c", null, false);
        // change via property objects
        it.itemNameProperty().set("B");
        assertEquals("B", it.getItemName());
        it.quantityProperty().set("3");
        assertEquals("3", it.getQuantity());
    }
}

