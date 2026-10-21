package com.arana.payout.auth;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Applies the bearer-token filter to the protected API only (/v1/*).
 * /oauth/token (to GET a token) and /webhooks/* (public callback) are left open.
 */
@Configuration
public class FilterConfig {

    @Bean
    public FilterRegistrationBean<BearerTokenFilter> bearerTokenFilter(
            TokenService tokenService, ObjectMapper objectMapper) {
        var registration = new FilterRegistrationBean<>(new BearerTokenFilter(tokenService, objectMapper));
        registration.addUrlPatterns("/v1/*");
        registration.setOrder(1);
        return registration;
    }
}
