package com.flatmanager.ui;

import javafx.stage.Window;

/**
 * Test-Stub, überschreibt die Main-Klasse während Tests (test classpath hat Vorrang).
 * Protokolliert Aufrufe an showAndWait, damit Event-Handler getestet werden können ohne UI.
 */
public final class AdminUserManagementView {
    public static volatile int callCount = 0;
    public static volatile String lastUser = null;
    public static volatile boolean throwOnCall = false;

    public static void reset() {
        callCount = 0;
        lastUser = null;
        throwOnCall = false;
    }

    public static void showAndWait(Window owner, String currentUser) {
        callCount++;
        lastUser = currentUser;
        if (throwOnCall) {
            throw new RuntimeException("test-exception");
        }
        // do not open UI during tests
    }
}
