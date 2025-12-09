# Flat Manager - Shared Living Space Management Application

A desktop application for managing shared living spaces, built with JavaFX, Maven, and SQLite.

## Features

- **User Authentication**: Secure login system with user management
- **Cleaning Schedules**: Create, assign, and track cleaning tasks with due dates
- **Shopping Lists**: Manage shared shopping lists with quantities and tracking
- **Household Budget**: Track expenses, payments, and categorize transactions

## Technology Stack

- **Java 17**: Programming language
- **JavaFX 21**: UI framework
- **Maven**: Build and dependency management
- **SQLite**: Embedded database

## Prerequisites

- Java 17 or higher
- Maven 3.6 or higher

## Building the Application

```bash
# Clone the repository
git clone https://github.com/lisjuscha/softwareengineering_gruppe3.git
cd softwareengineering_gruppe3

# Compile the project
mvn clean compile

# Package the application
mvn clean package
```

## Running the Application

```bash
# Run using Maven
mvn javafx:run
```

## Default Credentials

- **Username**: admin
- **Password**: admin

## Project Structure

```
softwareengineering_gruppe3/
├── src/
│   ├── main/
│   │   ├── java/
│   │   │   ├── com/flatmanager/
│   │   │   │   ├── App.java                 # Main application entry point
│   │   │   │   ├── database/
│   │   │   │   │   └── DatabaseManager.java  # Database connection and initialization
│   │   │   │   ├── model/                    # Data models
│   │   │   │   │   ├── User.java
│   │   │   │   │   ├── CleaningTask.java
│   │   │   │   │   ├── ShoppingItem.java
│   │   │   │   │   └── BudgetTransaction.java
│   │   │   │   └── ui/                       # User interface components
│   │   │   │       ├── LoginScreen.java
│   │   │   │       ├── DashboardScreen.java
│   │   │   │       ├── CleaningScheduleView.java
│   │   │   │       ├── ShoppingListView.java
│   │   │   │       └── BudgetView.java
│   │   │   └── module-info.java
│   │   └── resources/
│   │       └── styles.css                    # Application styling
│   └── test/
│       └── java/
├── pom.xml                                    # Maven configuration
└── README.md
```

## Database

The application uses SQLite as its database. The database file (`flatmanager.db`) is automatically created on first run with the following tables:

- **users**: User authentication and profiles
- **cleaning_schedules**: Cleaning task assignments and tracking
- **shopping_items**: Shopping list items with purchase status
- **budget_transactions**: Expense tracking and categorization

## Usage

1. **Login**: Start the application and log in with the default credentials
2. **Dashboard**: After login, you'll see the main dashboard with navigation options
3. **Cleaning Schedules**: 
   - Add new cleaning tasks with assignee and due date
   - Mark tasks as complete
   - Delete tasks
4. **Shopping Lists**:
   - Add items with quantities
   - Mark items as purchased
   - Remove items from the list
5. **Household Budget**:
   - Add transactions with description, amount, and category
   - View total expenses
   - Delete transactions

## License

This project is for educational purposes.