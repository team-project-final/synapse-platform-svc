package com.synapse.platform.auth.controller;

import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.synapse.platform.auth.service.TenantSelfServiceService;
import java.util.UUID;
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
class TenantSelfServiceSecurityIntegrationTest {

    @Autowired
    private WebApplicationContext context;

    @MockitoBean
    private TenantSelfServiceService tenantSelfServiceService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders
                .webAppContextSetup(context)
                .apply(springSecurity())
                .build();
    }

    @Test
    void tenantSelfServiceApis_withoutAuthenticationReturnUnauthorized() throws Exception {
        UUID memberId = UUID.randomUUID();

        mockMvc.perform(get("/api/v1/tenants/me"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/v1/tenants/me/members"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(put("/api/v1/tenants/me")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(put("/api/v1/tenants/me/members/{userId}", memberId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"role\":\"member\"}"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(delete("/api/v1/tenants/me/members/{userId}", memberId))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/v1/tenants/me/invitations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"new@example.com\",\"role\":\"member\"}"))
                .andExpect(status().isUnauthorized());
    }
}
