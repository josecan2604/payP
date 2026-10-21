package com.arana.payout.webhook;

import com.arana.payout.config.PayoutProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

import java.time.Clock;

/**
 * The RECEIVER side (what a merchant would host). It is deliberately PUBLIC —
 * so its only defense is the signature. The rule every webhook endpoint follows:
 *
 *   1. Read the RAW body (not a parsed object) — you must verify the exact bytes.
 *   2. Recompute the HMAC and compare in constant time; reject on mismatch (401).
 *   3. Only then act on the event, and always return 2xx quickly so the sender
 *      doesn't retry a message you already accepted.
 */
@RestController
public class WebhookController {

    private static final Logger log = LoggerFactory.getLogger(WebhookController.class);

    private final PayoutProperties props;
    private final Clock clock;

    public WebhookController(PayoutProperties props, Clock clock) {
        this.props = props;
        this.clock = clock;
    }

    @PostMapping("/webhooks/payouts")
    public ResponseEntity<Void> receive(
            @RequestHeader(value = "X-Signature", required = false) String signature,
            @RequestBody String rawBody) {

        boolean ok = SignatureUtil.verify(
                signature, rawBody, props.webhook().secret(),
                clock.instant().getEpochSecond(), props.webhook().toleranceSeconds());

        if (!ok) {
            log.warn("rejected webhook: bad or missing signature");
            return ResponseEntity.status(401).build();
        }

        // Signature is valid -> safe to trust the body and act on it.
        log.info("VERIFIED webhook received: {}", rawBody);
        // (A real merchant would parse the event here and mark its invoice paid.)
        return ResponseEntity.ok().build();
    }
}
