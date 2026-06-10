package com.synapse.platform.billing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.withSettings;

import com.stripe.StripeClient;
import com.stripe.exception.ApiException;
import com.stripe.model.checkout.Session;
import com.stripe.net.Webhook;
import com.stripe.param.checkout.SessionCreateParams;
import com.synapse.platform.auth.api.PlanQuotaInfo;
import com.synapse.platform.auth.api.TenantApi;
import com.synapse.platform.auth.api.TenantInfo;
import com.synapse.platform.billing.config.StripeProperties;
import com.synapse.platform.billing.entity.PaymentHistory;
import com.synapse.platform.billing.entity.PlanCode;
import com.synapse.platform.billing.entity.Subscription;
import com.synapse.platform.billing.entity.SubscriptionStatus;
import com.synapse.platform.billing.dto.request.CheckoutSessionRequest;
import com.synapse.platform.billing.exception.BillingException;
import com.synapse.platform.billing.repository.PaymentHistoryRepository;
import com.synapse.platform.billing.repository.ProcessedEventRepository;
import com.synapse.platform.billing.repository.SubscriptionRepository;
import com.synapse.platform.billing.service.BillingService;
import com.synapse.platform.user.api.UserApi;
import com.synapse.platform.user.api.UserInfo;
import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Answers;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

class BillingServiceTest {

    private static final String WEBHOOK_SECRET = "whsec_unit";

    private final SubscriptionRepository subscriptionRepository = mock(SubscriptionRepository.class);
    private final PaymentHistoryRepository paymentHistoryRepository = mock(PaymentHistoryRepository.class);
    private final ProcessedEventRepository processedEventRepository = mock(ProcessedEventRepository.class);
    private final UserApi userApi = mock(UserApi.class);
    private final TenantApi tenantApi = mock(TenantApi.class);
    private final StripeClient stripeClient = mock(
            StripeClient.class,
            withSettings().defaultAnswer(Answers.RETURNS_DEEP_STUBS));
    private BillingService billingService;

    @BeforeEach
    void setUp() {
        billingService = new BillingService(
                subscriptionRepository,
                paymentHistoryRepository,
                processedEventRepository,
                userApi,
                tenantApi,
                new StripeProperties(
                        new StripeProperties.Webhook(WEBHOOK_SECRET),
                        new StripeProperties.Plans(
                                new StripeProperties.Plan("price_pro"),
                                new StripeProperties.Plan("price_team"),
                                new StripeProperties.Plan("price_enterprise"))),
                stripeClient);
    }

    @Test
    void createCheckoutSession_usesUsersDefaultTenantAndConfiguredPriceId() throws Exception {
        UUID userId = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();
        Session session = new Session();
        session.setUrl("https://checkout.stripe.test/session");
        given(userApi.findById(userId)).willReturn(Optional.of(new UserInfo(
                userId, "billing@example.com", "Billing User", tenantId)));
        given(stripeClient.checkout().sessions().create(any(SessionCreateParams.class))).willReturn(session);

        CheckoutSessionRequest request = new CheckoutSessionRequest(
                PlanCode.PRO,
                "https://app.example.com/success",
                "https://app.example.com/cancel");

        assertThat(billingService.createCheckoutSession(userId, request).checkoutUrl())
                .isEqualTo("https://checkout.stripe.test/session");
        ArgumentCaptor<SessionCreateParams> paramsCaptor = ArgumentCaptor.forClass(SessionCreateParams.class);
        verify(stripeClient.checkout().sessions()).create(paramsCaptor.capture());
        SessionCreateParams params = paramsCaptor.getValue();
        assertThat(params.getLineItems()).singleElement()
                .satisfies(lineItem -> assertThat(lineItem.getPrice()).isEqualTo("price_pro"));
        assertThat(params.getMetadata()).containsEntry("tenant_id", tenantId.toString());
        assertThat(params.getMetadata()).containsEntry("plan_code", "PRO");
    }

    @Test
    void createCheckoutSession_rejectsFreePlan() {
        UUID userId = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();
        given(userApi.findById(userId)).willReturn(Optional.of(new UserInfo(
                userId, "billing@example.com", "Billing User", tenantId)));

        CheckoutSessionRequest request = new CheckoutSessionRequest(
                PlanCode.FREE,
                "https://app.example.com/success",
                "https://app.example.com/cancel");

        assertThatThrownBy(() -> billingService.createCheckoutSession(userId, request))
                .isInstanceOf(BillingException.class)
                .extracting("errorCode")
                .isEqualTo("BILLING-005");
        verifyNoInteractions(stripeClient);
    }

