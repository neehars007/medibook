package com.coolapp.medibook.payment;

import com.coolapp.medibook.common.config.AppProperties;
import com.stripe.Stripe;
import com.stripe.exception.EventDataObjectDeserializationException;
import com.stripe.exception.SignatureVerificationException;
import com.stripe.exception.StripeException;
import com.stripe.model.Event;
import com.stripe.model.PaymentIntent;
import com.stripe.model.StripeObject;
import com.stripe.net.Webhook;
import com.stripe.param.PaymentIntentCreateParams;
import com.stripe.param.RefundCreateParams;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

/**
 * Thin wrapper over the Stripe SDK (test mode). When no secret key is configured the gateway runs
 * in simulated mode so the whole flow works locally without a Stripe account.
 */
@Slf4j
@Component
public class StripeGateway {

    public record Intent(String id, String clientSecret) {}

    public record WebhookEvent(String type, String paymentIntentId) {}

    private final AppProperties.Stripe cfg;

    public StripeGateway(AppProperties props) {
        this.cfg = props.stripe();
        if (cfg.enabled()) {
            Stripe.apiKey = cfg.secretKey();
        } else {
            log.warn("Stripe secret key not set - payments run in SIMULATED mode");
        }
    }

    public boolean enabled() {
        return cfg.enabled();
    }

    public Intent createIntent(BigDecimal amount, UUID appointmentId) {
        if (!enabled()) {
            return new Intent("pi_simulated_" + UUID.randomUUID().toString().replace("-", ""), null);
        }
        try {
            PaymentIntent pi = PaymentIntent.create(PaymentIntentCreateParams.builder()
                    .setAmount(toCents(amount))
                    .setCurrency(cfg.currency())
                    .putMetadata("appointmentId", appointmentId.toString())
                    .setAutomaticPaymentMethods(PaymentIntentCreateParams.AutomaticPaymentMethods.builder()
                            .setEnabled(true).build())
                    .build());
            return new Intent(pi.getId(), pi.getClientSecret());
        } catch (StripeException e) {
            throw new IllegalStateException("Stripe PaymentIntent creation failed: " + e.getMessage(), e);
        }
    }

    public void refund(String paymentIntentId, BigDecimal amount) {
        if (!enabled() || paymentIntentId == null || paymentIntentId.startsWith("pi_simulated_")) {
            log.info("Simulated refund of {} for {}", amount, paymentIntentId);
            return;
        }
        try {
            com.stripe.model.Refund.create(RefundCreateParams.builder()
                    .setPaymentIntent(paymentIntentId)
                    .setAmount(toCents(amount))
                    .build());
        } catch (StripeException e) {
            throw new IllegalStateException("Stripe refund failed: " + e.getMessage(), e);
        }
    }

    public Optional<WebhookEvent> parseWebhook(String payload, String signature) {
        if (!enabled()) {
            return Optional.empty();
        }
        Event event;
        try {
            event = Webhook.constructEvent(payload, signature, cfg.webhookSecret());
        } catch (SignatureVerificationException e) {
            throw new IllegalArgumentException("Invalid Stripe signature");
        }
        var deserializer = event.getDataObjectDeserializer();
        StripeObject obj = deserializer.getObject().orElseGet(() -> {
            try {
                return deserializer.deserializeUnsafe();
            } catch (EventDataObjectDeserializationException e) {
                return null;
            }
        });
        if (obj instanceof PaymentIntent pi) {
            return Optional.of(new WebhookEvent(event.getType(), pi.getId()));
        }
        return Optional.empty();
    }

    private static long toCents(BigDecimal amount) {
        return amount.movePointRight(2).setScale(0, java.math.RoundingMode.HALF_UP).longValueExact();
    }
}
