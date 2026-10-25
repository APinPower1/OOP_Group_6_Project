package edu.mec.studentportal.client.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.core.type.TypeReference;
import edu.mec.studentportal.client.api.ApiClient;
import edu.mec.studentportal.client.api.ApiException;
import edu.mec.studentportal.client.api.SessionContext;
import edu.mec.studentportal.client.dto.LoginRequest;
import edu.mec.studentportal.client.dto.LoginResponse;
import edu.mec.studentportal.client.dto.Role;
import edu.mec.studentportal.client.dto.UserSummaryResponse;
import java.awt.Component;
import java.awt.Container;
import java.awt.event.ActionEvent;
import java.lang.reflect.Proxy;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPasswordField;
import javax.swing.KeyStroke;
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
            JPasswordField password = (JPasswordField) findByName(view, "login.password");
            password.setText("password");
            JButton visibility = (JButton) findByName(view, "login.password.visibility");
            char masked = password.getEchoChar();
            visibility.doClick();
            assertEquals((char) 0, password.getEchoChar());
            visibility.doClick();
            assertEquals(masked, password.getEchoChar());
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

    @Test
    void enterBindingShowsInlineValidationForEmptyCredentials() throws Exception {
        ApiClient api = (ApiClient) Proxy.newProxyInstance(
            ApiClient.class.getClassLoader(), new Class<?>[] {ApiClient.class},
            (proxy, method, args) -> {
                throw new AssertionError("empty credentials must not call the API");
            });
        SwingUtilities.invokeAndWait(() -> {
            LoginView view = new LoginView(api, new SessionContext());
            Object binding = view.getInputMap(JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT)
                .get(KeyStroke.getKeyStroke("ENTER"));
            assertEquals("login", binding);
            view.getActionMap().get(binding).actionPerformed(
                new ActionEvent(view, ActionEvent.ACTION_PERFORMED, "Enter"));
            assertEquals("Enter your email and password.",
                ((JLabel) findByName(view, "login.status")).getText());
        });
    }

    @Test
    void failedRequestShowsLoadingThenInlineErrorAndReenablesButton() throws Exception {
        CountDownLatch requestStarted = new CountDownLatch(1);
        CountDownLatch finishRequest = new CountDownLatch(1);
        CountDownLatch errorDisplayed = new CountDownLatch(1);
        ApiClient api = (ApiClient) Proxy.newProxyInstance(
            ApiClient.class.getClassLoader(), new Class<?>[] {ApiClient.class},
            (proxy, method, args) -> {
                if (method.getName().equals("post") && args != null && args.length == 3
                    && "/auth/login".equals(args[0])) {
                    requestStarted.countDown();
                    if (!finishRequest.await(5, TimeUnit.SECONDS)) {
                        throw new AssertionError("timed out waiting to finish fake request");
                    }
                    throw new ApiException(401, "INVALID_CREDENTIALS",
                        "Invalid email or password.");
                }
                throw new UnsupportedOperationException(method.getName());
            });
        AtomicReference<LoginView> viewRef = new AtomicReference<>();

        SwingUtilities.invokeAndWait(() -> {
            LoginView view = new LoginView(api, new SessionContext());
            viewRef.set(view);
            ((JTextField) findByName(view, "login.email")).setText("student@test.edu");
            ((JPasswordField) findByName(view, "login.password")).setText("wrong");
            JLabel status = (JLabel) findByName(view, "login.status");
            status.addPropertyChangeListener("text", event -> {
                if ("Invalid email or password.".equals(event.getNewValue())) {
                    errorDisplayed.countDown();
                }
            });
            view.getLoginButton().doClick();
        });

        assertTrue(requestStarted.await(5, TimeUnit.SECONDS), "login request did not start");
        SwingUtilities.invokeAndWait(() -> {
            LoginView view = viewRef.get();
            assertFalse(view.getLoginButton().isEnabled());
            assertEquals("Signing in…", ((JLabel) findByName(view, "login.status")).getText());
        });

        finishRequest.countDown();
        assertTrue(errorDisplayed.await(5, TimeUnit.SECONDS), "inline error was not shown");
        SwingUtilities.invokeAndWait(() -> {
            LoginView view = viewRef.get();
            assertTrue(view.getLoginButton().isEnabled());
            assertEquals("Invalid email or password.",
                ((JLabel) findByName(view, "login.status")).getText());
        });
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
