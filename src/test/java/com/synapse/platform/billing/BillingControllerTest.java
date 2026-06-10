package com.synapse.platform.billing;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.eq;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.synapse.platform.billing.controller.BillingController;
import com.synapse.platform.billing.dto.request.CheckoutSessionRequest;
import com.synapse.platform.billing.dto.response.BillingReceiptResponse;
import com.synapse.platform.billing.dto.response.BillingUsageResponse;
import com.synapse.platform.billing.dto.response.BillingUsageResponse.Quotas;
import com.synapse.platform.billing.dto.response.BillingUsageResponse.Usage;
import com.synapse.platform.billing.dto.response.BillingUsageResponse.UsageMetric;
import com.synapse.platform.billing.dto.response.CheckoutSessionResponse;
import com.synapse.platform.billing.dto.response.PaymentHistoryPageResponse;
import com.synapse.platform.billing.dto.response.PaymentHistoryResponse;
import com.synapse.platform.billing.dto.response.SubscriptionResponse;
import com.synapse.platform.billing.entity.PlanCode;
import com.synapse.platform.billing.exception.BillingException;
import com.synapse.platform.billing.service.BillingService;
import com.synapse.platform.global.exception.GlobalExceptionHandler;
import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;

class BillingControllerTest {

    private final BillingService billingService = mock(BillingService.class);
    private final ObjectMapper objectMapper = new ObjectMapper();
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        LocalValidatorFactoryBean validator = new LocalValidatorFactoryBean();
        validator.afterPropertiesSet();
        mockMvc = MockMvcBuilders
                .standaloneSetup(new BillingController(billingService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .setValidator(validator)
                .build();
    }

    @Test
    void createCheckout_authenticatedUserReturnsCheckoutUrl() throws Exception {
        UUID userId = UUID.randomUUID();
        CheckoutSessionRequest request = new CheckoutSessionRequest(
                PlanCode.PRO,
                "https://app.example.com/success",
                "https://app.example.com/cancel");
        given(billingService.createCheckoutSession(any(), any()))
                .willReturn(new CheckoutSessionResponse("https://checkout.stripe.test/session"));

        mockMvc.perform(post("/api/v1/billing/checkout")
                        .principal(new TestingAuthenticationToken(userId.toString(), null))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.checkoutUrl").value("https://checkout.stripe.test/session"));
        verify(billingService).createCheckoutSession(userId, request);
    }

    @Test
    void createCheckout_missingPrincipalReturnsUnauthorizedProblem() throws Exception {
        CheckoutSessionRequest request = new CheckoutSessionRequest(
                PlanCode.PRO,
                "https://app.example.com/success",
                "https://app.example.com/cancel");

        mockMvc.perform(post("/api/v1/billing/checkout")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("PLAT-002"));
    }

    @Test
    void createCheckout_malformedPrincipalReturnsUnauthorizedProblem() throws Exception {
        CheckoutSessionRequest request = new CheckoutSessionRequest(
                PlanCode.PRO,
                "https://app.example.com/success",
                "https://app.example.com/cancel");

        mockMvc.perform(post("/api/v1/billing/checkout")
                        .principal(new TestingAuthenticationToken("not-a-uuid", null))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("PLAT-002"));
    }

    @Test
    void handleWebhook_serviceSuccessReturnsOk() throws Exception {
        byte[] payload = "{}".getBytes(StandardCharsets.UTF_8);

        mockMvc.perform(post("/api/v1/billing/webhooks")
                        .header("Stripe-Signature", "sig")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isOk());
        verify(billingService).handleWebhook(payload, "sig");
    }

    @Test
    void handleWebhook_invalidSignatureReturnsBadRequestProblem() throws Exception {
        org.mockito.BDDMockito.willThrow(new BillingException("BILLING-002", 400, "Invalid Stripe signature"))
                .given(billingService)
                .handleWebhook(any(), any());

        mockMvc.perform(post("/api/v1/billing/webhooks")
                        .header("Stripe-Signature", "sig")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("BILLING-002"));
    }

    @Test
    void getSubscription_authenticatedUserReturnsSubscription() throws Exception {
        UUID userId = UUID.randomUUID();
        UUID subscriptionId = UUID.randomUUID();
        given(billingService.getSubscription(userId)).willReturn(new SubscriptionResponse(
                subscriptionId,
                "PRO",
                "ACTIVE",
                OffsetDateTime.parse("2026-06-19T00:00:00Z"),
                "sub_test"));

        mockMvc.perform(get("/api/v1/billing/subscription")
                        .principal(new TestingAuthenticationToken(userId.toString(), null)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(subscriptionId.toString()))
                .andExpect(jsonPath("$.planCode").value("PRO"));
    }

    @Test
    void getSubscription_missingSubscriptionReturnsNotFoundProblem() throws Exception {
        UUID userId = UUID.randomUUID();
        given(billingService.getSubscription(userId))
                .willThrow(new BillingException("BILLING-003", 404, "No active subscription found"));

        mockMvc.perform(get("/api/v1/billing/subscription")
                        .principal(new TestingAuthenticationToken(userId.toString(), null)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("BILLING-003"));
    }

    @Test
    void getPayments_authenticatedUserReturnsPaymentPage() throws Exception {
        UUID userId = UUID.randomUUID();
        UUID paymentId = UUID.randomUUID();
        UUID subscriptionId = UUID.randomUUID();
        given(billingService.getPayments(eq(userId), any())).willReturn(new PaymentHistoryPageResponse(
                List.of(new PaymentHistoryResponse(
                        paymentId,
                        subscriptionId,
                        999,
                        "usd",
                        "succeeded",
                        OffsetDateTime.parse("2026-06-09T06:30:00Z"),
                        OffsetDateTime.parse("2026-06-09T06:30:01Z"),
                        true)),
                0,
                20,
                1,
                1));

        mockMvc.perform(get("/api/v1/billing/payments")
                        .principal(new TestingAuthenticationToken(userId.toString(), null)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].id").value(paymentId.toString()))
                .andExpect(jsonPath("$.items[0].receiptAvailable").value(true))
                .andExpect(jsonPath("$.totalElements").value(1));
        verify(billingService).getPayments(eq(userId), any());
    }

    @Test
    void getUsage_authenticatedUserReturnsPlanQuotaUsage() throws Exception {
        UUID userId = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();
        given(billingService.getUsage(userId)).willReturn(new BillingUsageResponse(
                tenantId,
                "pro",
                "ACTIVE",
                OffsetDateTime.parse("2026-06-09T00:00:00Z"),
                OffsetDateTime.parse("2026-07-09T00:00:00Z"),
                new Quotas(50000, 50000, 10000000000L, 5000000L, 500, 1),
                new Usage(
                        new UsageMetric(null, 50000L, null, "NOT_CONNECTED"),
                        new UsageMetric(null, 50000L, null, "NOT_CONNECTED"),
                        new UsageMetric(null, 10000000000L, null, "NOT_CONNECTED"),
                        new UsageMetric(null, 5000000L, null, "NOT_CONNECTED"),
                        new UsageMetric(null, 500L, null, "NOT_CONNECTED"),
                        new UsageMetric(null, 1L, null, "NOT_CONNECTED"))));

        mockMvc.perform(get("/api/v1/billing/usage")
                        .principal(new TestingAuthenticationToken(userId.toString(), null)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tenantId").value(tenantId.toString()))
                .andExpect(jsonPath("$.planCode").value("pro"))
                .andExpect(jsonPath("$.quotas.maxNotes").value(50000))
                .andExpect(jsonPath("$.usage.notes.source").value("NOT_CONNECTED"));
    }

    @Test
    void getReceipt_authenticatedUserReturnsReceiptMetadata() throws Exception {
        UUID userId = UUID.randomUUID();
        UUID paymentId = UUID.randomUUID();
        given(billingService.getReceipt(userId, paymentId)).willReturn(new BillingReceiptResponse(
                paymentId,
                "pi_test",
                "in_test",
                "https://invoice.stripe.test/in_test",
                "https://invoice.stripe.test/in_test.pdf",
                true));

        mockMvc.perform(get("/api/v1/billing/payments/{id}/receipt", paymentId)
                        .principal(new TestingAuthenticationToken(userId.toString(), null)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paymentId").value(paymentId.toString()))
                .andExpect(jsonPath("$.stripeInvoiceId").value("in_test"))
                .andExpect(jsonPath("$.available").value(true));
    }

    @Test
    void getPayments_invalidPageReturnsBadRequestProblem() throws Exception {
        UUID userId = UUID.randomUUID();

        mockMvc.perform(get("/api/v1/billing/payments?page=-1")
                        .principal(new TestingAuthenticationToken(userId.toString(), null)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("BILLING-010"));
    }
}
