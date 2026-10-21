package com.arana.payout.auth;

import jakarta.validation.constraints.NotBlank;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * POST /oauth/token
 * Exchange client credentials for a bearer token. This endpoint is public;
 * everything under /v1/** then requires the token it returns.
 */
@RestController
public class TokenController {

    private final TokenService tokenService;

    public TokenController(TokenService tokenService) {
        this.tokenService = tokenService;
    }

    @PostMapping("/oauth/token")
    public TokenResponse token(@RequestBody TokenRequest request) {
        TokenService.IssuedToken issued =
                tokenService.issue(request.grantType(), request.clientId(), request.clientSecret());
        return new TokenResponse(issued.accessToken(), "Bearer", issued.expiresInSeconds());
    }

    public record TokenRequest(
            @NotBlank String grantType,
            @NotBlank String clientId,
            @NotBlank String clientSecret
    ) {}

    public record TokenResponse(String accessToken, String tokenType, long expiresIn) {}
}
