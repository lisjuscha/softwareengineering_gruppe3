package com.flatmanager.ui;

import javafx.scene.Parent;

/**
 * Einfaches Interface für UI-Screens, die eine JavaFX-View liefern.
 * Implementierungen geben die Root-Node der jeweiligen Seite zurück.
 */
public interface Screen {
    Parent getView();
}
