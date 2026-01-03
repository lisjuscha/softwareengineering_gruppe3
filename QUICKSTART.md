# Schnellstart - WG-Verwaltung (Flat Manager)
## Voraussetzungen
- Java 17 oder neuer installiert (`java -version`)
- Maven installiert (`mvn -version`)

## Installation & Start (ZIP‑Datei)
1. ZIP‑Datei entpacken:
    
    unzip /pfad/zur/FlatManager.zip
    cd softwareengineering_gruppe3

2. Anwendung bauen:

    mvn clean package

3. Anwendung starten:

    mvn javafx:run

## Erstes Login

Beim Start erscheint der Login‑Bildschirm.

Standardzugang:
- Benutzername: `admin`
- Passwort: `admin`

## Nutzung
- Reinigungspläne: Aufgaben hinzufügen, als erledigt markieren, löschen.
- Einkaufsliste: Artikel hinzufügen, als gekauft markieren, entfernen.
- Haushaltsbudget: Transaktionen hinzufügen, Gesamt anzeigen, Einträge löschen.

## Hinweise zur Datenbank
- Die Anwendung nutzt die SQLite‑Datei `flatmanager.db`.
- `flatmanager.db` wird beim ersten Start automatisch erstellt.
- Zum Zurücksetzen die `flatmanager.db` löschen (wird neu angelegt).

## JaCoCo Coverage‑Report anzeigen
1. Tests ausführen und Report erzeugen:

    mvn test jacoco:report

2. HTML‑Report öffnen (macOS):

    open target/site/jacoco/index.html

## Fehlerbehebung kurz
- Anwendung startet nicht: Prüfe `java -version` und `mvn -version`. Versuche `mvn clean package` erneut.
- Datenbankfehler: `rm flatmanager.db` und Anwendung neu starten.
- Build‑Fehler: Internetverbindung prüfen oder Maven‑Cache leeren (`mvn clean`).

## Zusätzliche Ressourcen
- Siehe `README.md` für Feature‑Details
- Siehe `ARCHITECTURE.md` für technische Dokumentation
- Quellcode in `src/main/java/com/flatmanager/`