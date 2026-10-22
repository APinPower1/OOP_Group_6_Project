package edu.mec.studentportal.client.api;

import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.function.Consumer;
import javax.swing.JComponent;
import javax.swing.SwingWorker;

/**
 * Runs an API call off the Event Dispatch Thread and delivers results back on the EDT.
 * Optional components (usually the submit button) are disabled while the call is in flight
 * to avoid duplicate submissions; backend rules remain the source of correctness.
 */
public final class ApiTask {
    private ApiTask() {}

    public static <T> void run(
        Callable<T> call,
        Consumer<T> onSuccess,
        Consumer<ApiException> onError,
        JComponent... disableDuringFlight
    ) {
        SwingWorker<T, Void> worker = new SwingWorker<>() {
            @Override
            protected T doInBackground() throws Exception {
                return call.call();
            }

            @Override
            protected void done() {
                try {
                    onSuccess.accept(get());
                } catch (ExecutionException exception) {
                    Throwable cause = exception.getCause();
                    if (cause instanceof ApiException apiException) {
                        onError.accept(apiException);
                    } else {
                        onError.accept(new ApiException(
                            ApiException.NETWORK_STATUS,
                            ApiException.NETWORK_ERROR,
                            cause == null || cause.getMessage() == null
                                ? "Unexpected error."
                                : cause.getMessage()
                        ));
                    }
                } catch (InterruptedException exception) {
                    Thread.currentThread().interrupt();
                    onError.accept(new ApiException(
                        ApiException.NETWORK_STATUS,
                        ApiException.NETWORK_ERROR,
                        "The request was interrupted."
                    ));
                } finally {
                    for (JComponent component : disableDuringFlight) {
                        component.setEnabled(true);
                    }
                }
            }
        };
        for (JComponent component : disableDuringFlight) {
            component.setEnabled(false);
        }
        worker.execute();
    }
}
