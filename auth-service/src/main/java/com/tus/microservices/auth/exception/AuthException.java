package com.tus.microservices.auth.exception;

import org.springframework.http.HttpStatus;

/**
 * Auth-specific exception with an HTTP status code.
 */
public class AuthException extends RuntimeException {

    private final HttpStatus status;

    public AuthException(HttpStatus status, String message) {
        super(message);
        this.status = status;
    }

    public HttpStatus getStatus() {
        return status;
    }
}
