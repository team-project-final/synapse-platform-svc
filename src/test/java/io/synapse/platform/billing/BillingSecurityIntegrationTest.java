package io.synapse.platform.billing;

import static org.mockito.Mockito.verify;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

@SpringBootTest
class BillingSecurityIntegrationTest {

    @Autowired
    private WebApplicationContext context;

    @MockitoBean
    private BillingService billingService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders
                .webAppContextSetup(context)
                .apply(springSecurity())
                .build();
    }

    @Test
    void checkout_withoutAuthenticationReturnsUnauthorized() throws Exception {
        mockMvc.perform(post("/api/v1/billing/checkout")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "planCode": "PRO",
                                  "successUrl": "https://app.example.com/success",
                                  "cancelUrl": "https://app.example.com/cancel"
                                }
                                """))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void webhook_withoutAuthenticationReachesController() throws Exception {
        byte[] payload = "{}".getBytes(java.nio.charset.StandardCharsets.UTF_8);

        mockMvc.perform(post("/api/v1/billing/webhooks")
                        .header("Stripe-Signature", "sig")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isOk());
        verify(billingService).handleWebhook(payload, "sig");
    }
}
