package com.synapse.platform.audit.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.synapse.platform.audit.service.AuditLogService;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

@SpringBootTest
class AuditLogControllerTest {

    @Autowired
    private WebApplicationContext context;

    @MockitoBean
    private AuditLogService auditLogService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders
                .webAppContextSetup(context)
                .apply(springSecurity())
                .build();
    }

    @Test
    void getAuditLogs_adminUser_shouldReturnOkAndDelegateFilters() throws Exception {
        UUID userId = UUID.randomUUID();
        given(auditLogService.getAuditLogs(eq("USER_REGISTERED"), eq(userId), any(Pageable.class)))
                .willReturn(new PageImpl<>(java.util.List.of()));

        mockMvc.perform(get("/api/v1/admin/audit-logs")
                        .param("action", "USER_REGISTERED")
                        .param("userId", userId.toString())
                        .with(user("admin").roles("ADMIN")))
                .andExpect(status().isOk());

        verify(auditLogService).getAuditLogs(eq("USER_REGISTERED"), eq(userId), any(Pageable.class));
    }

    @Test
    void getAuditLogs_nonAdminUser_shouldReturnForbidden() throws Exception {
        mockMvc.perform(get("/api/v1/admin/audit-logs")
                        .with(user("user").roles("USER")))
                .andExpect(status().isForbidden());
    }

    @Test
    void getAuditLogs_withoutAuthentication_shouldReturnUnauthorized() throws Exception {
        mockMvc.perform(get("/api/v1/admin/audit-logs"))
                .andExpect(status().isUnauthorized());
    }
}
