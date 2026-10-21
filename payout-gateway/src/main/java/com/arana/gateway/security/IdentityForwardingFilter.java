package com.arana.gateway.security;

import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.List;

/**
 * After authentication, forwards the caller's identity to the resource server as trusted
 * headers (X-Client-Id, X-Roles). It always SETS (never appends) these headers, so a client
 * cannot spoof them — anything they send is overwritten with the verified JWT values.
 *
 * Public routes (no authenticated principal) pass through unchanged.
 */
@Component
public class IdentityForwardingFilter implements GlobalFilter, Ordered {

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        return exchange.getPrincipal()
                .filter(JwtAuthenticationToken.class::isInstance)
                .cast(JwtAuthenticationToken.class)
                .map(auth -> withIdentityHeaders(exchange, auth.getToken()))
                .defaultIfEmpty(exchange)
                .flatMap(chain::filter);
    }

    private ServerWebExchange withIdentityHeaders(ServerWebExchange exchange, Jwt jwt) {
        String clientId = jwt.getSubject();
        List<String> roles = jwt.getClaimAsStringList("roles");
        String rolesHeader = roles == null ? "" : String.join(",", roles);

        ServerHttpRequest request = exchange.getRequest().mutate()
                .headers(h -> {
                    h.set("X-Client-Id", clientId);   // set, not add -> overwrites any client-supplied value
                    h.set("X-Roles", rolesHeader);
                })
                .build();
        return exchange.mutate().request(request).build();
    }

    @Override
    public int getOrder() {
        return Ordered.LOWEST_PRECEDENCE; // run after security has populated the principal
    }
}
