# WG-Verwaltung - Flat Manager

Eine Desktop-Anwendung zur Verwaltung von Wohngemeinschaften, implementiert mit JavaFX, Maven und SQLite.

## Features

- **Benutzer\-Authentifizierung**: Sicheres Login mit Benutzerverwaltung
- **Reinigungspläne**: Erstellen, zuweisen und Nachverfolgen von Reinigungsaufgaben mit Fälligkeitsdaten
- **Einkaufslisten**: Gemeinsame Einkaufslisten verwalten mit Mengenangaben und Status
- **Haushaltsbudget**: Ausgaben, Zahlungen und Kategorien verfolgen

## Technology Stack

- **Java 17**: Programmiersprache
- **JavaFX 21**: UI\-Framework
- **Maven**: Build- und Abhängigkeitsverwaltung
- **SQLite**: Eingebettete Datenbank

## Voraussetzungen

- Java 17 oder höher
- Maven 3.6 oder höher

## Building the Application

```bash
# Repository klonen
git clone https://github.com/lisjuscha/softwareengineering_gruppe3.git
cd softwareengineering_gruppe3

# Projekt kompilieren
mvn clean compile

# Anwendung paketieren
mvn clean package
```

## Running the Application

```bash
# Mit Maven starten
mvn javafx:run
```

## Default Credentials

- **Username**: admin
- **Password**: admin

## Project Structure

```
softwareengineering_gruppe3/
├── `src/`
│   ├── `main/`
│   │   ├── `java/`
│   │   │   └── `com/`
│   │   │       └── `flatmanager/`
│   │   │           ├── `App.java`
│   │   │           ├── `module-info.java`
│   │   │           ├── `database/`
│   │   │           │   └── `DatabaseManager.java`
│   │   │           ├── `dao/`
│   │   │           │   ├── `UserDao.java`
│   │   │           │   ├── `CleaningTaskDao.java`
│   │   │           │   ├── `ShoppingItemDao.java`
│   │   │           │   └── `BudgetTransactionDao.java`
│   │   │           ├── `model/`
│   │   │           │   ├── `User.java`
│   │   │           │   ├── `CleaningTask.java`
│   │   │           │   ├── `ShoppingItem.java`
│   │   │           │   └── `BudgetTransaction.java`
│   │   │           ├── `storage/`
│   │   │           │   └── `Database.java`
│   │   │           └── `ui/`
│   │   │               ├── `LoginScreen.java`
│   │   │               ├── `DashboardScreen.java`
│   │   │               ├── `CleaningScheduleView.java`
│   │   │               ├── `ShoppingListView.java`
│   │   │               ├── `BudgetView.java`
│   │   │               ├── `AdminCreateUserDialog.java`
│   │   │               ├── `AdminDeleteUserDialog.java`
│   │   │               └── `AdminToolbar.java`
│   │   └── `resources/`
│   │       ├── `styles.css`
│   │       └── `icons/`
│   └── `test/`
│       └── `java/`  (Unit- und Integrationstests)
└── `README.md`
```

## Database

Die Anwendung verwendet SQLite. Die Datenbankdatei (flatmanager.db) wird beim ersten Start automatisch angelegt. Erwartete Tabellen:

- **users**:  Benutzer-Authentifizierung und Profile
- **cleaning_schedules**: Reinigungsaufgaben und Zuordnungen
- **shopping_items**: Einkaufslisten-Einträge mit Kaufstatus
- **budget_transactions**: Ausgaben und Kategorisierung

## Usage

1. Login: Anwendung starten und mit den Standardzugangsdaten einloggen
2. Dashboard: Navigation zu den Hauptbereichen nach Login
3. Reinigungspläne:

- Neue Aufgaben hinzufügen mit Zuweisung und Fälligkeitsdatum
- Aufgaben als erledigt markieren
- Aufgaben löschen

4. Einkaufslisten:
- Artikel mit Menge hinzufügen
- Artikel als gekauft markieren
- Artikel entfernen

5. Haushaltsbudget:
- Transaktionen hinzufügen (Beschreibung, Betrag, Kategorie)
- Gesamtausgaben anzeigen
- Transaktionen löschen

<!-- Coverage-Badge -->
![Coverage](https://img.shields.io/badge/coverage-53%25-yellowgreen)

## Testabdeckung

| Bereich | Coverage |
|---|---:|
| Gesamte Codebasis | 53\% |
| `com.flatmanager.ui` | 45\% |
| `com.flatmanager.database` | 72\% |
| `com.flatmanager.dao` | 71\% |
| `com.flatmanager.model` | 90\% |
| `com.flatmanager.storage` | 91\% |

### JaCoCo-Report anzeigen

1. Tests ausführen und HTML-Report erzeugen:
   - `mvn test jacoco:report`

2. HTML-Report im Browser öffnen (macOS):
   - `open target/site/jacoco/index.html`
   - alternativ plattformunabhängig: Datei im Browser öffnen oder `xdg-open target/site/jacoco/index.html` (Linux)

3. Direkt in der IDE:
   - In IntelliJ: Tests mit „Run with Coverage“ ausführen und das Coverage-Fenster nutzen.

## License

Dieses Projekt dient zu Lehrzwecken.