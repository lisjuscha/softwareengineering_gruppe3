package com.flatmanager.ui;

import com.flatmanager.dao.CleaningTaskDao;
import com.flatmanager.model.CleaningTask;
import com.flatmanager.storage.Database;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.*;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.Period;
import java.util.Optional;

public class CleaningScheduleView {

    private BorderPane view;
    private String currentUser;

    private final CleaningTaskDao dao = new CleaningTaskDao();

    private ObservableList<CleaningTask> assignedTasks = FXCollections.observableArrayList();
    private ObservableList<CleaningTask> openTasks = FXCollections.observableArrayList();

    // Benutzerliste jetzt dynamisch aus der Datenbank geladen
    private ObservableList<String> users = FXCollections.observableArrayList();
    private ObservableList<String> recurrenceOptions = FXCollections.observableArrayList(
            "Einmalig", "Täglich", "Wöchentlich", "Monatlich", "Quartal", "Jährlich"
    );

    private VBox assignedContainer;
    private VBox openContainer;

    private Label errorLabel;

    public CleaningScheduleView(String username) {
        this.currentUser = username;

        // UI zuerst aufbauen, damit die Anwendung nicht komplett abstürzt
        createView();

        // Database initialisieren (erst sicherstellen, dann DAO init)
        try {
            Database.init();
        } catch (Exception ex) {
            showError("Fehler beim Initialisieren der Datenbank: " + ex.getMessage());
        }

        try {
            dao.init();
        } catch (Exception ex) {
            showError("Fehler beim Initialisieren der DAO: " + ex.getMessage());
        }

        try {
            loadUsersFromDb();
        } catch (Exception ex) {
            showError("Fehler beim Laden der Benutzer: " + ex.getMessage());
        }

        try {
            loadDataFromDb();
        } catch (Exception ex) {
            showError("Fehler beim Laden der Aufgaben: " + ex.getMessage());
        }

        refreshLists();
    }

    private void createView() {
        view = new BorderPane();
        view.getStyleClass().add("cleaning-view");

        // Top-Bar: Header links und Spacer (Admin-Button zentral in DashboardScreen)
        Label title = new Label("Putzplan");
        title.setFont(Font.font("Arial", FontWeight.BOLD, 22));
        title.getStyleClass().add("cleaning-title");
        title.setPadding(new Insets(10, 0, 10, 0));

        HBox topBar = new HBox();
        topBar.setAlignment(Pos.CENTER_LEFT);
        topBar.setPadding(new Insets(10, 12, 10, 12));

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        // Admin-Node entfernt, da zentral in DashboardScreen eingefügt
        topBar.getChildren().addAll(title, spacer);

        // Fehlerlabel (versteckt, wird bei Problemen sichtbar)
        errorLabel = new Label();
        errorLabel.setVisible(false);
        errorLabel.setManaged(false);
        errorLabel.setStyle("-fx-text-fill: white; -fx-background-color: #d32f2f; -fx-padding: 6; -fx-background-radius: 4;");
        HBox errorBox = new HBox(errorLabel);
        errorBox.setAlignment(Pos.CENTER);
        errorBox.setPadding(new Insets(6, 0, 6, 0));

        VBox top = new VBox(topBar, errorBox);
        view.setTop(top);

        // Two columns: assigned + open
        assignedContainer = new VBox(8);
        assignedContainer.setPadding(new Insets(10));
        assignedContainer.setFillWidth(true);

        openContainer = new VBox(8);
        openContainer.setPadding(new Insets(10));
        openContainer.setFillWidth(true);

        // Headers
        Label assignedHeader = new Label("Zugewiesene Aufgaben");
        assignedHeader.setFont(Font.font("Arial", FontWeight.BOLD, 16));
        Label openHeader = new Label("Offene Aufgaben");
        openHeader.setFont(Font.font("Arial", FontWeight.BOLD, 16));

        // Scroll panes
        ScrollPane assignedScroll = new ScrollPane(assignedContainer);
        assignedScroll.setFitToWidth(true);
        assignedScroll.setPrefHeight(500);
        assignedScroll.getStyleClass().add("task-scroll");

        ScrollPane openScroll = new ScrollPane(openContainer);
        openScroll.setFitToWidth(true);
        openScroll.setPrefHeight(500);
        openScroll.getStyleClass().add("task-scroll");

        VBox leftBox = new VBox(6, assignedHeader, assignedScroll);
        VBox rightBox = new VBox(6, openHeader, openScroll);

        leftBox.setVgrow(assignedScroll, Priority.ALWAYS);
        rightBox.setVgrow(openScroll, Priority.ALWAYS);

        // Buttons under open list
        Button newTaskBtn = new Button("Neue Aufgabe");
        newTaskBtn.setMaxWidth(Double.MAX_VALUE);
        newTaskBtn.setOnAction(e -> showNewTaskDialog());

        Button deleteCompletedBtn = new Button("Erledigte Aufgaben löschen");
        deleteCompletedBtn.setMaxWidth(Double.MAX_VALUE);
        deleteCompletedBtn.setStyle("-fx-background-color: #f0625e; -fx-text-fill: white;");
        deleteCompletedBtn.setOnAction(e -> deleteCompletedTasks());

        VBox rightControls = new VBox(8, newTaskBtn, deleteCompletedBtn);
        rightControls.setPadding(new Insets(8));
        rightControls.setAlignment(Pos.CENTER);
        rightControls.setFillWidth(true);

        VBox rightColumn = new VBox(10, rightBox, rightControls);
        rightColumn.setAlignment(Pos.TOP_CENTER);
        rightColumn.setFillWidth(true);

        // Layout spacing
        HBox center = new HBox(20, leftBox, rightColumn);
        center.setPadding(new Insets(10));
        HBox.setHgrow(leftBox, Priority.ALWAYS);
        HBox.setHgrow(rightColumn, Priority.ALWAYS);

        view.setCenter(center);
    }

