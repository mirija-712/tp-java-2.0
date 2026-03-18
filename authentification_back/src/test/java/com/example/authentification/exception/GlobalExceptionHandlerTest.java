package com.example.authentification.exception;

import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Tests unitaires du GlobalExceptionHandler pour couvrir les différents handlers TP2.
 */
class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    private HttpServletRequest mockRequest() {
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getRequestURI()).thenReturn("/api/test");
        return request;
    }

    /**
     * Teste le handler pour InvalidInputException, doit retourner 400 Bad Request.
     */
    @Test
    void handleInvalidInput_retourne400() {
        HttpServletRequest request = mockRequest();
        var response = handler.handleInvalidInput(new InvalidInputException("bad"), request);
        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        Map<String, Object> body = response.getBody();
        assertEquals(400, body.get("status"));
        assertEquals("Bad Request", body.get("error"));
    }

    /**
     * Teste le handler pour AuthenticationFailedException, doit retourner 401 Unauthorized.
     */
    @Test
    void handleAuthenticationFailed_retourne401() {
        HttpServletRequest request = mockRequest();
        var response = handler.handleAuthenticationFailed(new AuthenticationFailedException("unauth"), request);
        assertEquals(HttpStatus.UNAUTHORIZED, response.getStatusCode());
        Map<String, Object> body = response.getBody();
        assertEquals(401, body.get("status"));
        assertEquals("Unauthorized", body.get("error"));
    }

    /**
     * Teste le handler pour ResourceConflictException, doit retourner 409 Conflict.
     */
    @Test
    void handleResourceConflict_retourne409() {
        HttpServletRequest request = mockRequest();
        var response = handler.handleResourceConflict(new ResourceConflictException("conflict"), request);
        assertEquals(HttpStatus.CONFLICT, response.getStatusCode());
        Map<String, Object> body = response.getBody();
        assertEquals(409, body.get("status"));
        assertEquals("Conflict", body.get("error"));
    }

    /**
     * Teste le handler pour AccountLockedException, doit retourner 423 Locked.
     */
    @Test
    void handleAccountLocked_retourne423() {
        HttpServletRequest request = mockRequest();
        var response = handler.handleAccountLocked(new AccountLockedException("locked"), request);
        assertEquals(HttpStatus.LOCKED, response.getStatusCode());
        Map<String, Object> body = response.getBody();
        assertEquals(423, body.get("status"));
        assertEquals("Locked", body.get("error"));
    }
}
