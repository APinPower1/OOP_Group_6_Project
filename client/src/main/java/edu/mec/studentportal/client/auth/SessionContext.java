package edu.mec.studentportal.client.auth;

public final class SessionContext {
    private String accessToken;
    private UserSummary user;

    public synchronized void setSession(String token, UserSummary currentUser) {
        if (token == null || token.isBlank() || currentUser == null) {
            throw new IllegalArgumentException("A token and user summary are required.");
        }
        accessToken = token;
        user = currentUser;
    }

    public synchronized void clearSession() {
        accessToken = null;
        user = null;
    }

    public synchronized String token() {
        return accessToken;
    }

    public synchronized UserSummary user() {
        if (user == null) {
            throw new IllegalStateException("No user is signed in.");
        }
        return user;
    }

    public synchronized boolean isLoggedIn() {
        return accessToken != null && user != null;
    }

    public record UserSummary(long id, String fullName, String email, String role) {}
}