    private void loadDataFromDb() {
        assignedTasks.clear();
        openTasks.clear();
        try {
            for (CleaningTask t : dao.listAll()) {
                if (t.hasAssignee()) assignedTasks.add(t);
                else openTasks.add(t);
            }
            clearError();
        } catch (Exception ex) {
            // Fehler protokollieren, UI leer lassen, Fehler anzeigen
            showError("Kann Aufgaben nicht laden: " + ex.getMessage());
        }
    }

    private void refreshLists() {
        assignedContainer.getChildren().clear();
        openContainer.getChildren().clear();

        for (CleaningTask task : assignedTasks) {
            assignedContainer.getChildren().add(createTaskNode(task));
        }
        for (CleaningTask task : openTasks) {
            openContainer.getChildren().add(createTaskNode(task));
        }

        Region spacerAssigned = new Region();
        VBox.setVgrow(spacerAssigned, Priority.ALWAYS);
        assignedContainer.getChildren().add(spacerAssigned);

        Region spacerOpen = new Region();
        VBox.setVgrow(spacerOpen, Priority.ALWAYS);
        openContainer.getChildren().add(spacerOpen);
    }

    private Node createTaskNode(CleaningTask task) {
        CheckBox cb = new CheckBox();
        cb.setSelected(task.isCompleted());
        cb.setOnAction(e -> {
            task.setCompleted(cb.isSelected());
            try {
                dao.update(task);
            } catch (Exception ex) {
                showError("Fehler beim Aktualisieren: " + ex.getMessage());
            }
            refreshLists();
        });

        Label title = new Label(task.getTitle());
        title.setFont(Font.font("Arial", FontWeight.BOLD, 14));

        Label meta = new Label(getDueText(task));
        meta.setStyle("-fx-text-fill: #b00020;"); // rot für Fälligkeit
        if (!task.hasAssignee()) {
            meta.setStyle("-fx-text-fill: -fx-text-inner-color;");
        }

        String recText = task.getRecurrence() == null ? "Einmalig" : task.getRecurrence();
        Label recurring = new Label(recText);
        recurring.setStyle("-fx-text-fill: gray; -fx-font-size: 11;");

        Label urgentLbl = new Label(task.isUrgent() ? "DRINGEND" : "");
        urgentLbl.setStyle(task.isUrgent() ? "-fx-text-fill: #d32f2f; -fx-font-weight: bold; -fx-font-size: 11;" : "");

        // Titel oben, darunter Fälligkeits-Text, darunter Wiederholung (+ ggf. DRINGEND)
        VBox textBox = new VBox(2, title, meta, new HBox(8, recurring, urgentLbl));
        HBox.setHgrow(textBox, Priority.ALWAYS);
        textBox.setAlignment(Pos.CENTER_LEFT);

        // Auswahlbox für Zuweisung (statt Label) \- admin wird nicht angeboten
        ObservableList<String> assigneeChoices = FXCollections.observableArrayList();
        assigneeChoices.add("Nicht zugewiesen");
        for (String u : users) {
            if (u == null) continue;
            if ("admin".equalsIgnoreCase(u.trim())) continue;
            assigneeChoices.add(u);
        }
        ComboBox<String> assigneeCombo = new ComboBox<>(assigneeChoices);
        assigneeCombo.setEditable(false);
        assigneeCombo.setMinWidth(120);
        assigneeCombo.setValue(task.hasAssignee() ? task.getAssignedTo() : "Nicht zugewiesen");

        assigneeCombo.setOnAction(ev -> {
            String sel = assigneeCombo.getValue();
            String newAssignee = ("Nicht zugewiesen".equals(sel) ? null : sel);
            String old = task.getAssignedTo();
            boolean equal;
            if (old == null) equal = newAssignee == null;
            else equal = old.equals(newAssignee);
            if (equal) return; // keine Änderung
            task.setAssignedTo(newAssignee);
            try {
                dao.update(task);
                clearError();
            } catch (Exception ex) {
                showError("Fehler beim Aktualisieren der Zuweisung: " + ex.getMessage());
            }
            // Listen aktualisieren: verschiebe zwischen assigned/open
            if (task.hasAssignee()) {
                if (!assignedTasks.contains(task)) assignedTasks.add(task);
                openTasks.remove(task);
            } else {
                if (!openTasks.contains(task)) openTasks.add(task);
                assignedTasks.remove(task);
            }
            refreshLists();
        });

        HBox root = new HBox(10, cb, textBox, assigneeCombo);
        root.setPadding(new Insets(8));
        root.setAlignment(Pos.CENTER_LEFT);
        root.setStyle("-fx-background-color: #f6f6f6; -fx-border-color: #dcdcdc; -fx-border-radius: 4; -fx-background-radius: 4;");
        HBox.setHgrow(textBox, Priority.ALWAYS);

        if (task.isCompleted()) {
            root.setOpacity(0.6);
            title.setStyle("-fx-strikethrough: true;");
        } else {
            title.setStyle("");
            root.setOpacity(1.0);
        }

        return root;
    }

