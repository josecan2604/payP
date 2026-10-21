package com.arana.payout.auth;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.core.convert.converter.Converter;

import java.util.Collection;
import java.util.List;

/**
 * Defense in depth: the resource server validates the JWT itself (signature via the auth
 * server's JWKS), so it stays secure even if reached directly, not only through the gateway.
 * Identity comes from the verified token's "sub", never from a plaintext header.
 *
 *  - /webhooks/**   : public (signed callbacks, verified by HMAC in the controller)
 *  - /v1/reconcile  : ADMIN only (re-checked here as well as at the gateway)
 *  - /v1/**         : any authenticated client
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            .csrf(csrf -> csrf.disable())
            .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/webhooks/**").permitAll()
                .requestMatchers(HttpMethod.GET, "/v1/reconcile").hasRole("ADMIN")
                .requestMatchers("/v1/**").authenticated()
                .anyRequest().denyAll())
            .oauth2ResourceServer(oauth -> oauth
                .jwt(jwt -> jwt.jwtAuthenticationConverter(rolesConverter())));
        return http.build();
    }

    /** Maps the JWT "roles" claim to ROLE_* authorities. */
    private JwtAuthenticationConverter rolesConverter() {
        Converter<Jwt, Collection<GrantedAuthority>> authorities = jwt -> {
            List<String> roles = jwt.getClaimAsStringList("roles");
            if (roles == null) {
                return List.of();
            }
            return roles.stream()
                    .map(r -> (GrantedAuthority) new SimpleGrantedAuthority("ROLE_" + r))
                    .toList();
        };
        JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(authorities);
        return converter;
    }
}