    @Test
    void createCheckoutSession_usesTeamPriceId() throws Exception {
        assertCheckoutSessionPrice(PlanCode.TEAM, "price_team");
    }

    @Test
    void createCheckoutSession_usesEnterprisePriceId() throws Exception {
        assertCheckoutSessionPrice(PlanCode.ENTERPRISE, "price_enterprise");
    }

    @Test
    void createCheckoutSession_missingUserThrowsBillingException() {
        UUID userId = UUID.randomUUID();
        given(userApi.findById(userId)).willReturn(Optional.empty());

        CheckoutSessionRequest request = new CheckoutSessionRequest(
                PlanCode.PRO,
                "https://app.example.com/success",
                "https://app.example.com/cancel");

        assertThatThrownBy(() -> billingService.createCheckoutSession(userId, request))
                .isInstanceOf(BillingException.class)
                .extracting("errorCode")
                .isEqualTo("BILLING-004");
        verifyNoInteractions(stripeClient);
    }

    @Test
    void createCheckoutSession_stripeFailureThrowsBillingException() throws Exception {
        UUID userId = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();
        given(userApi.findById(userId)).willReturn(Optional.of(new UserInfo(
                userId, "billing@example.com", "Billing User", tenantId)));
        given(stripeClient.checkout().sessions().create(any(SessionCreateParams.class)))
                .willThrow(new ApiException("stripe unavailable", null, null, 502, null));

        CheckoutSessionRequest request = new CheckoutSessionRequest(
                PlanCode.PRO,
                "https://app.example.com/success",
                "https://app.example.com/cancel");

        assertThatThrownBy(() -> billingService.createCheckoutSession(userId, request))
                .isInstanceOf(BillingException.class)
                .extracting("errorCode")
                .isEqualTo("BILLING-001");
    }

    @Test
    void handleWebhook_invalidSignatureThrowsBillingExceptionBeforeMutation() {
        assertThatThrownBy(() -> billingService.handleWebhook("{}".getBytes(StandardCharsets.UTF_8), "invalid"))
                .isInstanceOf(BillingException.class)
                .extracting("errorCode")
                .isEqualTo("BILLING-002");
        verifyNoInteractions(processedEventRepository, subscriptionRepository, tenantApi);
    }

    @Test
    void handleWebhook_duplicateEventSkipsBusinessMutation() throws Exception {
        String payload = checkoutCompletedPayload(UUID.randomUUID(), PlanCode.PRO, "evt_duplicate");
        given(processedEventRepository.insertIfAbsent("evt_duplicate", "checkout.session.completed")).willReturn(0);

        billingService.handleWebhook(payload.getBytes(StandardCharsets.UTF_8), signature(payload));

        verify(processedEventRepository).insertIfAbsent("evt_duplicate", "checkout.session.completed");
        verifyNoInteractions(subscriptionRepository, tenantApi);
    }

    @Test
    void handleWebhook_checkoutCompletedCreatesSubscriptionAndActivatesTenantPlan() throws Exception {
        UUID tenantId = UUID.randomUUID();
        String payload = checkoutCompletedPayload(tenantId, PlanCode.TEAM, "evt_checkout");
        given(processedEventRepository.insertIfAbsent("evt_checkout", "checkout.session.completed")).willReturn(1);
        given(subscriptionRepository.findByTenantIdAndStatus(tenantId, SubscriptionStatus.ACTIVE))
                .willReturn(Optional.empty());
        given(subscriptionRepository.save(any(Subscription.class)))
                .willAnswer(invocation -> invocation.getArgument(0));

        billingService.handleWebhook(payload.getBytes(StandardCharsets.UTF_8), signature(payload));

        ArgumentCaptor<Subscription> subscriptionCaptor = ArgumentCaptor.forClass(Subscription.class);
        verify(subscriptionRepository, times(2)).save(subscriptionCaptor.capture());
        Subscription savedSubscription = subscriptionCaptor.getAllValues().getLast();
        assertThat(savedSubscription.getTenantId()).isEqualTo(tenantId);
        assertThat(savedSubscription.getPlanCode()).isEqualTo(PlanCode.TEAM);
        assertThat(savedSubscription.getStripeCustomerId()).isEqualTo("cus_test");
        assertThat(savedSubscription.getStripeSubscriptionId()).isEqualTo("sub_test");
        verify(tenantApi).activatePlan(tenantId, "team");
    }

