package com.arana.payout.auth;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Applies the forwarded-identity filter to the protected API only (/v1/*).
 * /webhooks/* (public callback) is left open.
 */
@Configuration
public class FilterConfig {

    @Bean
    public FilterRegistrationBean<ForwardedIdentityFilter> forwardedIdentityFilter(ObjectMapper objectMapper) {
        var registration = new FilterRegistrationBean<>(new ForwardedIdentityFilter(objectMapper));
        registration.addUrlPatterns("/v1/*");
        registration.setOrder(1);
        return registration;
    }
}
