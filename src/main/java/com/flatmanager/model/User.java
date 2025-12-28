package com.flatmanager.model;

/**
 * Einfaches Modell für einen Benutzer / WG-Mitbewohner.
 * Beinhaltet Felder für id, username, (gehashtes) password und ein optionales Anzeige-Name-Feld.
 */
public class User {
    private int id;
    private String username;
    private String password;
    private String name;

    public User(int id, String username, String password, String name) {
        this.id = id;
        this.username = username;
        this.password = password;
        this.name = name;
    }

    public int getId() { return id; }
    public String getUsername() { return username; }
    public String getPassword() { return password; }
    public String getName() { return name; }

    public void setId(int id) { this.id = id; }
    public void setUsername(String username) { this.username = username; }
    public void setPassword(String password) { this.password = password; }
    public void setName(String name) { this.name = name; }
}