    @Test
    void handleWebhook_unknownEventOnlyRecordsIdempotencyMarker() throws Exception {
        String payload = unknownEventPayload("evt_unknown");
        given(processedEventRepository.insertIfAbsent("evt_unknown", "customer.created")).willReturn(1);

        billingService.handleWebhook(payload.getBytes(StandardCharsets.UTF_8), signature(payload));

        verify(processedEventRepository).insertIfAbsent("evt_unknown", "customer.created");
        verifyNoInteractions(subscriptionRepository, paymentHistoryRepository, tenantApi);
    }

    @Test
    void handleWebhook_subscriptionDeletedCancelsAndResetsTenantPlan() throws Exception {
        UUID tenantId = UUID.randomUUID();
        Subscription subscription = Subscription.create(tenantId, PlanCode.PRO, "cus_test");
        subscription.activate("sub_test", java.time.OffsetDateTime.now(), java.time.OffsetDateTime.now().plusMonths(1));
        String payload = subscriptionDeletedPayload("evt_deleted", "sub_test");
        given(processedEventRepository.insertIfAbsent("evt_deleted", "customer.subscription.deleted")).willReturn(1);
        given(subscriptionRepository.findByStripeSubscriptionId("sub_test")).willReturn(Optional.of(subscription));

        billingService.handleWebhook(payload.getBytes(StandardCharsets.UTF_8), signature(payload));

        assertThat(subscription.getStatus().name()).isEqualTo("CANCELED");
        verify(subscriptionRepository).save(subscription);
        verify(tenantApi).activatePlan(tenantId, "free");
    }

    @Test
    void handleWebhook_invoicePaidStoresPaymentHistory() throws Exception {
        UUID tenantId = UUID.randomUUID();
        Subscription subscription = Subscription.create(tenantId, PlanCode.PRO, "cus_test");
        subscription.activate("sub_test", java.time.OffsetDateTime.now(), java.time.OffsetDateTime.now().plusMonths(1));
        String payload = invoicePaidPayload("evt_invoice", "sub_test", "pi_test");
        given(processedEventRepository.insertIfAbsent("evt_invoice", "invoice.paid")).willReturn(1);
        given(subscriptionRepository.findByStripeSubscriptionId("sub_test")).willReturn(Optional.of(subscription));

        billingService.handleWebhook(payload.getBytes(StandardCharsets.UTF_8), signature(payload));

        ArgumentCaptor<PaymentHistory> paymentCaptor = ArgumentCaptor.forClass(PaymentHistory.class);
        verify(paymentHistoryRepository).save(paymentCaptor.capture());
        assertThat(paymentCaptor.getValue())
                .satisfies(payment -> {
                    assertThat(payment.getTenantId()).isEqualTo(tenantId);
                    assertThat(payment.getStripePaymentIntentId()).isEqualTo("pi_test");
                    assertThat(payment.getStripeInvoiceId()).isEqualTo("in_test");
                    assertThat(payment.getInvoiceUrl()).isEqualTo("https://invoice.stripe.test/in_test");
                    assertThat(payment.getInvoicePdfUrl()).isEqualTo("https://invoice.stripe.test/in_test.pdf");
                    assertThat(payment.isReceiptAvailable()).isTrue();
                });
    }

    @Test
    void handleWebhook_invoicePaidWithoutSubscriptionReferenceDoesNotStorePaymentHistory() throws Exception {
        String payload = invoicePaidWithoutSubscriptionPayload("evt_invoice_missing_subscription");
        given(processedEventRepository.insertIfAbsent("evt_invoice_missing_subscription", "invoice.paid"))
                .willReturn(1);

        billingService.handleWebhook(payload.getBytes(StandardCharsets.UTF_8), signature(payload));

        verify(subscriptionRepository).findByStripeSubscriptionId(null);
        verifyNoInteractions(paymentHistoryRepository, tenantApi);
    }