    private String getDueText(CleaningTask task) {
        if (task.getDue() == null) return "";
        LocalDate today = LocalDate.now();
        Period p = Period.between(today, task.getDue());
        int days = p.getDays() + p.getMonths() * 30 + p.getYears() * 365; // simpel
        if (days == 0) return "heute";
        if (days > 0) return String.format("in %d Tage", days);
        return String.format("%d Tage", days); // z.B. -43 Tage
    }

    private void showNewTaskDialog() {
        Dialog<CleaningTask> dialog = new Dialog<>();
        dialog.setTitle("Neue Aufgabe");
        dialog.setHeaderText(null);

        ButtonType createType = new ButtonType("Erstellen", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(createType, ButtonType.CANCEL);

        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(10);
        grid.setPadding(new Insets(20, 150, 10, 10));

        TextField titleField = new TextField();
        titleField.setPromptText("Aufgabenname");

        DatePicker duePicker = new DatePicker(LocalDate.now().plusDays(1));

        ObservableList<String> assigneeChoices = FXCollections.observableArrayList();
        assigneeChoices.add("Nicht zugewiesen");
        for (String u : users) {
            if (u == null) continue;
            if ("admin".equalsIgnoreCase(u.trim())) continue;
            assigneeChoices.add(u);
        }
        ComboBox<String> assigneeCombo = new ComboBox<>(assigneeChoices);
        assigneeCombo.setEditable(false);
        assigneeCombo.setValue("Nicht zugewiesen"); // default
        assigneeCombo.setPromptText("Zugewiesen an (optional)");

        ComboBox<String> recurrenceCombo = new ComboBox<>(recurrenceOptions);
        recurrenceCombo.setEditable(false);
        recurrenceCombo.setValue("Einmalig");

        CheckBox urgentCheck = new CheckBox("Dringend");

        grid.add(new Label("Titel:"), 0, 0);
        grid.add(titleField, 1, 0);
        grid.add(new Label("Fällig:"), 0, 1);
        grid.add(duePicker, 1, 1);
        grid.add(new Label("Zugewiesen an:"), 0, 2);
        grid.add(assigneeCombo, 1, 2);
        grid.add(new Label("Regelmäßig:"), 0, 3);
        grid.add(recurrenceCombo, 1, 3);
        grid.add(new Label("Wichtig:"), 0, 4);
        grid.add(urgentCheck, 1, 4);

        Node createButton = dialog.getDialogPane().lookupButton(createType);
        createButton.setDisable(true);

        titleField.textProperty().addListener((obs, old, nw) -> createButton.setDisable(nw.trim().isEmpty()));
        dialog.getDialogPane().setContent(grid);

        // Enter sollte den Dialog nicht doppelt persistieren - keine Insert-Logik hier
        titleField.setOnKeyPressed(ev -> {
            if (ev.getCode() == KeyCode.ENTER && !createButton.isDisable()) {
                dialog.setResult(new CleaningTask(titleField.getText().trim(),
                        duePicker.getValue(),
                        ("Nicht zugewiesen".equals(assigneeCombo.getValue()) ? null : assigneeCombo.getValue()),
                        recurrenceCombo.getValue(),
                        urgentCheck.isSelected()));
                dialog.close();
            }
        });

        dialog.setResultConverter(dialogButton -> {
            if (dialogButton == createType) {
                String assigneeVal = assigneeCombo.getValue();
                if (assigneeVal == null || assigneeVal.trim().isEmpty() || "Nicht zugewiesen".equals(assigneeVal))
                    assigneeVal = null;
                return new CleaningTask(titleField.getText().trim(), duePicker.getValue(),
                        assigneeVal, recurrenceCombo.getValue(), urgentCheck.isSelected());
            }
            return null;
        });

        Optional<CleaningTask> result = dialog.showAndWait();
        result.ifPresent(task -> {
            // nur hier persistieren und UI aktualisieren
            try {
                dao.insert(task); // setzt task.id
                if (task.hasAssignee()) assignedTasks.add(task);
                else openTasks.add(task);
                refreshLists();
                clearError();
            } catch (Exception ex) {
                showError("Fehler beim Anlegen der Aufgabe: " + ex.getMessage());
            }
        });
    }

    private void deleteCompletedTasks() {
        try {
            dao.deleteCompleted();
            clearError();
        } catch (Exception ex) {
            showError("Fehler beim Löschen erledigter Aufgaben: " + ex.getMessage());
        }
        assignedTasks.removeIf(CleaningTask::isCompleted);
        openTasks.removeIf(CleaningTask::isCompleted);
        refreshLists();
    }

    public Node getView() {
        return view;
    }

    // Lädt Benutzernamen aus der DB und füllt die ObservableList users
    private void loadUsersFromDb() {
        users.clear();
        String sql = "SELECT username FROM users ORDER BY LOWER(username)";
        try (Connection conn = Database.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                String u = rs.getString("username");
                if (u != null && !u.trim().isEmpty()) {
                    if ("admin".equalsIgnoreCase(u.trim())) continue; // admin nicht in Auswahl
                    users.add(u.trim());
                }
            }
            clearError();
        } catch (SQLException ex) {
            // falls DB nicht erreichbar ist, bleibt users leer; Fehler kurz melden
            showError("Fehler beim Laden der Benutzer: " + ex.getMessage());
        }
    }

    // UI-Hilfsmethoden für Fehleranzeige
    private void showError(String msg) {
        Platform.runLater(() -> {
            errorLabel.setText(msg);
            errorLabel.setVisible(true);
            errorLabel.setManaged(true);
        });
    }

    private void clearError() {
        Platform.runLater(() -> {
            errorLabel.setText("");
            errorLabel.setVisible(false);
            errorLabel.setManaged(false);
        });
    }
}