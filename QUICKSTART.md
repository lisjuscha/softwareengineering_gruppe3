# Quick Start Guide - Flat Manager

## Prerequisites
- Java 17 or higher installed
- Maven 3.6 or higher installed

## Installation & Running

### 1. Clone the Repository
```bash
git clone https://github.com/lisjuscha/softwareengineering_gruppe3.git
cd softwareengineering_gruppe3
```

### 2. Build the Application
```bash
mvn clean package
```

### 3. Run the Application
```bash
mvn javafx:run
```

## First Login

When the application starts, you'll see the login screen.

**Default Credentials:**
- Username: `admin`
- Password: `admin`

## Using the Application

### Main Dashboard
After logging in, you'll see the main dashboard with three navigation options:

1. **Cleaning Schedules** - Manage household cleaning tasks
2. **Shopping Lists** - Keep track of items to buy
3. **Household Budget** - Track shared expenses

### Cleaning Schedules
- **Add a Task**: Fill in task name, assignee, and due date, then click "Add Task"
- **Complete a Task**: Click the "Complete" button next to the task
- **Delete a Task**: Click the "Delete" button next to the task

### Shopping Lists
- **Add an Item**: Enter item name and quantity, then click "Add Item"
- **Mark as Purchased**: Click the "Purchase" button next to the item
- **Remove an Item**: Click the "Delete" button next to the item

### Household Budget
- **Add a Transaction**: Enter description, amount, category, and date, then click "Add Transaction"
- **View Total**: See the total expenses at the top of the list
- **Delete a Transaction**: Click the "Delete" button next to the transaction

## Tips

- All data is automatically saved to the SQLite database (`flatmanager.db`)
- The database is created automatically on first run
- You can have multiple people use the same database file
- The current user's name appears in the top bar
- Use the "Logout" button to return to the login screen

## Troubleshooting

### Application won't start
- Make sure Java 17 or higher is installed: `java -version`
- Make sure Maven is installed: `mvn -version`
- Try rebuilding: `mvn clean package`

### Database errors
- Delete the `flatmanager.db` file to reset the database
- The database will be recreated with default data on next run

### Build errors
- Make sure you have an internet connection for Maven to download dependencies
- Try clearing Maven cache: `mvn clean`

## Additional Resources

- See [README.md](README.md) for detailed feature descriptions
- See [ARCHITECTURE.md](ARCHITECTURE.md) for technical documentation
- Check the source code in `src/main/java/com/flatmanager/` for implementation details

## Support

This is an educational project. For issues or questions, please refer to the documentation or examine the source code.
