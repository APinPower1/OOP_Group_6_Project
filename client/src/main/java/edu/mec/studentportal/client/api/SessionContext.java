package edu.mec.studentportal.client.api;

import edu.mec.studentportal.client.dto.Role;
import edu.mec.studentportal.client.dto.UserSummary;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * In-memory session only: JWT + user summary. Never persisted to disk. Listeners fire on the
 * calling thread (typically a background worker), so UI listeners must hop to the EDT themselves.
 */
public final class SessionContext {
    public interface Listener {
        void sessionChanged(SessionContext session);
    }

    private final List<Listener> listeners = new CopyOnWriteArrayList<>();

    private String token;
    private UserSummary user;

    public synchronized void setSession(String token, UserSummary user) {
        if (token == null || token.isBlank()) {
            throw new IllegalArgumentException("token must not be blank");
        }
        if (user == null) {
            throw new IllegalArgumentException("user must not be null");
        }
        this.token = token;
        this.user = user;
        notifyListeners();
    }

    public synchronized void clearSession() {
        if (this.token == null && this.user == null) {
            return;
        }
        this.token = null;
        this.user = null;
        notifyListeners();
    }

    public synchronized String getToken() {
        return token;
    }

    public synchronized UserSummary getUser() {
        return user;
    }

    public synchronized boolean isLoggedIn() {
        return token != null && user != null;
    }

    public synchronized Role getRole() {
        return user == null ? null : user.role();
    }

    public void addListener(Listener listener) {
        if (listener == null) {
            throw new IllegalArgumentException("listener must not be null");
        }
        listeners.add(listener);
    }

    public void removeListener(Listener listener) {
        listeners.remove(listener);
    }

    private void notifyListeners() {
        for (Listener listener : listeners) {
            listener.sessionChanged(this);
        }
    }
}
