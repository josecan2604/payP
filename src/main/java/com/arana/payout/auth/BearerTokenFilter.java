package com.arana.payout.auth;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.MediaType;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Requires a valid "Authorization: Bearer <token>" on every protected request.
 * Registered for /v1/* only (see FilterConfig); /oauth/token and /webhooks stay public.
 *
 * On success it stashes the caller's client id as a request attribute so controllers
 * can read "who is calling" without re-parsing the header.
 */
public class BearerTokenFilter extends OncePerRequestFilter {

    public static final String CLIENT_ID_ATTR = "payout.clientId";

    private final TokenService tokenService;
    private final ObjectMapper objectMapper;

    public BearerTokenFilter(TokenService tokenService, ObjectMapper objectMapper) {
        this.tokenService = tokenService;
        this.objectMapper = objectMapper;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {

        String header = request.getHeader("Authorization");
        if (header == null || !header.startsWith("Bearer ")) {
            unauthorized(response, "missing bearer token");
            return;
        }
        String token = header.substring("Bearer ".length()).trim();
        String clientId = tokenService.validate(token);
        if (clientId == null) {
            unauthorized(response, "invalid or expired token");
            return;
        }
        request.setAttribute(CLIENT_ID_ATTR, clientId);
        chain.doFilter(request, response);
    }

    private void unauthorized(HttpServletResponse response, String detail) throws IOException {
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        var body = new ProblemBody("about:blank", "Unauthorized", 401, detail);
        objectMapper.writeValue(response.getWriter(), body);
    }

    /** Minimal RFC 9457 problem detail written directly from the filter. */
    private record ProblemBody(String type, String title, int status, String detail) {}
}
