package com.arana.authserver.web;

/** Bad credentials or unsupported grant. Maps to 401 / 400. */
public class AuthException extends RuntimeException {
    private final boolean badRequest;

    public AuthException(String message, boolean badRequest) {
        super(message);
        this.badRequest = badRequest;
    }

    public boolean isBadRequest() {
        return badRequest;
    }
}
