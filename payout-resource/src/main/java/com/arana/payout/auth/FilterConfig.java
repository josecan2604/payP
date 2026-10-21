package com.arana.payout.auth;

import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;

/**
 * Registers the JWT-identity filter for the protected API (/v1/*). It runs after Spring
 * Security has validated the token, so the verified identity is available to stamp.
 */
@Configuration
public class FilterConfig {

    @Bean
    public FilterRegistrationBean<JwtIdentityFilter> jwtIdentityFilter() {
        var registration = new FilterRegistrationBean<>(new JwtIdentityFilter());
        registration.addUrlPatterns("/v1/*");
        registration.setOrder(Ordered.LOWEST_PRECEDENCE);
        return registration;
    }
}