    @Test
    void handleWebhook_invoicePaidWithoutPaymentIntentStoresNullDefaults() throws Exception {
        UUID tenantId = UUID.randomUUID();
        Subscription subscription = Subscription.create(tenantId, PlanCode.PRO, "cus_test");
        subscription.activate("sub_test", java.time.OffsetDateTime.now(), java.time.OffsetDateTime.now().plusMonths(1));
        String payload = invoicePaidWithoutPaymentIntentPayload("evt_invoice_no_payment", "sub_test");
        given(processedEventRepository.insertIfAbsent("evt_invoice_no_payment", "invoice.paid")).willReturn(1);
        given(subscriptionRepository.findByStripeSubscriptionId("sub_test")).willReturn(Optional.of(subscription));

        billingService.handleWebhook(payload.getBytes(StandardCharsets.UTF_8), signature(payload));

        verify(paymentHistoryRepository).save(any());
    }

    @Test
    void getSubscription_returnsActiveSubscriptionForUsersDefaultTenant() {
        UUID userId = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();
        Subscription subscription = Subscription.create(tenantId, PlanCode.PRO, "cus_test");
        java.time.OffsetDateTime periodStart = java.time.OffsetDateTime.now();
        java.time.OffsetDateTime periodEnd = periodStart.plusMonths(1);
        subscription.activate("sub_test", periodStart, periodEnd);
        given(userApi.findById(userId)).willReturn(Optional.of(new UserInfo(
                userId, "billing@example.com", "Billing User", tenantId)));
        given(subscriptionRepository.findByTenantIdAndStatus(
                tenantId,
                SubscriptionStatus.ACTIVE))
                .willReturn(Optional.of(subscription));

        assertThat(billingService.getSubscription(userId))
                .satisfies(response -> {
                    assertThat(response.planCode()).isEqualTo("PRO");
                    assertThat(response.status()).isEqualTo("ACTIVE");
                    assertThat(response.currentPeriodEnd()).isEqualTo(periodEnd);
                    assertThat(response.stripeSubscriptionId()).isEqualTo("sub_test");
                });
    }

    @Test
    void getSubscription_withoutActiveSubscriptionThrowsBillingException() {
        UUID userId = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();
        given(userApi.findById(userId)).willReturn(Optional.of(new UserInfo(
                userId, "billing@example.com", "Billing User", tenantId)));
        given(subscriptionRepository.findByTenantIdAndStatus(
                tenantId,
                SubscriptionStatus.ACTIVE))
                .willReturn(Optional.empty());

        assertThatThrownBy(() -> billingService.getSubscription(userId))
                .isInstanceOf(BillingException.class)
                .extracting("errorCode")
                .isEqualTo("BILLING-003");
    }

    @Test
    void getPayments_returnsUsersDefaultTenantPayments() {
        UUID userId = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();
        PaymentHistory payment = PaymentHistory.of(
                tenantId,
                UUID.randomUUID(),
                "pi_test",
                "in_test",
                "https://invoice.stripe.test/in_test",
                "https://invoice.stripe.test/in_test.pdf",
                999,
                "usd",
                "succeeded",
                OffsetDateTime.parse("2026-06-09T06:30:00Z"));
        PageRequest pageable = PageRequest.of(0, 20);
        given(userApi.findById(userId)).willReturn(Optional.of(new UserInfo(
                userId, "billing@example.com", "Billing User", tenantId)));
        given(paymentHistoryRepository.findByTenantId(tenantId, pageable))
                .willReturn(new PageImpl<>(List.of(payment), pageable, 1));

        assertThat(billingService.getPayments(userId, pageable))
                .satisfies(response -> {
                    assertThat(response.totalElements()).isOne();
                    assertThat(response.items()).singleElement()
                            .satisfies(item -> {
                                assertThat(item.amount()).isEqualTo(999);
                                assertThat(item.receiptAvailable()).isTrue();
                            });
                });
    }

    @Test
    void getReceipt_hidesOtherTenantPaymentsAsNotFound() {
        UUID userId = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();
        UUID paymentId = UUID.randomUUID();
        given(userApi.findById(userId)).willReturn(Optional.of(new UserInfo(
                userId, "billing@example.com", "Billing User", tenantId)));
        given(paymentHistoryRepository.findByIdAndTenantId(paymentId, tenantId)).willReturn(Optional.empty());

        assertThatThrownBy(() -> billingService.getReceipt(userId, paymentId))
                .isInstanceOf(BillingException.class)
                .extracting("errorCode")
                .isEqualTo("BILLING-007");
    }

