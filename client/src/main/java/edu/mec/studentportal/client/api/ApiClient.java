package edu.mec.studentportal.client.api;

import com.fasterxml.jackson.core.type.TypeReference;

/**
 * One shared HTTP API client per the Swing client contract: base URL, JSON handling,
 * bearer-token injection, timeouts, standardized {@link ApiException}, and a single place
 * for HTTP status mapping. Paths are relative to the configured base URL (which already
 * includes {@code /api}); requests carry no token before login.
 */
public interface ApiClient {
    <T> T get(String path, Class<T> responseType);

    <T> T get(String path, TypeReference<T> responseType);

    <T> T post(String path, Object requestBody, Class<T> responseType);

    <T> T post(String path, Object requestBody, TypeReference<T> responseType);

    <T> T put(String path, Object requestBody, Class<T> responseType);

    <T> T patch(String path, Object requestBody, Class<T> responseType);

    void delete(String path);

    <T> T delete(String path, Class<T> responseType);
}
