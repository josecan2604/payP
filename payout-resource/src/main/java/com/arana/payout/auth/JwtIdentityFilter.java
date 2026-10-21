package com.arana.payout.auth;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

/**
 * Stamps the VERIFIED JWT identity onto the request so controllers/services can do
 * per-object ownership checks. The client id comes from the token's "sub" claim after
 * Spring Security has validated the signature — not from any client-supplied header.
 *
 * Runs after the security filter chain, so for /v1/* the authentication is already present.
 */
public class JwtIdentityFilter extends OncePerRequestFilter {

    public static final String CLIENT_ID_ATTR = "payout.clientId";
    public static final String ROLES_ATTR = "payout.roles";

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth instanceof JwtAuthenticationToken jwtAuth) {
            Jwt jwt = jwtAuth.getToken();
            request.setAttribute(CLIENT_ID_ATTR, jwt.getSubject());
            List<String> roles = jwt.getClaimAsStringList("roles");
            request.setAttribute(ROLES_ATTR, roles == null ? List.of() : roles);
        }
        chain.doFilter(request, response);
    }
}
