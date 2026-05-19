package io.synapse.platform.billing;

import io.synapse.platform.billing.dto.CheckoutSessionRequest;
import io.synapse.platform.billing.dto.CheckoutSessionResponse;
import io.synapse.platform.billing.dto.SubscriptionResponse;
import io.synapse.platform.billing.exception.BillingException;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/billing")
public class BillingController {

    private final BillingService billingService;

    public BillingController(BillingService billingService) {
        this.billingService = billingService;
    }

    @PostMapping("/checkout")
    public ResponseEntity<CheckoutSessionResponse> createCheckout(
            Authentication authentication,
            @Valid @RequestBody CheckoutSessionRequest request) {
        return ResponseEntity.ok(billingService.createCheckoutSession(currentUserId(authentication), request));
    }

    @PostMapping("/webhooks")
    public ResponseEntity<Void> handleWebhook(
            @RequestBody byte[] payload,
            @RequestHeader("Stripe-Signature") String sigHeader) {
        billingService.handleWebhook(payload, sigHeader);
        return ResponseEntity.ok().build();
    }

    @GetMapping("/subscription")
    public ResponseEntity<SubscriptionResponse> getSubscription(Authentication authentication) {
        return ResponseEntity.ok(billingService.getSubscription(currentUserId(authentication)));
    }

    private UUID currentUserId(Authentication authentication) {
        if (authentication == null) {
            throw new BillingException("PLAT-002", 401, "Authentication required");
        }
        try {
            return UUID.fromString(authentication.getName());
        } catch (IllegalArgumentException exception) {
            throw new BillingException("PLAT-002", 401, "Authentication required");
        }
    }
}
