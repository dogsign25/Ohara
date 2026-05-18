package com.ohara.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "users")
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(unique = true, nullable = false, length = 50)
    private String username;

    @Column(unique = true, nullable = false, length = 100)
    private String email;

    @Column(nullable = false)
    private String password;  // BCrypt 해시

    @Column(name = "created_at")
    private LocalDateTime createdAt = LocalDateTime.now();

    // ── getters / setters ─────────────────────────────────────────
    public Long getId()                      { return id; }
    public String getUsername()              { return username; }
    public void setUsername(String username) { this.username = username; }
    public String getEmail()                 { return email; }
    public void setEmail(String email)       { this.email = email; }
    public String getPassword()              { return password; }
    public void setPassword(String password) { this.password = password; }
    public LocalDateTime getCreatedAt()      { return createdAt; }
}
