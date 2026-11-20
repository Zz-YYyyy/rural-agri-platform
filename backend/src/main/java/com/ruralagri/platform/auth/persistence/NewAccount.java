package com.ruralagri.platform.auth.persistence;

public class NewAccount {
    private Long id;
    private final String username;
    private final String passwordHash;
    private final String nickname;

    public NewAccount(String username, String passwordHash, String nickname) {
        this.username = username;
        this.passwordHash = passwordHash;
        this.nickname = nickname;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getUsername() { return username; }
    public String getPasswordHash() { return passwordHash; }
    public String getNickname() { return nickname; }
}