    @Test
    void getReceipt_returnsStoredInvoiceMetadata() {
        UUID userId = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();
        UUID paymentId = UUID.randomUUID();
        PaymentHistory payment = PaymentHistory.of(
                tenantId,
                UUID.randomUUID(),
                "pi_test",
                "in_test",
                "https://invoice.stripe.test/in_test",
                "https://invoice.stripe.test/in_test.pdf",
                999,
                "usd",
                "succeeded",
                OffsetDateTime.parse("2026-06-09T06:30:00Z"));
        given(userApi.findById(userId)).willReturn(Optional.of(new UserInfo(
                userId, "billing@example.com", "Billing User", tenantId)));
        given(paymentHistoryRepository.findByIdAndTenantId(paymentId, tenantId)).willReturn(Optional.of(payment));

        assertThat(billingService.getReceipt(userId, paymentId))
                .satisfies(response -> {
                    assertThat(response.stripePaymentIntentId()).isEqualTo("pi_test");
                    assertThat(response.stripeInvoiceId()).isEqualTo("in_test");
                    assertThat(response.invoiceUrl()).isEqualTo("https://invoice.stripe.test/in_test");
                    assertThat(response.available()).isTrue();
                });
    }

    @Test
    void getUsage_returnsActiveSubscriptionPlanQuotaWithoutConnectedUsageCounts() {
        UUID userId = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();
        Subscription subscription = Subscription.create(tenantId, PlanCode.PRO, "cus_test");
        subscription.activate(
                "sub_test",
                OffsetDateTime.parse("2026-06-09T00:00:00Z"),
                OffsetDateTime.parse("2026-07-09T00:00:00Z"));
        given(userApi.findById(userId)).willReturn(Optional.of(new UserInfo(
                userId, "billing@example.com", "Billing User", tenantId)));
        given(subscriptionRepository.findByTenantIdAndStatus(tenantId, SubscriptionStatus.ACTIVE))
                .willReturn(Optional.of(subscription));
        given(tenantApi.findPlanQuota("pro")).willReturn(Optional.of(proQuota()));

        assertThat(billingService.getUsage(userId))
                .satisfies(response -> {
                    assertThat(response.tenantId()).isEqualTo(tenantId);
                    assertThat(response.planCode()).isEqualTo("pro");
                    assertThat(response.subscriptionStatus()).isEqualTo("ACTIVE");
                    assertThat(response.quotas().maxNotes()).isEqualTo(50000);
                    assertThat(response.usage().notes().used()).isNull();
                    assertThat(response.usage().notes().limit()).isEqualTo(50000L);
                    assertThat(response.usage().notes().source()).isEqualTo("NOT_CONNECTED");
                });
    }

    @Test
    void getUsage_withoutActiveSubscriptionFallsBackToTenantPlan() {
        UUID userId = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();
        given(userApi.findById(userId)).willReturn(Optional.of(new UserInfo(
                userId, "billing@example.com", "Billing User", tenantId)));
        given(subscriptionRepository.findByTenantIdAndStatus(tenantId, SubscriptionStatus.ACTIVE))
                .willReturn(Optional.empty());
        given(tenantApi.findById(tenantId)).willReturn(Optional.of(new TenantInfo(tenantId, "free", "active")));
        given(tenantApi.findPlanQuota("free")).willReturn(Optional.of(new PlanQuotaInfo(
                "free", "Free", 1000, 500, 100000000L, 100000L, 10, 1)));

        assertThat(billingService.getUsage(userId))
                .satisfies(response -> {
                    assertThat(response.planCode()).isEqualTo("free");
                    assertThat(response.subscriptionStatus()).isNull();
                    assertThat(response.quotas().maxCards()).isEqualTo(500);
                });
    }

    private void assertCheckoutSessionPrice(PlanCode planCode, String expectedPriceId) throws Exception {
        UUID userId = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();
        Session session = new Session();
        session.setUrl("https://checkout.stripe.test/session");
        given(userApi.findById(userId)).willReturn(Optional.of(new UserInfo(
                userId, "billing@example.com", "Billing User", tenantId)));
        given(stripeClient.checkout().sessions().create(any(SessionCreateParams.class))).willReturn(session);

        CheckoutSessionRequest request = new CheckoutSessionRequest(
                planCode,
                "https://app.example.com/success",
                "https://app.example.com/cancel");

        billingService.createCheckoutSession(userId, request);

        ArgumentCaptor<SessionCreateParams> paramsCaptor = ArgumentCaptor.forClass(SessionCreateParams.class);
        verify(stripeClient.checkout().sessions()).create(paramsCaptor.capture());
        assertThat(paramsCaptor.getValue().getLineItems()).singleElement()
                .satisfies(lineItem -> assertThat(lineItem.getPrice()).isEqualTo(expectedPriceId));
    }

