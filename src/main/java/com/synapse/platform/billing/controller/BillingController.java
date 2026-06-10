package com.synapse.platform.billing.controller;

import com.synapse.platform.billing.dto.request.CheckoutSessionRequest;
import com.synapse.platform.billing.dto.response.BillingReceiptResponse;
import com.synapse.platform.billing.dto.response.BillingUsageResponse;
import com.synapse.platform.billing.dto.response.CheckoutSessionResponse;
import com.synapse.platform.billing.dto.response.PaymentHistoryPageResponse;
import com.synapse.platform.billing.dto.response.SubscriptionResponse;
import com.synapse.platform.billing.exception.BillingException;
import com.synapse.platform.billing.service.BillingService;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/billing")
public class BillingController {

    private static final int MAX_PAGE_SIZE = 100;

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

    @GetMapping("/payments")
    public ResponseEntity<PaymentHistoryPageResponse> getPayments(
            Authentication authentication,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(billingService.getPayments(currentUserId(authentication), pageable(page, size)));
    }

    @GetMapping("/usage")
    public ResponseEntity<BillingUsageResponse> getUsage(Authentication authentication) {
        return ResponseEntity.ok(billingService.getUsage(currentUserId(authentication)));
    }

    @GetMapping("/payments/{id}/receipt")
    public ResponseEntity<BillingReceiptResponse> getReceipt(
            Authentication authentication,
            @PathVariable UUID id) {
        return ResponseEntity.ok(billingService.getReceipt(currentUserId(authentication), id));
    }

    private Pageable pageable(int page, int size) {
        if (page < 0) {
            throw new BillingException("BILLING-010", 400, "page must be greater than or equal to 0");
        }
        if (size < 1 || size > MAX_PAGE_SIZE) {
            throw new BillingException("BILLING-010", 400, "size must be between 1 and 100");
        }
        return PageRequest.of(page, size, Sort.by(
                Sort.Order.desc("paidAt").nullsLast(),
                Sort.Order.desc("createdAt")));
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
