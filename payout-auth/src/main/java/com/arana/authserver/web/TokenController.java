package com.arana.authserver.web;

import com.arana.authserver.config.AuthProperties;
import com.arana.authserver.jwt.JwtIssuer;
import jakarta.validation.constraints.NotBlank;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * POST /oauth/token  (grant_type=client_credentials)
 * Validates client credentials and returns a signed JWT carrying the client's roles.
 */
@RestController
public class TokenController {

    private final AuthProperties props;
    private final JwtIssuer jwtIssuer;

    public TokenController(AuthProperties props, JwtIssuer jwtIssuer) {
        this.props = props;
        this.jwtIssuer = jwtIssuer;
    }

    @PostMapping("/oauth/token")
    public TokenResponse token(@RequestBody TokenRequest request) {
        if (!"client_credentials".equals(request.grantType())) {
            throw new AuthException("unsupported grant_type: " + request.grantType(), true);
        }
        AuthProperties.Client client = props.clients().get(request.clientId());
        if (client == null || !client.secret().equals(request.clientSecret())) {
            throw new AuthException("invalid client credentials", false);
        }
        String jwt = jwtIssuer.issue(request.clientId(), client.roles());
        return new TokenResponse(jwt, "Bearer", jwtIssuer.ttlSeconds());
    }

    public record TokenRequest(
            @NotBlank String grantType,
            @NotBlank String clientId,
            @NotBlank String clientSecret
    ) {}

    public record TokenResponse(String accessToken, String tokenType, long expiresIn) {}
}
