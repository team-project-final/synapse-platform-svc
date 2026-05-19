package io.synapse.platform.billing;

import com.stripe.StripeClient;
import com.stripe.exception.SignatureVerificationException;
import com.stripe.exception.StripeException;
import com.stripe.model.Event;
import com.stripe.model.EventDataObjectDeserializer;
import com.stripe.model.Invoice;
import com.stripe.model.InvoicePayment;
import com.stripe.model.StripeObject;
import com.stripe.model.checkout.Session;
import com.stripe.net.Webhook;
import com.stripe.param.checkout.SessionCreateParams;
import io.synapse.platform.auth.api.TenantApi;
import io.synapse.platform.billing.config.StripeProperties;
import io.synapse.platform.billing.domain.PaymentHistory;
import io.synapse.platform.billing.domain.PlanCode;
import io.synapse.platform.billing.domain.Subscription;
import io.synapse.platform.billing.domain.SubscriptionStatus;
import io.synapse.platform.billing.dto.CheckoutSessionRequest;
import io.synapse.platform.billing.dto.CheckoutSessionResponse;
import io.synapse.platform.billing.dto.SubscriptionResponse;
import io.synapse.platform.billing.exception.BillingException;
import io.synapse.platform.billing.repository.PaymentHistoryRepository;
import io.synapse.platform.billing.repository.ProcessedEventRepository;
import io.synapse.platform.billing.repository.SubscriptionRepository;
import io.synapse.platform.user.api.UserApi;
import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class BillingService {

    private final SubscriptionRepository subscriptionRepository;
    private final PaymentHistoryRepository paymentHistoryRepository;
    private final ProcessedEventRepository processedEventRepository;
    private final UserApi userApi;
    private final TenantApi tenantApi;
    private final StripeProperties stripeProperties;
    private final StripeClient stripeClient;

    public BillingService(
            SubscriptionRepository subscriptionRepository,
            PaymentHistoryRepository paymentHistoryRepository,
            ProcessedEventRepository processedEventRepository,
            UserApi userApi,
            TenantApi tenantApi,
            StripeProperties stripeProperties,
            StripeClient stripeClient) {
        this.subscriptionRepository = subscriptionRepository;
        this.paymentHistoryRepository = paymentHistoryRepository;
        this.processedEventRepository = processedEventRepository;
        this.userApi = userApi;
        this.tenantApi = tenantApi;
        this.stripeProperties = stripeProperties;
        this.stripeClient = stripeClient;
    }

    public CheckoutSessionResponse createCheckoutSession(UUID userId, CheckoutSessionRequest request) {
        UUID tenantId = resolveTenantId(userId);
        String priceId = resolvePriceId(request.planCode());
        try {
            Session session = stripeClient.checkout().sessions().create(
                    SessionCreateParams.builder()
                            .setMode(SessionCreateParams.Mode.SUBSCRIPTION)
                            .addLineItem(SessionCreateParams.LineItem.builder()
                                    .setPrice(priceId)
                                    .setQuantity(1L)
                                    .build())
                            .setSuccessUrl(request.successUrl())
                            .setCancelUrl(request.cancelUrl())
                            .putMetadata("tenant_id", tenantId.toString())
                            .putMetadata("plan_code", request.planCode().name())
                            .build());
            return new CheckoutSessionResponse(session.getUrl());
        } catch (StripeException exception) {
            throw new BillingException("BILLING-001", 502, "Failed to create checkout session");
        }
    }

    @Transactional
    public void handleWebhook(byte[] rawPayload, String sigHeader) {
        String payload = new String(rawPayload, StandardCharsets.UTF_8);
        Event event;
        try {
            event = Webhook.constructEvent(payload, sigHeader, stripeProperties.webhook().secret());
        } catch (SignatureVerificationException exception) {
            throw new BillingException("BILLING-002", 400, "Invalid Stripe signature");
        }

        int inserted = processedEventRepository.insertIfAbsent(event.getId(), event.getType());
        if (inserted == 0) {
            return;
        }

        switch (event.getType()) {
            case "checkout.session.completed" -> handleCheckoutCompleted(event);
            case "invoice.paid" -> handleInvoicePaid(event);
            case "customer.subscription.deleted" -> handleSubscriptionDeleted(event);
            default -> {
            }
        }
    }

    public SubscriptionResponse getSubscription(UUID userId) {
        UUID tenantId = resolveTenantId(userId);
        return subscriptionRepository.findByTenantIdAndStatus(tenantId, SubscriptionStatus.ACTIVE)
                .map(subscription -> new SubscriptionResponse(
                        subscription.getId(),
                        subscription.getPlanCode().name(),
                        subscription.getStatus().name(),
                        subscription.getCurrentPeriodEnd(),
                        subscription.getStripeSubscriptionId()))
                .orElseThrow(() -> new BillingException("BILLING-003", 404, "No active subscription found"));
    }

    private void handleCheckoutCompleted(Event event) {
        Session session = (Session) deserialize(event);
        UUID tenantId = UUID.fromString(session.getMetadata().get("tenant_id"));
        PlanCode planCode = PlanCode.valueOf(session.getMetadata().get("plan_code"));

        Subscription subscription = subscriptionRepository
                .findByTenantIdAndStatus(tenantId, SubscriptionStatus.ACTIVE)
                .orElseGet(() -> subscriptionRepository.save(
                        Subscription.create(tenantId, planCode, session.getCustomer())));

        subscription.activate(session.getSubscription(), OffsetDateTime.now(), OffsetDateTime.now().plusMonths(1));
        subscriptionRepository.save(subscription);
        tenantApi.activatePlan(tenantId, planCode.value());
    }

    private void handleInvoicePaid(Event event) {
        Invoice invoice = (Invoice) deserialize(event);
        String paymentIntentId = paymentIntentId(invoice);
        String stripeSubscriptionId = subscriptionId(invoice);
        subscriptionRepository.findByStripeSubscriptionId(stripeSubscriptionId)
                .ifPresent(subscription -> paymentHistoryRepository.save(PaymentHistory.of(
                        subscription.getTenantId(),
                        subscription.getId(),
                        paymentIntentId,
                        invoice.getAmountPaid() == null ? 0 : invoice.getAmountPaid().intValue(),
                        invoice.getCurrency() == null ? "usd" : invoice.getCurrency(),
                        "succeeded",
                        OffsetDateTime.now())));
    }

    private String subscriptionId(Invoice invoice) {
        if (invoice.getParent() == null || invoice.getParent().getSubscriptionDetails() == null) {
            return null;
        }
        return invoice.getParent().getSubscriptionDetails().getSubscription();
    }

    private String paymentIntentId(Invoice invoice) {
        if (invoice.getPayments() == null || invoice.getPayments().getData().isEmpty()) {
            return null;
        }
        InvoicePayment payment = invoice.getPayments().getData().get(0);
        if (payment.getPayment() == null) {
            return null;
        }
        return payment.getPayment().getPaymentIntent();
    }

    private void handleSubscriptionDeleted(Event event) {
        com.stripe.model.Subscription stripeSubscription =
                (com.stripe.model.Subscription) deserialize(event);
        subscriptionRepository.findByStripeSubscriptionId(stripeSubscription.getId())
                .ifPresent(subscription -> {
                    subscription.cancel();
                    subscriptionRepository.save(subscription);
                    tenantApi.activatePlan(subscription.getTenantId(), PlanCode.FREE.value());
                });
    }

    private UUID resolveTenantId(UUID userId) {
        return userApi.findById(userId)
                .map(user -> user.defaultTenantId())
                .orElseThrow(() -> new BillingException("BILLING-004", 404, "User not found"));
    }

    private String resolvePriceId(PlanCode planCode) {
        return switch (planCode) {
            case PRO -> stripeProperties.plans().pro().priceId();
            case TEAM -> stripeProperties.plans().team().priceId();
            case ENTERPRISE -> stripeProperties.plans().enterprise().priceId();
            default -> throw new BillingException("BILLING-005", 400, "Invalid plan for checkout");
        };
    }

    private StripeObject deserialize(Event event) {
        EventDataObjectDeserializer deserializer = event.getDataObjectDeserializer();
        return deserializer.getObject()
                .orElseThrow(() -> new BillingException("BILLING-006", 500, "Failed to deserialize Stripe event"));
    }
}
