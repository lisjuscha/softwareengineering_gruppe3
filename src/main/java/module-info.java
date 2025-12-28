module com.flatmanager {
    requires javafx.controls;
    requires javafx.fxml;
    requires java.sql;
    requires javafx.graphics;

    exports com.flatmanager;
    exports com.flatmanager.ui;
    exports com.flatmanager.model;
    exports com.flatmanager.database;
    exports com.flatmanager.storage;
}
