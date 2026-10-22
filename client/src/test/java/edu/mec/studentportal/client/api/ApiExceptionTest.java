package edu.mec.studentportal.client.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class ApiExceptionTest {

    @Test
    void classifiesStatusCodes() {
        ApiException unauthorized = new ApiException(401, "INVALID_CREDENTIALS", "nope");
        assertTrue(unauthorized.isUnauthorized());
        assertFalse(unauthorized.isForbidden());
        assertFalse(unauthorized.isNetworkError());
        assertEquals(401, unauthorized.getStatus());
        assertEquals("INVALID_CREDENTIALS", unauthorized.getCode());
        assertEquals("nope", unauthorized.getMessage());

        ApiException forbidden = new ApiException(403, "FORBIDDEN", "denied");
        assertTrue(forbidden.isForbidden());
        assertFalse(forbidden.isUnauthorized());

        ApiException network = new ApiException(
            ApiException.NETWORK_STATUS, ApiException.NETWORK_ERROR, "down");
        assertTrue(network.isNetworkError());
        assertEquals(0, network.getStatus());
        assertEquals("NETWORK_ERROR", network.getCode());
    }
}
