package com.arana.payout.auth;

/** Thrown when credentials are wrong or a token is missing/invalid. Maps to HTTP 401. */
public class AuthException extends RuntimeException {
    public AuthException(String message) {
        super(message);
    }
}
