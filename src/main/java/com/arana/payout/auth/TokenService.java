package com.arana.payout.auth;

import com.arana.payout.config.PayoutProperties;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.util.Base64;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Issues and validates short-lived, opaque bearer tokens.
 *
 * Real providers usually issue JWTs, but an opaque random token kept in a store
 * is the same idea and keeps the demo dependency-free: the token is just a key
 * that maps to "who you are + when you expire".
 */
@Service
public class TokenService {

    private record Session(String clientId, Instant expiresAt) {}

    private final SecureRandom random = new SecureRandom();
    private final Map<String, Session> sessions = new ConcurrentHashMap<>();
    private final PayoutProperties props;
    private final Clock clock;

    public TokenService(PayoutProperties props, Clock clock) {
        this.props = props;
        this.clock = clock;
    }

    /** Validates client credentials and returns a fresh token, or throws if they are wrong. */
    public IssuedToken issue(String grantType, String clientId, String clientSecret) {
        if (!"client_credentials".equals(grantType)) {
            throw new AuthException("unsupported grant_type: " + grantType);
        }
        String expected = props.clients().get(clientId);
        if (expected == null || !expected.equals(clientSecret)) {
            throw new AuthException("invalid client credentials");
        }
        byte[] buf = new byte[32];
        random.nextBytes(buf);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(buf);
        Instant expiresAt = clock.instant().plusSeconds(props.tokenTtlSeconds());
        sessions.put(token, new Session(clientId, expiresAt));
        return new IssuedToken(token, props.tokenTtlSeconds());
    }

    /** Returns the client id for a valid, non-expired token, or null otherwise. */
    public String validate(String token) {
        Session s = sessions.get(token);
        if (s == null) {
            return null;
        }
        if (Objects.requireNonNull(s.expiresAt()).isBefore(clock.instant())) {
            sessions.remove(token);
            return null;
        }
        return s.clientId();
    }

    public record IssuedToken(String accessToken, long expiresInSeconds) {}
}
