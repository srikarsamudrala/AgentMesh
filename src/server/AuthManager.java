package server;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;

public class AuthManager {
    private final Database db;

    public AuthManager(Database db) {
        this.db = db;
    }

    public boolean registerAccount(String username, String password) {
        String hash = hashPassword(password);
        return db != null && db.createUser(username, hash);
    }

    public boolean authenticate(String username, String password) {
        String hash = hashPassword(password);
        return db != null && db.validateUser(username, hash);
    }

    private String hashPassword(String password) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] hash = md.digest(password.getBytes());
            return Base64.getEncoder().encodeToString(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException(e);
        }
    }
}