package edu.mec.studentportal.client.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.core.type.TypeReference;
import edu.mec.studentportal.client.api.ApiClient;
import edu.mec.studentportal.client.api.SessionContext;
import edu.mec.studentportal.client.dto.LoginRequest;
import edu.mec.studentportal.client.dto.LoginResponse;
import edu.mec.studentportal.client.dto.Role;
import edu.mec.studentportal.client.dto.UserSummaryResponse;
import java.awt.Component;
import java.awt.Container;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import javax.swing.JPasswordField;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.Test;

class LoginViewTest {
    @Test
    void signInButtonPostsCredentialsAndCreatesSession() throws Exception {
        AtomicReference<String> path = new AtomicReference<>();
        AtomicReference<LoginRequest> request = new AtomicReference<>();
        ApiClient api = new ApiClient() {
            @Override
            public <T> T post(String requestPath, Object body, Class<T> responseType) {
                path.set(requestPath);
                request.set((LoginRequest) body);
                return responseType.cast(new LoginResponse(
                    "test-token",
                    "Bearer",
                    1800,
                    new UserSummaryResponse(5, "Test Student", "student@test.edu", Role.STUDENT)
                ));
            }

            @Override
            public <T> T get(String requestPath, Class<T> responseType) {
                throw new UnsupportedOperationException();
            }

            @Override
            public <T> T get(String requestPath, TypeReference<T> responseType) {
                throw new UnsupportedOperationException();
            }

            @Override
            public <T> T post(String requestPath, Object body, TypeReference<T> responseType) {
                throw new UnsupportedOperationException();
            }

            @Override
            public <T> T put(String requestPath, Object body, Class<T> responseType) {
                throw new UnsupportedOperationException();
            }

            @Override
            public <T> T patch(String requestPath, Object body, Class<T> responseType) {
                throw new UnsupportedOperationException();
            }

            @Override
            public void delete(String requestPath) {
                throw new UnsupportedOperationException();
            }

            @Override
            public <T> T delete(String requestPath, Class<T> responseType) {
                throw new UnsupportedOperationException();
            }
        };
        SessionContext session = new SessionContext();
        CountDownLatch signedIn = new CountDownLatch(1);
        session.addListener(changed -> {
            if (changed.isLoggedIn()) {
                signedIn.countDown();
            }
        });

        SwingUtilities.invokeAndWait(() -> {
            LoginView view = new LoginView(api, session);
            ((JTextField) findByName(view, "login.email")).setText(" student@test.edu ");
            ((JPasswordField) findByName(view, "login.password")).setText("password");
            assertTrue(view.getLoginButton().isDefaultCapable());
            view.getLoginButton().doClick();
        });

        assertTrue(signedIn.await(5, TimeUnit.SECONDS), "login did not create a session");
        assertEquals("/auth/login", path.get());
        assertEquals("student@test.edu", request.get().email());
        assertEquals("password", request.get().password());
        assertEquals("test-token", session.getToken());
        assertEquals(Role.STUDENT, session.getRole());
    }

    private static Component findByName(Container parent, String name) {
        Component match = find(parent, name);
        if (match == null) {
            throw new AssertionError("Component not found: " + name);
        }
        return match;
    }

    private static Component find(Container parent, String name) {
        for (Component child : parent.getComponents()) {
            if (name.equals(child.getName())) {
                return child;
            }
            if (child instanceof Container nested) {
                Component match = find(nested, name);
                if (match != null) {
                    return match;
                }
            }
        }
        return null;
    }
}
