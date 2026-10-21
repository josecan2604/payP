package com.arana.payout.webhook;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SignatureUtilTest {

    private static final String SECRET = "whsec_test";
    private static final long NOW = 1_760_000_000L;

    @Test
    void validSignatureVerifies() {
        String body = "{\"event\":\"payout.completed\"}";
        String header = SignatureUtil.sign(NOW, body, SECRET);
        assertThat(SignatureUtil.verify(header, body, SECRET, NOW, 300)).isTrue();
    }

    @Test
    void tamperedBodyIsRejected() {
        String header = SignatureUtil.sign(NOW, "{\"amount\":100}", SECRET);
        assertThat(SignatureUtil.verify(header, "{\"amount\":999}", SECRET, NOW, 300)).isFalse();
    }

    @Test
    void wrongSecretIsRejected() {
        String body = "{\"ok\":true}";
        String header = SignatureUtil.sign(NOW, body, SECRET);
        assertThat(SignatureUtil.verify(header, body, "attacker-secret", NOW, 300)).isFalse();
    }

    @Test
    void staleTimestampIsRejected() {
        String body = "{\"ok\":true}";
        String header = SignatureUtil.sign(NOW, body, SECRET);
        // "now" is 10 minutes later than the signature's timestamp, tolerance is 5 minutes.
        assertThat(SignatureUtil.verify(header, body, SECRET, NOW + 600, 300)).isFalse();
    }

    @Test
    void missingHeaderIsRejected() {
        assertThat(SignatureUtil.verify(null, "{}", SECRET, NOW, 300)).isFalse();
    }
}
