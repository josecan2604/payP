package com.arana.payout.webhook;

import com.arana.payout.config.PayoutProperties;
import com.arana.payout.payout.Payout;
import com.arana.payout.payout.PayoutDtos.PayoutResponse;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.time.Clock;
import java.util.Map;

/**
 * Delivers a SIGNED webhook when a payout changes state.
 *
 * The body is serialized ONCE to a raw string, and that exact string is both
 * signed and sent — because the receiver must verify the signature against the
 * raw bytes, not a re-serialized version (whitespace/key order would differ).
 */
@Component
public class WebhookSender {

    private static final Logger log = LoggerFactory.getLogger(WebhookSender.class);

    private final RestClient restClient;
    private final ObjectMapper objectMapper;
    private final PayoutProperties props;
    private final Clock clock;

    public WebhookSender(RestClient restClient, ObjectMapper objectMapper,
                         PayoutProperties props, Clock clock) {
        this.restClient = restClient;
        this.objectMapper = objectMapper;
        this.props = props;
        this.clock = clock;
    }

    public void sendPayoutEvent(String eventType, Payout payout) {
        String url = props.webhook().callbackUrl();
        String rawBody;
        try {
            rawBody = objectMapper.writeValueAsString(Map.of(
                    "event", eventType,
                    "data", PayoutResponse.from(payout)
            ));
        } catch (JsonProcessingException e) {
            log.error("could not serialize webhook for payout {}", payout.id(), e);
            return;
        }

        long ts = clock.instant().getEpochSecond();
        String signature = SignatureUtil.sign(ts, rawBody, props.webhook().secret());

        try {
            restClient.post()
                    .uri(url)
                    .contentType(MediaType.APPLICATION_JSON)
                    .header("X-Signature", signature)
                    .body(rawBody)
                    .retrieve()
                    .toBodilessEntity();
            log.info("delivered {} for payout {} to {}", eventType, payout.id(), url);
        } catch (Exception e) {
            // Real providers retry with backoff here. For the demo we just log.
            log.warn("webhook delivery failed for payout {} (would retry): {}", payout.id(), e.getMessage());
        }
    }
}
