package com.arana.payout.auth;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.MediaType;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

/**
 * The resource server does NOT validate tokens — the gateway already did that and enforced
 * RBAC. The gateway forwards the authenticated identity as trusted headers:
 *   X-Client-Id : the caller's client id (JWT "sub")
 *   X-Roles     : comma-separated roles from the JWT
 *
 * This filter reads those headers (for /v1/*) and stamps them onto the request so the
 * controllers/services can do per-object ownership checks. In production the resource
 * server is only reachable via the gateway (network isolation), which is what makes
 * trusting these headers safe.
 */
public class ForwardedIdentityFilter extends OncePerRequestFilter {

    public static final String CLIENT_ID_ATTR = "payout.clientId";
    public static final String ROLES_ATTR = "payout.roles";

    private final ObjectMapper objectMapper;

    public ForwardedIdentityFilter(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {

        String clientId = request.getHeader("X-Client-Id");
        if (clientId == null || clientId.isBlank()) {
            // Should never happen when reached through the gateway.
            unauthorized(response, "missing forwarded identity");
            return;
        }
        String roles = request.getHeader("X-Roles");
        request.setAttribute(CLIENT_ID_ATTR, clientId);
        request.setAttribute(ROLES_ATTR, roles == null ? List.of() : List.of(roles.split(",")));
        chain.doFilter(request, response);
    }

    private void unauthorized(HttpServletResponse response, String detail) throws IOException {
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        objectMapper.writeValue(response.getWriter(),
                new ProblemBody("about:blank", "Unauthorized", 401, detail));
    }

    private record ProblemBody(String type, String title, int status, String detail) {}
}
