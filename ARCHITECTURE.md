# Flat Manager - Architecture Documentation

## Overview
Flat Manager is a JavaFX desktop application for managing shared living spaces. It provides features for cleaning schedules, shopping lists, and household budget tracking.

## Architecture

### Layer Structure

```
┌─────────────────────────────────────────────┐
│           User Interface Layer              │
│  (LoginScreen, DashboardScreen, Views)      │
└─────────────────────────────────────────────┘
                     ↓
┌─────────────────────────────────────────────┐
│          Application Layer                   │
│               (App.java)                     │
└─────────────────────────────────────────────┘
                     ↓
┌─────────────────────────────────────────────┐
│           Model Layer                        │
│  (User, CleaningTask, ShoppingItem, etc.)   │
└─────────────────────────────────────────────┘
                     ↓
┌─────────────────────────────────────────────┐
│         Database Layer                       │
│        (DatabaseManager)                     │
└─────────────────────────────────────────────┘
                     ↓
┌─────────────────────────────────────────────┐
│          SQLite Database                     │
│        (flatmanager.db)                      │
└─────────────────────────────────────────────┘
```

## User Interface Structure

### Login Screen
```
┌────────────────────────────────────────────┐
│                                            │
│           Flat Manager                     │
│    Shared Living Space Management          │
│                                            │
│         ┌──────────────────┐               │
│         │ Username:        │               │
│         │ [____________]   │               │
│         │                  │               │
│         │ Password:        │               │
│         │ [____________]   │               │
│         │                  │               │
│         │   [  Login  ]    │               │
│         │                  │               │
│         │ Default: admin/admin             │
│         └──────────────────┘               │
│                                            │
└────────────────────────────────────────────┘
```

### Dashboard
```
┌────────────────────────────────────────────────────────┐
│  Flat Manager - Welcome, admin              [Logout]   │
├──────────────┬─────────────────────────────────────────┤
│              │                                         │
│ Navigation   │         Content Area                    │
│              │                                         │
│ ─────────    │  Welcome to Flat Manager!               │
│              │                                         │
│ [Cleaning    │  Use the navigation menu:               │
│  Schedules]  │  • Manage Cleaning Schedules            │
│              │  • Create Shopping Lists                │
│ [Shopping    │  • Track Household Budget               │
│  Lists]      │                                         │
│              │                                         │
│ [Household   │                                         │
│  Budget]     │                                         │
│              │                                         │
└──────────────┴─────────────────────────────────────────┘
```

### Cleaning Schedules View
```
┌──────────────────────────────────────────────────────────────┐
│  Cleaning Schedules                                          │
│                                                              │
│  [Task____] [Assignee___] [Date____] [Add Task]             │
│                                                              │
│  ┌────────────────────────────────────────────────────────┐ │
│  │ Task       │ Assigned To │ Due Date │ Comp. │ Actions │ │
│  ├────────────────────────────────────────────────────────┤ │
│  │ Kitchen    │ John        │ 2025-12  │ ☐     │[✓][✕]   │ │
│  │ Bathroom   │ Sarah       │ 2025-12  │ ☑     │[✓][✕]   │ │
│  └────────────────────────────────────────────────────────┘ │
└──────────────────────────────────────────────────────────────┘
```

### Shopping Lists View
```
┌──────────────────────────────────────────────────────────────┐
│  Shopping Lists                                              │
│                                                              │
│  [Item Name___] [Quantity___] [Add Item]                    │
│                                                              │
│  ┌────────────────────────────────────────────────────────┐ │
│  │ Item      │ Quantity │ Added By │ Purchased │ Actions │ │
│  ├────────────────────────────────────────────────────────┤ │
│  │ Milk      │ 2L       │ John     │ ☐         │[✓][✕]   │ │
│  │ Bread     │ 1        │ Sarah    │ ☑         │[✓][✕]   │ │
│  └────────────────────────────────────────────────────────┘ │
└──────────────────────────────────────────────────────────────┘
```

### Household Budget View
```
┌──────────────────────────────────────────────────────────────┐
│  Household Budget                                            │
│                                                              │
│  [Description_] [Amount_] [Category_] [Date_] [Add Trans.]  │
│                                                              │
│  Total: €523.45                                              │
│                                                              │
│  ┌────────────────────────────────────────────────────────┐ │
│  │ Description │ Amount  │ Category  │ Paid By │ Actions │ │
│  ├────────────────────────────────────────────────────────┤ │
│  │ Groceries   │ €125.50 │ Food      │ John    │  [✕]    │ │
│  │ Electricity │ €89.99  │ Utilities │ Sarah   │  [✕]    │ │
│  └────────────────────────────────────────────────────────┘ │
└──────────────────────────────────────────────────────────────┘
```

## Database Schema

### Tables

#### users
- `id` (INTEGER PRIMARY KEY) - User ID
- `username` (TEXT UNIQUE) - Login username
- `password` (TEXT) - Password (plain text for educational purposes)
- `name` (TEXT) - Full name

#### cleaning_schedules
- `id` (INTEGER PRIMARY KEY) - Task ID
- `task` (TEXT) - Task description
- `assigned_to` (TEXT) - Person assigned to the task
- `due_date` (TEXT) - Due date (ISO format)
- `completed` (INTEGER) - Completion status (0/1)

#### shopping_items
- `id` (INTEGER PRIMARY KEY) - Item ID
- `item_name` (TEXT) - Item name
- `quantity` (TEXT) - Quantity description
- `added_by` (TEXT) - User who added the item
- `purchased` (INTEGER) - Purchase status (0/1)

#### budget_transactions
- `id` (INTEGER PRIMARY KEY) - Transaction ID
- `description` (TEXT) - Transaction description
- `amount` (REAL) - Transaction amount
- `paid_by` (TEXT) - User who paid
- `date` (TEXT) - Transaction date (ISO format)
- `category` (TEXT) - Transaction category

## Key Features

### 1. Authentication
- Login screen with username/password
- Default admin user (admin/admin)
- Session management with current user tracking

### 2. Cleaning Schedules
- Add new cleaning tasks with assignee and due date
- Mark tasks as complete
- Delete tasks
- View all tasks sorted by due date

### 3. Shopping Lists
- Add items with quantities
- Mark items as purchased
- Remove items
- Track who added each item

### 4. Household Budget
- Add transactions with description, amount, category
- Calculate total expenses
- View transaction history
- Delete transactions
- Category-based organization (Groceries, Utilities, Cleaning, Other)

## Technology Stack

- **Java 17**: Core programming language
- **JavaFX 21**: UI framework
- **SQLite 3.44.1**: Database
- **Maven**: Build and dependency management
- **JUnit 5**: Testing framework

## Design Patterns

### Separation of Concerns
- **UI Layer**: Handles user interaction and display
- **Model Layer**: Represents data entities
- **Database Layer**: Manages data persistence

### Resource Management
- Try-with-resources for automatic database connection cleanup
- Proper exception handling

### Component Reusability
- Modular view components
- Shared styling via CSS

## Security Considerations

⚠️ **Educational Limitations**:
- Passwords stored in plain text (should use bcrypt/PBKDF2 in production)
- Default admin credentials (should require password change)
- Single static database connection (should use connection pooling)

## Future Enhancements

Potential improvements for production use:
1. Implement password hashing (bcrypt, PBKDF2)
2. Add user registration and management
3. Implement multi-user authentication
4. Add data validation and error handling
5. Implement connection pooling
6. Add data export/import features
7. Implement recurring tasks for cleaning schedules
8. Add expense splitting calculations
9. Implement notification system for due tasks
10. Add mobile/web companion application
