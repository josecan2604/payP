package com.arana.gateway.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.ReactiveJwtAuthenticationConverterAdapter;
import org.springframework.security.web.server.SecurityWebFilterChain;
import org.springframework.core.convert.converter.Converter;
import org.springframework.security.core.GrantedAuthority;

import java.util.Collection;
import java.util.List;

/**
 * Reactive security + RBAC rules enforced at the edge.
 *
 *  - /oauth/**, /.well-known/**, /webhooks/** : public (token issuance, JWKS, inbound webhooks)
 *  - /v1/reconcile                            : ADMIN only
 *  - /v1/**                                   : any authenticated client
 *
 * Roles come from the JWT "roles" claim, mapped to ROLE_* authorities.
 */
@Configuration
@EnableWebFluxSecurity
public class SecurityConfig {

    @Bean
    public SecurityWebFilterChain springSecurityFilterChain(ServerHttpSecurity http) {
        http
            .csrf(ServerHttpSecurity.CsrfSpec::disable)
            .authorizeExchange(ex -> ex
                .pathMatchers("/oauth/**", "/.well-known/**", "/webhooks/**").permitAll()
                .pathMatchers(HttpMethod.GET, "/v1/reconcile").hasRole("ADMIN")
                .pathMatchers("/v1/**").authenticated()
                .anyExchange().denyAll())
            .oauth2ResourceServer(oauth -> oauth
                .jwt(jwt -> jwt.jwtAuthenticationConverter(reactiveRolesConverter())));
        return http.build();
    }

    /** Maps the JWT "roles" claim (e.g. ["ADMIN","CLIENT"]) to ROLE_ADMIN / ROLE_CLIENT authorities. */
    private ReactiveJwtAuthenticationConverterAdapter reactiveRolesConverter() {
        Converter<Jwt, Collection<GrantedAuthority>> authorities = jwt -> {
            List<String> roles = jwt.getClaimAsStringList("roles");
            if (roles == null) {
                return List.of();
            }
            return roles.stream()
                    .map(r -> (GrantedAuthority) new SimpleGrantedAuthority("ROLE_" + r))
                    .toList();
        };
        JwtAuthenticationConverter delegate = new JwtAuthenticationConverter();
        delegate.setJwtGrantedAuthoritiesConverter(authorities);
        return new ReactiveJwtAuthenticationConverterAdapter(delegate);
    }
}
