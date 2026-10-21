package com.arana.authserver.jwt;

import com.arana.authserver.config.AuthProperties;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Instant;
import java.util.Date;
import java.util.List;

/** Signs RS256 JWTs. The token carries the client id (sub) and its roles. */
@Service
public class JwtIssuer {

    private final RSAKey rsaKey;
    private final AuthProperties props;
    private final Clock clock;

    public JwtIssuer(RSAKey rsaKey, AuthProperties props, Clock clock) {
        this.rsaKey = rsaKey;
        this.props = props;
        this.clock = clock;
    }

    public String issue(String clientId, List<String> roles) {
        try {
            Instant now = clock.instant();
            JWTClaimsSet claims = new JWTClaimsSet.Builder()
                    .subject(clientId)
                    .issuer(props.issuer())
                    .issueTime(Date.from(now))
                    .expirationTime(Date.from(now.plusSeconds(props.tokenTtlSeconds())))
                    .claim("roles", roles)
                    .build();

            SignedJWT jwt = new SignedJWT(
                    new JWSHeader.Builder(JWSAlgorithm.RS256).keyID(rsaKey.getKeyID()).build(),
                    claims);
            jwt.sign(new RSASSASigner(rsaKey.toPrivateKey()));
            return jwt.serialize();
        } catch (Exception e) {
            throw new IllegalStateException("could not sign JWT", e);
        }
    }

    public long ttlSeconds() {
        return props.tokenTtlSeconds();
    }
}
