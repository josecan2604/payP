package com.arana.authserver.config;

import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;
import java.util.UUID;

/**
 * Generates an RSA key pair at startup for signing JWTs.
 *
 * Demo-only: a fresh key each boot means tokens don't survive a restart. A real auth server
 * loads a persisted/rotated key from a secret store or HSM. The signing (private) key never
 * leaves this service; only the public key is published via JWKS.
 */
@Configuration
public class RsaKeyConfig {

    @Bean
    public RSAKey rsaKey() {
        try {
            return new RSAKeyGenerator(2048)
                    .keyID(UUID.randomUUID().toString())
                    .generate();
        } catch (Exception e) {
            throw new IllegalStateException("could not generate RSA signing key", e);
        }
    }

    @Bean
    public Clock clock() {
        return Clock.systemUTC();
    }
}
