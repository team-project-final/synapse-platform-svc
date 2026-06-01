package com.synapse.platform.user.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.synapse.platform.global.exception.GlobalExceptionHandler;
import com.synapse.platform.user.dto.request.AdminUserSearchRequest;
import com.synapse.platform.user.service.AdminUserService;
import com.synapse.platform.user.service.InvalidUserStatusFilterException;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;

class AdminUserControllerTest {

    private final AdminUserService adminUserService = org.mockito.Mockito.mock(AdminUserService.class);
    private final ObjectMapper objectMapper = new ObjectMapper();
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        LocalValidatorFactoryBean validator = new LocalValidatorFactoryBean();
        validator.afterPropertiesSet();
        mockMvc = MockMvcBuilders
                .standaloneSetup(new AdminUserController(adminUserService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .setValidator(validator)
                .build();
    }

    @Test
    void listUsers_shouldDelegateSearchRequest() throws Exception {
        given(adminUserService.listUsers(any(AdminUserSearchRequest.class)))
                .willReturn(new PageImpl<>(java.util.List.of()));

        mockMvc.perform(get("/api/v1/admin/users")
                        .queryParam("q", "user")
                        .queryParam("status", "active"))
                .andExpect(status().isOk());

        verify(adminUserService).listUsers(new AdminUserSearchRequest("user", "active", 0, 20));
    }

    @Test
    void listUsers_invalidStatus_shouldReturnBadRequest() throws Exception {
        given(adminUserService.listUsers(any(AdminUserSearchRequest.class)))
                .willThrow(new InvalidUserStatusFilterException("invalid"));

        mockMvc.perform(get("/api/v1/admin/users")
                        .queryParam("status", "invalid"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void changeUserStatus_suspended_shouldDelegateSuspend() throws Exception {
        UUID adminId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();

        mockMvc.perform(put("/api/v1/admin/users/{id}/status", userId)
                        .principal(new TestingAuthenticationToken(adminId.toString(), null))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(java.util.Map.of("status", "suspended"))))
                .andExpect(status().isNoContent());

        verify(adminUserService).suspendUser(userId, adminId);
    }

    @Test
    void changeUserStatus_active_shouldDelegateActivate() throws Exception {
        UUID adminId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();

        mockMvc.perform(put("/api/v1/admin/users/{id}/status", userId)
                        .principal(new TestingAuthenticationToken(adminId.toString(), null))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(java.util.Map.of("status", "active"))))
                .andExpect(status().isNoContent());

        verify(adminUserService).activateUser(userId);
    }

    @Test
    void deleteUser_shouldDelegateDelete() throws Exception {
        UUID adminId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();

        mockMvc.perform(delete("/api/v1/admin/users/{id}", userId)
                        .principal(new TestingAuthenticationToken(adminId.toString(), null)))
                .andExpect(status().isNoContent());

        verify(adminUserService).deleteUser(userId, adminId);
    }

    @Test
    void deleteUser_malformedPrincipal_shouldReturnUnauthorized() throws Exception {
        UUID userId = UUID.randomUUID();

        mockMvc.perform(delete("/api/v1/admin/users/{id}", userId)
                        .principal(new TestingAuthenticationToken("not-a-uuid", null)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void changeUserStatus_invalidStatus_shouldReturnBadRequest() throws Exception {
        UUID adminId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();

        mockMvc.perform(put("/api/v1/admin/users/{id}/status", userId)
                        .principal(new TestingAuthenticationToken(adminId.toString(), null))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(java.util.Map.of("status", "deleted"))))
                .andExpect(status().isBadRequest());
    }
}
