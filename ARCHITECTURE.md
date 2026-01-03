# WG-Verwaltung — Architekturdokumentation

## Übersicht
Flat Manager ist eine JavaFX-Desktopanwendung zur Verwaltung von Wohngemeinschaften. Features: Reinigungspläne, Einkaufslisten und Haushaltsbudget‑Verwaltung.

## Architektur

### Schichtenübersicht

```
┌─────────────────────────────────────────────┐
│          Benutzeroberfläche (UI)            │
│   (LoginScreen, DashboardScreen, Views)     │
└─────────────────────────────────────────────┘
                     ↓
┌─────────────────────────────────────────────┐
│            Anwendungsschicht                │
│                (App.java)                   │
└─────────────────────────────────────────────┘
                     ↓
┌─────────────────────────────────────────────┐
│             Modell-Schicht                  │
│   (User, CleaningTask, ShoppingItem, etc.)  │
└─────────────────────────────────────────────┘
                     ↓
┌─────────────────────────────────────────────┐
│            Datenbank-Schicht                │
│         (DatabaseManager, DAOs)             │
└─────────────────────────────────────────────┘
                     ↓
┌─────────────────────────────────────────────┐
│             SQLite Datenbank                │
│             (flatmanager.db)                │
└─────────────────────────────────────────────┘
```

Die Anwendung ist modular aufgebaut (siehe `module-info.class` in `target/classes`).

## Benutzeroberfläche (UI)

- Login Screen: Benutzername/Passwort, Standardzugang `admin` / `admin`.
- Dashboard: Navigation (Cleaning Schedules, Shopping Lists, Household Budget) und Content‑Area.
- Views:
  - Cleaning Schedules: Aufgaben anlegen, Zuweisung, Fälligkeitsdatum, als erledigt markieren, löschen.
  - Shopping Lists: Artikel anlegen, Menge, hinzugefügt von, als gekauft markieren, löschen.
  - Household Budget: Transaktionen anlegen, Summenberechnung, Kategorien, löschen.

UI‑Komponenten sind als wiederverwendbare Views/Controller implementiert; Styling über gemeinsame CSS‑Datei in `target/classes/styles.css` / `src/main/resources`.

## Datenbankschema

Tabellen (vereinfachte Darstellung):

- `users`
  - `id` INTEGER PRIMARY KEY
  - `username` TEXT UNIQUE
  - `password` TEXT (zurzeit plaintext, nur zu Lernzwecken)
  - `name` TEXT

- `cleaning_schedules`
  - `id` INTEGER PRIMARY KEY
  - `task` TEXT
  - `assigned_to` TEXT
  - `due_date` TEXT (ISO)
  - `completed` INTEGER (0/1)

- `shopping_items`
  - `id` INTEGER PRIMARY KEY
  - `item_name` TEXT
  - `quantity` TEXT
  - `added_by` TEXT
  - `purchased` INTEGER (0/1)

- `budget_transactions`
  - `id` INTEGER PRIMARY KEY
  - `description` TEXT
  - `amount` REAL
  - `paid_by` TEXT
  - `date` TEXT (ISO)
  - `category` TEXT

Die Datei `flatmanager.db` liegt im Projektstamm und wird beim ersten Start angelegt, falls nicht vorhanden.

## Wichtige Komponenten & Pfade

- Quellcode: `src/main/java`
- Tests: `src/test/java`
- Maven‑Build: `pom.xml`
- Lokale DB: `flatmanager.db`
- Test‑Reports: `target/surefire-reports`
- JaCoCo Coverage: `target/site/jacoco/index.html` (Erzeugung siehe unten)

## Tests & Coverage
- Tests mit Maven laufen: `mvn test`
- JaCoCo HTML‑Report erzeugen: `mvn test jacoco:report`
- Report öffnen (macOS): `open target/site/jacoco/index.html`
- Coverage‑Daten liegen als `jacoco.exec` in `target/`

## Designprinzipien

- Separation of Concerns: UI, Application, Model und Database klar getrennt.
- Wiederverwendbare Komponenten: modularisierte Views/Controller.
- Resource Management: Try‑with‑resources für DB‑Zugriffe.
- Fehlerbehandlung: zentrale Logging/Exception‑Behandlung in DatabaseManager/Services.

## Sicherheit (bildend / nicht produktiv)
- Aktuell: Passwörter im Klartext (nur zu Lehrzwecken).
- Default‑Admin: `admin` / `admin` — in produktiven Systemen unzulässig.
- Empfohlene Verbesserungen: Passwort‑Hashing, Validierung, Zugriffsrechte, sichere Konfiguration der DB‑Datei.

## Bekannte Limitierungen
- Single static DB‑Connection / kein Connection‑Pooling.
- Keine Produktions‑Härtung (Input‑Validierung, Sanitization).
- Keine verteilte/mehrbenutzerfähige Backend‑Architektur.

## Vorschläge für Weiterentwicklung
1. Passwort‑Hashing und obligatorischer Passwortwechsel beim ersten Login.  
2. Benutzerverwaltung mit Rollen/Permissions.  
3. Datenvalidierung und erweiterte Fehlerbehandlung.  
4. Connection‑Pooling oder verschachtelte Transaktionen verbessern.  
5. Datenexport/Import (CSV/JSON).  
6. Wiederkehrende Reinigungsaufgaben/Benachrichtigungen.  
7. CI‑Integration: Coverage automatisch erzeugen und Reports in ZIP aufnehmen.  
8. Mobile oder Web‑Frontend als Ergänzung.

## Kurz: Start & Debugging‑Hinweise
- Build: `mvn clean package`  
- Start in Entwicklung: `mvn javafx:run`  
- Tests + Coverage: `mvn test jacoco:report` → `target/site/jacoco/index.html`  
- Logs und Testausgaben: `target/surefire-reports`