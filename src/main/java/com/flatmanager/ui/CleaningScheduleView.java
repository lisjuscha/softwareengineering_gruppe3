package com.flatmanager.ui;

import com.flatmanager.database.DatabaseManager;
import com.flatmanager.model.CleaningTask;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.layout.*;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;

import java.sql.*;
import java.time.LocalDate;

public class CleaningScheduleView {
    private VBox view;
    private String currentUser;
    private TableView<CleaningTask> tableView;
    private ObservableList<CleaningTask> tasks;

    public CleaningScheduleView(String username) {
        this.currentUser = username;
        this.tasks = FXCollections.observableArrayList();
        createView();
        loadTasks();
    }

    private void createView() {
        view = new VBox(15);
        view.setPadding(new Insets(20));
        view.setMaxWidth(900);

        Label titleLabel = new Label("Cleaning Schedules");
        titleLabel.setFont(Font.font("Arial", FontWeight.BOLD, 24));
        titleLabel.getStyleClass().add("section-title");

        // Add task form
        HBox formBox = new HBox(10);
        formBox.setAlignment(javafx.geometry.Pos.CENTER_LEFT);

        TextField taskField = new TextField();
        taskField.setPromptText("Task name");
        taskField.setPrefWidth(200);

        TextField assigneeField = new TextField();
        assigneeField.setPromptText("Assigned to");
        assigneeField.setPrefWidth(150);

        DatePicker datePicker = new DatePicker();
        datePicker.setPromptText("Due date");
        datePicker.setPrefWidth(150);

        Button addButton = new Button("Add Task");
        addButton.getStyleClass().add("primary-button");
        addButton.setOnAction(e -> {
            String task = taskField.getText().trim();
            String assignee = assigneeField.getText().trim();
            LocalDate date = datePicker.getValue();

            if (!task.isEmpty() && !assignee.isEmpty() && date != null) {
                addTask(task, assignee, date.toString());
                taskField.clear();
                assigneeField.clear();
                datePicker.setValue(null);
                loadTasks();
            } else {
                showAlert("Please fill in all fields");
            }
        });

        formBox.getChildren().addAll(taskField, assigneeField, datePicker, addButton);

        // Table view
        tableView = new TableView<>();
        tableView.setItems(tasks);

        TableColumn<CleaningTask, String> taskCol = new TableColumn<>("Task");
        taskCol.setCellValueFactory(new PropertyValueFactory<>("task"));
        taskCol.setPrefWidth(250);

        TableColumn<CleaningTask, String> assigneeCol = new TableColumn<>("Assigned To");
        assigneeCol.setCellValueFactory(new PropertyValueFactory<>("assignedTo"));
        assigneeCol.setPrefWidth(150);

        TableColumn<CleaningTask, String> dateCol = new TableColumn<>("Due Date");
        dateCol.setCellValueFactory(new PropertyValueFactory<>("dueDate"));
        dateCol.setPrefWidth(120);

        TableColumn<CleaningTask, Boolean> completedCol = new TableColumn<>("Completed");
        completedCol.setCellValueFactory(new PropertyValueFactory<>("completed"));
        completedCol.setPrefWidth(100);

        TableColumn<CleaningTask, Void> actionCol = new TableColumn<>("Actions");
        actionCol.setPrefWidth(180);
        actionCol.setCellFactory(param -> new TableCell<>() {
            private final Button completeBtn = new Button("Complete");
            private final Button deleteBtn = new Button("Delete");

            {
                completeBtn.getStyleClass().add("complete-button");
                deleteBtn.getStyleClass().add("delete-button");

                completeBtn.setOnAction(e -> {
                    CleaningTask task = getTableView().getItems().get(getIndex());
                    markTaskComplete(task.getId());
                    loadTasks();
                });

                deleteBtn.setOnAction(e -> {
                    CleaningTask task = getTableView().getItems().get(getIndex());
                    deleteTask(task.getId());
                    loadTasks();
                });
            }

            @Override
            protected void updateItem(Void item, boolean empty) {
                super.updateItem(item, empty);
                if (empty) {
                    setGraphic(null);
                } else {
                    HBox buttons = new HBox(5, completeBtn, deleteBtn);
                    setGraphic(buttons);
                }
            }
        });

        tableView.getColumns().addAll(taskCol, assigneeCol, dateCol, completedCol, actionCol);

        view.getChildren().addAll(titleLabel, formBox, tableView);
    }

    private void loadTasks() {
        tasks.clear();
        try (Connection conn = DatabaseManager.getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery("SELECT * FROM cleaning_schedules ORDER BY due_date")) {

            while (rs.next()) {
                tasks.add(new CleaningTask(
                    rs.getInt("id"),
                    rs.getString("task"),
                    rs.getString("assigned_to"),
                    rs.getString("due_date"),
                    rs.getInt("completed") == 1
                ));
            }
        } catch (SQLException e) {
            e.printStackTrace();
            showAlert("Error loading tasks");
        }
    }

    private void addTask(String task, String assignee, String dueDate) {
        try {
            Connection conn = DatabaseManager.getConnection();
            String sql = "INSERT INTO cleaning_schedules (task, assigned_to, due_date) VALUES (?, ?, ?)";
            PreparedStatement pstmt = conn.prepareStatement(sql);
            pstmt.setString(1, task);
            pstmt.setString(2, assignee);
            pstmt.setString(3, dueDate);
            pstmt.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
            showAlert("Error adding task");
        }
    }

    private void markTaskComplete(int taskId) {
        try {
            Connection conn = DatabaseManager.getConnection();
            String sql = "UPDATE cleaning_schedules SET completed = 1 WHERE id = ?";
            PreparedStatement pstmt = conn.prepareStatement(sql);
            pstmt.setInt(1, taskId);
            pstmt.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
            showAlert("Error updating task");
        }
    }

    private void deleteTask(int taskId) {
        try {
            Connection conn = DatabaseManager.getConnection();
            String sql = "DELETE FROM cleaning_schedules WHERE id = ?";
            PreparedStatement pstmt = conn.prepareStatement(sql);
            pstmt.setInt(1, taskId);
            pstmt.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
            showAlert("Error deleting task");
        }
    }

    private void showAlert(String message) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setContentText(message);
        alert.showAndWait();
    }

    public VBox getView() {
        return view;
    }
}
