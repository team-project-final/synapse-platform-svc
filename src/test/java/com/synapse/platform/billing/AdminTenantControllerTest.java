package com.synapse.platform.billing;

import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.synapse.platform.billing.controller.AdminTenantController;
import com.synapse.platform.billing.dto.response.AdminTenantResponse;
import com.synapse.platform.billing.service.AdminTenantService;
import com.synapse.platform.global.exception.GlobalExceptionHandler;
import java.time.OffsetDateTime;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;

class AdminTenantControllerTest {

    private final AdminTenantService adminTenantService = mock(AdminTenantService.class);
    private final ObjectMapper objectMapper = new ObjectMapper();
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        LocalValidatorFactoryBean validator = new LocalValidatorFactoryBean();
        validator.afterPropertiesSet();
        mockMvc = MockMvcBuilders
                .standaloneSetup(new AdminTenantController(adminTenantService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .setValidator(validator)
                .build();
    }

    @Test
    void listTenants_shouldReturnTenantPage() throws Exception {
        UUID tenantId = UUID.randomUUID();
        given(adminTenantService.listTenants(anyInt(), anyInt()))
                .willReturn(new PageImpl<>(java.util.List.of(new AdminTenantResponse(
                        tenantId,
                        "Acme",
                        "acme",
                        "free",
                        "active",
                        OffsetDateTime.parse("2026-05-28T00:00:00Z")))));

        mockMvc.perform(get("/api/v1/admin/tenants")
                        .queryParam("page", "1")
                        .queryParam("size", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].id").value(tenantId.toString()));
        verify(adminTenantService).listTenants(1, 10);
    }

    @Test
    void changeTenantStatus_validStatus_shouldDelegate() throws Exception {
        UUID tenantId = UUID.randomUUID();

        mockMvc.perform(put("/api/v1/admin/tenants/{id}/status", tenantId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(java.util.Map.of("status", "suspended"))))
                .andExpect(status().isNoContent());

        verify(adminTenantService).changeTenantStatus(tenantId, "suspended");
    }

    @Test
    void changeTenantStatus_invalidStatus_shouldReturnBadRequest() throws Exception {
        UUID tenantId = UUID.randomUUID();

        mockMvc.perform(put("/api/v1/admin/tenants/{id}/status", tenantId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(java.util.Map.of("status", "deleted"))))
                .andExpect(status().isBadRequest());
    }
}
