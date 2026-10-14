package library.model;

import java.util.ArrayList;
import java.util.List;

public class Member {
    public String username;
    public String passwordHash;   // salted SHA-256 hex
    public String salt;           // random 16-byte salt hex
    public String role;           // member or librarian
    public List<Loan> loans = new ArrayList<>();
    public List<Hold> holds = new ArrayList<>();

    public Member() {}

    public Member(String username, String passwordHash, String salt, String role) {
        this.username = username;
        this.passwordHash = passwordHash;
        this.salt = salt;
        this.role = role;
    }
}