    private static String signature(String payload) throws Exception {
        long timestamp = Webhook.Util.getTimeNow();
        String signedPayload = timestamp + "." + payload;
        String signature = Webhook.Util.computeHmacSha256(WEBHOOK_SECRET, signedPayload);
        return "t=" + timestamp + ",v1=" + signature;
    }

    private static PlanQuotaInfo proQuota() {
        return new PlanQuotaInfo("pro", "Pro", 50000, 50000, 10000000000L, 5000000L, 500, 1);
    }

    private static String checkoutCompletedPayload(UUID tenantId, PlanCode planCode, String eventId) {
        return """
                {
                  "id": "%s",
                  "object": "event",
                  "api_version": "2026-04-22.dahlia",
                  "type": "checkout.session.completed",
                  "data": {
                    "object": {
                      "id": "cs_test",
                      "object": "checkout.session",
                      "customer": "cus_test",
                      "subscription": "sub_test",
                      "metadata": {
                        "tenant_id": "%s",
                        "plan_code": "%s"
                      }
                    }
                  }
                }
                """.formatted(eventId, tenantId, planCode.name());
    }

    private static String unknownEventPayload(String eventId) {
        return """
                {
                  "id": "%s",
                  "object": "event",
                  "api_version": "2026-04-22.dahlia",
                  "type": "customer.created",
                  "data": {
                    "object": {
                      "id": "cus_test",
                      "object": "customer"
                    }
                  }
                }
                """.formatted(eventId);
    }

    private static String subscriptionDeletedPayload(String eventId, String subscriptionId) {
        return """
                {
                  "id": "%s",
                  "object": "event",
                  "api_version": "2026-04-22.dahlia",
                  "type": "customer.subscription.deleted",
                  "data": {
                    "object": {
                      "id": "%s",
                      "object": "subscription"
                    }
                  }
                }
                """.formatted(eventId, subscriptionId);
    }

    private static String invoicePaidPayload(String eventId, String subscriptionId, String paymentIntentId) {
        return """
                {
                  "id": "%s",
                  "object": "event",
                  "api_version": "2026-04-22.dahlia",
                  "type": "invoice.paid",
                  "data": {
                    "object": {
                      "id": "in_test",
                      "object": "invoice",
                      "amount_paid": 1200,
                      "currency": "usd",
                      "hosted_invoice_url": "https://invoice.stripe.test/in_test",
                      "invoice_pdf": "https://invoice.stripe.test/in_test.pdf",
                      "parent": {
                        "type": "subscription_details",
                        "subscription_details": {
                          "subscription": "%s"
                        }
                      },
                      "payments": {
                        "object": "list",
                        "data": [
                          {
                            "id": "inpay_test",
                            "object": "invoice_payment",
                            "payment": {
                              "type": "payment_intent",
                              "payment_intent": "%s"
                            }
                          }
                        ]
                      }
                    }
                  }
                }
                """.formatted(eventId, subscriptionId, paymentIntentId);
    }

    private static String invoicePaidWithoutSubscriptionPayload(String eventId) {
        return """
                {
                  "id": "%s",
                  "object": "event",
                  "api_version": "2026-04-22.dahlia",
                  "type": "invoice.paid",
                  "data": {
                    "object": {
                      "id": "in_test",
                      "object": "invoice"
                    }
                  }
                }
                """.formatted(eventId);
    }

    private static String invoicePaidWithoutPaymentIntentPayload(String eventId, String subscriptionId) {
        return """
                {
                  "id": "%s",
                  "object": "event",
                  "api_version": "2026-04-22.dahlia",
                  "type": "invoice.paid",
                  "data": {
                    "object": {
                      "id": "in_test",
                      "object": "invoice",
                      "parent": {
                        "type": "subscription_details",
                        "subscription_details": {
                          "subscription": "%s"
                        }
                      },
                      "payments": {
                        "object": "list",
                        "data": [
                          {
                            "id": "inpay_test",
                            "object": "invoice_payment"
                          }
                        ]
                      }
                    }
                  }
                }
                """.formatted(eventId, subscriptionId);
    }
}
